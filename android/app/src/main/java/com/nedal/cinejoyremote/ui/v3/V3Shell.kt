package com.nedal.cinejoyremote.ui.v3

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Tune
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nedal.cinejoyremote.domain.AuthState
import com.nedal.cinejoyremote.notifications.EventNotifier
import com.nedal.cinejoyremote.presentation.AppViewModelFactory
import com.nedal.cinejoyremote.presentation.AuthViewModel
import com.nedal.cinejoyremote.presentation.ConnectionViewModel
import com.nedal.cinejoyremote.presentation.DevicesViewModel
import com.nedal.cinejoyremote.presentation.DownloadsViewModel
import com.nedal.cinejoyremote.presentation.LibraryViewModel
import com.nedal.cinejoyremote.presentation.PcToolsViewModel
import com.nedal.cinejoyremote.presentation.RequestsViewModel
import com.nedal.cinejoyremote.presentation.ServiceLocator
import com.nedal.cinejoyremote.presentation.ShowDetailViewModel
import com.nedal.cinejoyremote.presentation.UpdateViewModel
import com.nedal.cinejoyremote.share.SharedLinkInbox
import com.nedal.cinejoyremote.share.TmdbRef
import com.nedal.cinejoyremote.share.parseTmdbRef
import com.nedal.cinejoyremote.ui.AuthScreen
import com.nedal.cinejoyremote.ui.theme.Motion
import com.nedal.cinejoyremote.ui.LibrarySearchScreen
import com.nedal.cinejoyremote.ui.SeerrSession
import com.nedal.cinejoyremote.ui.SharedTitleScreen
import com.nedal.cinejoyremote.ui.ShowDetailScreen
import com.nedal.cinejoyremote.ui.UpdateDialog
import com.nedal.cinejoyremote.ui.v3.components.BarVisibility
import com.nedal.cinejoyremote.ui.v3.components.NavSpec
import com.nedal.cinejoyremote.ui.v3.components.V3BottomNav
import com.nedal.cinejoyremote.ui.v3.components.V3TopBar
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** Entry point of the v3 redesign — wired into MainActivity. */
@Composable
fun V3App(authVm: AuthViewModel = viewModel()) {
    val authState by authVm.authState.collectAsState(initial = AuthState.Loading)
    when (authState) {
        is AuthState.Loading -> Box(Modifier) // brief blank; replaced in Phase 6
        is AuthState.SignedOut -> AuthScreen(authVm) // v3 auth lands in Phase 6
        is AuthState.SignedIn -> V3MainShell(onSignOut = authVm::signOut, authVm = authVm)
    }
}

// PC heartbeats once a minute; older than 2.5 min reads as away (same window
// ConnectionViewModel uses, so the dot can never contradict the diagnostics).
private fun pcOnline(lastSeen: String?): Boolean {
    if (lastSeen.isNullOrBlank()) return false
    val at = runCatching { Instant.parse(lastSeen) }.getOrNull()
        ?: runCatching { java.time.OffsetDateTime.parse(lastSeen).toInstant() }.getOrNull()
        ?: return false
    return Duration.between(at, Instant.now()).seconds < 150
}

private fun pcAgeLabel(lastSeen: String?): String? {
    if (lastSeen.isNullOrBlank()) return null
    val at = runCatching { Instant.parse(lastSeen) }.getOrNull()
        ?: runCatching { java.time.OffsetDateTime.parse(lastSeen).toInstant() }.getOrNull()
        ?: return null
    val s = Duration.between(at, Instant.now()).seconds
    return when {
        s < 90 -> "seen ${s}s ago"
        s < 5400 -> "seen ${s / 60}m ago"
        else -> "seen ${s / 3600}h ago"
    }
}

private val ACTIVE_TASK_STATES = setOf("queued", "resolving", "downloading", "paused", "extracting")

@Composable
fun V3MainShell(onSignOut: () -> Unit, authVm: AuthViewModel) {
    val nav = rememberNavController()
    val devicesVm: DevicesViewModel = viewModel()
    val factory = remember(devicesVm) { AppViewModelFactory(devicesVm) }
    val requestsVm: RequestsViewModel = viewModel(factory = factory)
    val downloadsVm: DownloadsViewModel = viewModel(factory = factory)
    val pendingRequests by requestsVm.requests.collectAsState()
    val tasks by downloadsVm.tasks.collectAsState()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "library"
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var backAt by remember { mutableLongStateOf(0L) }

    // Bars are pure scroll OBSERVERS (always Offset.Zero) — lists keep every
    // pixel of their scroll; the bars just watch deltas and slide away.
    var topBarVisible by remember { mutableStateOf(true) }
    var topAcc by remember { mutableStateOf(0f) }
    var bottomBarVisible by remember { mutableStateOf(true) }
    var bottomAcc by remember { mutableStateOf(0f) }
    val topConn = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val dy = available.y
                if (dy == 0f) return Offset.Zero
                if ((dy > 0f) != (topAcc > 0f)) topAcc = 0f
                topAcc += dy
                if (topAcc <= -48f) { topBarVisible = false; topAcc = 0f }
                else if (topAcc >= 160f) { topBarVisible = true; topAcc = 0f }
                return Offset.Zero
            }
        }
    }
    val bottomConn = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val dy = available.y
                if (dy == 0f) return Offset.Zero
                if ((dy > 0f) != (bottomAcc > 0f)) bottomAcc = 0f
                bottomAcc += dy
                if (bottomAcc <= -48f) { bottomBarVisible = false; bottomAcc = 0f }
                else if (bottomAcc >= 160f) { bottomBarVisible = true; bottomAcc = 0f }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(route) {
        topBarVisible = true; topAcc = 0f
        bottomBarVisible = true; bottomAcc = 0f
    }

    // Update flow: silent launch check; popup reused from the old UI until
    // the v3 dialog lands in Phase 6.
    val updateVm: UpdateViewModel = viewModel()
    val updateState by updateVm.state.collectAsState()
    val updatePopup by updateVm.popupVisible.collectAsState()
    LaunchedEffect(Unit) { updateVm.autoCheck(context) }

    // Back: detail/search/share -> pop; root tab -> library; library -> the
    // "press back again to exit" guard. (Seerr adds its own WebView back
    // handler in Phase 3.)
    BackHandler(enabled = true) {
        when {
            nav.previousBackStackEntry != null -> nav.popBackStack()
            route == "library" -> {
                val now = System.currentTimeMillis()
                if (now - backAt < 2200) {
                    (context as? Activity)?.finish()
                } else {
                    backAt = now
                    scope.launch { snackbar.showSnackbar("Press back again to exit") }
                }
            }
            else -> nav.navigate("library") {
                popUpTo("library") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Android 13+ notification grant + local notification channel.
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

    // Realtime cj_events inserts -> local notifications, once per event.
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
    LaunchedEffect(devices) { devicesVm.autoSelect(devices) }
    val online = remember(devices) { devices.any { pcOnline(it.lastSeen) } }
    val lastSeenAge = remember(devices) {
        devices.mapNotNull { it.lastSeen }.maxByOrNull { it }?.let { pcAgeLabel(it) }
    }

    val shellAuth by authVm.authState.collectAsState(initial = AuthState.Loading)
    val signedInEmail = (shellAuth as? AuthState.SignedIn)?.email
    var profileOpen by remember { mutableStateOf(false) }

    val fullscreen = route == "seerr" // Seerr owns the whole screen (spec §3.5)
    val activeDownloads = remember(tasks) { tasks.count { it.state in ACTIVE_TASK_STATES } }

    Scaffold(
        modifier = Modifier
            .nestedScroll(topConn)
            .nestedScroll(bottomConn),
        topBar = {
            if (!fullscreen) {
                BarVisibility(visible = topBarVisible, fromBottom = false) {
                    V3TopBar(
                        title = when (route) {
                            "downloads" -> "Downloads"
                            "notifications" -> "Notifications"
                            "settings" -> "Settings"
                            else -> "Library"
                        },
                        online = online,
                        avatarLetter = (signedInEmail?.firstOrNull() ?: '●').uppercaseChar(),
                        onAvatar = { profileOpen = true },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!fullscreen) {
                BarVisibility(visible = bottomBarVisible, fromBottom = true) {
                    V3BottomNav(
                        items = listOf(
                            NavSpec("library", "Library", Icons.Rounded.Home),
                            NavSpec("downloads", "Downloads", Icons.Rounded.Download, badge = activeDownloads),
                            NavSpec("notifications", "Notifications", Icons.Rounded.Notifications, dot = pendingRequests.isNotEmpty()),
                            NavSpec("seerr", "Seerr", Icons.Rounded.Language),
                            NavSpec("settings", "Settings", Icons.Rounded.Tune),
                        ),
                        current = route,
                        onSelect = { target ->
                            nav.navigate(target) {
                                popUpTo("library") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        // A link shared from any app opens the details page, even cold start.
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

        NavHost(
            navController = nav,
            startDestination = "library",
            enterTransition = { Motion.screenEnter },
            exitTransition = { Motion.screenExit },
            popEnterTransition = { Motion.screenPopEnter },
            popExitTransition = { Motion.screenPopExit },
            modifier = Modifier.padding(padding),
        ) {
            composable("library") {
                val libraryVm: LibraryViewModel = viewModel(factory = factory)
                V3LibraryScreen(
                    devicesVm = devicesVm,
                    vm = libraryVm,
                    snackbar = snackbar,
                    onOpenShow = { showId -> nav.navigate("show/${Uri.encode(showId)}") },
                    onOpenSearch = { nav.navigate("search") },
                )
            }
            composable("downloads") {
                V3DownloadsScreen(
                    devicesVm = devicesVm,
                    vm = downloadsVm,
                    snackbar = snackbar,
                    onBrowseLibrary = {
                        nav.navigate("library") {
                            popUpTo("library") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenSeerr = {
                        nav.navigate("seerr") {
                            popUpTo("library") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable("notifications") {
                V3NotificationsScreen(
                    vm = requestsVm,
                    snackbar = snackbar,
                    onAcceptedShow = { showId ->
                        nav.navigate("show/${Uri.encode(showId)}").let { }
                    },
                    onOpenSeerr = {
                        nav.navigate("seerr") {
                            popUpTo("library") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable("search") {
                val vm: LibraryViewModel = viewModel(factory = factory)
                V3SearchScreen(devicesVm, vm) { showId ->
                    nav.navigate("show/${Uri.encode(showId)}")
                }
            }
            composable("show/{showId}") { entry ->
                val id = Uri.decode(entry.arguments?.getString("showId") ?: "")
                val vm: ShowDetailViewModel = viewModel(factory = factory)
                V3DetailScreen(devicesVm, vm, id, snackbar) { nav.popBackStack() }
            }
            composable("shared/{type}/{tmdbId}") { entry ->
                val type = entry.arguments?.getString("type") ?: "movie"
                val id = entry.arguments?.getString("tmdbId")?.toLongOrNull() ?: 0L
                SharedTitleScreen(devicesVm, TmdbRef(type, id), snackbar)
            }
            composable("seerr") {
                // ONE WebView for the app's lifetime: Activity-scoped session
                // VM, reused across tab switches and rotation (spec §3.5.4).
                val seerrSession: SeerrSession = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(
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
                V3SeerrScreen(
                    vm = requestsVm,
                    snackbar = snackbar,
                    session = seerrSession,
                    selectedDevice = selectedDevice,
                    onGoHome = {
                        nav.navigate("library") {
                            popUpTo("library") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable("settings") {
                val pcToolsVm: PcToolsViewModel = viewModel(factory = factory)
                val connectionVm: ConnectionViewModel = viewModel(factory = factory)
                V3SettingsScreen(
                    authVm = authVm,
                    pcTools = pcToolsVm,
                    updateVm = updateVm,
                    devicesVm = devicesVm,
                    connection = connectionVm,
                )
            }
        }
    }

    if (profileOpen) {
        V3ProfileSheet(
            email = signedInEmail,
            devices = devices.map { d ->
                Triple(d.name, pcOnline(d.lastSeen), d.deviceId == selectedDevice)
            },
            onSelectDevice = { name ->
                devices.firstOrNull { it.name == name }?.let { devicesVm.select(it.deviceId) }
                profileOpen = false
            },
            onSignOut = { profileOpen = false; onSignOut() },
            onDismiss = { profileOpen = false },
        )
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

@Composable
internal fun V3Placeholder(title: String, subtitle: String) {
    val c = v3
    Box(Modifier.padding(V3Space.gutter)) {
        androidx.compose.material3.Text(
            "$title — $subtitle",
            style = V3Type.body14,
            color = c.textTertiary,
        )
    }
}
