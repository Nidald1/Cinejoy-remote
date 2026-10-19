package com.nedal.cinejoyremote.ui.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nedal.cinejoyremote.domain.AuthState
import com.nedal.cinejoyremote.presentation.AuthViewModel
import com.nedal.cinejoyremote.presentation.ConnectionViewModel
import com.nedal.cinejoyremote.presentation.DevicesViewModel
import com.nedal.cinejoyremote.presentation.PcToolsViewModel
import com.nedal.cinejoyremote.presentation.UpdateUiState
import com.nedal.cinejoyremote.presentation.UpdateViewModel
import com.nedal.cinejoyremote.ui.DetailStyle
import com.nedal.cinejoyremote.ui.DetailStyleController
import com.nedal.cinejoyremote.ui.LibraryStyle
import com.nedal.cinejoyremote.ui.LibraryStyleController
import com.nedal.cinejoyremote.ui.theme.ThemeController
import com.nedal.cinejoyremote.ui.theme.ThemeMode
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/**
 * V3 Settings (spec mockup 10): flat section cards — title left, controls
 * right/below; Appearance / Title page / Library style / Connection / PC
 * tools / Account / App updates, plus the version footer.
 */
@Composable
fun V3SettingsScreen(
    authVm: AuthViewModel,
    pcTools: PcToolsViewModel,
    updateVm: UpdateViewModel,
    devicesVm: DevicesViewModel,
    connection: ConnectionViewModel,
) {
    val c = v3
    val context = LocalContext.current
    val authState by authVm.authState.collectAsState(initial = AuthState.Loading)
    val updateState by updateVm.state.collectAsState()

    val busy by pcTools.busy.collectAsState()
    val logText by pcTools.log.collectAsState()
    val toolError by pcTools.error.collectAsState()
    val toolNotice by pcTools.notice.collectAsState()
    val themeMode = ThemeController.mode
    val detailStyle = DetailStyleController.mode
    val libraryStyle = LibraryStyleController.mode

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = V3Space.gutter),
        verticalArrangement = Arrangement.spacedBy(V3Space.listGap),
    ) {
        // --- Display -------------------------------------------------------
        V3SectionLabel("Display")
        // Appearance: dark / light / OLED — drives both v3 palette and the
        // legacy theme constant (status bar icons follow it).
        V3SettingsCard("Appearance") {
            for (m in ThemeMode.entries) {
                V3SettingRow(
                    label = when (m) { ThemeMode.DARK -> "Dark"; ThemeMode.LIGHT -> "Light"; ThemeMode.OLED -> "OLED" },
                    selected = themeMode == m,
                    onClick = { ThemeController.set(context, m) },
                )
            }
        }

        V3SettingsCard("Title page") {
            for (d in DetailStyle.entries) {
                V3SettingRow(
                    label = when (d) { DetailStyle.CLASSIC -> "Classic"; DetailStyle.NETFLIX -> "Netflix style" },
                    selected = detailStyle == d,
                    onClick = { DetailStyleController.set(context, d) },
                )
            }
        }

        V3SettingsCard("Library") {
            for (l in LibraryStyle.entries) {
                V3SettingRow(
                    label = when (l) { LibraryStyle.CLASSIC -> "Classic"; LibraryStyle.NETFLIX -> "Netflix style" },
                    selected = libraryStyle == l,
                    onClick = { LibraryStyleController.set(context, l) },
                )
            }
        }

        // --- PC & connection ------------------------------------------------
        V3SectionLabel("PC & connection")
        val devices by devicesVm.devices.collectAsState()
        val selected by devicesVm.selectedDeviceId.collectAsState()
        val check by connection.state.collectAsState()
        val wakeNotice by connection.notice.collectAsState()
        val pc = remember(devices, selected) { devices.find { it.deviceId == selected } }
        V3SettingsCard("Connection") {
            Text(
                if (pc == null) "No PC selected yet."
                else "${pc.name} · v${pc.appVersion ?: "?"} · " +
                    (pc.lastSeen?.let { settingsPcAgeLabel(it) } ?: "never seen"),
                style = V3Type.body,
                color = c.textSecondary,
            )
            Spacer(Modifier.height(V3Space.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(V3Space.sm)) {
                Button(
                    onClick = { connection.check() },
                    enabled = check !is ConnectionViewModel.CheckState.Running,
                    shape = RoundedCornerShape(V3Radius.sm),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (check is ConnectionViewModel.CheckState.Running) "Checking…" else "Check connection",
                        style = V3Type.chip,
                    )
                }
                OutlinedButton(
                    onClick = { connection.wakePc() },
                    enabled = pc != null,
                    shape = RoundedCornerShape(V3Radius.sm),
                    modifier = Modifier.weight(1f),
                ) { Text("Wake PC", style = V3Type.chip, color = c.text) }
            }
            when (val s = check) {
                is ConnectionViewModel.CheckState.Idle -> Unit
                is ConnectionViewModel.CheckState.Running -> {
                    Spacer(Modifier.height(V3Space.sm))
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = c.accent, trackColor = c.raised)
                }
                is ConnectionViewModel.CheckState.Done -> {
                    Spacer(Modifier.height(V3Space.sm))
                    for (step in s.steps) V3CheckStepRow(step)
                    if (s.ok) {
                        Text("Every leg answered — this phone can see and control that PC.", style = V3Type.caption, color = c.success)
                    }
                }
            }
            wakeNotice?.let {
                Spacer(Modifier.height(V3Space.sm))
                Text(it, style = V3Type.caption, color = c.success)
                TextButton(onClick = connection::clearNotice) { Text("dismiss", style = V3Type.caption) }
            }
        }

        V3SettingsCard("PC tools") {
            Text(
                "Maintenance actions run on the connected PC through the same code paths the desktop app uses.",
                style = V3Type.caption,
                color = c.textTertiary,
            )
            Spacer(Modifier.height(V3Space.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(V3Space.sm)) {
                Button(
                    onClick = { pcTools.fetchLog() },
                    enabled = busy == null,
                    shape = RoundedCornerShape(V3Radius.sm),
                    modifier = Modifier.weight(1f),
                ) { Text("View PC log", style = V3Type.chip) }
                Button(
                    onClick = { pcTools.restartEngine() },
                    enabled = busy == null,
                    shape = RoundedCornerShape(V3Radius.sm),
                    modifier = Modifier.weight(1f),
                ) { Text("Restart engine", style = V3Type.chip) }
            }
            Spacer(Modifier.height(V3Space.sm))
            OutlinedButton(
                onClick = { pcTools.scanSeerr() },
                enabled = busy == null,
                shape = RoundedCornerShape(V3Radius.sm),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Check for new requests", style = V3Type.chip, color = c.text) }

            toolError?.let {
                Text(it, color = c.error, style = V3Type.caption)
            }
            toolNotice?.let {
                Text(it, style = V3Type.caption, color = c.success)
            }
            if (busy != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = c.accent, trackColor = c.raised)
            }
        }

        // --- Wake relay ----------------------------------------------------
        // The one piece that makes wake work from outside the home network:
        // magic packets are a LAN broadcast, so some device has to be on the
        // LAN to send them. This card turns this device into that sender.
        V3SectionLabel("Wake relay")
        V3SettingsCard("Wake relay") {
            val relayOn by com.nedal.cinejoyremote.relay.WakeRelayService.running.collectAsState()
            val relayEnabled = remember(relayOn) {
                com.nedal.cinejoyremote.relay.RelayPrefs.enabled(context)
            }
            Text(
                "Keeps this device able to wake your PC from anywhere. Enable it on a phone that " +
                    "stays home and is plugged in — not the one you carry out with you.",
                style = V3Type.caption,
                color = c.textTertiary,
            )
            Spacer(Modifier.height(V3Space.sm))
            Button(
                onClick = {
                    val next = !relayEnabled
                    com.nedal.cinejoyremote.relay.RelayPrefs.setEnabled(context, next)
                    if (next) com.nedal.cinejoyremote.relay.WakeRelayService.start(context)
                    else com.nedal.cinejoyremote.relay.WakeRelayService.stop(context)
                },
                enabled = relayEnabled || check !is ConnectionViewModel.CheckState.Running,
                shape = RoundedCornerShape(V3Radius.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (relayEnabled) "Relay on — tap to stop" else "Turn on relay", style = V3Type.chip)
            }
            if (relayOn) {
                Text("This device is listening for wake requests.", style = V3Type.caption, color = c.success)
            }
        }

        // --- App -----------------------------------------------------------
        V3SectionLabel("App")
        V3SettingsCard("Account") {
            Text(
                (authState as? AuthState.SignedIn)?.email?.let { "Signed in as $it" } ?: "…",
                style = V3Type.body,
                color = c.textSecondary,
            )
            Spacer(Modifier.height(V3Space.sm))
            OutlinedButton(onClick = authVm::signOut, shape = RoundedCornerShape(V3Radius.sm)) {
                Text("Sign out", style = V3Type.chip, color = c.error)
            }
        }

        V3SettingsCard("App updates") {
            Text(
                "Installed: v${updateVm.versionName(context)}",
                style = V3Type.caption,
                color = c.textTertiary,
            )
            Spacer(Modifier.height(V3Space.sm))
            when (val s = updateState) {
                is UpdateUiState.Idle, is UpdateUiState.Checking ->
                    Button(
                        onClick = { updateVm.check(context) },
                        enabled = s !is UpdateUiState.Checking,
                        shape = RoundedCornerShape(V3Radius.sm),
                    ) {
                        Text(if (s is UpdateUiState.Checking) "Checking…" else "Check for updates", style = V3Type.chip)
                    }

                is UpdateUiState.UpToDate -> {
                    Text("You're on the latest version.", style = V3Type.caption, color = c.success)
                    TextButton(onClick = updateVm::clear) { Text("dismiss", style = V3Type.caption) }
                }

                is UpdateUiState.Available -> {
                    Text("Update ${s.info.version} available.", style = V3Type.caption, color = c.warning)
                    Spacer(Modifier.height(V3Space.xs))
                    Button(onClick = updateVm::showPopup, shape = RoundedCornerShape(V3Radius.sm)) {
                        Text("View update", style = V3Type.chip)
                    }
                }

                is UpdateUiState.Downloading, is UpdateUiState.NeedsPermission, is UpdateUiState.Ready ->
                    Button(onClick = updateVm::showPopup, shape = RoundedCornerShape(V3Radius.sm)) {
                        Text("View update", style = V3Type.chip)
                    }

                is UpdateUiState.Error -> {
                    Text(s.message, color = c.error, style = V3Type.caption)
                    TextButton(onClick = { updateVm.check(context) }) { Text("retry", style = V3Type.caption) }
                }
            }
        }

        HorizontalDivider(color = c.divider)
        Text(
            "CineJoy Remote · phone ↔ PC over Supabase",
            style = V3Type.caption,
            color = c.textTertiary,
            modifier = Modifier.padding(bottom = V3Space.bottomClearance),
        )
    }

    logText?.let { lines ->
        AlertDialog(
            onDismissRequest = { pcTools.closeLog() },
            containerColor = c.sheet,
            title = { Text("PC log (last lines)", color = c.text) },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        lines,
                        style = V3Type.mono.copy(fontFamily = FontFamily.Monospace),
                        color = c.textSecondary,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { pcTools.closeLog() }) { Text("Close", color = c.accent) } },
        )
    }
}

/** Group heading that sits above a cluster of [V3SettingsCard]s. */
@Composable
private fun V3SectionLabel(title: String) {
    Text(
        title.uppercase(),
        style = V3Type.caption12,
        color = v3.textTertiary,
        modifier = Modifier.padding(top = V3Space.sm),
    )
}

@Composable
private fun V3SettingsCard(title: String, content: @Composable () -> Unit) {
    val c = v3
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(V3Radius.card))
            .background(c.raised)
            .padding(V3Space.lg),
        verticalArrangement = Arrangement.spacedBy(V3Space.sm),
    ) {
        Text(title, style = V3Type.sectionTitle, color = c.text)
        content()
    }
}

@Composable
private fun V3SettingRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = v3
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(V3Radius.sm))
            .background(if (selected) c.accent.copy(alpha = 0.14f) else c.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = V3Space.md, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = V3Type.body14,
            color = if (selected) c.accent else c.text,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = c.accent,
                modifier = Modifier.width(18.dp),
            )
        }
    }
}

@Composable
private fun V3CheckStepRow(step: ConnectionViewModel.CheckStep) {
    val c = v3
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (step.ok) "✓" else "✗",
            style = V3Type.caption,
            color = if (step.ok) c.success else c.error,
        )
        Spacer(Modifier.width(V3Space.sm))
        Column {
            Text(step.label, style = V3Type.caption, color = c.text, fontWeight = FontWeight.SemiBold)
            Text(
                step.detail,
                style = V3Type.caption,
                color = if (step.ok) c.textTertiary else c.error,
            )
        }
    }
}

// PostgREST renders timestamptz as "...+00:00", which Instant.parse rejects;
// OffsetDateTime handles both that and the plain "Z" form.
private fun settingsPcAgeLabel(lastSeen: String): String? {
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
