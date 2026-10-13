package com.nedal.cinejoyremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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

// Three separate screens inside Downloads — the user picks Active, Failed or
// Finished up top and only that group's cards render, nothing to scroll past.
private val SEGMENTS = listOf("Active", "Failed", "Finished")
private val activeStates = listOf("queued", "resolving", "downloading", "stopping", "paused")

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
    var segment by rememberSaveable { mutableIntStateOf(0) }

    val active = remember(tasks) { tasks.filter { it.state in activeStates } }
    val failed = remember(tasks) { tasks.filter { it.state == "error" } }
    val finished = remember(tasks) { tasks.filter { it.state == "done" } }
    val counts = listOf(active.size, failed.size, finished.size)
    val shown = when (segment) {
        0 -> active
        1 -> failed
        else -> finished
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(4.dp))
        DeviceRow(devices, selected, onSelect = devicesVm::select)

        // ---- segment switcher: Active | Failed | Finished
        if (selected != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(4.dp)) {
                    SEGMENTS.forEachIndexed { i, label ->
                        SegmentTab(
                            label = label,
                            count = counts[i],
                            selected = segment == i,
                            accent = when (i) {
                                0 -> MaterialTheme.colorScheme.secondary
                                1 -> MaterialTheme.colorScheme.error
                                else -> androidx.compose.ui.graphics.Color(0xFF34D399)
                            },
                            onClick = { segment = i },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // ---- controls that apply to the shown segment
            when (segment) {
                0 -> ActiveControls(
                    selected = selected!!,
                    vm = vm,
                    queuePaused = queuePaused,
                    onQueuePaused = { checked ->
                        queuePaused = checked
                        vm.queuePause(checked)
                    },
                )

                1 -> if (failed.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            failed.forEach { vm.command(selected!!, "retry", it.taskId) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 0.dp),
                    ) { Text("Retry all (${failed.size})", style = MaterialTheme.typography.labelLarge) }
                }

                2 -> if (finished.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            finished.forEach { vm.command(selected!!, "forget", it.taskId) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 0.dp),
                    ) { Text("Clear all (${finished.size})", style = MaterialTheme.typography.labelLarge) }
                }
            }
            if (segment == 0) Spacer(Modifier.height(4.dp))
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 96.dp),
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
            } else if (shown.isEmpty()) {
                item(key = "empty-seg") {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            when (segment) {
                                0 -> "🎬"
                                1 -> "✅"
                                else -> "📦"
                            },
                            style = MaterialTheme.typography.displayMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (segment) {
                                0 -> "Nothing downloading"
                                1 -> "Nothing failed"
                                else -> "Nothing finished yet"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            when (segment) {
                                0 -> "Queued downloads run here live with speed and progress."
                                1 -> "Failed downloads land here with the reason and a Retry button."
                                else -> "Completed downloads collect here with their final size."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(shown, key = { "t-" + it.taskId }) { task ->
                    TaskCard(task, onAction = { action -> selected?.let { vm.command(it, action, task.taskId) } })
                }
            }
        }
    }
}

@Composable
private fun SegmentTab(
    label: String,
    count: Int,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest
        else androidx.compose.ui.graphics.Color.Transparent,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (count > 0) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) accent else MaterialTheme.colorScheme.outline),
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (count > 0) {
                Spacer(Modifier.width(5.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (selected) accent.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        count.toString(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveControls(
    selected: String,
    vm: DownloadsViewModel,
    queuePaused: Boolean,
    onQueuePaused: (Boolean) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { vm.command(selected, "pause-all") },
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 0.dp),
            ) { Text("Pause all", style = MaterialTheme.typography.labelLarge) }
            FilledTonalButton(
                onClick = { vm.command(selected, "resume-all") },
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 0.dp),
            ) { Text("Resume all", style = MaterialTheme.typography.labelLarge) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { vm.command(selected, "queue-missing") },
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 0.dp),
            ) { Text("Queue missing", style = MaterialTheme.typography.labelLarge) }
            OutlinedButton(
                onClick = { vm.command(selected, "queue-clear") },
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 0.dp),
            ) { Text("Clear queue", style = MaterialTheme.typography.labelLarge) }
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
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
                Switch(checked = queuePaused, onCheckedChange = onQueuePaused)
            }
        }
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
