package com.nedal.cinejoyremote.ui.v3

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nedal.cinejoyremote.domain.DownloadTask
import com.nedal.cinejoyremote.presentation.DevicesViewModel
import com.nedal.cinejoyremote.presentation.DownloadsViewModel
import com.nedal.cinejoyremote.ui.v3.components.EmptyState
import com.nedal.cinejoyremote.ui.v3.components.SheetRow
import com.nedal.cinejoyremote.ui.v3.components.V3CardIconAction
import com.nedal.cinejoyremote.ui.v3.components.V3IconAction
import com.nedal.cinejoyremote.ui.v3.components.V3Pill
import com.nedal.cinejoyremote.ui.v3.components.V3Sheet
import java.time.Instant
import java.time.OffsetDateTime

// Spec mockup 07: three pill tabs — only the chosen group's cards render.
private val SEGMENTS = listOf("Active", "Failed", "Finished")
private val activeStates = listOf("queued", "resolving", "downloading", "stopping", "paused")

private val SORT_OPTIONS = listOf(
    "Name (A–Z)",
    "Name (Z–A)",
    "Date added (newest first)",
    "Date added (oldest first)",
    "File size (largest first)",
    "File size (smallest first)",
)

// task_id is a random UUID; the mirror only tracks updated_at until the v3
// migration adds created_at — fall back so ordering works today.
private fun dateKey(task: DownloadTask): Long {
    fun parse(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return runCatching { Instant.parse(raw) }.getOrNull()?.toEpochMilli()
            ?: runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
    }
    return parse(task.createdAt) ?: parse(task.updatedAt) ?: 0L
}

private fun sizeKey(task: DownloadTask): Long = task.total ?: task.downloaded ?: 0L

// Library mirror is the authority; the id pattern covers removed shows.
private fun isSeries(task: DownloadTask, types: Map<String, String>): Boolean {
    val showId = task.episodeKey.substringBefore(':')
    types[showId]?.let { return it == "tv" }
    return !showId.contains(Regex("-movie-\\d+$"))
}

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

private fun taskFormatBytes(n: Long): String {
    if (n < 1024) return "$n B"
    val units = listOf("KB", "MB", "GB")
    var v = n / 1024.0
    var u = 0
    while (v >= 1024 && u < units.size - 1) { v /= 1024; u++ }
    return "%.1f %s".format(v, units[u])
}

@Composable
fun V3DownloadsScreen(
    devicesVm: DevicesViewModel,
    vm: DownloadsViewModel,
    snackbar: SnackbarHostState,
    onBrowseLibrary: () -> Unit = {},
    onOpenSeerr: () -> Unit = {},
) {
    val c = v3
    val devices by devicesVm.devices.collectAsState()
    val selected by devicesVm.selectedDeviceId.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val posters by vm.posters.collectAsState()
    val showTypes by vm.showTypes.collectAsState()
    val error by vm.error.collectAsState()

    LaunchedEffect(devices) { devicesVm.autoSelect(devices) }
    LaunchedEffect(error) {
        error?.let { msg -> snackbar.showSnackbar(msg); vm.clearError() }
    }

    var queuePaused by remember { mutableStateOf(false) }
    var mediaTab by rememberSaveable { mutableIntStateOf(0) } // 0 all, 1 movies, 2 series
    var segment by rememberSaveable { mutableIntStateOf(0) }
    var sortMode by rememberSaveable { mutableIntStateOf(2) }
    var bulkDelete by remember { mutableStateOf<Pair<String, List<DownloadTask>>?>(null) }
    var sheet by remember { mutableStateOf<String?>(null) } // filter | sort | more

    val mediaCounts = remember(tasks, showTypes) {
        listOf(
            tasks.size,
            tasks.count { !isSeries(it, showTypes) },
            tasks.count { isSeries(it, showTypes) },
        )
    }
    val mediaTasks = remember(tasks, mediaTab, showTypes) {
        when (mediaTab) {
            1 -> tasks.filter { !isSeries(it, showTypes) }
            2 -> tasks.filter { isSeries(it, showTypes) }
            else -> tasks
        }
    }
    val active = remember(mediaTasks) { mediaTasks.filter { it.state in activeStates } }
    val failed = remember(mediaTasks) { mediaTasks.filter { it.state == "error" } }
    val finished = remember(mediaTasks) { mediaTasks.filter { it.state == "done" } }
    val counts = listOf(active.size, failed.size, finished.size)
    val shown = sortTasks(
        when (segment) {
            0 -> active
            1 -> failed
            else -> finished
        },
        sortMode,
    )

    Column(Modifier.fillMaxSize()) {
        // Thin banner — only when the queue actually IS paused.
        if (queuePaused && selected != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = V3Space.gutter, vertical = V3Space.xs)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.warning.copy(alpha = 0.12f))
                    .height(32.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Queue paused",
                    style = V3Type.caption12,
                    color = c.warning,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Resume",
                    style = V3Type.caption12,
                    fontWeight = FontWeight.Bold,
                    color = c.text,
                    modifier = Modifier
                        .clickable {
                            queuePaused = false
                            vm.queuePause(false)
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }

        // Pill tabs + top-right icon actions (mockup 07).
        if (selected != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = V3Space.gutter, end = V3Space.xs, top = V3Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SEGMENTS.forEachIndexed { i, label ->
                    if (i > 0) Spacer(Modifier.width(V3Space.sm))
                    V3Pill(
                        label = "$label ${counts[i]}",
                        selected = segment == i,
                        onClick = { segment = i },
                    )
                }
                Spacer(Modifier.weight(1f))
                V3IconAction(
                    icon = if (queuePaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    desc = if (queuePaused) "Resume queue" else "Pause queue",
                ) {
                    queuePaused = !queuePaused
                    vm.queuePause(queuePaused)
                }
                V3IconAction(Icons.Rounded.FilterList, "Filter downloads") { sheet = "filter" }
                V3IconAction(Icons.Rounded.Sort, "Sort downloads") { sheet = "sort" }
                V3IconAction(Icons.Rounded.MoreVert, "More download actions") { sheet = "more" }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(V3Space.listGap),
            contentPadding = PaddingValues(
                start = V3Space.gutter,
                end = V3Space.gutter,
                top = V3Space.sm,
                bottom = V3Space.bottomClearance,
            ),
        ) {
            if (tasks.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.Download,
                        title = "No downloads",
                        body = "Nothing here yet. Queue episodes from the Library or Seerr.",
                        actions = {
                            Button(onClick = onBrowseLibrary) { Text("Browse Library") }
                        },
                    )
                }
            } else if (shown.isEmpty()) {
                item(key = "empty-seg") {
                    EmptyState(
                        icon = when (segment) {
                            0 -> Icons.Rounded.Download
                            1 -> Icons.Rounded.ErrorOutline
                            else -> Icons.Rounded.CheckCircleOutline
                        },
                        title = when (segment) {
                            0 -> "Nothing active"
                            1 -> "No failed downloads"
                            else -> "Nothing finished yet"
                        },
                        body = when (segment) {
                            0 -> "Downloads you queue will collect here."
                            1 -> "Failed downloads will collect here."
                            else -> "Finished downloads will collect here."
                        },
                        actions = if (segment == 0) {
                            { Button(onClick = onOpenSeerr) { Text("Open Seerr") } }
                        } else null,
                    )
                }
            } else {
                items(shown, key = { "t-" + it.taskId }) { task ->
                    V3TaskCard(
                        task = task,
                        poster = task.poster ?: posters[task.episodeKey.substringBefore(':')],
                        onAction = { action ->
                            selected?.let { dev ->
                                when (action) {
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

    // ---- top-bar sheets: filter / sort / overflow
    when (sheet) {
        "filter" -> V3Sheet(title = "Show", onDismiss = { sheet = null }) {
            listOf("All" to mediaCounts[0], "Movies" to mediaCounts[1], "Series" to mediaCounts[2])
                .forEachIndexed { i, (label, count) ->
                    SheetRow(
                        label = "$label  ·  $count",
                        selected = mediaTab == i,
                        onClick = { mediaTab = i; sheet = null },
                    )
                }
        }
        "sort" -> V3Sheet(title = "Sort by", onDismiss = { sheet = null }) {
            SORT_OPTIONS.forEachIndexed { i, label ->
                SheetRow(
                    label = label,
                    selected = sortMode == i,
                    onClick = { sortMode = i; sheet = null },
                )
            }
        }
        "more" -> V3Sheet(title = "Downloads", onDismiss = { sheet = null }) {
            SheetRow("Pause all", onClick = { selected?.let { vm.command(it, "pause-all") }; sheet = null })
            SheetRow("Resume all", onClick = { selected?.let { vm.command(it, "resume-all") }; sheet = null })
            SheetRow("Fix queue missing", onClick = { selected?.let { vm.command(it, "queue-missing") }; sheet = null })
            SheetRow("Clear all downloads", onClick = { selected?.let { vm.command(it, "queue-clear") }; sheet = null })
            if (failed.isNotEmpty()) {
                SheetRow("Retry all failed (${failed.size})", onClick = {
                    failed.forEach { t -> selected?.let { vm.command(it, "retry", t.taskId) } }; sheet = null
                })
                SheetRow("Delete all failed (${failed.size})", danger = true, onClick = {
                    bulkDelete = "failed" to failed; sheet = null
                })
            }
            if (finished.isNotEmpty()) {
                SheetRow("Delete all finished (${finished.size})", danger = true, onClick = {
                    bulkDelete = "finished" to finished; sheet = null
                })
            }
        }
    }

    // ---- bulk delete confirmation (failed / finished, from the ⋮ menu)
    bulkDelete?.let { (kind, rows) ->
        val dev = selected
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { bulkDelete = null },
            containerColor = c.sheet,
            title = { Text("Delete all $kind (${rows.size})?", color = c.text) },
            text = {
                Text(
                    "Delete removes every $kind download from the list and deletes the " +
                        "downloaded files from the PC. You can also just clear the list and keep the files.",
                    color = c.textSecondary,
                )
            },
            dismissButton = {
                TextButton(onClick = { bulkDelete = null }) { Text("Cancel", color = c.textSecondary) }
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

private fun stateColor(state: String, c: V3Colors): Color = when (state) {
    "done" -> c.success
    "error" -> c.error
    "paused" -> c.warning
    "downloading", "resolving" -> c.accent
    else -> c.textTertiary
}

/**
 * v3 download card: poster thumb, title/episode, state chip, meta line,
 * slim progress bar — actions hidden until the card is tapped (spec §Downloads).
 */
@Composable
private fun V3TaskCard(task: DownloadTask, poster: String?, onAction: (String) -> Unit) {
    val c = v3
    val accent = remember(task.state) { stateColor(task.state, c) }
    var confirmDelete by remember(task.taskId) { mutableStateOf(false) }
    var expanded by remember(task.taskId) { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(V3Radius.card))
            .background(c.raised)
            .clickable { expanded = !expanded }
            .padding(V3Space.md),
        verticalArrangement = Arrangement.spacedBy(V3Space.sm),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            V3PosterThumb(poster, accent)
            Spacer(Modifier.width(V3Space.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(V3Space.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        task.title,
                        style = V3Type.itemTitle,
                        color = c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(V3Space.sm))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(V3Radius.pill))
                            .background(accent.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            task.state.replaceFirstChar { it.uppercase() } +
                                (task.percent?.let { " · ${"%.0f".format(it)}%" } ?: ""),
                            style = V3Type.caption,
                            color = accent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (task.episodeLabel.isNotBlank() && task.episodeLabel != task.title) {
                    Text(
                        task.episodeLabel,
                        style = V3Type.caption,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val sizeText = when {
                    task.downloaded != null && task.total != null && task.total!! > 0 ->
                        "${taskFormatBytes(task.downloaded!!)} / ${taskFormatBytes(task.total!!)}"
                    task.total != null && task.total!! > 0 -> taskFormatBytes(task.total!!)
                    task.downloaded != null -> taskFormatBytes(task.downloaded!!)
                    else -> null
                }
                val bits = buildList {
                    sizeText?.let { add(it) }
                    task.speed?.let { add("${taskFormatBytes(it)}/s") }
                    task.eta?.let { add(it) }
                    task.quality?.let { add(it) }
                    task.source?.let { add(it.uppercase()) }
                }
                if (bits.isNotEmpty()) {
                    Text(
                        bits.joinToString(" · "),
                        style = V3Type.caption,
                        color = c.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (task.percent != null) {
            LinearProgressIndicator(
                progress = { (task.percent!! / 100).toFloat() },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = accent,
                trackColor = c.surface,
            )
        }

        task.lastErr?.takeIf { task.state == "error" }?.let {
            Text(it, color = c.error, style = V3Type.caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        // Tap-revealed actions — the default card carries no buttons.
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(V3Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (task.state) {
                    "downloading", "queued", "resolving", "stopping" -> {
                        V3CardIconAction(Icons.Rounded.Pause, "Pause download", tint = c.warning) { onAction("pause") }
                        V3CardIconAction(Icons.Rounded.Stop, "Stop download", tint = c.error) { onAction("stop") }
                    }
                    "paused" -> {
                        V3CardIconAction(Icons.Rounded.PlayArrow, "Resume download", tint = c.success) { onAction("resume") }
                        V3CardIconAction(Icons.Rounded.Stop, "Stop download", tint = c.error) { onAction("stop") }
                    }
                    "error" -> {
                        V3CardIconAction(Icons.Rounded.Refresh, "Retry download", tint = c.accent) { onAction("retry") }
                    }
                }
                Spacer(Modifier.weight(1f))
                V3CardIconAction(Icons.Rounded.Delete, "Delete download", tint = c.error) { confirmDelete = true }
            }
        }
    }

    if (confirmDelete) {
        val who = task.title +
            (task.episodeLabel.takeIf { it.isNotBlank() && it != task.title }?.let { " — $it" } ?: "")
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.sheet,
            title = { Text("Delete download?", color = c.text) },
            text = {
                Text(
                    "$who\n\nDelete removes it from the list and deletes the downloaded " +
                        "file from the PC. You can also just remove it from the list and keep the file.",
                    color = c.textSecondary,
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = c.textSecondary) }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    Button(onClick = { confirmDelete = false; onAction("delete") }) { Text("Delete with file") }
                    TextButton(onClick = { confirmDelete = false; onAction("delete-list") }) {
                        Text("Remove from list only")
                    }
                }
            },
        )
    }
}

@Composable
private fun V3PosterThumb(poster: String?, accent: Color) {
    val c = v3
    if (poster.isNullOrBlank()) {
        Box(
            Modifier
                .size(width = 54.dp, height = 81.dp)
                .clip(RoundedCornerShape(V3Radius.poster))
                .background(
                    Brush.linearGradient(listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.10f))),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Movie, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
    } else {
        AsyncImage(
            model = poster,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 54.dp, height = 81.dp)
                .clip(RoundedCornerShape(V3Radius.poster))
                .background(c.surface),
        )
    }
}
