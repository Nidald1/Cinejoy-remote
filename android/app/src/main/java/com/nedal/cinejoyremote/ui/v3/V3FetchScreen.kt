package com.nedal.cinejoyremote.ui.v3

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nedal.cinejoyremote.presentation.FetchJob
import com.nedal.cinejoyremote.presentation.FetchQueueViewModel
import com.nedal.cinejoyremote.presentation.SpeedTest
import com.nedal.cinejoyremote.ui.v3.components.EmptyState
import com.nedal.cinejoyremote.ui.v3.components.SpeedChip
import com.nedal.cinejoyremote.ui.v3.components.V3Divider
import com.nedal.cinejoyremote.ui.v3.components.V3SourcePane

private val SOURCE_LABELS = mapOf(
    "cinejoy" to "Cinejoy",
    "movy" to "Movy",
    "akwam" to "AKWAM",
)

/**
 * The fetch queue: every title the user asked links for, with all 3 sources
 * side by side and live.
 *
 * This replaces the old "Choose a source" bottom sheet. The sheet held one
 * source for one episode and died on dismissal; this screen is backed by the
 * shell-scoped [FetchQueueViewModel], so lookups keep running while the user
 * browses the rest of the app, several titles can resolve at once, and each
 * (episode, source) pair keeps its own cached result — going back to Cinejoy
 * no longer re-captures from scratch.
 *
 * The panes and the commands they send are untouched; only where they live
 * changed.
 */
@Composable
fun V3FetchScreen(
    vm: FetchQueueViewModel,
    snackbar: SnackbarHostState,
    onOpenShow: (String) -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onBrowseLibrary: () -> Unit = {},
) {
    val c = v3
    val jobs by vm.jobs.collectAsState()
    val busyKey by vm.busyKey.collectAsState()
    val error by vm.error.collectAsState()
    val notice by vm.notice.collectAsState()
    val speed by vm.speed.collectAsState()
    // Which (job, source) pane is expanded — one at a time keeps a long queue
    // readable. rememberSaveable so it survives rotation.
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.refreshStale() }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); vm.clearError() } }
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); vm.clearNotice() } }

    if (jobs.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.Download,
            title = "Nothing queued yet",
            body = "Open a movie or episode and tap Download to queue it here. " +
                "Links resolve in the background — you can keep browsing while you wait.",
            actions = {
                androidx.compose.material3.Button(
                    onClick = onBrowseLibrary,
                    shape = RoundedCornerShape(V3Radius.sm),
                ) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(V3Space.sm))
                    Text("Browse library", style = V3Type.chip)
                }
            },
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(V3Space.gutter),
        verticalArrangement = Arrangement.spacedBy(V3Space.md),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${jobs.size} title${if (jobs.size == 1) "" else "s"} queued", style = V3Type.sectionTitle, color = c.text)
                    val ready = jobs.count { it.queued }
                    val resolving = jobs.count { it.anyLoading }
                    Text(
                        when {
                            resolving > 0 -> "$resolving resolving now · ${jobs.size - resolving} ready"
                            else -> "All sources resolved"
                        },
                        style = V3Type.caption,
                        color = c.textSecondary,
                    )
                }
                if (jobs.any { it.queued }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Clear queued", style = V3Type.caption12, color = c.activeText, modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { vm.clearFinished() }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        items(jobs, key = { it.key }) { job ->
            FetchJobCard(
                job = job,
                starting = busyKey == job.key,
                expandedSource = expanded?.takeIf { it.startsWith(job.key + "|") }?.substringAfter("|"),
                onToggle = { source ->
                    val id = "${job.key}|$source"
                    expanded = if (expanded == id) null else id
                },
                onQueue = { opts -> vm.startDownload(job.key, opts) },
                onRefresh = { source -> vm.refresh(job.key, source) },
                onOpenShow = { onOpenShow(job.showId) },
                onRemove = { vm.remove(job.key) },
                onGoDownloads = onOpenDownloads,
                speed = speed,
                onSpeed = { id, url -> vm.speedTest(id, url) },
                onSpeedSource = { src -> vm.speedTestSource(job.key, src) },
            )
        }
    }
}

@Composable
private fun FetchJobCard(
    job: FetchJob,
    starting: Boolean,
    expandedSource: String?,
    onToggle: (String) -> Unit,
    onQueue: (Map<String, String>) -> Unit,
    onRefresh: (String) -> Unit,
    onOpenShow: () -> Unit,
    onRemove: () -> Unit,
    onGoDownloads: () -> Unit,
    speed: Map<String, SpeedTest>,
    onSpeed: (id: String, url: String) -> Unit,
    onSpeedSource: (source: String) -> Unit,
) {
    val c = v3
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(V3Radius.card))
            .background(c.raised)
            .padding(V3Space.md),
        verticalArrangement = Arrangement.spacedBy(V3Space.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(V3Space.md)) {
            Box(
                Modifier
                    .width(84.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(V3Radius.poster))
                    .background(c.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (job.still.isNullOrBlank()) {
                    Icon(
                        if (job.isMovie) Icons.Rounded.Download else Icons.Rounded.VideoLibrary,
                        null, tint = c.textTertiary, modifier = Modifier.size(22.dp),
                    )
                } else {
                    AsyncImage(
                        model = job.still,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    job.title,
                    style = V3Type.itemTitle,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onOpenShow),
                )
                Text(job.subtitle, style = V3Type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (job.queued) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = c.success, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(V3Space.xs))
                        Text("Queued on the PC", style = V3Type.caption, color = c.success)
                    }
                }
            }
            androidx.compose.material3.IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Delete, "Remove from queue", tint = c.textTertiary, modifier = Modifier.size(18.dp))
            }
        }

        V3Divider()

        FetchJob.SOURCE_KEYS.forEach { source ->
            val slot = job.sources[source]
            SourceRow(
                source = source,
                loading = slot?.loading == true,
                hasData = slot?.hasData == true,
                error = slot?.error,
                expanded = expandedSource == source,
                speed = speed,
                id = "${job.key}|$source",
                onSpeedTest = { onSpeedSource(source) },
                onToggle = { onToggle(source) },
                onRefresh = { onRefresh(source) },
            )
            if (expandedSource == source && slot?.data != null) {
                V3SourcePane(
                    source = source,
                    data = slot.data,
                    stateKey = job.key,
                    starting = starting,
                    onQueue = onQueue,
                    onRefresh = { onRefresh(source) },
                    speed = speed,
                    onSpeed = onSpeed,
                )
            }
        }

        if (job.queued) {
            androidx.compose.material3.TextButton(onClick = onGoDownloads) {
                Icon(Icons.Rounded.Download, null, tint = c.activeText, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(V3Space.sm))
                Text("Watch progress in Downloads", style = V3Type.chip, color = c.activeText)
            }
        }
    }
}

/** One source line: live state on the left, expand/retry on the right. */
@Composable
private fun SourceRow(
    source: String,
    loading: Boolean,
    hasData: Boolean,
    error: String?,
    expanded: Boolean,
    speed: Map<String, SpeedTest>,
    id: String,
    onSpeedTest: () -> Unit,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
) {
    val c = v3
    // Aggregate fan-out results: a source-level entry if one exists (cinejoy),
    // otherwise combine the per-server probes under "$id|…" — running while
    // any child runs, failed if they all failed, else the fastest child read.
    val test = speed[id]
        ?: speed.filterKeys { it.startsWith("$id|") }.values.let { kids ->
            when {
                kids.isEmpty() -> null
                kids.any { it.running } -> SpeedTest(running = true)
                kids.all { it.failed } -> SpeedTest(failed = true)
                else -> kids.filter { it.bytesPerSec != null }.maxByOrNull { it.bytesPerSec!! }
            }
        }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(V3Radius.sm))
            .background(if (expanded) c.accent.copy(alpha = 0.12f) else c.surface)
            .clickable(enabled = hasData || loading) { onToggle() }
            .padding(horizontal = V3Space.md, vertical = V3Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            loading && !hasData -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = c.accent)
            error != null && !hasData -> Icon(Icons.Rounded.ErrorOutline, null, tint = c.error, modifier = Modifier.size(14.dp))
            hasData -> Icon(Icons.Rounded.CheckCircle, null, tint = c.success, modifier = Modifier.size(14.dp))
            else -> Box(Modifier.size(8.dp).clip(CircleShape).background(c.textTertiary))
        }
        Spacer(Modifier.width(V3Space.sm))
        Text(
            SOURCE_LABELS[source] ?: source,
            style = V3Type.body14,
            color = when {
                hasData -> c.text
                error != null && !hasData -> c.error
                else -> c.textSecondary
            },
            modifier = Modifier.weight(1f),
        )
        Text(
            when {
                loading && hasData -> "refreshing…"
                loading -> "fetching…"
                error != null && !hasData -> error.take(38)
                hasData -> "ready"
                else -> "idle"
            },
            style = V3Type.caption,
            color = when {
                loading -> c.accent
                error != null && !hasData -> c.error
                hasData -> c.success
                else -> c.textTertiary
            },
        )
        Spacer(Modifier.width(V3Space.sm))
        SpeedChip(test = test, onClick = onSpeedTest)
        if (error != null || hasData) {
            androidx.compose.material3.IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Refresh, "Refresh ${SOURCE_LABELS[source]}", tint = c.textTertiary, modifier = Modifier.size(16.dp))
            }
        }
    }
}
