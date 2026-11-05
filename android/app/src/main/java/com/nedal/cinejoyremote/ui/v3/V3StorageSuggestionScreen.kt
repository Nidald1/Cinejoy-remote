package com.nedal.cinejoyremote.ui.v3

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nedal.cinejoyremote.domain.StorageEpisode
import com.nedal.cinejoyremote.domain.StorageFileEntry
import com.nedal.cinejoyremote.domain.StorageSeason
import com.nedal.cinejoyremote.domain.StorageShow
import com.nedal.cinejoyremote.domain.StorageSnapshot
import com.nedal.cinejoyremote.domain.StorageSuggestion

/**
 * Review one cleanup suggestion before anything is deleted. The suggestion's
 * delete units are shown as selectable rows (movies, episodes or individual
 * files), so the phone can keep any subset while deleting the rest.
 */
@Composable
internal fun SuggestionDetailScreen(
    snap: StorageSnapshot,
    suggestion: StorageSuggestion,
    deleting: Boolean,
    onBack: () -> Unit,
    onDelete: (List<String>) -> Unit,
) {
    val c = v3
    val targets = remember(snap, suggestion) { buildSuggestionTargets(snap, suggestion) }
    var selected by remember(suggestion.id, snap.scannedAt) {
        mutableStateOf(targets.map { it.key }.toSet())
    }
    var confirm by remember { mutableStateOf(false) }

    val selectedTargets = targets.filter { selected.contains(it.key) }
    val selectedBytes = selectedTargets.sumOf { it.size }
    val selectedPaths = selectedTargets.flatMap { it.paths }.distinct()

    BackHandler(enabled = !deleting && !confirm) { onBack() }

    Column(Modifier.fillMaxSize().background(c.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = V3Space.gutter, vertical = V3Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, enabled = !deleting) {
                Icon(Icons.Rounded.ArrowBack, "Back", tint = c.text)
            }
            Spacer(Modifier.width(V3Space.xs))
            Column(Modifier.weight(1f)) {
                Text(suggestion.title, style = V3Type.sectionTitle, color = c.text)
                Text(
                    suggestion.subtitle,
                    style = V3Type.caption,
                    color = c.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = V3Space.gutter,
                end = V3Space.gutter,
                top = V3Space.sm,
                bottom = V3Space.md,
            ),
            verticalArrangement = Arrangement.spacedBy(V3Space.sm),
        ) {
            item(key = "suggestion-summary") {
                Surface(color = c.surface, shape = RoundedCornerShape(V3Radius.card)) {
                    Column(
                        Modifier.padding(V3Space.md),
                        verticalArrangement = Arrangement.spacedBy(V3Space.sm),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${selected.size} of ${targets.size} selected",
                                    style = V3Type.itemTitle,
                                    color = c.text,
                                )
                                Text(fmtBytes(selectedBytes), style = V3Type.caption, color = c.activeText)
                            }
                            TextButton(
                                onClick = { selected = targets.map { it.key }.toSet() },
                                enabled = !deleting,
                            ) {
                                Text("Select all", style = V3Type.chip, color = c.textSecondary)
                            }
                            TextButton(
                                onClick = { selected = emptySet() },
                                enabled = !deleting,
                            ) {
                                Text("Clear", style = V3Type.chip, color = c.textSecondary)
                            }
                        }
                        Text(
                            "Deselect anything you want to keep. The PC moves the selected items to the Windows Recycle Bin.",
                            style = V3Type.caption,
                            color = c.textTertiary,
                        )
                    }
                }
            }

            if (targets.isEmpty()) {
                item(key = "suggestion-empty") {
                    InfoCard(
                        "Nothing to list",
                        "This suggestion has no individual files to review.",
                        c.textTertiary,
                    )
                }
            }

            items(targets, key = { it.key }) { target ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(V3Radius.sm))
                        .clickable(enabled = !deleting) {
                            selected = if (selected.contains(target.key)) {
                                selected - target.key
                            } else {
                                selected + target.key
                            }
                        }
                        .padding(vertical = V3Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = selected.contains(target.key),
                        onCheckedChange = {
                            selected = if (selected.contains(target.key)) {
                                selected - target.key
                            } else {
                                selected + target.key
                            }
                        },
                        enabled = !deleting,
                        colors = checkboxColors(),
                    )
                    Icon(
                        if (target.isFolder) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                        null,
                        tint = c.textTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(V3Space.sm))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                target.title,
                                style = V3Type.body14,
                                color = c.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (target.watched) {
                                Spacer(Modifier.width(V3Space.xs))
                                WatchedChip()
                            }
                        }
                        Text(
                            target.subtitle,
                            style = V3Type.caption,
                            color = c.textTertiary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(V3Space.sm))
                    Text(fmtBytes(target.size), style = V3Type.caption, color = c.textSecondary)
                }
            }
        }

        Surface(color = c.sheet, shadowElevation = 12.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = V3Space.gutter, vertical = V3Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${selected.size} selected", style = V3Type.itemTitle, color = c.text)
                    Text(fmtBytes(selectedBytes), style = V3Type.caption, color = c.textTertiary)
                }
                Button(
                    onClick = { confirm = true },
                    enabled = selected.isNotEmpty() && selectedPaths.isNotEmpty() && !deleting,
                    shape = RoundedCornerShape(V3Radius.sm),
                    colors = ButtonDefaults.buttonColors(containerColor = c.error),
                ) {
                    Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(V3Space.xs))
                    Text("Delete selected", style = V3Type.chip)
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { if (!deleting) confirm = false },
            title = { Text("Move ${selectedTargets.size} item(s) to the Recycle Bin?") },
            text = {
                Text(
                    "${fmtBytes(selectedBytes)} will be moved to the Windows Recycle Bin. " +
                        "Anything you deselected stays on disk. Jellyfin will drop the deleted items from its library.",
                    style = V3Type.body,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = false
                        onBack()
                        onDelete(selectedPaths)
                    },
                    enabled = !deleting,
                ) { Text("Delete", color = c.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }, enabled = !deleting) {
                    Text("Cancel", color = c.textSecondary)
                }
            },
        )
    }
}

private data class SuggestionTarget(
    val key: String,
    val title: String,
    val subtitle: String,
    val paths: List<String>,
    val size: Long,
    val watched: Boolean,
    val isFolder: Boolean,
)

private fun addEpisodeTarget(
    targets: LinkedHashMap<String, SuggestionTarget>,
    show: StorageShow,
    episode: StorageEpisode,
) {
    val key = "episode:${show.path}:${episode.season}:${episode.episode}"
    val paths = episode.deletePaths.ifEmpty { episode.files.map { it.path } }
    targets[key] = SuggestionTarget(
        key = key,
        title = "S%02dE%02d · %s".format(
            episode.season,
            episode.episode,
            episode.name.ifBlank { "Episode ${episode.episode}" },
        ),
        subtitle = "${show.title} · ${paths.size} file(s)",
        paths = paths,
        size = episode.bytes,
        watched = episode.watched,
        isFolder = false,
    )
}

/**
 * Turn a suggestion's safe delete units into display rows. Watched suggestions
 * resolve movie folders, season folders and episode file groups from the scan;
 * duplicate/junk suggestions resolve individual files. Unknown paths keep the
 * suggestion's average size so the total remains useful.
 */
private fun buildSuggestionTargets(
    snap: StorageSnapshot,
    suggestion: StorageSuggestion,
): List<SuggestionTarget> {
    val movieByPath = snap.movies.associateBy { it.path }
    val seasonByPath = LinkedHashMap<String, Pair<StorageShow, StorageSeason>>()
    val episodeByPath = LinkedHashMap<String, Pair<StorageShow, StorageEpisode>>()
    val fileByPath = LinkedHashMap<String, StorageFileEntry>()

    snap.movies.forEach { movie ->
        movie.files.forEach { fileByPath[it.path] = it }
    }
    snap.shows.forEach { show ->
        show.seasons.forEach { season ->
            seasonByPath[season.path] = show to season
            season.episodes.forEach { episode ->
                episode.deletePaths.forEach { episodeByPath[it] = show to episode }
                episode.files.forEach { fileByPath[it.path] = it }
            }
        }
    }
    snap.largeFiles.forEach { file ->
        if (file.path !in fileByPath) {
            fileByPath[file.path] = StorageFileEntry(
                path = file.path,
                name = file.name,
                size = file.size,
            )
        }
    }

    val fallbackSize = if (suggestion.paths.isEmpty()) 0L else suggestion.bytes / suggestion.paths.size
    val targets = LinkedHashMap<String, SuggestionTarget>()

    for (path in suggestion.paths) {
        if (suggestion.kind == "watched") {
            val movie = movieByPath[path]
            if (movie != null) {
                targets["movie:$path"] = SuggestionTarget(
                    key = "movie:$path",
                    title = movie.title.ifBlank { path.substringAfterLast('\\') },
                    subtitle = "Movie folder · ${movie.files.size} file(s)",
                    paths = movie.deletePaths.ifEmpty { listOf(path) },
                    size = movie.bytes,
                    watched = movie.watched,
                    isFolder = true,
                )
                continue
            }

            val seasonEntry = seasonByPath[path]
            if (seasonEntry != null) {
                val (show, season) = seasonEntry
                if (season.episodes.isEmpty()) {
                    val label = if (season.season > 0) "Season ${season.season}" else "Specials"
                    targets["season:$path"] = SuggestionTarget(
                        key = "season:$path",
                        title = "${show.title} · $label",
                        subtitle = "Season folder",
                        paths = season.deletePaths.ifEmpty { listOf(path) },
                        size = season.bytes,
                        watched = season.watched,
                        isFolder = true,
                    )
                } else {
                    season.episodes.forEach { episode ->
                        addEpisodeTarget(targets, show, episode)
                    }
                }
                continue
            }

            val episodeEntry = episodeByPath[path]
            if (episodeEntry != null) {
                addEpisodeTarget(targets, episodeEntry.first, episodeEntry.second)
                continue
            }
        }

        val file = fileByPath[path]
        if (file != null) {
            targets["file:$path"] = SuggestionTarget(
                key = "file:$path",
                title = file.name.ifBlank { path.substringAfterLast('\\') },
                subtitle = file.path.substringBeforeLast('\\').ifBlank { "PC library" },
                paths = listOf(path),
                size = file.size,
                watched = file.watched,
                isFolder = false,
            )
            continue
        }

        targets["path:$path"] = SuggestionTarget(
            key = "path:$path",
            title = path.substringAfterLast('\\').ifBlank { path },
            subtitle = path,
            paths = listOf(path),
            size = fallbackSize,
            watched = false,
            isFolder = false,
        )
    }

    return targets.values.toList()
}
