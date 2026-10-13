package com.nedal.cinejoyremote.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.nedal.cinejoyremote.presentation.ServiceLocator
import com.nedal.cinejoyremote.notifications.EventNotifier
import kotlinx.coroutines.flow.catch

@Composable
fun CinejoyApp(authVm: AuthViewModel = viewModel()) {
    val authState by authVm.authState.collectAsState(initial = AuthState.Loading)
    when (authState) {
        is AuthState.Loading -> Text("Loading…")
        is AuthState.SignedOut -> AuthScreen(authVm)
        is AuthState.SignedIn -> MainShell(onSignOut = authVm::signOut, authVm = authVm)
    }
}

@Composable
fun MainShell(onSignOut: () -> Unit, authVm: AuthViewModel) {
    val nav = rememberNavController()
    val devicesVm: DevicesViewModel = viewModel()
    val factory = remember(devicesVm) { AppViewModelFactory(devicesVm) }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "downloads"
    val context = LocalContext.current

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

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            androidx.compose.material3.TopAppBar(
                title = { Text("CineJoy Remote") },
                actions = { TextButton(onClick = onSignOut) { Text("Sign out") } },
            )
        },
        bottomBar = {
            NavigationBar {
                for ((r, label) in listOf("downloads" to "Downloads", "library" to "Library", "events" to "Activity", "settings" to "Settings")) {
                    NavigationBarItem(
                        selected = route == r,
                        onClick = { nav.navigate(r) { launchSingleTop = true } },
                        icon = {},
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = "downloads", modifier = Modifier.padding(padding)) {
            composable("downloads") {
                val vm: DownloadsViewModel = viewModel(factory = factory)
                DownloadsScreen(devicesVm, vm)
            }
            composable("library") {
                val vm: LibraryViewModel = viewModel(factory = factory)
                LibraryScreen(devicesVm, vm)
            }
            composable("events") {
                val vm: EventsViewModel = viewModel(factory = factory)
                EventsScreen(vm)
            }
            composable("settings") {
                SettingsScreen(authVm)
            }
        }
    }
}
