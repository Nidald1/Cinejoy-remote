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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nedal.cinejoyremote.domain.Episode
import com.nedal.cinejoyremote.domain.LibraryShow
import com.nedal.cinejoyremote.presentation.DevicesViewModel
import com.nedal.cinejoyremote.presentation.SharedTitleState
import com.nedal.cinejoyremote.presentation.SharedTitleViewModel
import com.nedal.cinejoyremote.presentation.ShowDetailViewModel
import com.nedal.cinejoyremote.presentation.SourceInfoState
import com.nedal.cinejoyremote.share.TmdbHint
import com.nedal.cinejoyremote.share.TmdbRef
import com.nedal.cinejoyremote.ui.DetailPickInbox
import com.nedal.cinejoyremote.ui.v3.components.ErrorState
import com.nedal.cinejoyremote.ui.v3.components.SheetRow
import com.nedal.cinejoyremote.ui.v3.components.Skeleton
import com.nedal.cinejoyremote.ui.v3.components.V3Pill
import com.nedal.cinejoyremote.ui.v3.components.V3Sheet
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ---------------------------------------------------------------- helpers
private fun JsonObject.str(key: String): String? = runCatching {
    this[key]?.jsonPrimitive?.contentOrNull
}.getOrNull()?.takeIf { it != "null" }

private fun JsonObject.int(key: String): Int? = runCatching {
    this[key]?.jsonPrimitive?.intOrNull
}.getOrNull()

private fun JsonObject.bool(key: String): Boolean? = runCatching {
    this[key]?.jsonPrimitive?.booleanOrNull
}.getOrNull()

private fun JsonObject.arr(key: String): List<JsonObject> =
    runCatching { (this[key] as? JsonArray) ?: return emptyList() }
        .getOrNull()
        ?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
        ?: emptyList()

private fun optStr(value: String?): String? = value?.takeIf { it.isNotBlank() && it != "null" }

private fun heightOf(label: String?): Int? =
    Regex("(\\d{3,4})p", RegexOption.IGNORE_CASE).find(label ?: "")?.groupValues?.get(1)?.toIntOrNull()
        ?: Regex("\\b(\\d{3,4})\\b").find(label ?: "")?.groupValues?.get(1)?.toIntOrNull()

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
 * field + sheet, and the source picker live below.
 */
@Composable
fun V3DetailScreen(
    devicesVm: DevicesViewModel,
    vm: ShowDetailViewModel,
    showId: String,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    LaunchedEffect(showId) { vm.load(showId) }

    val show by vm.show.collectAsState()
    val episodes by vm.episodes.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val notice by vm.notice.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var menuOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Episode?>(null) }
    var sheetEpisode by remember { mutableStateOf<Episode?>(null) }

    LaunchedEffect(error) { error?.let { msg -> snackbar.showSnackbar(msg); vm.clearError() } }
    LaunchedEffect(notice) { notice?.let { msg -> snackbar.showSnackbar(msg); vm.clearNotice() } }

    // Seerr accept hands this showId over (DetailPickInbox): once the episodes
    // are loaded, raise the sources window straight away.
    var autoPicked by remember { mutableStateOf(false) }
    LaunchedEffect(showId, episodes) {
        if (autoPicked || episodes.isEmpty()) return@LaunchedEffect
        if (!DetailPickInbox.take(showId)) return@LaunchedEffect
        autoPicked = true
        sheetEpisode = episodes.firstOrNull { it.state != "done" } ?: episodes.first()
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
                                        onDownload = { sheetEpisode = ep },
                                        onWatched = { vm.watchedToggle(ep.key) },
                                        onSubsDownload = { vm.subsDownload(ep.key) },
                                        onSubsPublish = { vm.subsPublish(ep.key) },
                                        onSubsClear = { vm.subsClear(ep.key) },
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
                                        onDownload = { sheetEpisode = ep },
                                        onWatched = { vm.watchedToggle(ep.key) },
                                        onSubsDownload = { vm.subsDownload(ep.key) },
                                        onSubsPublish = { vm.subsPublish(ep.key) },
                                        onSubsClear = { vm.subsClear(ep.key) },
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
                                        if (isMovie) sheetEpisode = episodes.firstOrNull()
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

    sheetEpisode?.let { ep ->
        V3SourcePickerSheet(vm = vm, episode = ep, onDismiss = { sheetEpisode = null; vm.clearSourceInfo() })
    }
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

// ---------------------------------------------------------------- source picker

@Composable
private fun V3SourcePickerSheet(
    vm: ShowDetailViewModel,
    episode: Episode,
    onDismiss: () -> Unit,
) {
    val c = v3
    val state by vm.sourceInfo.collectAsState()
    val busy by vm.busy.collectAsState()
    var source by remember(episode.key) { mutableStateOf("cinejoy") }

    LaunchedEffect(source, episode.key) { vm.loadSource(episode.key, source) }

    V3Sheet(title = "Choose a source", onDismiss = onDismiss) {
        Text(
            episode.name.ifBlank { "Episode ${episode.episode}" },
            style = V3Type.body,
            color = c.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(V3Space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(V3Space.sm)) {
            listOf("cinejoy" to "Cinejoy", "movy" to "Movy", "akwam" to "AKWAM").forEach { (key, label) ->
                V3Pill(label = label, selected = source == key, onClick = { source = key })
            }
        }

        when (val s = state) {
            SourceInfoState.Idle, SourceInfoState.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(V3Space.md),
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            ) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Text(
                    if (source == "cinejoy") "Capturing a fresh Cinejoy link — this can take up to a minute…"
                    else "Asking the PC for $source servers…",
                    style = V3Type.body14,
                    color = c.textSecondary,
                )
            }

            is SourceInfoState.Failed -> ErrorState(
                message = "Could not reach the PC: ${s.message}",
                onRetry = { vm.loadSource(episode.key, source) },
            )

            is SourceInfoState.Ready -> V3SourcePane(vm, episode, s.source, s.data, busy == "start-download")
        }
    }
}

@Composable
private fun V3SourcePane(
    vm: ShowDetailViewModel,
    episode: Episode,
    source: String,
    data: JsonObject,
    starting: Boolean,
) {
    val c = v3
    if (data.bool("ok") == false) {
        Text(
            data.str("error") ?: "The PC could not resolve this source.",
            style = V3Type.body14,
            color = c.error,
        )
        return
    }
    when (source) {
        "movy" -> V3MovyPane(vm, episode, data, starting)
        "akwam" -> V3AkwamPane(vm, episode, data, starting)
        else -> V3CinejoyPane(vm, episode, data, starting)
    }
}

@Composable
private fun V3StartButton(starting: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !starting,
        shape = RoundedCornerShape(V3Radius.sm),
        modifier = Modifier.fillMaxWidth().height(44.dp),
    ) {
        if (starting) {
            androidx.compose.material3.CircularProgressIndicator(
                Modifier.size(16.dp), strokeWidth = 2.dp, color = v3.onAccent,
            )
            Spacer(Modifier.width(V3Space.sm))
            Text("Queueing…", style = V3Type.chip)
        } else {
            Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
            Spacer(Modifier.width(V3Space.sm))
            Text("Start download", style = V3Type.chip)
        }
    }
}

// --- Cinejoy: one captured master, choose a variant (or Auto).
@Composable
private fun V3CinejoyPane(
    vm: ShowDetailViewModel,
    episode: Episode,
    data: JsonObject,
    starting: Boolean,
) {
    val c = v3
    val streamError = data.str("streamError")
    val server = data.str("server") ?: "nebula.bright67.online"
    if (streamError != null) {
        Text(
            "Link unavailable ($streamError). Try Refresh, or use Movy/AKWAM for this item.",
            style = V3Type.body14,
            color = c.error,
        )
        OutlinedButton(onClick = { vm.loadSource(episode.key, "cinejoy") }, shape = RoundedCornerShape(V3Radius.sm)) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Refresh", color = c.text)
        }
        return
    }

    val variants = data.arr("variants")
    var selected by remember(episode.key) { mutableIntStateOf(-1) } // -1 = Auto
    val options = listOf(-1) + variants.mapIndexed { i, _ -> i }

    Column(verticalArrangement = Arrangement.spacedBy(V3Space.md)) {
        Text(
            "Server: $server · ${if (variants.isEmpty()) "single stream" else "${variants.size} quality variant(s)"}",
            style = V3Type.caption,
            color = c.textTertiary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(V3Space.sm), modifier = Modifier.fillMaxWidth()) {
            options.take(6).forEach { i ->
                val label = if (i < 0) "Auto" else (variants[i].int("height")?.let { "${it}p" } ?: "stream")
                V3Pill(label = label, selected = selected == i, onClick = { selected = i })
            }
        }
        if (selected >= 0) {
            variants[selected].str("size")?.takeIf { it.isNotBlank() }?.let { size ->
                Text("Estimated size: $size", style = V3Type.caption, color = c.textTertiary)
            }
        }
        V3StartButton(starting) {
            val height = if (selected < 0) null else variants[selected].int("height")
            val label = if (selected < 0) "Auto (best)" else height?.let { "${it}p" } ?: "stream"
            vm.queueDownload(
                episode.key,
                buildMap {
                    put("source", "cinejoy")
                    put("qualityLabel", label)
                    height?.let { put("quality", it.toString()) }
                },
            )
        }
    }
}

// --- Movy: pick a server, then a quality from that server.
@Composable
private fun V3MovyPane(
    vm: ShowDetailViewModel,
    episode: Episode,
    data: JsonObject,
    starting: Boolean,
) {
    val c = v3
    val servers = data.arr("servers")
    if (servers.isEmpty()) {
        Text("No movy servers found for this item.", style = V3Type.body14, color = c.textSecondary)
        return
    }
    val usable = servers.filter { it.bool("ok") == true && it.arr("sources").isNotEmpty() }
    var selectedServer by remember(episode.key) {
        mutableStateOf(usable.firstOrNull()?.str("key") ?: servers.firstOrNull()?.str("key"))
    }
    val current = servers.firstOrNull { it.str("key") == selectedServer }
    val sources = current?.arr("sources") ?: emptyList()
    val labels = current?.arr("sources")?.mapNotNull { it.str("quality") }?.distinct()
        ?: current?.arr("qualities")?.mapNotNull { it.str("it") } ?: emptyList()
    var selectedLabel by remember(episode.key, selectedServer) {
        mutableStateOf(labels.firstOrNull())
    }

    Column(verticalArrangement = Arrangement.spacedBy(V3Space.sm)) {
        if (data.bool("rateLimited") == true) {
            Text("Movy rate-limited some servers — wait a moment and Refresh.", style = V3Type.caption, color = c.warning)
        }

        // The server list scrolls inside a capped-height box so the quality
        // chips and the Start button stay visible no matter how many servers.
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(V3Space.sm),
        ) {
            servers.forEach { srv ->
                val key = srv.str("key") ?: return@forEach
                val ok = srv.bool("ok") == true && srv.arr("sources").isNotEmpty()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(V3Radius.sm))
                        .background(if (selectedServer == key) c.accent.copy(alpha = 0.14f) else c.surface)
                        .clickable(enabled = ok) { selectedServer = key }
                        .padding(horizontal = V3Space.md, vertical = V3Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selectedServer == key, onClick = null, enabled = ok)
                    Column(Modifier.weight(1f)) {
                        Text(key, style = V3Type.itemTitle, color = c.text)
                        if (!ok) {
                            Text(srv.str("error") ?: "no sources", style = V3Type.caption, color = c.textTertiary)
                        } else {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(V3Space.xs),
                            ) {
                                srv.arr("sources").mapNotNull { it.str("quality") }.distinct().take(6).forEach { q ->
                                    val size = srv.arr("sources").firstOrNull { it.str("quality") == q }?.str("size")
                                    Text(
                                        if (size.isNullOrBlank()) q else "$q · $size",
                                        style = V3Type.caption,
                                        color = c.textTertiary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (sources.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(V3Space.xs)) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(V3Space.sm),
                ) {
                    labels.distinct().take(6).forEach { label ->
                        V3Pill(label = label, selected = selectedLabel == label, onClick = { selectedLabel = label })
                    }
                }
                sources.firstOrNull { it.str("quality") == selectedLabel }?.str("size")
                    ?.takeIf { it.isNotBlank() }?.let { size ->
                        Text("File size: $size", style = V3Type.caption, color = c.textTertiary)
                    }
            }
        }

        V3StartButton(starting && selectedServer != null) {
            val src = sources.firstOrNull { it.str("quality") == selectedLabel }
            vm.queueDownload(
                episode.key,
                buildMap {
                    put("source", "movy")
                    selectedServer?.let { put("server", it) }
                    selectedLabel?.let { put("qualityLabel", it) }
                    (src?.int("height") ?: heightOf(selectedLabel))?.let { put("quality", it.toString()) }
                },
            )
        }
    }
}

// --- AKWAM: every quality/server row is its own download option.
@Composable
private fun V3AkwamPane(
    vm: ShowDetailViewModel,
    episode: Episode,
    data: JsonObject,
    starting: Boolean,
) {
    val c = v3
    val entries = data.arr("entries")
    if (entries.isEmpty()) {
        Text("No AKWAM qualities found for this item.", style = V3Type.body14, color = c.textSecondary)
        return
    }
    var selectedIndex by remember(episode.key) { mutableIntStateOf(0) }
    val entry = entries.getOrNull(selectedIndex)

    Column(verticalArrangement = Arrangement.spacedBy(V3Space.sm)) {
        entries.forEachIndexed { i, e ->
            val label = e.str("label") ?: "Option ${i + 1}"
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(V3Radius.sm))
                    .background(if (selectedIndex == i) c.accent.copy(alpha = 0.14f) else c.surface)
                    .clickable { selectedIndex = i }
                    .padding(horizontal = V3Space.md, vertical = V3Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selectedIndex == i, onClick = null)
                Column(Modifier.weight(1f)) {
                    Text(label, style = V3Type.itemTitle, color = c.text)
                    Text(
                        listOfNotNull(
                            e.str("size")?.takeIf { it.isNotBlank() },
                            e.str("server")?.let { "server $it" },
                        ).joinToString(" · ").ifBlank { "quality option" },
                        style = V3Type.caption,
                        color = c.textTertiary,
                    )
                }
            }
        }

        V3StartButton(starting && entry != null) {
            val e = entry ?: return@V3StartButton
            vm.queueDownload(
                episode.key,
                buildMap {
                    put("source", "akwam")
                    e.str("server")?.let { put("server", it) }
                    e.str("label")?.let { put("qualityLabel", it) }
                    heightOf(e.str("label"))?.let { put("quality", it.toString()) }
                    e.str("downloadPage")?.let { put("sourceEntry", it) }
                },
            )
        }
    }
}
