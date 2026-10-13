package com.nedal.cinejoyremote.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.nedal.cinejoyremote.presentation.EventsViewModel
import com.nedal.cinejoyremote.presentation.LibraryViewModel
import com.nedal.cinejoyremote.presentation.PcToolsViewModel
import com.nedal.cinejoyremote.presentation.ServiceLocator
import com.nedal.cinejoyremote.presentation.ShowDetailViewModel
import com.nedal.cinejoyremote.notifications.EventNotifier
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
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "downloads"
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var backAt by remember { mutableLongStateOf(0L) }

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
    val online = remember(devices) { devices.any { pcOnline(it.lastSeen) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "▶",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "CineJoy Remote",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(7.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (online) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline,
                                        ),
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    if (online) "PC online" else "PC offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.Rounded.Logout, contentDescription = "Sign out")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                TabItem(nav, route, "downloads", "Downloads", Icons.Rounded.Download)
                TabItem(nav, route, "library", "Library", Icons.Rounded.VideoLibrary)
                TabItem(nav, route, "events", "Activity", Icons.Rounded.History)
                TabItem(nav, route, "settings", "Settings", Icons.Rounded.Settings)
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "downloads",
            modifier = Modifier.padding(padding),
        ) {
            composable("downloads") {
                val vm: DownloadsViewModel = viewModel(factory = factory)
                DownloadsScreen(devicesVm, vm, snackbar)
            }
            composable("library") {
                val vm: LibraryViewModel = viewModel(factory = factory)
                LibraryScreen(devicesVm, vm, factory, snackbar) { showId ->
                    nav.navigate("show/${Uri.encode(showId)}")
                }
            }
            composable("show/{showId}") { entry ->
                val id = Uri.decode(entry.arguments?.getString("showId") ?: "")
                val vm: ShowDetailViewModel = viewModel(factory = factory)
                ShowDetailScreen(devicesVm, vm, id, snackbar) { nav.popBackStack() }
            }
            composable("events") {
                val vm: EventsViewModel = viewModel(factory = factory)
                EventsScreen(vm)
            }
            composable("settings") {
                SettingsScreen(authVm, viewModel(factory = factory))
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(
    nav: androidx.navigation.NavHostController,
    current: String,
    route: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    // The show detail page belongs to the Library tab visually.
    val selected = current == route || (route == "library" && current.startsWith("show/"))
    NavigationBarItem(
        selected = selected,
        onClick = {
            nav.navigate(route) {
                popUpTo("downloads") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    )
}
