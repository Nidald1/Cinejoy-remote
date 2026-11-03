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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nedal.cinejoyremote.domain.Episode
import com.nedal.cinejoyremote.domain.LibraryShow
import com.nedal.cinejoyremote.presentation.DevicesViewModel
import com.nedal.cinejoyremote.presentation.FetchQueueViewModel
import com.nedal.cinejoyremote.presentation.SharedTitleState
import com.nedal.cinejoyremote.presentation.SharedTitleViewModel
import com.nedal.cinejoyremote.presentation.ShowDetailViewModel
import com.nedal.cinejoyremote.share.TmdbHint
import com.nedal.cinejoyremote.share.TmdbRef
import com.nedal.cinejoyremote.ui.DetailPickInbox
import com.nedal.cinejoyremote.ui.v3.components.SheetRow
import com.nedal.cinejoyremote.ui.v3.components.Skeleton
import com.nedal.cinejoyremote.ui.v3.components.V3Pill
import com.nedal.cinejoyremote.ui.v3.components.V3Sheet
import com.nedal.cinejoyremote.presentation.SubtitleLangState
import com.nedal.cinejoyremote.presentation.SubtitleLinkState
import com.nedal.cinejoyremote.presentation.defaultSubtitleLangs

// ---------------------------------------------------------------- helpers
// The source-pane JSON helpers (str/int/bool/arr/heightOf) moved to
// components/V3SourcePanes.kt with the panes themselves.
private fun optStr(value: String?): String? = value?.takeIf { it.isNotBlank() && it != "null" }

private fun stateLabel(state: String): String = when (state) {
    "done" -> "Downloaded"
    "partial" -> "Partial"
    "downloading" -> "Downloading"
    else -> "Missing"
}

private fun detailFormatBytes(n: Long): String {
    if (n < 1024) return "$n B"
    val units = listOf("KB", "MB", "GB")
    var v = n / 1024.0
    var u = 0
    while (v >= 1024 && u < units.size - 1) { v /= 1024; u++ }
    return "%.1f %s".format(v, units[u])
}

/**
 * V3 detail (spec mockups 03/04): artwork fills the top and the old opaque
 * top bar becomes a transparent back + more overlay; sticky CTA row pinned
 * at the bottom plays the movie/series primary action; episodes, season
 * field + sheet, and the fetch queue live below.
 *
 * Download does NOT open a picker any more: it enqueues the episode in the
 * shell-scoped [FetchQueueViewModel], which resolves all 3 sources in the
 * background so the user can keep queueing and browsing, then reviews
 * everything on the fetch screen.
 */
@Composable
fun V3DetailScreen(
    devicesVm: DevicesViewModel,
    vm: ShowDetailViewModel,
    fetchVm: FetchQueueViewModel,
    showId: String,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpenFetch: () -> Unit = {},
) {
    LaunchedEffect(showId) { vm.load(showId) }

    val show by vm.show.collectAsState()
    val episodes by vm.episodes.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val notice by vm.notice.collectAsState()
    val subSourcesState by vm.subSources.collectAsState()
    val subLangsState by vm.subLangs.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var menuOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Episode?>(null) }
    // Episode whose saved subtitles the link dialog is showing.
    var linkTarget by remember { mutableStateOf<Episode?>(null) }
    // Episode whose subtitle-language picker is open.
    var langTarget by remember { mutableStateOf<Episode?>(null) }

    LaunchedEffect(error) { error?.let { msg -> snackbar.showSnackbar(msg); vm.clearError() } }
    LaunchedEffect(notice) { notice?.let { msg -> snackbar.showSnackbar(msg); vm.clearNotice() } }

    // Tap Download on as many episodes as you like: each one is queued for
    // background link resolution and a snackbar points at the fetch screen.
    // Nothing is modal, so browsing continues uninterrupted.
    var queuedToast by remember { mutableStateOf(0) }
    LaunchedEffect(queuedToast) {
        if (queuedToast == 0) return@LaunchedEffect
        snackbar.showSnackbar("Fetching links — see the fetch icon up top")
    }
    fun enqueue(s: LibraryShow, ep: Episode) {
        fetchVm.enqueue(s, ep)
        queuedToast++
    }

    // Seerr accept hands this showId over (DetailPickInbox): once the episodes
    // are loaded, queue the first unfinished one and jump to the fetch screen
    // so the user sees the sources resolve (the old "raise the picker" flow).
    var autoPicked by remember { mutableStateOf(false) }
    LaunchedEffect(showId, episodes) {
        if (autoPicked || episodes.isEmpty()) return@LaunchedEffect
        if (!DetailPickInbox.take(showId)) return@LaunchedEffect
        autoPicked = true
        val s = show ?: return@LaunchedEffect
        val ep = episodes.firstOrNull { it.state != "done" } ?: episodes.first()
        fetchVm.enqueue(s, ep)
        onOpenFetch()
    }

    Box(Modifier.fillMaxSize().background(v3.background)) {
        when {
            show == null -> Column(
                Modifier.fillMaxSize().padding(V3Space.gutter),
                verticalArrangement = Arrangement.spacedBy(V3Space.md),
            ) {
                Skeleton(Modifier.fillMaxWidth().height(280.dp), corner = 0.dp)
                Skeleton(Modifier.fillMaxWidth(0.6f).height(22.dp))
                Skeleton(Modifier.fillMaxWidth().height(64.dp))
                Skeleton(Modifier.fillMaxWidth().height(64.dp))
                Skeleton(Modifier.fillMaxWidth().height(64.dp))
            }

            else -> {
                val s = show!!
                val bySeason = episodes.groupBy { it.season }.toSortedMap()
                val seasons = bySeason.keys.toList()
                val doneCount = episodes.count { it.state == "done" }
                var chosenSeason by rememberSaveable(s.showId, seasons) {
                    mutableStateOf(seasonMemoryGet(context, s.showId, seasons.firstOrNull() ?: 0))
                }
                if (chosenSeason !in seasons) chosenSeason = seasons.firstOrNull() ?: 0
                var seasonSheet by remember { mutableStateOf(false) }

                // TMDb-enriched header data (backdrop, genres, Arabic synopsis,
                // cast) — same VM the share sheet uses; library data is the
                // fallback while it loads (or when there's no tmdb id).
                val sharedVm: SharedTitleViewModel = viewModel(
                    key = "detail-tmdb-${s.showId}",
                    factory = remember(s.showId) {
                        SharedTitleViewModel.factory(
                            ref = s.tmdb?.let { TmdbRef(if (s.type == "movie") "movie" else "tv", it) },
                            devicesVm = devicesVm,
                            hint = TmdbHint(
                                type = if (s.type == "movie") "movie" else "tv",
                                title = s.title,
                                year = s.year,
                            ),
                        )
                    },
                )
                val sharedState: SharedTitleState by sharedVm.state.collectAsState()

                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(V3Space.lg),
                    ) {
                        item(key = "hero") { V3DetailHero(sharedState, s, episodes, doneCount) }

                        if (busy != null) {
                            item(key = "busy") {
                                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = V3Space.gutter).height(3.dp))
                            }
                        }

                        if (s.type == "movie") {
                            items(episodes, key = { it.key }) { ep ->
                                Box(Modifier.padding(horizontal = V3Space.gutter)) {
                                    V3EpisodeCard(
                                        ep = ep,
                                        isMovie = true,
                                        onDownload = { enqueue(s, ep) },
                                        onWatched = { vm.watchedToggle(ep.key) },
                                        onSubsDownload = { langTarget = ep; vm.loadSubtitleLanguages(ep.key) },
                                        onSubsPublish = { vm.subsPublish(ep.key) },
                                        onSubsClear = { vm.subsClear(ep.key) },
                                        onSubsLink = { linkTarget = ep; vm.loadSubtitleSources(ep.key) },
                                        onDelete = { confirmDelete = ep },
                                    )
                                }
                            }
                        } else {
                            item(key = "season-select") {
                                Box(Modifier.padding(horizontal = V3Space.gutter)) {
                                    V3SeasonField(
                                        season = chosenSeason,
                                        episodeCount = bySeason[chosenSeason]?.size ?: 0,
                                        onClick = { seasonSheet = true },
                                    )
                                }
                            }
                            item(key = "season-hd") {
                                val eps = bySeason[chosenSeason].orEmpty()
                                val done = eps.count { it.state == "done" }
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = V3Space.gutter),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            if (chosenSeason <= 0) "Specials" else "Season $chosenSeason",
                                            style = V3Type.sectionTitle,
                                            color = v3.text,
                                        )
                                        Text(
                                            "$done of ${eps.size} downloaded",
                                            style = V3Type.caption,
                                            color = v3.textSecondary,
                                        )
                                    }
                                    if (done < eps.size) {
                                        Text(
                                            "Queue season",
                                            style = V3Type.caption12,
                                            color = v3.activeText,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { vm.queueSeason(chosenSeason) }
                                                .padding(horizontal = 6.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                            items(bySeason[chosenSeason].orEmpty(), key = { it.key }) { ep ->
                                Box(Modifier.padding(horizontal = V3Space.gutter)) {
                                    V3EpisodeCard(
                                        ep = ep,
                                        isMovie = false,
                                        onDownload = { enqueue(s, ep) },
                                        onWatched = { vm.watchedToggle(ep.key) },
                                        onSubsDownload = { langTarget = ep; vm.loadSubtitleLanguages(ep.key) },
                                        onSubsPublish = { vm.subsPublish(ep.key) },
                                        onSubsClear = { vm.subsClear(ep.key) },
                                        onSubsLink = { linkTarget = ep; vm.loadSubtitleSources(ep.key) },
                                        onDelete = { confirmDelete = ep },
                                    )
                                }
                            }
                        }
                    }

                    // ---- sticky CTA row (mockup 03/04)
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(v3.background.copy(alpha = 0f), v3.background, v3.background),
                                ),
                            ),
                    ) {
                        HorizontalDivider(color = v3.divider)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = V3Space.gutter, vertical = V3Space.md),
                            horizontalArrangement = Arrangement.spacedBy(V3Space.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (episodes.isNotEmpty()) {
                                val isMovie = s.type == "movie"
                                Button(
                                    onClick = {
                                        if (isMovie) episodes.firstOrNull()?.let { enqueue(s, it) }
                                        else vm.queueMissing()
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(V3Radius.sm),
                                ) {
                                    Icon(
                                        if (isMovie) Icons.Rounded.Download else Icons.Rounded.PlaylistAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(V3Space.sm))
                                    Text(
                                        if (isMovie) "Download" else "Add missing episodes",
                                        style = V3Type.chip,
                                        maxLines = 1,
                                    )
                                }
                                Box {
                                    // episode-level overflow duplicated here so the
                                    // sheet actions are reachable without scrolling.
                                    androidx.compose.material3.IconButton(onClick = { menuOpen = true }) {
                                        Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = v3.textSecondary)
                                    }
                                    androidx.compose.material3.DropdownMenu(
                                        expanded = menuOpen,
                                        onDismissRequest = { menuOpen = false },
                                        containerColor = v3.sheet,
                                    ) {
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Queue missing episodes", color = v3.text) },
                                            leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null, tint = v3.textSecondary) },
                                            onClick = { menuOpen = false; vm.queueMissing() },
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Publish all subtitles", color = v3.text) },
                                            leadingIcon = { Icon(Icons.Rounded.Subtitles, null, tint = v3.textSecondary) },
                                            onClick = { menuOpen = false; vm.subsPublishAll() },
                                        )
                                        HorizontalDivider(color = v3.divider)
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Remove from PC", color = v3.error) },
                                            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = v3.error) },
                                            onClick = { menuOpen = false; confirmRemove = true },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (seasonSheet) {
                    SeasonSheetVM(
                        vm = vm,
                        contextShowId = s.showId,
                        seasons = seasons,
                        counts = seasons.associateWith { bySeason[it]?.size ?: 0 },
                        chosen = chosenSeason,
                        onChoose = {
                            chosenSeason = it
                            seasonMemorySet(context, s.showId, it)
                            seasonSheet = false
                        },
                        onDismiss = { seasonSheet = false },
                    )
                }
            }
        }

        // ---- transparent overlay chrome (over hero)
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .padding(horizontal = V3Space.xs, vertical = V3Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = v3.text)
            }
            Spacer(Modifier.weight(1f))
        }
    }

    // dialogs + sheets
    if (confirmRemove) {
        val cc = v3
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            containerColor = cc.sheet,
            title = { Text("Remove this title?", color = cc.text) },
            text = { Text("Deletes its files on the PC and drops it from the library.", color = cc.textSecondary) },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; vm.removeFromPc(); onBack() }) {
                    Text("Remove", color = cc.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Cancel", color = cc.textSecondary) }
            },
        )
    }

    confirmDelete?.let { ep ->
        val cc = v3
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = cc.sheet,
            title = { Text("Delete episode files?", color = cc.text) },
            text = {
                Text("Removes the downloaded video and part files for this episode on the PC.", color = cc.textSecondary)
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; vm.deleteEpisode(ep.key) }) { Text("Delete", color = cc.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = cc.textSecondary) }
            },
        )
    }

    // Pick the languages to download (same rows/counts as the desktop modal).
    if (langTarget != null) {
        V3SubtitleLangDialog(
            state = subLangsState,
            onDismiss = { langTarget = null; vm.clearSubtitleLanguages() },
            onDownload = { key, langs -> langTarget = null; vm.downloadSubtitles(key, langs) },
        )
    }

    // Link subtitles already downloaded for this episode to the video on disk.
    if (linkTarget != null) {
        V3LinkSubtitlesDialog(
            state = subSourcesState,
            onDismiss = { linkTarget = null; vm.clearSubtitleSources() },
            onLink = { key, base, ids -> linkTarget = null; vm.linkSubtitles(key, base, ids) },
        )
    }
}

// ---------------------------------------------------------------- subtitle languages

@Composable
private fun V3SubtitleLangDialog(
    state: SubtitleLangState,
    onDismiss: () -> Unit,
    onDownload: (String, List<String>) -> Unit,
) {
    val c = v3
    when (state) {
        SubtitleLangState.Idle -> Unit
        is SubtitleLangState.Loading -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = c.sheet,
            title = { Text("Download subtitles", color = c.text) },
            text = { Text("Asking the PC which languages it can find for this episode…", color = c.textSecondary) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.textSecondary) } },
        )
        is SubtitleLangState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = c.sheet,
            title = { Text("Download subtitles", color = c.text) },
            text = { Text(state.message, color = c.textSecondary) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = c.activeText) } },
        )
        is SubtitleLangState.Ready -> V3SubtitleLangReadyDialog(state, onDismiss, onDownload)
    }
}

@Composable
private fun V3SubtitleLangReadyDialog(
    state: SubtitleLangState.Ready,
    onDismiss: () -> Unit,
    onDownload: (String, List<String>) -> Unit,
) {
    val c = v3
    var selected by remember(state.key) { mutableStateOf(defaultSubtitleLangs(state.languages)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.sheet,
        title = { Text("Choose subtitle languages", color = c.text) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(V3Space.xs),
            ) {
                Text(
                    if (state.languages.isEmpty()) {
                        "No matching subtitles found for this episode."
                    } else {
                        "${state.total} subtitle${if (state.total == 1) "" else "s"} available in " +
                            "${state.languages.size} language${if (state.languages.size == 1) "" else "s"}"
                    },
                    style = V3Type.caption,
                    color = c.textSecondary,
                )
                state.languages.forEach { lang ->
                    val checked = lang.code in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (checked) selected - lang.code else selected + lang.code
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = checked,
                            onCheckedChange = { on ->
                                selected = if (on) selected + lang.code else selected - lang.code
                            },
                        )
                        Text(
                            if (lang.name.isBlank()) lang.code else lang.name,
                            style = V3Type.caption,
                            color = c.text,
                            modifier = Modifier.weight(1f),
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${lang.count} available", style = V3Type.caption12, color = c.textSecondary)
                            if (lang.existing > 0) {
                                Text("${lang.existing} downloaded", style = V3Type.caption12, color = c.textTertiary)
                            }
                        }
                    }
                }
                if (state.languages.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(V3Space.xs)) {
                        TextButton(onClick = { selected = state.languages.map { it.code }.toSet() }) {
                            Text("All", style = V3Type.caption, color = c.textSecondary)
                        }
                        TextButton(onClick = { selected = emptySet() }) {
                            Text("None", style = V3Type.caption, color = c.textSecondary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty(),
                onClick = { onDownload(state.key, selected.toList()) },
            ) {
                Text(
                    if (selected.isEmpty()) "Download" else "Download (${selected.size})",
                    color = if (selected.isEmpty()) c.textTertiary else c.activeText,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.textSecondary) } },
    )
}

// ---------------------------------------------------------------- link subtitles

private fun v3Bytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> String.format(java.util.Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "${bytes / 1_000_000L} MB"
    else -> "${bytes / 1000L} KB"
}

@Composable
private fun V3LinkSubtitlesDialog(
    state: SubtitleLinkState,
    onDismiss: () -> Unit,
    onLink: (String, String, List<String>) -> Unit,
) {
    val c = v3
    when (state) {
        SubtitleLinkState.Idle -> Unit
        is SubtitleLinkState.Loading -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = c.sheet,
            title = { Text("Link subtitles", color = c.text) },
            text = { Text("Reading the subtitles saved for this episode…", color = c.textSecondary) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.textSecondary) } },
        )
        is SubtitleLinkState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = c.sheet,
            title = { Text("Link subtitles", color = c.text) },
            text = { Text(state.message, color = c.textSecondary) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = c.activeText) } },
        )
        is SubtitleLinkState.Ready -> V3LinkReadyDialog(state, onDismiss, onLink)
    }
}

@Composable
private fun V3LinkReadyDialog(
    state: SubtitleLinkState.Ready,
    onDismiss: () -> Unit,
    onLink: (String, String, List<String>) -> Unit,
) {
    val c = v3
    var base by remember(state.key) {
        mutableStateOf((state.videos.firstOrNull { it.current } ?: state.videos.firstOrNull())?.base ?: "")
    }
    var selected by remember(state.key) {
        // Pre-tick the best Arabic + English file that is not linked yet: that
        // is the subtitle the user already has for the other source.
        val pool = state.subtitles.filter { !it.linked }
        val defaults = listOf("ar", "en").mapNotNull { lang ->
            pool.filter { it.lang == lang }.maxByOrNull { it.cues }?.id
        }
        mutableStateOf((defaults.ifEmpty { listOfNotNull(state.subtitles.maxByOrNull { it.cues }?.id) }).toSet())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.sheet,
        title = { Text("Link subtitles", color = c.text) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(V3Space.xs),
            ) {
                Text(
                    "Reuse the subtitles already downloaded for this episode — nothing is fetched again.",
                    style = V3Type.caption,
                    color = c.textSecondary,
                )
                if (state.videos.size > 1) {
                    Text(
                        "Video file",
                        style = V3Type.chip,
                        color = c.text,
                        modifier = Modifier.padding(top = V3Space.sm),
                    )
                    state.videos.forEach { video ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(V3Radius.sm))
                                .clickable { base = video.base },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = base == video.base,
                                onClick = { base = video.base },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(video.name, style = V3Type.caption, color = c.text, maxLines = 2)
                                Text(
                                    "${v3Bytes(video.size)}${if (video.current) " · current" else ""}",
                                    style = V3Type.caption12,
                                    color = c.textTertiary,
                                )
                            }
                        }
                    }
                }
                if (state.subtitles.isEmpty()) {
                    Text(
                        "No subtitles saved for this episode yet — download some first.",
                        style = V3Type.caption,
                        color = c.textSecondary,
                    )
                } else {
                    Text(
                        "Subtitles on disk (${state.subtitles.size})",
                        style = V3Type.chip,
                        color = c.text,
                        modifier = Modifier.padding(top = V3Space.sm),
                    )
                    state.subtitles.forEach { subtitle ->
                        val checked = subtitle.id in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (checked) selected - subtitle.id else selected + subtitle.id
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = checked,
                                onCheckedChange = { on ->
                                    selected = if (on) selected + subtitle.id else selected - subtitle.id
                                },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "[${subtitle.lang}] ${subtitle.label}",
                                    style = V3Type.caption,
                                    color = c.text,
                                    maxLines = 2,
                                )
                                Text(
                                    "${subtitle.cues} cues" + if (subtitle.linked) " · linked" else "",
                                    style = V3Type.caption12,
                                    color = c.textTertiary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty() && base.isNotBlank(),
                onClick = { onLink(state.key, base, selected.toList()) },
            ) {
                Text(
                    if (selected.isEmpty()) "Link" else "Link (${selected.size})",
                    color = if (selected.isEmpty()) c.textTertiary else c.activeText,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.textSecondary) } },
    )
}

@Composable
private fun SeasonSheetVM(
    vm: ShowDetailViewModel,
    contextShowId: String,
    seasons: List<Int>,
    counts: Map<Int, Int>,
    chosen: Int,
    onChoose: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    V3Sheet(title = "Season", onDismiss = onDismiss) {
        seasons.forEach { season ->
            SheetRow(
                label = (if (season <= 0) "Specials" else "Season $season") + "  ·  ${counts[season] ?: 0} ep",
                selected = season == chosen,
                onClick = { onChoose(season) },
            )
        }
    }
}

// ---------------------------------------------------------------- hero

@Composable
private fun V3DetailHero(
    state: SharedTitleState,
    show: LibraryShow,
    episodes: List<Episode>,
    doneCount: Int,
) {
    val c = v3
    val t = (state as? SharedTitleState.Ready)?.title
    val poster = t?.posterUrl ?: show.poster
    val backdrop = t?.backdropUrl ?: show.poster
    Column(verticalArrangement = Arrangement.spacedBy(V3Space.lg)) {
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            if (backdrop.isNullOrBlank()) {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF16233B), Color(0xFF0B0D12)))))
            } else {
                AsyncImage(
                    model = backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.30f),
                        0.5f to Color.Black.copy(alpha = 0.55f),
                        1f to c.background,
                    ),
                ),
            )
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = V3Space.gutter, vertical = V3Space.lg),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(V3Space.lg),
            ) {
                Box(
                    Modifier
                        .width(110.dp)
                        .height(165.dp)
                        .clip(RoundedCornerShape(V3Radius.card))
                        .background(c.raised),
                ) {
                    if (poster.isNullOrBlank()) {
                        Icon(
                            if (show.type == "movie") Icons.Rounded.Movie else Icons.Rounded.Tv,
                            null,
                            tint = c.textTertiary,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        AsyncImage(
                            model = poster,
                            contentDescription = show.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(V3Space.xs)) {
                    Text(
                        show.title,
                        style = V3Type.screenTitle.copy(fontSize = 24.sp, lineHeight = 30.sp),
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = buildList {
                        add(if (show.type == "movie") "Movie" else "Series")
                        optStr(show.year)?.let { add(it) }
                        t?.rating?.let { add("★ ${"%.1f".format(it)}") }
                    }.joinToString("  •  ")
                    Text(meta, style = V3Type.caption12, color = Color.White.copy(alpha = 0.85f))
                    if (episodes.isNotEmpty()) {
                        Text(
                            "$doneCount of ${episodes.size} downloaded",
                            style = V3Type.caption,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                        LinearProgressIndicator(
                            progress = { if (episodes.isEmpty()) 0f else doneCount.toFloat() / episodes.size },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = c.accent,
                            trackColor = c.raised,
                        )
                    }
                }
            }
        }

        // genres / synopsis / cast — inside the gutters
        Column(
            Modifier.padding(horizontal = V3Space.gutter),
            verticalArrangement = Arrangement.spacedBy(V3Space.lg),
        ) {
            val genres = t?.genres.orEmpty()
            if (genres.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(V3Space.sm),
                ) {
                    genres.forEach { g -> V3Pill(label = g, selected = false, onClick = {}) }
                }
            }
            val overview = t?.overview?.ifBlank { null } ?: show.overview
            if (!overview.isNullOrBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(V3Space.sm)) {
                    Text("القصة", style = V3Type.sectionTitle, color = c.text)
                    Text(
                        overview,
                        style = V3Type.body14,
                        color = c.textSecondary,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val cast = t?.cast.orEmpty()
            if (cast.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(V3Space.md)) {
                    Text("طاقم التمثيل", style = V3Type.sectionTitle, color = c.text)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(V3Space.lg),
                    ) {
                        cast.forEach { member ->
                            Column(
                                Modifier.width(84.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(c.raised),
                                ) {
                                    if (member.photoUrl != null) {
                                        AsyncImage(
                                            model = member.photoUrl,
                                            contentDescription = member.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(V3Space.xs))
                                Text(
                                    member.name,
                                    style = V3Type.caption,
                                    color = c.text,
                                    maxLines = 2,
                                    textAlign = TextAlign.Center,
                                )
                                if (member.character.isNotBlank()) {
                                    Text(
                                        member.character,
                                        style = V3Type.caption,
                                        color = c.textTertiary,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- episode row

@Composable
private fun V3EpisodeCard(
    ep: Episode,
    isMovie: Boolean,
    onDownload: () -> Unit,
    onWatched: () -> Unit,
    onSubsDownload: () -> Unit,
    onSubsPublish: () -> Unit,
    onSubsClear: () -> Unit,
    onSubsLink: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = v3
    var menuOpen by remember { mutableStateOf(false) }
    val stateColor = when (ep.state) {
        "done" -> c.success
        "partial" -> c.warning
        "downloading" -> c.accent
        else -> c.textTertiary
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(V3Radius.card))
            .background(c.raised)
            .padding(V3Space.md),
        verticalArrangement = Arrangement.spacedBy(V3Space.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(V3Space.md)) {
            Box(
                Modifier
                    .width(112.dp)
                    .height(63.dp)
                    .clip(RoundedCornerShape(V3Radius.poster))
                    .background(c.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (ep.still.isNullOrBlank()) {
                    Icon(Icons.Rounded.Movie, null, tint = c.textTertiary)
                } else {
                    AsyncImage(
                        model = ep.still,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(V3Space.xs)) {
                Text(
                    if (isMovie) ep.name.ifBlank { "Full movie" }
                    else "E${ep.episode}" + (if (ep.name.isBlank()) "" else " · ${ep.name}"),
                    style = V3Type.itemTitle,
                    color = c.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(V3Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(stateColor))
                    Text(stateLabel(ep.state), style = V3Type.caption, color = stateColor)
                    ep.rating?.takeIf { it > 0 }?.let { r ->
                        Icon(Icons.Rounded.Star, null, modifier = Modifier.size(12.dp), tint = c.warning)
                        Text("%.1f".format(r), style = V3Type.caption, color = c.textTertiary)
                    }
                    ep.runtime?.takeIf { it > 0 }?.let { rt ->
                        Icon(Icons.Rounded.Schedule, null, modifier = Modifier.size(12.dp), tint = c.textTertiary)
                        Text("${rt}m", style = V3Type.caption, color = c.textTertiary)
                    }
                }
                // size line + progress
                val sizeText = when {
                    ep.state == "done" && (ep.total ?: ep.downloaded) != null ->
                        "${detailFormatBytes(ep.total ?: ep.downloaded!!)} · on disk"

                    ep.total != null && ep.downloaded != null ->
                        "${detailFormatBytes(ep.downloaded)} of ${detailFormatBytes(ep.total)}"

                    ep.state == "downloading" -> "Starting…"
                    else -> "Not downloaded yet"
                }
                Text(sizeText, style = V3Type.caption, color = c.textTertiary)
                if (ep.total != null && ep.downloaded != null && ep.state != "done") {
                    LinearProgressIndicator(
                        progress = { (ep.downloaded.toFloat() / ep.total.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = stateColor,
                        trackColor = c.surface,
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(V3Space.sm),
        ) {
            when (ep.state) {
                "missing", "partial" -> V3PrimaryButton(
                    label = if (ep.state == "partial") "Resume" else "Download",
                    onClick = onDownload,
                )
                "downloading" -> OutlinedButton(
                    onClick = {},
                    enabled = false,
                    shape = RoundedCornerShape(V3Radius.sm),
                ) { Text("Downloading…", style = V3Type.chip) }
                else -> OutlinedButton(
                    onClick = onDownload,
                    shape = RoundedCornerShape(V3Radius.sm),
                ) { Text("Redownload", style = V3Type.chip, color = c.text) }
            }

            if (ep.subs > 0 || ep.arSubs > 0 || ep.needsPub) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(V3Radius.pill))
                        .background(if (ep.needsPub) c.warning.copy(alpha = 0.18f) else c.accent.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        when {
                            ep.needsPub -> "publish subs"
                            ep.pubAr > 0 -> "ar ✓"
                            else -> "${ep.subs} sub"
                        },
                        style = V3Type.caption,
                        color = if (ep.needsPub) c.warning else c.accent,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            androidx.compose.material3.IconButton(onClick = onWatched) {
                Icon(
                    if (ep.watched) Icons.Rounded.CheckCircle else Icons.Rounded.Schedule,
                    contentDescription = if (ep.watched) "Watched" else "Mark watched",
                    tint = if (ep.watched) c.accent else c.textTertiary,
                )
            }
            Box {
                androidx.compose.material3.IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Episode actions", tint = c.textTertiary)
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = c.sheet,
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Get subtitles", color = c.text) },
                        leadingIcon = { Icon(Icons.Rounded.Subtitles, null, tint = c.textSecondary) },
                        onClick = { menuOpen = false; onSubsDownload() },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Publish subtitles next to video", color = c.text) },
                        leadingIcon = { Icon(Icons.Rounded.Download, null, tint = c.textSecondary) },
                        onClick = { menuOpen = false; onSubsPublish() },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Link subtitles from another source", color = c.text) },
                        leadingIcon = { Icon(Icons.Rounded.Subtitles, null, tint = c.textSecondary) },
                        onClick = { menuOpen = false; onSubsLink() },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Clear converted (Arabic)", color = c.text) },
                        leadingIcon = { Icon(Icons.Rounded.Refresh, null, tint = c.textSecondary) },
                        onClick = { menuOpen = false; onSubsClear() },
                    )
                    HorizontalDivider(color = c.divider)
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Delete files", color = c.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = c.error) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun V3PrimaryButton(label: String, onClick: () -> Unit) {
    val c = v3
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp),
        shape = RoundedCornerShape(V3Radius.sm),
        modifier = Modifier.height(34.dp),
    ) {
        Icon(Icons.Rounded.Download, null, Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = V3Type.chip)
    }
}

// ---------------------------------------------------------- season selector

@Composable
private fun V3SeasonField(season: Int, episodeCount: Int, onClick: () -> Unit) {
    val c = v3
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(V3Radius.field))
            .background(c.raised)
            .clickable(onClick = onClick)
            .height(48.dp)
            .padding(horizontal = V3Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (season <= 0) "Specials" else "Season $season",
            style = V3Type.chip,
            color = c.text,
        )
        Spacer(Modifier.width(V3Space.sm))
        Text(
            "$episodeCount ep",
            style = V3Type.caption,
            color = c.textTertiary,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Pick season", tint = c.textTertiary)
    }
}

// Last-shown season per series, one small prefs file (survives restarts).
private object V3SeasonMemory {
    private const val PREF = "cj_detail"
    fun get(context: android.content.Context, showId: String, fallback: Int): Int =
        context.getSharedPreferences(PREF, android.content.Context.MODE_PRIVATE)
            .getInt("season_$showId", fallback)
    fun set(context: android.content.Context, showId: String, season: Int) {
        context.getSharedPreferences(PREF, android.content.Context.MODE_PRIVATE)
            .edit().putInt("season_$showId", season).apply()
    }
}

private fun seasonMemoryGet(context: android.content.Context, showId: String, fallback: Int): Int =
    V3SeasonMemory.get(context, showId, fallback)

private fun seasonMemorySet(context: android.content.Context, showId: String, season: Int) =
    V3SeasonMemory.set(context, showId, season)
