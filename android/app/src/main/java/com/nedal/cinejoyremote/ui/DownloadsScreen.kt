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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nedal.cinejoyremote.domain.Device
import com.nedal.cinejoyremote.domain.DownloadTask
import com.nedal.cinejoyremote.presentation.DevicesViewModel
import com.nedal.cinejoyremote.presentation.DownloadsViewModel
import coil.compose.AsyncImage
import java.time.Instant
import java.time.OffsetDateTime

// Three separate screens inside Downloads — the user picks Active, Failed or
// Finished up top and only that group's cards render, nothing to scroll past.
private val SEGMENTS = listOf("Active", "Failed", "Finished")
private val activeStates = listOf("queued", "resolving", "downloading", "stopping", "paused")

// Sort menu shared by all three segments. The chosen mode lives in
// rememberSaveable, so it survives switching Active/Failed/Finished and
// navigating between bottom-nav tabs.
private val SORT_OPTIONS = listOf(
    "Name (A–Z)",
    "Name (Z–A)",
    "Date added (newest first)",
    "Date added (oldest first)",
    "File size (largest first)",
    "File size (smallest first)",
)

// task_id is a random UUID and the mirror only tracks updated_at until the v3
// migration adds created_at — fall back to updated_at so date ordering works
// today and becomes exact "date added" once migration-v3-cards-sorting.sql runs.
private fun dateKey(task: DownloadTask): Long {
    fun parse(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return runCatching { Instant.parse(raw) }.getOrNull()?.toEpochMilli()
            ?: runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
    }
    return parse(task.createdAt) ?: parse(task.updatedAt) ?: 0L
}

private fun sizeKey(task: DownloadTask): Long = task.total ?: task.downloaded ?: 0L

private fun sortTasks(tasks: List<DownloadTask>, mode: Int): List<DownloadTask> {
    val byName = compareBy<DownloadTask>({ it.title.lowercase() }, { it.episodeLabel.lowercase() })
    return when (mode) {
        0 -> tasks.sortedWith(byName)
        1 -> tasks.sortedWith(byName.reversed())
        2 -> tasks.sortedWith(compareByDescending { dateKey(it) })
        3 -> tasks.sortedWith(compareBy { dateKey(it) })
        4 -> tasks.sortedWith(compareByDescending { sizeKey(it) })
        else -> tasks.sortedWith(compareBy { sizeKey(it) })
    }
}

@Composable
fun DownloadsScreen(
    devicesVm: DevicesViewModel,
    vm: DownloadsViewModel,
    snackbar: SnackbarHostState,
) {
    val devices by devicesVm.devices.collectAsState()
    val selected by devicesVm.selectedDeviceId.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val posters by vm.posters.collectAsState()
    val error by vm.error.collectAsState()

    LaunchedEffect(devices) { devicesVm.autoSelect(devices) }
    LaunchedEffect(error) {
        error?.let { msg -> snackbar.showSnackbar(msg); vm.clearError() }
    }

    var queuePaused by remember { mutableStateOf(false) }
    var segment by rememberSaveable { mutableIntStateOf(0) }
    var sortMode by rememberSaveable { mutableIntStateOf(2) }
    // "failed" / "finished" bulk delete confirmation (which group + its rows).
    var bulkDelete by remember { mutableStateOf<Pair<String, List<DownloadTask>>?>(null) }

    val active = remember(tasks) { tasks.filter { it.state in activeStates } }
    val failed = remember(tasks) { tasks.filter { it.state == "error" } }
    val finished = remember(tasks) { tasks.filter { it.state == "done" } }
    val counts = listOf(active.size, failed.size, finished.size)
    val shown = sortTasks(
        when (segment) {
            0 -> active
            1 -> failed
            else -> finished
        },
        sortMode,
    )

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(4.dp))
        DeviceRow(devices, selected, onSelect = devicesVm::select)

        // ---- segment switcher: Active | Failed | Finished, with the sort menu
        if (selected != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f),
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
                                    else -> Color(0xFF34D399)
                                },
                                onClick = { segment = i },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                SortButton(sortMode) { sortMode = it }
                MoreMenu(
                    failedCount = failed.size,
                    finishedCount = finished.size,
                    onPauseAll = { selected?.let { vm.command(it, "pause-all") } },
                    onResumeAll = { selected?.let { vm.command(it, "resume-all") } },
                    onQueueMissing = { selected?.let { vm.command(it, "queue-missing") } },
                    onQueueClear = { selected?.let { vm.command(it, "queue-clear") } },
                    onRetryAll = { failed.forEach { t -> selected?.let { vm.command(it, "retry", t.taskId) } } },
                    onBulkDelete = { kind ->
                        bulkDelete = kind to when (kind) {
                            "failed" -> failed
                            else -> finished
                        }
                    },
                )
            }
            Spacer(Modifier.height(8.dp))

            // ---- bulk actions live in the ⋮ menu; only the queue toggle stays
            if (segment == 0) {
                Spacer(Modifier.height(4.dp))
                QueueToggle(
                    queuePaused = queuePaused,
                    onQueuePaused = { checked ->
                        queuePaused = checked
                        vm.queuePause(checked)
                    },
                )
                Spacer(Modifier.height(4.dp))
            }
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
                    TaskCard(
                        task,
                        poster = task.poster ?: posters[task.episodeKey.substringBefore(':')],
                        onAction = { action ->
                            selected?.let { dev ->
                                when (action) {
                                    // stop / delete go through the composite
                                    // task-delete command (stop + forget in one).
                                    "stop", "delete-list" -> vm.deleteTask(dev, task.taskId, withFiles = false)
                                    "delete" -> vm.deleteTask(dev, task.taskId, withFiles = true)
                                    else -> vm.command(dev, action, task.taskId)
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    // ---- bulk delete confirmation (failed / finished, from the ⋮ menu)
    bulkDelete?.let { (kind, rows) ->
        val dev = selected
        AlertDialog(
            onDismissRequest = { bulkDelete = null },
            title = { Text("Delete all $kind (${rows.size})?") },
            text = {
                Text(
                    "Delete removes every $kind download from the list and deletes the " +
                        "downloaded files from the PC. You can also just clear the list and keep the files.",
                )
            },
            dismissButton = {
                TextButton(onClick = { bulkDelete = null }) { Text("Cancel") }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    Button(onClick = {
                        bulkDelete = null
                        if (dev != null) rows.forEach { t -> vm.deleteTask(dev, t.taskId, withFiles = true) }
                    }) { Text("Delete with files") }
                    TextButton(onClick = {
                        bulkDelete = null
                        if (dev != null) rows.forEach { t -> vm.deleteTask(dev, t.taskId, withFiles = false) }
                    }) { Text("Clear list only") }
                }
            },
        )
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
    // All three pills render the exact same parts (dot + label + count badge)
    // at the same fixed height, so the row stays straight whatever the counts.
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else Color.Transparent,
        modifier = modifier
            .height(38.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) accent else MaterialTheme.colorScheme.outline),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
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

@Composable
private fun MoreMenu(
    failedCount: Int,
    finishedCount: Int,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onQueueMissing: () -> Unit,
    onQueueClear: () -> Unit,
    onRetryAll: () -> Unit,
    onBulkDelete: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { open = true },
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = "More download actions",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Pause all downloads") },
                onClick = { onPauseAll(); open = false },
            )
            DropdownMenuItem(
                text = { Text("Resume all downloads") },
                onClick = { onResumeAll(); open = false },
            )
            DropdownMenuItem(
                text = { Text("Fix queue missing") },
                onClick = { onQueueMissing(); open = false },
            )
            DropdownMenuItem(
                text = { Text("Clear all downloads") },
                onClick = { onQueueClear(); open = false },
            )
            // Contextual bulk action for the current segment (replaces the old
            // per-tab "Retry all" / "Clear all" buttons).
            if (failedCount > 0) {
                DropdownMenuItem(
                    text = { Text("Retry all failed ($failedCount)") },
                    onClick = { onRetryAll(); open = false },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            "Delete all failed ($failedCount)",
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = { onBulkDelete("failed"); open = false },
                )
            }
            if (finishedCount > 0) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "Delete all finished ($finishedCount)",
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = { onBulkDelete("finished"); open = false },
                )
            }
        }
    }
}

@Composable
private fun QueueToggle(
    queuePaused: Boolean,
    onQueuePaused: (Boolean) -> Unit,
) {
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
                    modifier = Modifier.height(40.dp),
                ) { Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } else {
                OutlinedButton(
                    onClick = { onSelect(d.deviceId) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(40.dp),
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
private fun SortButton(sortMode: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { open = true },
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Icon(
                Icons.Rounded.Sort,
                contentDescription = "Sort downloads",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SORT_OPTIONS.forEachIndexed { i, label ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label,
                            fontWeight = if (i == sortMode) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (i == sortMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    trailingIcon = {
                        if (i == sortMode) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    onClick = { onSelect(i); open = false },
                )
            }
        }
    }
}

@Composable
fun TaskCard(task: DownloadTask, poster: String? = null, onAction: (String) -> Unit) {
    val accent = taskColor(task)
    var confirmDelete by remember(task.taskId) { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        // Frosted top sheen — the Morphe card gradient, kept subtle.
        Box(
            Modifier.background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.045f), Color.Transparent),
                ),
            ),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                PosterThumb(poster, accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                task.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
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
                        Spacer(Modifier.width(8.dp))
                        StatusPill(task, accent)
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val sizeText = when {
                            task.downloaded != null && task.total != null && task.total!! > 0 ->
                                "${formatBytes(task.downloaded!!)} / ${formatBytes(task.total!!)}"
                            task.total != null && task.total!! > 0 -> formatBytes(task.total!!)
                            task.downloaded != null -> formatBytes(task.downloaded!!)
                            else -> null
                        }
                        if (sizeText != null) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Text(
                                    sizeText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        val bits = buildList {
                            task.speed?.let { add("${formatBytes(it)}/s") }
                            task.eta?.let { add(it) }
                            task.quality?.let { add(it) }
                            task.source?.let { add(it.uppercase()) }
                        }
                        if (bits.isNotEmpty()) {
                            Text(
                                bits.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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

                    task.lastErr?.takeIf { task.state == "error" }?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Small icon actions — no words, so the card stays clean.
                    // Pause/Stop for running, Resume/Stop when paused, Retry on
                    // failed; a trash icon on every card opens the delete confirm.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when (task.state) {
                            "downloading", "queued", "resolving", "stopping" -> {
                                IconAction(Icons.Rounded.Pause, "Pause download") { onAction("pause") }
                                IconAction(
                                    Icons.Rounded.Stop,
                                    "Stop download",
                                    tint = MaterialTheme.colorScheme.error,
                                ) { onAction("stop") }
                            }

                            "paused" -> {
                                IconAction(
                                    Icons.Rounded.PlayArrow,
                                    "Resume download",
                                    tint = Color(0xFF34D399),
                                ) { onAction("resume") }
                                IconAction(
                                    Icons.Rounded.Stop,
                                    "Stop download",
                                    tint = MaterialTheme.colorScheme.error,
                                ) { onAction("stop") }
                            }

                            "error" -> IconAction(
                                Icons.Rounded.Refresh,
                                "Retry download",
                                tint = Color(0xFF38BDF8),
                            ) { onAction("retry") }
                        }
                        Spacer(Modifier.weight(1f))
                        IconAction(
                            Icons.Rounded.Delete,
                            "Delete download",
                            tint = MaterialTheme.colorScheme.error,
                        ) { confirmDelete = true }
                    }
                }
            }
        }
    }

    // ---- delete confirmation (single task)
    if (confirmDelete) {
        val who = task.title +
            (task.episodeLabel.takeIf { it.isNotBlank() && it != task.title }?.let { " — $it" } ?: "")
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete download?") },
            text = {
                Text(
                    "$who\n\nDelete removes it from the list and deletes the downloaded " +
                        "file from the PC. You can also just remove it from the list and keep the file.",
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    Button(onClick = {
                        confirmDelete = false
                        onAction("delete")
                    }) { Text("Delete with file") }
                    TextButton(onClick = {
                        confirmDelete = false
                        onAction("delete-list")
                    }) { Text("Remove from list only") }
                }
            },
        )
    }
}

@Composable
private fun PosterThumb(poster: String?, accent: Color) {
    val shape = RoundedCornerShape(14.dp)
    if (poster.isNullOrBlank()) {
        Box(
            Modifier
                .size(width = 54.dp, height = 81.dp)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.10f)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Movie,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
        }
    } else {
        AsyncImage(
            model = poster,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 54.dp, height = 81.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
    }
}

@Composable
private fun StatusPill(task: DownloadTask, accent: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.16f),
    ) {
        Text(
            task.state.replaceFirstChar { it.uppercase() } +
                (task.percent?.let { " · ${"%.0f".format(it)}%" } ?: ""),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun IconAction(
    icon: ImageVector,
    desc: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(17.dp))
    }
}

fun formatBytes(n: Long): String {
    if (n < 1024) return "$n B"
    val units = listOf("KB", "MB", "GB")
    var v = n / 1024.0
    var u = 0
    while (v >= 1024 && u < units.size - 1) { v /= 1024; u++ }
    return "%.1f %s".format(v, units[u])
}
