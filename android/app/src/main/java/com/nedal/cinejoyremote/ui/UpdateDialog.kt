package com.nedal.cinejoyremote.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nedal.cinejoyremote.presentation.UpdateUiState
import com.nedal.cinejoyremote.update.UpdateInfo

/**
 * The in-app update popup. One dialog covers every step of the flow:
 * release notes → download progress → "confirm on the system sheet".
 * Dark-only app, so the gradients are tuned for the teal scheme.
 */
@Composable
fun UpdateDialog(
    state: UpdateUiState,
    installedVersion: String,
    onDownload: (UpdateInfo) -> Unit,
    onCancel: (UpdateInfo) -> Unit,
    onDismiss: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val info: UpdateInfo = when (state) {
        is UpdateUiState.Available -> state.info
        is UpdateUiState.Downloading -> state.info
        is UpdateUiState.NeedsPermission -> state.info
        is UpdateUiState.Ready -> state.info
        is UpdateUiState.Error -> state.info ?: return
        else -> return
    }

    Dialog(onDismissRequest = { onDismiss(info.version) }) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.animateContentSize()) {
                // ---- branded header band
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    com.nedal.cinejoyremote.ui.theme.MorpheBrandBlue,
                                    com.nedal.cinejoyremote.ui.theme.MorpheBrandTeal,
                                ),
                            ),
                        )
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                "Update available",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                VersionChip("v$installedVersion", dim = true)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "→",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(6.dp))
                                VersionChip(info.version, dim = false)
                            }
                        }
                    }
                }

                Column(
                    Modifier
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                ) {
                    when (state) {
                        is UpdateUiState.Available -> AvailableContent(info, onDownload, onDismiss)

                        is UpdateUiState.Downloading -> DownloadingContent(info, state.progress, onCancel)

                        is UpdateUiState.NeedsPermission -> PermissionContent(info, onDownload)

                        is UpdateUiState.Ready -> ReadyContent(onReopen = { onDownload(info) })

                        is UpdateUiState.Error -> ErrorContent(
                            message = state.message,
                            onRetry = { onDownload(info) },
                        )

                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionChip(version: String, dim: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (dim) {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.30f)
        },
    ) {
        Text(
            version,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun AvailableContent(
    info: UpdateInfo,
    onDownload: (UpdateInfo) -> Unit,
    onDismiss: (String) -> Unit,
) {
    val notes = rememberNotes(info.notes)
    if (notes.isNotEmpty()) {
        Text(
            "What's new",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                notes.forEach { line ->
                    if (line.header) {
                        Text(
                            line.text,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Row {
                            Text(
                                "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                line.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    Button(
        onClick = { onDownload(info) },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text("Download & install", style = MaterialTheme.typography.titleSmall)
    }
    TextButton(
        onClick = { onDismiss(info.version) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Later", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DownloadingContent(
    info: UpdateInfo,
    progress: Float?,
    onCancel: (UpdateInfo) -> Unit,
) {
    Text(
        "Downloading ${info.version}…",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(12.dp))
    LinearProgressIndicator(
        progress = { progress ?: 0f },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        if (progress != null) "${(progress * 100).toInt()}% — the install sheet opens when it finishes"
        else "Starting…",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(14.dp))
    TextButton(
        onClick = { onCancel(info) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PermissionContent(info: UpdateInfo, onDownload: (UpdateInfo) -> Unit) {
    Text(
        "One-time permission",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Android blocks installs from unknown apps until you allow it once. " +
            "Turn on \"Allow from this source\" for CineJoy Remote on the next screen, " +
            "then come back and tap Install update.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { onDownload(info) },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text("Continue", style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ReadyContent(onReopen: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            "Ready — confirm the install on the system screen.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Spacer(Modifier.height(14.dp))
    OutlinedButton(
        onClick = onReopen,
        modifier = Modifier.fillMaxWidth().height(46.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text("Open install screen again", style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            message,
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
    Spacer(Modifier.height(14.dp))
    Button(
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth().height(46.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text("Try again", style = MaterialTheme.typography.titleSmall)
    }
}

private data class NoteLine(val text: String, val header: Boolean)

// GitHub auto-notes are markdown-ish: "## What's Changed" headers and
// "* item by @user in #12" bullets. Flatten to clean lines for the popup.
@Composable
private fun rememberNotes(raw: String): List<NoteLine> {
    val lines = raw.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("**Full Changelog**") && !it.startsWith("## v") }
    return buildList {
        for (line in lines) {
            when {
                line.startsWith("## ") ->
                    add(NoteLine(line.removePrefix("## "), header = true))

                line.startsWith("* ") || line.startsWith("- ") -> {
                    val cleaned = line.drop(2)
                        .replace(Regex("\\s*by @\\S+.*$"), "")
                        .trimEnd()
                    if (cleaned.isNotBlank()) add(NoteLine(cleaned, header = false))
                }

                !line.startsWith("#") && !line.startsWith("**") ->
                    add(NoteLine(line, header = false))
            }
        }
    }.take(24)
}
