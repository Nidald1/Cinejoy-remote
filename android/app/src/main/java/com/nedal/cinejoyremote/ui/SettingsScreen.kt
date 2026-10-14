package com.nedal.cinejoyremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nedal.cinejoyremote.ui.theme.ThemeController
import com.nedal.cinejoyremote.ui.theme.ThemeMode
import com.nedal.cinejoyremote.domain.AuthState
import com.nedal.cinejoyremote.presentation.AuthViewModel
import com.nedal.cinejoyremote.presentation.ConnectionViewModel
import com.nedal.cinejoyremote.presentation.DevicesViewModel
import com.nedal.cinejoyremote.presentation.PcToolsViewModel
import com.nedal.cinejoyremote.presentation.UpdateUiState
import com.nedal.cinejoyremote.presentation.UpdateViewModel
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

@Composable
fun SettingsScreen(
    authVm: AuthViewModel,
    pcTools: PcToolsViewModel,
    updateVm: UpdateViewModel,
    devicesVm: DevicesViewModel,
    connection: ConnectionViewModel,
) {
    val context = LocalContext.current
    val authState by authVm.authState.collectAsState(initial = AuthState.Loading)
    val updateState by updateVm.state.collectAsState()

    val busy by pcTools.busy.collectAsState()
    val logText by pcTools.log.collectAsState()
    val toolError by pcTools.error.collectAsState()
    val toolNotice by pcTools.notice.collectAsState()
    val themeMode = ThemeController.mode
    val detailStyle = DetailStyleController.mode

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- appearance: dark / light / OLED
        SectionCard(title = "Appearance") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in ThemeMode.entries) {
                    val sel = themeMode == m
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (sel) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { ThemeController.set(context, m) },
                    ) {
                        Text(
                            when (m) {
                                ThemeMode.DARK -> "Dark"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.OLED -> "OLED"
                            },
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                            color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ---- title page: classic detail vs Netflix-style hero (feature toggle)
        SectionCard(title = "Title page") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in DetailStyle.entries) {
                    val sel = detailStyle == d
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (sel) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { DetailStyleController.set(context, d) },
                    ) {
                        Text(
                            when (d) {
                                DetailStyle.CLASSIC -> "Classic"
                                DetailStyle.NETFLIX -> "Netflix style"
                            },
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                            color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ---- connection: why the phone thinks the PC is offline, and wake it
        SectionCard(title = "Connection") {
            val devices by devicesVm.devices.collectAsState()
            val selected by devicesVm.selectedDeviceId.collectAsState()
            val check by connection.state.collectAsState()
            val wakeNotice by connection.notice.collectAsState()
            val pc = remember(devices, selected) { devices.find { it.deviceId == selected } }

            Text(
                if (pc == null) "No PC selected yet."
                else "${pc.name} · v${pc.appVersion ?: "?"} · " +
                    (pc.lastSeen?.let { pcAgeLabel(it) } ?: "never seen"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { connection.check() },
                    enabled = check !is ConnectionViewModel.CheckState.Running,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (check is ConnectionViewModel.CheckState.Running) "Checking…" else "Check connection")
                }
                OutlinedButton(
                    onClick = { connection.wakePc() },
                    enabled = pc != null,
                    modifier = Modifier.weight(1f),
                ) { Text("Wake PC") }
            }
            when (val s = check) {
                is ConnectionViewModel.CheckState.Idle -> Unit
                is ConnectionViewModel.CheckState.Running -> {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is ConnectionViewModel.CheckState.Done -> {
                    Spacer(Modifier.height(8.dp))
                    for (step in s.steps) CheckStepRow(step)
                    if (s.ok) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Every leg answered — this phone can see and control that PC.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            wakeNotice?.let {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        it,
                        Modifier.padding(10.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                TextButton(onClick = connection::clearNotice) { Text("dismiss") }
            }
        }

        // ---- account
        SectionCard(title = "Account") {
            Text(
                (authState as? AuthState.SignedIn)?.email?.let { "Signed in as $it" } ?: "…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = authVm::signOut) { Text("Sign out") }
        }

        // ---- PC tools (parity with the desktop app)
        SectionCard(title = "PC tools") {
            Text(
                "Maintenance actions run on the connected PC through the same code paths the desktop app uses.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { pcTools.fetchLog() },
                    enabled = busy == null,
                    modifier = Modifier.weight(1f),
                ) { Text("View PC log") }
                Button(
                    onClick = { pcTools.restartEngine() },
                    enabled = busy == null,
                    modifier = Modifier.weight(1f),
                ) { Text("Restart engine") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { pcTools.scanSeerr() },
                enabled = busy == null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Check for new requests") }

            toolError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            toolNotice?.let {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        it,
                        Modifier.padding(10.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            if (busy != null) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }

        // ---- app updates
        SectionCard(title = "App updates") {
            Text(
                "Installed: v${updateVm.versionName(context)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            when (val s = updateState) {
                is UpdateUiState.Idle,
                is UpdateUiState.Checking,
                -> Button(onClick = { updateVm.check(context) }, enabled = s !is UpdateUiState.Checking) {
                    Text(if (s is UpdateUiState.Checking) "Checking…" else "Check for updates")
                }

                is UpdateUiState.UpToDate -> {
                    Text("You're on the latest version.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = updateVm::clear) { Text("dismiss") }
                }

                is UpdateUiState.Available -> {
                    Text("Update ${s.info.version} available.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = updateVm::showPopup) {
                        Text("View update")
                    }
                }

                is UpdateUiState.Downloading,
                is UpdateUiState.NeedsPermission,
                is UpdateUiState.Ready,
                -> Button(onClick = updateVm::showPopup) {
                    Text("View update")
                }

                is UpdateUiState.Error -> {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { updateVm.check(context) }) { Text("retry") }
                }
            }
        }

        HorizontalDivider()
        Text(
            "CineJoy Remote · phone ↔ PC over Supabase",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 96.dp),
        )
    }

    // PC log viewer
    logText?.let { lines ->
        AlertDialog(
            onDismissRequest = { pcTools.closeLog() },
            title = { Text("PC log (last lines)") },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        lines,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { pcTools.closeLog() }) { Text("Close") } },
        )
    }
}

@Composable
private fun CheckStepRow(step: ConnectionViewModel.CheckStep) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (step.ok) "✓" else "✗",
            style = MaterialTheme.typography.labelLarge,
            color = if (step.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                step.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                step.detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (step.ok) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error,
            )
        }
    }
}

// PostgREST renders timestamptz as "...+00:00", which Instant.parse rejects;
// OffsetDateTime handles both that and the plain "Z" form.
private fun pcAgeLabel(lastSeen: String): String? {
    val at = runCatching { Instant.parse(lastSeen) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(lastSeen).toInstant() }.getOrNull()
        ?: return null
    val s = Duration.between(at, Instant.now()).seconds
    return when {
        s < 90 -> "seen ${s}s ago"
        s < 5400 -> "seen ${s / 60}m ago"
        else -> "seen ${s / 3600}h ago"
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}
