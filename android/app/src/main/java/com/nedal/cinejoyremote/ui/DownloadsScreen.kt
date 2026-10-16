package com.nedal.cinejoyremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nedal.cinejoyremote.domain.DownloadTask
import com.nedal.cinejoyremote.presentation.DevicesViewModel
import com.nedal.cinejoyremote.presentation.DownloadsViewModel
import com.nedal.cinejoyremote.ui.components.EmptyState
import com.nedal.cinejoyremote.ui.components.SmallIconButton
import com.nedal.cinejoyremote.ui.theme.Spacing
import com.nedal.cinejoyremote.ui.theme.StatusColors
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

// Movies and series live on separate screens. The library mirror is the
// authority; the show id pattern ("<slug>-movie|tv-<tmdb>") covers rows whose
// show has since been removed from the library.
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

@Composable
fun DownloadsScreen(
    devicesVm: DevicesViewModel,
    vm: DownloadsViewModel,
    snackbar: SnackbarHostState,
    onBrowseLibrary: () -> Unit = {},
    onOpenSeerr: () -> Unit = {},
) {
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
    var mediaTab by rememberSaveable { mutableIntStateOf(0) } // 0 = all, 1 = movies, 2 = series
    var segment by rememberSaveable { mutableIntStateOf(0) }
    var sortMode by rememberSaveable { mutableIntStateOf(2) }
    // "failed" / "finished" bulk delete confirmation (which group + its rows).
    var bulkDelete by remember { mutableStateOf<Pair<String, List<DownloadTask>>?>(null) }
    // Design v2: filter / sort / overflow live as top-bar icons opening
    // bottom sheets — no more pill farms or octagon buttons in the layout.
    var sheet by remember { mutableStateOf<String?>(null) } // "filter" | "sort" | "more"
    ProvideTopBar(
        title = "Downloads",
        actions = buildList {
            if (selected != null) {
                add(ChromeAction(Icons.Rounded.FilterList, "Filter downloads") { sheet = "filter" })
                add(ChromeAction(Icons.Rounded.Sort, "Sort downloads") { sheet = "sort" })
                add(
                    ChromeAction(
                        if (queuePaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        if (queuePaused) "Resume queue" else "Pause queue",
                    ) {
                        queuePaused = !queuePaused
                        vm.queuePause(queuePaused)
                    },
                )
                add(ChromeAction(Icons.Rounded.MoreVert, "More download actions") { sheet = "more" })
            }
        },
    )

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

    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.gutter)) {
        // Thin banner — only when the queue actually IS paused.
        if (queuePaused && selected != null) {
            Surface(
                color = StatusColors.Warning.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.height(32.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Queue paused",
                        style = MaterialTheme.typography.labelMedium,
                        color = StatusColors.Warning,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Resume",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clickable {
                                queuePaused = false
                                vm.queuePause(false)
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // ---- slim underline tabs: Active | Failed | Finished
        if (selected != null) {
            UnderlineTabs(
                labels = SEGMENTS,
                counts = counts,
                selected = segment,
                onSelect = { segment = it },
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Spacing.listGap),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = Spacing.sm,
                bottom = Spacing.bottomClearance,
            ),
        ) {
            if (tasks.isEmpty()) {
                item(key = "empty") {
                    CineEmpty(
                        icon = Icons.Rounded.Download,
                        message = "Nothing here yet. Queue episodes from the Library or Seerr.",
                        modifier = Modifier.fillMaxWidth(),
                        actionLabel = "Browse Library",
                        onAction = onBrowseLibrary,
                    )
                }
            } else if (shown.isEmpty()) {
                item(key = "empty-seg") {
                    CineEmpty(
                        icon = when (segment) {
                            0 -> Icons.Rounded.Download
                            1 -> Icons.Rounded.ErrorOutline
                            else -> Icons.Rounded.CheckCircleOutline
                        },
                        message = when (segment) {
                            0 -> "Nothing downloading right now."
                            1 -> "No failed downloads."
                            else -> "Finished downloads will collect here."
                        },
                        modifier = Modifier.fillMaxWidth(),
                        actionLabel = if (segment == 0) "Open Seerr" else null,
                        onAction = if (segment == 0) onOpenSeerr else null,
                    )
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
    }    // ---- top-bar sheets: filter / sort / overflow
    when (sheet) {
        "filter" -> CineSheet(title = "Show", onDismiss = { sheet = null }) {
            listOf("All" to mediaCounts[0], "Movies" to mediaCounts[1], "Series" to mediaCounts[2])
                .forEachIndexed { i, (label, count) ->
                    SheetOption(
                        label = "$label  ·  $count",
                        selected = mediaTab == i,
                        onClick = { mediaTab = i; sheet = null },
                    )
                }
        }
        "sort" -> CineSheet(title = "Sort by", onDismiss = { sheet = null }) {
            SORT_OPTIONS.forEachIndexed { i, label ->
                SheetOption(
                    label = label,
                    selected = sortMode == i,
                    onClick = { sortMode = i; sheet = null },
                )
            }
        }
        "more" -> CineSheet(title = "Downloads", onDismiss = { sheet = null }) {
            SheetOption("Pause all", onClick = { selected?.let { vm.command(it, "pause-all") }; sheet = null })
            SheetOption("Resume all", onClick = { selected?.let { vm.command(it, "resume-all") }; sheet = null })
            SheetOption("Fix queue missing", onClick = { selected?.let { vm.command(it, "queue-missing") }; sheet = null })
            SheetOption("Clear all downloads", onClick = { selected?.let { vm.command(it, "queue-clear") }; sheet = null })
            if (failed.isNotEmpty()) {
                SheetOption("Retry all failed (${failed.size})", onClick = {
                    failed.forEach { t -> selected?.let { vm.command(it, "retry", t.taskId) } }; sheet = null
                })
                SheetOption("Delete all failed (${failed.size})", danger = true, onClick = {
                    bulkDelete = "failed" to failed; sheet = null
                })
            }
            if (finished.isNotEmpty()) {
                SheetOption("Delete all finished (${finished.size})", danger = true, onClick = {
                    bulkDelete = "finished" to finished; sheet = null
                })
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

/** Text-only underline tabs with tiny count badges — 40dp tall. */
@Composable
private fun UnderlineTabs(
    labels: List<String>,
    counts: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(40.dp)) {
        labels.forEachIndexed { i, label ->
            val isSelected = selected == i
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (counts[i] > 0) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            counts[i].toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .width(if (isSelected) 22.dp else 0.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun taskColor(task: DownloadTask): androidx.compose.ui.graphics.Color =
    when (task.state) {
        "done" -> StatusColors.Success
        "error" -> MaterialTheme.colorScheme.error
        "paused" -> StatusColors.Warning
        "downloading", "resolving" -> StatusColors.Info
        else -> StatusColors.Neutral
    }

@Composable
fun TaskCard(task: DownloadTask, poster: String? = null, onAction: (String) -> Unit) {
    val accent = taskColor(task)
    var confirmDelete by remember(task.taskId) { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
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
                                    tint = StatusColors.Success,
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
                                tint = StatusColors.Info,
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
    val shape = MaterialTheme.shapes.extraSmall
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
    // 32dp chip inside a 48dp touch target (SmallIconButton).
    SmallIconButton(icon = icon, contentDescription = desc, onClick = onClick, tint = tint)
}

fun formatBytes(n: Long): String {
    if (n < 1024) return "$n B"
    val units = listOf("KB", "MB", "GB")
    var v = n / 1024.0
    var u = 0
    while (v >= 1024 && u < units.size - 1) { v /= 1024; u++ }
    return "%.1f %s".format(v, units[u])
}
