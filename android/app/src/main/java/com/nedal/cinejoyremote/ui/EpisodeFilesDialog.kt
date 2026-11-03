package com.nedal.cinejoyremote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nedal.cinejoyremote.presentation.EpisodeFile
import com.nedal.cinejoyremote.presentation.EpisodeFilesState
import java.util.Locale

/**
 * The delete picker: every file the episode owns on the PC — each source's video
 * (the same episode can be on disk twice), unfinished `.part` leftovers, the
 * subtitles published next to those videos and the archived originals — with a
 * tick per file. Everything starts ticked so the common case is one tap, and
 * unticking keeps the rest.
 */
@Composable
fun EpisodeFilesDialog(
    state: EpisodeFilesState,
    onDismiss: () -> Unit,
    onDelete: (List<String>) -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    bodyColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    dangerColor: Color = MaterialTheme.colorScheme.error,
    checkColor: Color = MaterialTheme.colorScheme.primary,
) {
    when (state) {
        EpisodeFilesState.Idle -> Unit

        is EpisodeFilesState.Loading -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = containerColor,
            title = { Text("Delete episode files", color = titleColor) },
            text = { Text("Asking the PC which files this episode owns…", color = bodyColor) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = bodyColor) } },
        )

        is EpisodeFilesState.Deleting -> AlertDialog(
            onDismissRequest = {},
            containerColor = containerColor,
            title = { Text("Deleting…", color = titleColor) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = checkColor)
                    Spacer(Modifier.width(10.dp))
                    Text("Removing ${state.count} file(s) from the PC disk", color = bodyColor)
                }
            },
            confirmButton = {},
        )

        is EpisodeFilesState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = containerColor,
            title = { Text("Could not list the files", color = titleColor) },
            text = { Text(state.message, color = bodyColor) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = bodyColor) } },
        )

        is EpisodeFilesState.Ready -> {
            val files = state.files
            var picked by remember(state.key, files) {
                mutableStateOf(files.map { it.path }.toSet())
            }
            val pickedBytes = files.filter { it.path in picked }.sumOf { it.size }
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = containerColor,
                title = {
                    Text(
                        if (files.isEmpty()) "Nothing to delete" else "Delete episode files",
                        color = titleColor,
                    )
                },
                text = {
                    if (files.isEmpty()) {
                        Text(
                            "The PC has no files for this episode right now — nothing is on disk.",
                            color = bodyColor,
                        )
                    } else {
                        Column(
                            Modifier
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                "${picked.size} of ${files.size} selected · ${formatBytes(pickedBytes)}",
                                color = bodyColor,
                                fontSize = 12.sp,
                            )
                            files.groupBy { it.kind }.forEach { (kind, group) ->
                                Text(
                                    kindTitle(kind, group.size),
                                    color = titleColor,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                                )
                                group.forEach { f ->
                                    val checked = f.path in picked
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                picked = if (checked) picked - f.path else picked + f.path
                                            }
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { on ->
                                                picked = if (on) picked + f.path else picked - f.path
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = checkColor),
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                f.name,
                                                color = titleColor,
                                                fontSize = 13.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                fileSubtitle(f),
                                                color = bodyColor,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { onDelete(picked.toList()) },
                        enabled = picked.isNotEmpty(),
                    ) {
                        Text(
                            "Delete ${picked.size}",
                            color = if (picked.isEmpty()) bodyColor else dangerColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                dismissButton = {
                    Row {
                        if (files.isNotEmpty() && picked.size < files.size) {
                            TextButton(onClick = { picked = files.map { it.path }.toSet() }) {
                                Text("Select all", color = titleColor)
                            }
                        }
                        TextButton(onClick = onDismiss) { Text("Cancel", color = bodyColor) }
                    }
                },
            )
        }
    }
}

private fun kindTitle(kind: String, count: Int): String = when (kind) {
    "video" -> "Videos ($count)"
    "part" -> "Unfinished downloads ($count)"
    "subtitle" -> "Subtitles next to the video ($count)"
    "archive" -> "Archived subtitles ($count)"
    else -> "Other files ($count)"
}

private fun fileSubtitle(f: EpisodeFile): String {
    val size = formatBytes(f.size)
    return when (f.kind) {
        "video" -> "Video · $size"
        "part" -> "Partial download · $size"
        "subtitle" -> if (f.lang.isBlank()) "Subtitle · $size" else "${f.lang.uppercase(Locale.US)} subtitle · $size"
        "archive" -> if (f.lang.isBlank()) "Archive · $size" else "Archive ${f.lang.uppercase(Locale.US)} · $size"
        else -> size
    }
}

// formatBytes() lives in DownloadsScreen.kt (same package) and is shared here.
