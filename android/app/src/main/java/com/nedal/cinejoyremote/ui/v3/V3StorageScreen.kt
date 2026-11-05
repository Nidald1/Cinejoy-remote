package com.nedal.cinejoyremote.ui.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.nedal.cinejoyremote.domain.StorageEpisode
import com.nedal.cinejoyremote.domain.StorageFileEntry
import com.nedal.cinejoyremote.domain.StorageLargeFile
import com.nedal.cinejoyremote.domain.StorageMovie
import com.nedal.cinejoyremote.domain.StorageSeason
import com.nedal.cinejoyremote.domain.StorageShow
import com.nedal.cinejoyremote.domain.StorageSnapshot
import com.nedal.cinejoyremote.domain.StorageSuggestion
import com.nedal.cinejoyremote.presentation.StorageUiState
import com.nedal.cinejoyremote.presentation.StorageViewModel
import com.nedal.cinejoyremote.ui.v3.components.V3Pill

/**
 * Storage manager: what is on the PC's disks, what can be reclaimed, and a
 * hierarchical browser. "Watched" is decided by Jellyfin (the PC mirrors its
 * user data); nothing here guesses from filenames.
 */
@Composable
fun V3StorageScreen(vm: StorageViewModel, snackbar: SnackbarHostState) {
    val state by vm.state.collectAsState()
    val busy by vm.busy.collectAsState()
    val deleting by vm.deleting.collectAsState()
    val notice by vm.notice.collectAsState()

    LaunchedEffect(Unit) { vm.load(force = false) }
    LaunchedEffect(notice) {
        notice?.let { snackbar.showSnackbar(it); vm.clearNotice() }
    }

    Box(Modifier.fillMaxSize().background(v3.background)) {
        when (val s = state) {
            is StorageUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = v3.accent)
                    Spacer(Modifier.height(V3Space.md))
                    Text("Scanning the PC's library…", style = V3Type.body, color = v3.textSecondary)
                }
            }

            is StorageUiState.Error -> Box(Modifier.fillMaxSize().padding(V3Space.gutter), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(V3Space.md)) {
                    Icon(Icons.Rounded.Warning, null, tint = v3.warning, modifier = Modifier.size(40.dp))
                    Text("Storage unavailable", style = V3Type.sectionTitle, color = v3.text)
                    Text(s.message, style = V3Type.body, color = v3.textSecondary)
                    Button(onClick = { vm.load(force = true) }, shape = RoundedCornerShape(V3Radius.sm)) {
                        Text("Try again", style = V3Type.chip)
                    }
                }
            }

            is StorageUiState.Ready -> StorageContent(
                snap = s.snapshot,
                refreshing = busy,
                deleting = deleting,
                onRefresh = { vm.load(force = true) },
                onDelete = { vm.delete(it) },
            )
        }
        if (deleting) {
            Box(
                Modifier.fillMaxSize().background(v3.background.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(color = v3.sheet, shape = RoundedCornerShape(V3Radius.card)) {
                    Row(Modifier.padding(V3Space.lg), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = v3.accent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(V3Space.md))
                        Text("Moving to the Recycle Bin…", style = V3Type.body, color = v3.text)
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageContent(
    snap: StorageSnapshot,
    refreshing: Boolean,
    deleting: Boolean,
    onRefresh: () -> Unit,
    onDelete: (List<String>) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var pending by remember { mutableStateOf<Pair<List<String>, Long>?>(null) }
    var detail by remember { mutableStateOf<StorageSuggestion?>(null) }

    val sizeByPath = remember(snap) {
        buildMap {
            snap.movies.forEach { movie ->
                put(movie.path, movie.bytes)
                movie.files.forEach { put(it.path, it.size) }
            }
            snap.shows.forEach { show ->
                show.seasons.forEach { season ->
                    put(season.path, season.bytes)
                    season.episodes.forEach { ep ->
                        val share = ep.bytes / ep.deletePaths.size.coerceAtLeast(1)
                        ep.deletePaths.forEach { put(it, share) }
                    }
                }
            }
            snap.largeFiles.forEach { put(it.path, it.size) }
        }
    }
    val selectedBytes = selected.sumOf { sizeByPath[it] ?: 0L }

    LaunchedEffect(snap.scannedAt) { selected = emptySet() }

    val browseRows = remember(snap, expanded) {
        buildList {
            if (snap.movies.isNotEmpty()) add(BrowseRow.Header("Movies · ${snap.movies.size}"))
            snap.movies.forEach { movie ->
                add(BrowseRow.Movie(movie))
                if (expanded.contains("movie:${movie.path}")) {
                    movie.files.forEach { add(BrowseRow.File(it, 1)) }
                }
            }
            if (snap.shows.isNotEmpty()) add(BrowseRow.Header("Series · ${snap.shows.size}"))
            snap.shows.forEach { show ->
                add(BrowseRow.Show(show))
                if (expanded.contains("show:${show.path}")) {
                    show.seasons.forEach { season ->
                        add(BrowseRow.Season(season))
                        if (expanded.contains("season:${season.path}")) {
                            season.episodes.forEach { add(BrowseRow.Episode(it)) }
                        }
                    }
                }
            }
        }
    }

    fun toggle(paths: List<String>) {
        if (paths.isEmpty()) return
        selected = if (paths.all { selected.contains(it) }) selected - paths.toSet() else selected + paths.toSet()
    }

    fun toggleExpanded(key: String) {
        expanded = if (expanded.contains(key)) expanded - key else expanded + key
    }

    val openSuggestion = detail
    if (openSuggestion != null) {
        SuggestionDetailScreen(
            snap = snap,
            suggestion = openSuggestion,
            deleting = deleting,
            onBack = { detail = null },
            onDelete = onDelete,
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(V3Space.sm),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "overview") {
                Box(Modifier.padding(horizontal = V3Space.gutter)) {
                    StorageOverview(snap, refreshing, onRefresh)
                }
            }

            if (snap.warnings.isNotEmpty()) {
                item(key = "warnings") {
                    Box(Modifier.padding(horizontal = V3Space.gutter)) {
                        InfoCard(
                            title = "Scan notes",
                            body = snap.warnings.joinToString("\n"),
                            tint = v3.warning,
                        )
                    }
                }
            }

            if (snap.suggestions.isNotEmpty()) {
                item(key = "suggestions-title") {
                    Text(
                        "Ways to free up space",
                        style = V3Type.sectionTitle,
                        color = v3.text,
                        modifier = Modifier.padding(horizontal = V3Space.gutter).padding(top = V3Space.md),
                    )
                }
                items(snap.suggestions, key = { "suggestion:${it.id}" }) { suggestion ->
                    Box(Modifier.padding(horizontal = V3Space.gutter)) {
                        SuggestionCard(suggestion) {
                            if (suggestion.paths.isNotEmpty()) detail = suggestion
                        }
                    }
                }
            }

            item(key = "tabs") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = V3Space.gutter, vertical = V3Space.sm),
                    horizontalArrangement = Arrangement.spacedBy(V3Space.sm),
                ) {
                    V3Pill("Browse", tab == 0, { tab = 0 })
                    V3Pill("Large files", tab == 1, { tab = 1 })
                    V3Pill("Watched", tab == 2, { tab = 2 })
                }
            }

            when (tab) {
                0 -> {
                    if (browseRows.isEmpty()) {
                        item(key = "browse-empty") {
                            Box(Modifier.padding(horizontal = V3Space.gutter)) {
                                InfoCard(
                                    title = "Nothing found",
                                    body = "No video files in ${snap.roots.joinToString(" or ") { it.path }}.",
                                    tint = v3.textTertiary,
                                )
                            }
                        }
                    }
                    items(browseRows, key = { it.key }) { row ->
                        when (row) {
                            is BrowseRow.Header -> Text(
                                row.text,
                                style = V3Type.caption,
                                color = v3.textTertiary,
                                modifier = Modifier.padding(horizontal = V3Space.gutter).padding(top = V3Space.sm),
                            )

                            is BrowseRow.Movie -> MovieRow(
                                movie = row.movie,
                                expanded = expanded.contains("movie:${row.movie.path}"),
                                onExpand = { toggleExpanded("movie:${row.movie.path}") },
                                checked = row.movie.deletePaths.isNotEmpty()
                                    && row.movie.deletePaths.all { selected.contains(it) },
                                onCheck = { toggle(row.movie.deletePaths) },
                            )

                            is BrowseRow.Show -> ShowRow(
                                show = row.show,
                                expanded = expanded.contains("show:${row.show.path}"),
                                onExpand = { toggleExpanded("show:${row.show.path}") },
                            )

                            is BrowseRow.Season -> SeasonRow(
                                season = row.season,
                                expanded = expanded.contains("season:${row.season.path}"),
                                onExpand = { toggleExpanded("season:${row.season.path}") },
                                checked = row.season.deletePaths.isNotEmpty()
                                    && row.season.deletePaths.all { selected.contains(it) },
                                onCheck = { toggle(row.season.deletePaths) },
                            )

                            is BrowseRow.Episode -> EpisodeRow(
                                episode = row.episode,
                                checked = row.episode.deletePaths.isNotEmpty()
                                    && row.episode.deletePaths.all { selected.contains(it) },
                                onCheck = { toggle(row.episode.deletePaths) },
                            )

                            is BrowseRow.File -> FileRow(
                                file = row.file,
                                depth = row.depth,
                                checked = selected.contains(row.file.path),
                                onCheck = { toggle(listOf(row.file.path)) },
                            )
                        }
                    }
                }

                1 -> {
                    if (snap.largeFiles.isEmpty()) {
                        item(key = "large-empty") {
                            Box(Modifier.padding(horizontal = V3Space.gutter)) {
                                InfoCard("No files found", "The library folders are empty.", v3.textTertiary)
                            }
                        }
                    }
                    items(snap.largeFiles, key = { it.path }) { file ->
                        Box(Modifier.padding(horizontal = V3Space.gutter)) {
                            LargeFileRow(
                                file = file,
                                checked = selected.contains(file.path),
                                onCheck = { toggle(listOf(file.path)) },
                            )
                        }
                    }
                }

                else -> {
                    item(key = "jellyfin-status") {
                        Box(Modifier.padding(horizontal = V3Space.gutter)) {
                            JellyfinCard(snap)
                        }
                    }
                    val watched = snap.suggestions.filter { it.kind == "watched" }
                    if (watched.isEmpty()) {
                        item(key = "watched-empty") {
                            Box(Modifier.padding(horizontal = V3Space.gutter)) {
                                InfoCard(
                                    "Nothing watched yet",
                                    if (!snap.jellyfin.configured) {
                                        "Connect Jellyfin on the PC's Remote tab and CineJoy will know exactly what you finished."
                                    } else {
                                        "Finish something in Jellyfin and it will show up here, ready to delete."
                                    },
                                    v3.textTertiary,
                                )
                            }
                        }
                    } else {
                        items(watched, key = { "watched-tab:${it.id}" }) { suggestion ->
                            Box(Modifier.padding(horizontal = V3Space.gutter)) {
                                SuggestionCard(suggestion) {
                                    if (suggestion.paths.isNotEmpty()) detail = suggestion
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selected.isNotEmpty()) {
            Surface(
                color = v3.sheet,
                shadowElevation = 12.dp,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = V3Space.gutter, vertical = V3Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${selected.size} selected", style = V3Type.itemTitle, color = v3.text)
                        Text(fmtBytes(selectedBytes), style = V3Type.caption, color = v3.textTertiary)
                    }
                    TextButton(onClick = { selected = emptySet() }) {
                        Text("Clear", style = V3Type.chip, color = v3.textSecondary)
                    }
                    Spacer(Modifier.width(V3Space.sm))
                    Button(
                        onClick = { pending = selected.toList() to selectedBytes },
                        enabled = !deleting,
                        shape = RoundedCornerShape(V3Radius.sm),
                        colors = ButtonDefaults.buttonColors(containerColor = v3.error),
                    ) {
                        Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(V3Space.xs))
                        Text("Delete", style = V3Type.chip)
                    }
                }
            }
        }
    }

    pending?.let { (paths, bytes) ->
        AlertDialog(
            onDismissRequest = { if (!deleting) pending = null },
            title = { Text("Move to the Recycle Bin?") },
            text = {
                Text(
                    "${paths.size} item(s) · ${fmtBytes(bytes)}\n\n" +
                        "The PC moves them to the Windows Recycle Bin, so you can restore them. " +
                        "Jellyfin will drop them from its library.",
                    style = V3Type.body,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { pending = null; onDelete(paths) },
                    enabled = !deleting,
                ) { Text("Delete", color = v3.error) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }, enabled = !deleting) {
                    Text("Cancel", color = v3.textSecondary)
                }
            },
        )
    }
}

// ---------------------------------------------------------------- overview

@Composable
private fun StorageOverview(snap: StorageSnapshot, refreshing: Boolean, onRefresh: () -> Unit) {
    val c = v3
    val volume = snap.volumes.firstOrNull()
    val total = volume?.total ?: 0L
    val free = volume?.free ?: 0L
    val used = volume?.used ?: 0L
    val usedFraction = if (total > 0) (used.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f
    val low = total > 0 && free.toDouble() / total < 0.15
    val other = (used - snap.totals.bytes).coerceAtLeast(0)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(V3Radius.card))
            .background(c.raised)
            .padding(V3Space.lg),
        verticalArrangement = Arrangement.spacedBy(V3Space.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Storage, null, tint = c.activeText, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(V3Space.sm))
            Column(Modifier.weight(1f)) {
                Text("Disk space", style = V3Type.caption, color = c.textTertiary)
                Text(
                    if (volume != null) "${fmtBytes(free)} free of ${fmtBytes(total)}" else "Library ${fmtBytes(snap.totals.bytes)}",
                    style = V3Type.sectionTitle,
                    color = c.text,
                )
            }
            androidx.compose.material3.IconButton(onClick = onRefresh, enabled = !refreshing) {
                if (refreshing) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, "Rescan", tint = c.textSecondary)
                }
            }
        }

        if (total > 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(V3Radius.pill))
                    .background(c.surface),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(usedFraction)
                        .height(10.dp)
                        .clip(RoundedCornerShape(V3Radius.pill))
                        .background(if (low) c.warning else c.accent),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(V3Space.md)) {
                Legend("Library", snap.totals.bytes, c.activeText, Modifier.weight(1f))
                Legend("Other used", other, c.textTertiary, Modifier.weight(1f))
                Legend("Free", free, if (low) c.warning else c.success, Modifier.weight(1f))
            }
            Text(
                "${volume?.path ?: ""} · ${(usedFraction * 100).toInt()}% used" +
                    if (low) " · running low" else "",
                style = V3Type.caption,
                color = if (low) c.warning else c.textTertiary,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(if (snap.jellyfin.connected) c.success else c.textTertiary),
            )
            Spacer(Modifier.width(V3Space.sm))
            Text(
                when {
                    snap.jellyfin.connected -> "Jellyfin connected${snap.jellyfin.server.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}"
                    snap.jellyfin.configured -> "Jellyfin: ${snap.jellyfin.error ?: "not reachable"}"
                    else -> "Jellyfin not connected — watched cleanup is off"
                },
                style = V3Type.caption,
                color = c.textTertiary,
            )
        }
    }
}

@Composable
private fun Legend(label: String, bytes: Long, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(5.dp))
            Text(label, style = V3Type.caption, color = v3.textTertiary, maxLines = 1)
        }
        Text(fmtBytes(bytes), style = V3Type.caption12, color = v3.text, maxLines = 1)
    }
}

// ---------------------------------------------------------------- cards/rows

@Composable
private fun SuggestionCard(suggestion: StorageSuggestion, onOpen: () -> Unit) {
    val c = v3
    val canDelete = suggestion.paths.isNotEmpty()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(V3Radius.card))
            .background(c.surface)
            .padding(V3Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(V3Space.md),
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .background(if (suggestion.kind == "low-space") c.warning.copy(alpha = 0.16f) else c.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (suggestion.kind == "low-space") Icons.Rounded.Warning else Icons.Rounded.Delete,
                null,
                tint = if (suggestion.kind == "low-space") c.warning else c.activeText,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(suggestion.title, style = V3Type.itemTitle, color = c.text)
            Text(suggestion.subtitle, style = V3Type.caption, color = c.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (suggestion.bytes > 0) {
                Text(fmtBytes(suggestion.bytes), style = V3Type.caption12, color = c.activeText)
            }
        }
        if (canDelete) {
            TextButton(onClick = onOpen) { Text("Delete", style = V3Type.chip, color = c.error) }
        }
    }
}

@Composable
private fun MovieRow(
    movie: StorageMovie,
    expanded: Boolean,
    onExpand: () -> Unit,
    checked: Boolean,
    onCheck: () -> Unit,
) {
    val c = v3
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = V3Space.gutter)
            .clip(RoundedCornerShape(V3Radius.sm))
            .clickable(onClick = onExpand)
            .padding(vertical = V3Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onCheckedChange = { onCheck() }, colors = checkboxColors())
        Icon(Icons.Rounded.Movie, null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(V3Space.sm))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(movie.title, style = V3Type.body14, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (movie.watched) {
                    Spacer(Modifier.width(V3Space.xs))
                    WatchedChip()
                }
            }
            Text("${movie.files.size} file(s) · ${fmtBytes(movie.bytes)}", style = V3Type.caption, color = c.textTertiary)
            JfProgress(movie.watched, movie.positionSeconds, movie.runtimeSeconds)
        }
        Icon(
            if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
            "Files",
            tint = c.textTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ShowRow(show: StorageShow, expanded: Boolean, onExpand: () -> Unit) {
    val c = v3
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = V3Space.gutter)
            .clip(RoundedCornerShape(V3Radius.sm))
            .clickable(onClick = onExpand)
            .padding(vertical = V3Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Tv, null, tint = c.activeText, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(V3Space.sm))
        Column(Modifier.weight(1f)) {
            Text(show.title, style = V3Type.body14, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${show.seasons.size} season(s) · ${fmtBytes(show.bytes)}", style = V3Type.caption, color = c.textTertiary)
        }
        Icon(
            if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
            "Seasons",
            tint = c.textTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SeasonRow(
    season: StorageSeason,
    expanded: Boolean,
    onExpand: () -> Unit,
    checked: Boolean,
    onCheck: () -> Unit,
) {
    val c = v3
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = V3Space.gutter + 26.dp, end = V3Space.gutter)
            .clip(RoundedCornerShape(V3Radius.sm))
            .clickable(onClick = onExpand)
            .padding(vertical = V3Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onCheckedChange = { onCheck() }, colors = checkboxColors())
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (season.season > 0) "Season ${season.season}" else "Specials", style = V3Type.caption12, color = c.textSecondary)
                if (season.watched) {
                    Spacer(Modifier.width(V3Space.xs))
                    WatchedChip()
                }
            }
            Text("${season.episodes.size} episode(s) · ${fmtBytes(season.bytes)}", style = V3Type.caption, color = c.textTertiary)
        }
        Icon(
            if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
            "Episodes",
            tint = c.textTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun EpisodeRow(episode: StorageEpisode, checked: Boolean, onCheck: () -> Unit) {
    val c = v3
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = V3Space.gutter + 52.dp, end = V3Space.gutter)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onCheckedChange = { onCheck() }, colors = checkboxColors())
        Column(Modifier.weight(1f)) {
            Text(
                "S%02dE%02d · %s".format(episode.season, episode.episode, episode.name.ifBlank { "Episode ${episode.episode}" }),
                style = V3Type.caption12,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fmtBytes(episode.bytes), style = V3Type.caption, color = c.textTertiary)
                if (episode.watched) {
                    Spacer(Modifier.width(V3Space.sm))
                    WatchedChip()
                }
                if (episode.positionSeconds > 0 && !episode.watched) {
                    Spacer(Modifier.width(V3Space.sm))
                    Text(fmtLeft(episode.runtimeSeconds - episode.positionSeconds), style = V3Type.caption, color = c.activeText)
                }
            }
        }
    }
}

@Composable
private fun FileRow(file: StorageFileEntry, depth: Int, checked: Boolean, onCheck: () -> Unit) {
    val c = v3
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = V3Space.gutter + 40.dp + (depth * 14).dp, end = V3Space.gutter)
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onCheckedChange = { onCheck() }, colors = checkboxColors())
        Icon(Icons.Rounded.InsertDriveFile, null, tint = c.textTertiary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(V3Space.xs))
        Text(file.name, style = V3Type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(fmtBytes(file.size), style = V3Type.caption, color = c.textTertiary)
    }
}

@Composable
private fun LargeFileRow(file: StorageLargeFile, checked: Boolean, onCheck: () -> Unit) {
    val c = v3
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(V3Radius.sm))
            .clickable { onCheck() }
            .padding(vertical = V3Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onCheckedChange = { onCheck() }, colors = checkboxColors())
        Column(Modifier.weight(1f)) {
            Text(file.name, style = V3Type.caption12, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(file.parent, style = V3Type.caption, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(fmtBytes(file.size), style = V3Type.caption12, color = c.textSecondary)
    }
}

@Composable
private fun JellyfinCard(snap: StorageSnapshot) {
    val c = v3
    val jf = snap.jellyfin
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(V3Radius.card))
            .background(c.surface)
            .padding(V3Space.md),
        verticalArrangement = Arrangement.spacedBy(V3Space.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(if (jf.connected) c.success else c.textTertiary),
            )
            Spacer(Modifier.width(V3Space.sm))
            Text(
                if (jf.connected) "Jellyfin · ${jf.server.ifBlank { "connected" }}" else "Jellyfin",
                style = V3Type.itemTitle,
                color = c.text,
            )
        }
        Text(
            when {
                jf.connected && jf.user.isNotBlank() -> "Watched state for ${jf.user}. Deleting sends files to the Recycle Bin."
                jf.connected -> "Watched state is live. Deleting sends files to the Recycle Bin."
                jf.configured -> "Configured but unreachable: ${jf.error ?: "check the PC's Jellyfin settings"}"
                else -> "Connect Jellyfin on the PC's Remote tab to unlock watched cleanup and live progress."
            },
            style = V3Type.caption,
            color = c.textTertiary,
        )
    }
}

@Composable
internal fun InfoCard(title: String, body: String, tint: androidx.compose.ui.graphics.Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(V3Radius.card))
            .background(v3.surface)
            .padding(V3Space.md),
        verticalArrangement = Arrangement.spacedBy(V3Space.xs),
    ) {
        Text(title, style = V3Type.itemTitle, color = v3.text)
        Text(body, style = V3Type.caption, color = tint)
    }
}

@Composable
internal fun WatchedChip() {
    val c = v3
    Row(
        Modifier
            .clip(RoundedCornerShape(V3Radius.pill))
            .background(c.success.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.CheckCircle, null, tint = c.success, modifier = Modifier.size(10.dp))
        Spacer(Modifier.width(3.dp))
        Text("watched", style = V3Type.caption, color = c.success)
    }
}

@Composable
private fun JfProgress(watched: Boolean, position: Double, runtime: Double) {
    if (!watched && (position <= 0 || runtime <= 0)) return
    val c = v3
    val fraction = if (watched) 1f else (position / runtime).toFloat().coerceIn(0f, 1f)
    Spacer(Modifier.height(2.dp))
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier.fillMaxWidth().height(3.dp),
        color = if (watched) c.success else c.activeText,
        trackColor = c.surface,
    )
    Text(
        if (watched) "Watched" else "${fmtClock(position)} / ${fmtClock(runtime)} · ${fmtLeft(runtime - position)}",
        style = V3Type.caption,
        color = if (watched) c.success else c.textTertiary,
    )
}

@Composable
internal fun checkboxColors() = CheckboxDefaults.colors(
    checkedColor = v3.accent,
    uncheckedColor = v3.textTertiary,
    checkmarkColor = androidx.compose.ui.graphics.Color.White,
)

// ---------------------------------------------------------------- rows model

private sealed interface BrowseRow {
    val key: String

    data class Header(val text: String) : BrowseRow {
        override val key get() = "header:$text"
    }

    data class Movie(val movie: StorageMovie) : BrowseRow {
        override val key get() = "movie:${movie.path}"
    }

    data class Show(val show: StorageShow) : BrowseRow {
        override val key get() = "show:${show.path}"
    }

    data class Season(val season: StorageSeason) : BrowseRow {
        override val key get() = "season:${season.path}"
    }

    data class Episode(val episode: StorageEpisode) : BrowseRow {
        override val key get() = "episode:${episode.season}:${episode.episode}:${episode.name}"
    }

    data class File(val file: StorageFileEntry, val depth: Int) : BrowseRow {
        override val key get() = "file:${file.path}"
    }
}

// ---------------------------------------------------------------- formatting

internal fun fmtBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0) "${value.toLong()} ${units[index]}" else "%.1f %s".format(value, units[index])
}

private fun fmtClock(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun fmtLeft(seconds: Double): String {
    val mins = (seconds / 60).toInt().coerceAtLeast(0)
    return when {
        mins <= 0 -> "almost done"
        mins >= 60 -> "${mins / 60} h ${mins % 60} min left"
        else -> "$mins min left"
    }
}
