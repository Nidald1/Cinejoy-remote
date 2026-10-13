package com.nedal.cinejoyremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nedal.cinejoyremote.presentation.AuthViewModel
import com.nedal.cinejoyremote.presentation.UpdateUiState
import com.nedal.cinejoyremote.presentation.UpdateViewModel
import com.nedal.cinejoyremote.domain.AuthState

@Composable
fun SettingsScreen(authVm: AuthViewModel, updateVm: UpdateViewModel = viewModel()) {
    val context = LocalContext.current
    val authState by authVm.authState.collectAsState(initial = AuthState.Loading)
    val updateState by updateVm.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Account", style = MaterialTheme.typography.titleSmall)
                Text(
                    (authState as? AuthState.SignedIn)?.email?.let { "Signed in as $it" } ?: "…",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = authVm::signOut) { Text("Sign out") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("App updates", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Installed: v${updateVm.versionName(context)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                when (val s = updateState) {
                    is UpdateUiState.Idle -> Button(onClick = { updateVm.check(context) }) {
                        Text("Check for updates")
                    }
                    is UpdateUiState.Checking -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    is UpdateUiState.UpToDate -> {
                        Text("You're on the latest version.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = updateVm::clear) { Text("dismiss") }
                    }
                    is UpdateUiState.Available -> {
                        Text("Update ${s.info.version} available.", style = MaterialTheme.typography.bodySmall)
                        if (s.info.notes.isNotBlank()) {
                            Text(
                                s.info.notes.take(500),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = { updateVm.downloadAndInstall(context, s.info) }) {
                            Text("Download & install")
                        }
                    }
                    is UpdateUiState.Downloading -> {
                        Text("Downloading update…", style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    is UpdateUiState.Error -> {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { updateVm.check(context) }) { Text("retry") }
                    }
                }
            }
        }
    }
}
