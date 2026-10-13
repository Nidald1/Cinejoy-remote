package com.nedal.cinejoyremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nedal.cinejoyremote.domain.Device
import com.nedal.cinejoyremote.domain.DownloadTask
import com.nedal.cinejoyremote.presentation.DevicesViewModel
import com.nedal.cinejoyremote.presentation.DownloadsViewModel

@Composable
fun DownloadsScreen(
    devicesVm: DevicesViewModel,
    vm: DownloadsViewModel,
    snackbar: SnackbarHostState,
) {
    val devices by devicesVm.devices.collectAsState()
    val selected by devicesVm.selectedDeviceId.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val error by vm.error.collectAsState()

    LaunchedEffect(devices) { devicesVm.autoSelect(devices) }
    LaunchedEffect(error) {
        error?.let { msg -> snackbar.showSnackbar(msg); vm.clearError() }
    }

    var queuePaused by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(4.dp))
        DeviceRow(devices, selected, onSelect = devicesVm::select)

        if (selected != null) {
            // Queue controls — same executors as the desktop toolbar.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { vm.command(selected!!, "pause-all") },
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 0.dp),
                ) { Text("Pause all", style = MaterialTheme.typography.labelLarge) }
                FilledTonalButton(
                    onClick = { vm.command(selected!!, "resume-all") },
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 0.dp),
                ) { Text("Resume all", style = MaterialTheme.typography.labelLarge) }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.command(selected!!, "queue-missing") },
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 0.dp),
                ) { Text("Queue missing", style = MaterialTheme.typography.labelLarge) }
                OutlinedButton(
                    onClick = { vm.command(selected!!, "queue-clear") },
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 0.dp),
                ) { Text("Clear queue", style = MaterialTheme.typography.labelLarge) }
            }
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Pause queue", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Hold new downloads on the PC",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = queuePaused,
                        onCheckedChange = { checked ->
                            queuePaused = checked
                            vm.queuePause(checked)
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Grouped view: live work on top, problems next, history last.
        val active = tasks.filter { it.state in inStates }
        val paused = tasks.filter { it.state == "paused" }
        val failed = tasks.filter { it.state == "error" }
        val finished = tasks.filter { it.state == "done" }
        val other = tasks.filter {
            it.state !in inStates && it.state != "paused" && it.state != "error" && it.state != "done"
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
        ) {
            if (tasks.isEmpty()) {
                item(key = "empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("⬇", style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("No downloads yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Queue episodes from the Library tab and they show up here live.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (active.isNotEmpty()) {
                item(key = "h-active") { SectionHeader("Active", active.size, MaterialTheme.colorScheme.secondary) }
                items(active, key = { "a-" + it.taskId }) { task ->
                    TaskCard(task, onAction = { action -> selected?.let { vm.command(it, action, task.taskId) } })
                }
            }
            if (paused.isNotEmpty()) {
                item(key = "h-paused") { SectionHeader("Paused", paused.size, androidx.compose.ui.graphics.Color(0xFFFBBF24)) }
                items(paused, key = { "p-" + it.taskId }) { task ->
                    TaskCard(task, onAction = { action -> selected?.let { vm.command(it, action, task.taskId) } })
                }
            }
            if (failed.isNotEmpty()) {
                item(key = "h-failed") { SectionHeader("Failed", failed.size, MaterialTheme.colorScheme.error) }
                items(failed, key = { "e-" + it.taskId }) { task ->
                    TaskCard(task, onAction = { action -> selected?.let { vm.command(it, action, task.taskId) } })
                }
            }
            if (other.isNotEmpty()) {
                item(key = "h-other") { SectionHeader("Other", other.size, androidx.compose.ui.graphics.Color(0xFF94A3B8)) }
                items(other, key = { "o-" + it.taskId }) { task ->
                    TaskCard(task, onAction = { action -> selected?.let { vm.command(it, action, task.taskId) } })
                }
            }
            if (finished.isNotEmpty()) {
                item(key = "h-finished") { SectionHeader("Finished", finished.size, androidx.compose.ui.graphics.Color(0xFF34D399)) }
                items(finished, key = { "d-" + it.taskId }) { task ->
                    TaskCard(task, onAction = { action -> selected?.let { vm.command(it, action, task.taskId) } })
                }
            }
        }
    }
}

private val inStates = listOf("queued", "resolving", "downloading", "stopping")

@Composable
private fun SectionHeader(title: String, count: Int, accent: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(accent),
        )
        Text(
            "$title · $count",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun DeviceRow(devices: List<Device>, selected: String?, onSelect: (String) -> Unit) {
    if (devices.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "No PC seen yet — open the PC app and sign in with the same account.",
                Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (d in devices) {
            if (d.deviceId == selected) {
                Button(
                    onClick = {},
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp),
                ) { Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } else {
                OutlinedButton(
                    onClick = { onSelect(d.deviceId) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp),
                ) { Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun taskColor(task: DownloadTask): androidx.compose.ui.graphics.Color =
    when (task.state) {
        "done" -> androidx.compose.ui.graphics.Color(0xFF34D399)
        "error" -> MaterialTheme.colorScheme.error
        "paused" -> androidx.compose.ui.graphics.Color(0xFFFBBF24)
        "downloading", "resolving" -> androidx.compose.ui.graphics.Color(0xFF38BDF8)
        else -> androidx.compose.ui.graphics.Color(0xFF94A3B8)
    }

@Composable
fun TaskCard(task: DownloadTask, onAction: (String) -> Unit) {
    val accent = taskColor(task)
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (task.state) {
                        "done" -> Icons.Rounded.CheckCircle
                        "error" -> Icons.Rounded.ErrorOutline
                        else -> Icons.Rounded.HourglassTop
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = accent,
                )
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (task.episodeLabel.isNotBlank() && task.episodeLabel != task.title) {
                        Text(
                            task.episodeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accent.copy(alpha = 0.16f),
                ) {
                    Text(
                        task.state + (task.percent?.let { " · ${"%.0f".format(it)}%" } ?: ""),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (task.percent != null) {
                LinearProgressIndicator(
                    progress = { (task.percent!! / 100).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val bits = buildList {
                    task.speed?.let { add("${formatBytes(it)}/s") }
                    if (task.downloaded != null && task.total != null && task.total!! > 0) {
                        add("${formatBytes(task.downloaded!!)} of ${formatBytes(task.total!!)}")
                    } else {
                        task.downloaded?.let { add(formatBytes(it)) }
                        task.total?.takeIf { it > 0 }?.let { add(formatBytes(it)) }
                    }
                    task.eta?.let { add(it) }
                    task.quality?.let { add(it) }
                    task.source?.let { add(it) }
                }
                Text(
                    bits.joinToString(" · ").ifBlank { task.state },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            task.lastErr?.takeIf { task.state == "error" }?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (task.state) {
                    "downloading", "queued", "resolving", "stopping" ->
                        SmallAction("Pause") { onAction("pause") }

                    "paused" -> {
                        SmallAction("Resume") { onAction("resume") }
                        SmallAction("Remove") { onAction("forget") }
                    }

                    "error" -> SmallAction("Retry") { onAction("retry") }
                    "done" -> SmallAction("Clear") { onAction("forget") }
                }
            }
        }
    }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        modifier = Modifier.height(30.dp),
    ) { Text(label, style = MaterialTheme.typography.labelMedium) }
}

fun formatBytes(n: Long): String {
    if (n < 1024) return "$n B"
    val units = listOf("KB", "MB", "GB")
    var v = n / 1024.0
    var u = 0
    while (v >= 1024 && u < units.size - 1) { v /= 1024; u++ }
    return "%.1f %s".format(v, units[u])
}
