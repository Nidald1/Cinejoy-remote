package com.nedal.cinejoyremote.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nedal.cinejoyremote.domain.AuthState
import com.nedal.cinejoyremote.presentation.AppViewModelFactory
import com.nedal.cinejoyremote.presentation.AuthViewModel
import com.nedal.cinejoyremote.presentation.DevicesViewModel
import com.nedal.cinejoyremote.presentation.DownloadsViewModel
import com.nedal.cinejoyremote.presentation.RequestsViewModel
import com.nedal.cinejoyremote.presentation.LibraryViewModel
import com.nedal.cinejoyremote.presentation.PcToolsViewModel
import com.nedal.cinejoyremote.presentation.ServiceLocator
import com.nedal.cinejoyremote.presentation.ShowDetailViewModel
import com.nedal.cinejoyremote.presentation.UpdateViewModel
import com.nedal.cinejoyremote.notifications.EventNotifier
import com.nedal.cinejoyremote.share.SharedLinkInbox
import com.nedal.cinejoyremote.ui.theme.Motion
import com.nedal.cinejoyremote.share.TmdbRef
import com.nedal.cinejoyremote.share.parseTmdbRef
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun CinejoyApp(authVm: AuthViewModel = viewModel()) {
    val authState by authVm.authState.collectAsState(initial = AuthState.Loading)
    when (authState) {
        is AuthState.Loading -> Text("Loading…")
        is AuthState.SignedOut -> AuthScreen(authVm)
        is AuthState.SignedIn -> MainShell(onSignOut = authVm::signOut, authVm = authVm)
    }
}

// The PC heartbeats once a minute; anything older than 2.5 minutes reads as away.
// PostgREST renders timestamptz as "...+00:00" which Instant.parse rejects —
// OffsetDateTime handles both that and the plain "Z" form.
private fun pcAgeLabel(lastSeen: String?): String? {
    if (lastSeen.isNullOrBlank()) return null
    val at = runCatching { Instant.parse(lastSeen) }.getOrNull()
        ?: runCatching { java.time.OffsetDateTime.parse(lastSeen).toInstant() }.getOrNull()
        ?: return null
    val s = java.time.Duration.between(at, Instant.now()).seconds
    return when {
        s < 90 -> "seen ${s}s ago"
        s < 5400 -> "seen ${s / 60}m ago"
        else -> "seen ${s / 3600}h ago"
    }
}

private fun pcOnline(lastSeen: String?): Boolean {
    if (lastSeen.isNullOrBlank()) return false
    val at = runCatching { Instant.parse(lastSeen) }.getOrNull()
        ?: runCatching { java.time.OffsetDateTime.parse(lastSeen).toInstant() }.getOrNull()
        ?: return false
    return java.time.Duration.between(at, Instant.now()).seconds < 150
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainShell(onSignOut: () -> Unit, authVm: AuthViewModel) {
    val nav = rememberNavController()
    val devicesVm: DevicesViewModel = viewModel()
    val factory = remember(devicesVm) { AppViewModelFactory(devicesVm) }
    // One RequestsViewModel for the whole shell: the Notifications tab badge
    // needs the pending count even while another tab is open, and the
    // notifications + Seerr pages should share busy/notice state anyway.
    val requestsVm: RequestsViewModel = viewModel(factory = factory)
    val pendingRequests by requestsVm.requests.collectAsState()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "downloads"
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var backAt by remember { mutableLongStateOf(0L) }

    // Hide-on-scroll bars. These connections are pure OBSERVERS (they always
    // return Offset.Zero), so lists keep every pixel of their scroll — the
    // bars just watch child scroll deltas and slide out of the way.
    //
    // (The previous wiring used TopAppBarDefaults.enterAlwaysScrollBehavior
    // for the top bar. That behavior's TopAppBarState.heightOffsetLimit is
    // negative-infinity until a real M3 TopAppBar measures itself and sets a
    // limit; our slim custom bar never did, so the behavior swallowed EVERY
    // scroll delta forever and no screen in the app could scroll at all.)
    var topBarVisible by remember { mutableStateOf(true) }
    var topScrollAcc by remember { mutableStateOf(0f) }
    var bottomBarVisible by remember { mutableStateOf(true) }
    var bottomScrollAcc by remember { mutableStateOf(0f) }
    val topBarConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val dy = available.y
                if (dy == 0f) return Offset.Zero
                if ((dy > 0f) != (topScrollAcc > 0f)) topScrollAcc = 0f
                topScrollAcc += dy
                if (topScrollAcc <= -48f) { topBarVisible = false; topScrollAcc = 0f }
                else if (topScrollAcc >= 160f) { topBarVisible = true; topScrollAcc = 0f }
                return Offset.Zero
            }
        }
    }

    // Design-v2 chrome: the slim bar reads its title + contextual icons from
    // here (screens publish via ProvideTopBar), and the avatar opens the
    // profile sheet that took over sign-out and the PC picker.
    val topBar = remember { TopBarController() }
    var profileOpen by remember { mutableStateOf(false) }
    val shellAuth by authVm.authState.collectAsState(initial = AuthState.Loading)
    val signedInEmail = (shellAuth as? AuthState.SignedIn)?.email
    val bottomBarConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val dy = available.y
                if (dy == 0f) return Offset.Zero
                // Direction change resets the accumulator so a small wiggle
                // can't flip the bar both ways.
                if ((dy > 0f) != (bottomScrollAcc > 0f)) bottomScrollAcc = 0f
                bottomScrollAcc += dy
                // Asymmetric on purpose: hiding is a deliberate scroll-down
                // (48px), but showing needs a much longer pull because a short
                // list hitting its end bounces back with a positive dy that
                // would otherwise flip the bar right back open.
                if (bottomScrollAcc <= -48f) { bottomBarVisible = false; bottomScrollAcc = 0f }
                else if (bottomScrollAcc >= 160f) { bottomBarVisible = true; bottomScrollAcc = 0f }
                return Offset.Zero
            }
        }
    }
    // A freshly opened screen always shows the bars again.
    LaunchedEffect(route) {
        topBarVisible = true; topScrollAcc = 0f
        bottomBarVisible = true; bottomScrollAcc = 0f
    }

    // Update flow: silent check on launch; when a newer release exists on
    // GitHub (and wasn't dismissed for that exact version) the popup shows.
    val updateVm: UpdateViewModel = viewModel()
    val updateState by updateVm.state.collectAsState()
    val updatePopup by updateVm.popupVisible.collectAsState()
    LaunchedEffect(Unit) { updateVm.autoCheck(context) }

    // Standard Android back model:
    //   detail page      -> one step back (show detail -> library)
    //   any other tab    -> home tab (Downloads)
    //   home tab         -> "press back again" toast, second press exits
    BackHandler(enabled = true) {
        when {
            nav.previousBackStackEntry != null -> nav.popBackStack()
            route == "downloads" -> {
                val now = System.currentTimeMillis()
                if (now - backAt < 2200) {
                    (context as? Activity)?.finish()
                } else {
                    backAt = now
                    scope.launch { snackbar.showSnackbar("Press back again to exit") }
                }
            }
            else -> nav.navigate("downloads") { launchSingleTop = true }
        }
    }

    // Android 13+ needs an explicit grant before any notification can post.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        EventNotifier.ensureChannel(context)
    }

    // Supabase-only alert path: Realtime INSERTs on cj_events for the selected
    // PC become local notifications. Realtime emits new rows only (no backfill),
    // and the seen-set guards the resubscribe edge — each event alerts once.
    val selectedDevice by devicesVm.selectedDeviceId.collectAsState()
    LaunchedEffect(selectedDevice) {
        val deviceId = selectedDevice ?: return@LaunchedEffect
        val seen = LinkedHashSet<Long>()
        ServiceLocator.mirrorRepository.observeEventInserts(deviceId)
            .catch { }
            .collect { event ->
                if (!seen.add(event.id)) return@collect
                if (seen.size > 200) seen.remove(seen.first())
                runCatching { EventNotifier.show(context, event) }
            }
    }

    val devices by devicesVm.devices.collectAsState()
    // Pair with the PC here, at app level — this used to live only in the
    // Downloads/Library screens, so opening straight on Seerr or Notifications
    // (or sharing into the app) sent commands with no PC selected.
    LaunchedEffect(devices) { devicesVm.autoSelect(devices) }
    val online = remember(devices) { devices.any { pcOnline(it.lastSeen) } }
    // Freshest heartbeat across devices, as an age label for the status chip.
    val lastSeenAge = remember(devices) {
        devices.mapNotNull { it.lastSeen }.maxByOrNull { it }?.let { pcAgeLabel(it) }
    }

    // Screens publish their top-bar icons through the provided controller.
    CompositionLocalProvider(LocalTopBarController provides topBar) {
    Scaffold(
        // Both bar controllers sit above the content so every screen's
        // LazyColumn/LazyVerticalGrid drives them through nested scroll.
        modifier = Modifier
            .nestedScroll(topBarConnection)
            .nestedScroll(bottomBarConnection),
        // Seerr is TRULY full screen — the app's own chrome (top bar too)
        // leaves while the tab is open; only system insets remain.
        topBar = {
            if (route != "seerr") {
                AnimatedVisibility(
                    visible = topBarVisible,
                    enter = slideInVertically { -it } + expandVertically() + fadeIn(),
                    exit = slideOutVertically { -it } + shrinkVertically() + fadeOut(),
                ) {
                    CineTopBar(
                        controller = topBar,
                        online = online,
                        avatarLetter = (signedInEmail?.firstOrNull() ?: '●').uppercaseChar(),
                        onAvatarClick = { profileOpen = true },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            // Slide + shrink so the content padding animates with the bar
            // instead of snapping a gap closed.
            AnimatedVisibility(
                visible = bottomBarVisible,
                enter = slideInVertically { it } + expandVertically() + fadeIn(),
                exit = slideOutVertically { it } + shrinkVertically() + fadeOut(),
            ) {
                // The Seerr tab is truly full screen — the app's own bar
                // leaves entirely while it is open (Seerr's web nav is the
                // navigation there), then returns on any other tab.
                if (route != "seerr") {
                    SlimBottomNav(
                        items = listOf(
                            TabSpec("downloads", "Downloads", Icons.Rounded.Download),
                            TabSpec("library", "Library", Icons.Rounded.VideoLibrary),
                            TabSpec("notifications", "Alerts", Icons.Rounded.Notifications, pendingRequests.size),
                            TabSpec("seerr", "Seerr", Icons.Rounded.Language),
                            TabSpec("settings", "Settings", Icons.Rounded.Settings),
                        ),
                        current = route,
                        onSelect = { target ->
                            nav.navigate(target) {
                                popUpTo("downloads") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        // A link shared from SeriesGuide (or any app) opens the details page.
        // Held in the inbox until the shell is up, so it also works from a cold start.
        val sharedText by SharedLinkInbox.pending.collectAsState()
        LaunchedEffect(sharedText) {
            val text = sharedText ?: return@LaunchedEffect
            SharedLinkInbox.consume()
            val ref = parseTmdbRef(text)
            if (ref == null) {
                snackbar.showSnackbar("The shared text has no TMDb movie or series link")
            } else {
                nav.navigate("shared/${ref.type}/${ref.id}") { launchSingleTop = true }
            }
        }

        // Quiet cross-fades instead of the default slide — the design
        // system's motion tokens (theme/Motion.kt).
        NavHost(
            navController = nav,
            startDestination = "downloads",
            enterTransition = { Motion.screenEnter },
            exitTransition = { Motion.screenExit },
            popEnterTransition = { Motion.screenPopEnter },
            popExitTransition = { Motion.screenPopExit },
            modifier = Modifier.padding(padding),
        ) {
            composable("downloads") {
                val vm: DownloadsViewModel = viewModel(factory = factory)
                DownloadsScreen(devicesVm, vm, snackbar)
            }
            composable("library") {
                val vm: LibraryViewModel = viewModel(factory = factory)
                LibraryScreen(
                    devicesVm, vm, factory, snackbar,
                    onOpenShow = { showId -> nav.navigate("show/${Uri.encode(showId)}") },
                    onOpenSearch = { nav.navigate("search") },
                )
            }
            composable("search") {
                val vm: LibraryViewModel = viewModel(factory = factory)
                LibrarySearchScreen(devicesVm, vm) { showId ->
                    nav.navigate("show/${Uri.encode(showId)}")
                }
            }
            composable("show/{showId}") { entry ->
                val id = Uri.decode(entry.arguments?.getString("showId") ?: "")
                val vm: ShowDetailViewModel = viewModel(factory = factory)
                ShowDetailScreen(devicesVm, vm, id, snackbar) { nav.popBackStack() }
            }
            composable("shared/{type}/{tmdbId}") { entry ->
                val type = entry.arguments?.getString("type") ?: "movie"
                val id = entry.arguments?.getString("tmdbId")?.toLongOrNull() ?: 0L
                SharedTitleScreen(devicesVm, TmdbRef(type, id), snackbar)
            }
            composable("notifications") {
                NotificationsScreen(
                    requestsVm,
                    snackbar,
                    onAcceptedShow = { showId ->
                        // Accepted: jump to the title and let its page open the
                        // sources window right away (see DetailPickInbox).
                        DetailPickInbox.offer(showId)
                        nav.navigate("show/$showId")
                    },
                    // Seerr is a tab now — same single-top tab semantics.
                    onOpenSeerr = {
                        nav.navigate("seerr") {
                            popUpTo("downloads") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable("seerr") {
                // ONE WebView for the app's lifetime (B4.4): the session is an
                // Activity-scoped ViewModel, so tab switches and rotation reuse
                // the same live page instead of reloading it.
                val seerrSession: SeerrSession = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : androidx.lifecycle.ViewModel> create(
                            modelClass: Class<T>,
                            extras: CreationExtras,
                        ): T {
                            val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                                as android.app.Application
                            val handle = extras.createSavedStateHandle()
                            @Suppress("UNCHECKED_CAST")
                            return SeerrSession(app, handle) as T
                        }
                    },
                )
                SeerrScreen(
                    requestsVm,
                    snackbar,
                    seerrSession,
                    selectedDevice = selectedDevice,
                    onGoHome = { nav.navigate("downloads") { launchSingleTop = true } },
                )
            }
            composable("settings") {
                SettingsScreen(
                    authVm,
                    viewModel(factory = factory),
                    updateVm,
                    devicesVm,
                    viewModel(factory = factory),
                )
            }
        }
    }
    } // CompositionLocalProvider(LocalTopBarController)

    if (profileOpen) {
        var confirmSignOut by remember { mutableStateOf(false) }
        ProfileSheet(
            email = signedInEmail,
            devices = devices.map { it.name to pcOnline(it.lastSeen) },
            selectedDevice = selectedDevice?.let { id -> devices.firstOrNull { it.deviceId == id }?.name },
            onSelectDevice = { name ->
                devices.firstOrNull { it.name == name }?.let { devicesVm.select(it.deviceId) }
                profileOpen = false
            },
            onSignOut = { confirmSignOut = true },
            onDismiss = { profileOpen = false },
        )
        if (confirmSignOut) {
            AlertDialog(
                onDismissRequest = { confirmSignOut = false },
                title = { Text("Sign out?") },
                text = { Text("You will need the account email and password to sign back in.") },
                confirmButton = {
                    TextButton(onClick = { confirmSignOut = false; profileOpen = false; onSignOut() }) {
                        Text("Sign out", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
                },
            )
        }
    }

    if (updatePopup) {
        UpdateDialog(
            state = updateState,
            installedVersion = updateVm.versionName(context),
            onDownload = { info -> updateVm.downloadAndInstall(context, info) },
            onCancel = { info -> updateVm.cancelDownload(info) },
            onDismiss = { version -> updateVm.dismissPopup(context, version) },
            onRetry = { updateVm.check(context) },
        )
    }
}

