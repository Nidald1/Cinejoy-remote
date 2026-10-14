package com.nedal.cinejoyremote.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

private fun JsonObject.long(key: String): Long? = runCatching {
    this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        ?: this[key]?.jsonPrimitive?.intOrNull?.toLong()
}.getOrNull()

private fun JsonObject.double(key: String): Double? = runCatching {
    this[key]?.jsonPrimitive?.doubleOrNull
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

// "1080p" / "720 HD" -> 1080 / 720, else null (same rule the PC uses).
private fun heightOf(label: String?): Int? =
    Regex("(\\d{3,4})p", RegexOption.IGNORE_CASE).find(label ?: "")?.groupValues?.get(1)?.toIntOrNull()
        ?: Regex("\\b(\\d{3,4})\\b").find(label ?: "")?.groupValues?.get(1)?.toIntOrNull()

private fun stateLabel(state: String): String = when (state) {
    "done" -> "Downloaded"
    "partial" -> "Partial"
    "downloading" -> "Downloading"
    else -> "Missing"
}

@Composable
private fun StateDot(state: String) {
    val color = when (state) {
        "done" -> MaterialTheme.colorScheme.primary
        "partial" -> MaterialTheme.colorScheme.tertiary
        "downloading" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        Modifier
            .size(8.dp)
            .clip(RoundedCornerShape(50))
            .background(color),
    )
}

// ---------------------------------------------------------------- screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowDetailScreen(
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

    LaunchedEffect(error) { error?.let { msg -> snackbar.showSnackbar(msg); vm.clearError() } }
    LaunchedEffect(notice) { notice?.let { msg -> snackbar.showSnackbar(msg); vm.clearNotice() } }

    var menuOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Episode?>(null) }
    var sheetEpisode by remember { mutableStateOf<Episode?>(null) }

    // Seerr accept hands this showId over (DetailPickInbox): once the episodes
    // are loaded, raise the sources window straight away so the user picks the
    // source and quality instead of a default download starting by itself.
    var autoPicked by remember { mutableStateOf(false) }
    LaunchedEffect(showId, episodes) {
        if (autoPicked || episodes.isEmpty()) return@LaunchedEffect
        if (!DetailPickInbox.take(showId)) return@LaunchedEffect
        autoPicked = true
        sheetEpisode = episodes.firstOrNull { it.state != "done" } ?: episodes.first()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        show?.title ?: "…",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = !menuOpen }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Queue missing episodes") },
                            leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) },
                            onClick = { menuOpen = false; vm.queueMissing() },
                        )
                        DropdownMenuItem(
                            text = { Text("Publish all subtitles") },
                            leadingIcon = { Icon(Icons.Rounded.Subtitles, null) },
                            onClick = { menuOpen = false; vm.subsPublishAll() },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Remove from PC") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = { menuOpen = false; confirmRemove = true },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (show != null && episodes.isNotEmpty()) {
                val isMovie = show?.type == "movie"
                ExtendedFloatingActionButton(
                    onClick = {
                        // Movies download straight into the picker; series start
                        // with everything that is still missing.
                        if (isMovie) sheetEpisode = episodes.firstOrNull()
                        else vm.queueMissing()
                    },
                    icon = {
                        Icon(
                            if (isMovie) Icons.Rounded.Download else Icons.Rounded.PlaylistAdd,
                            contentDescription = null,
                        )
                    },
                    text = { Text(if (isMovie) "Download" else "Queue missing") },
                )
            }
        },
    ) { padding ->
        if (busy != null) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(padding))
        }

        when {
            show == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            else -> {
                val s = show!!
                val bySeason = episodes.groupBy { it.season }.toSortedMap()
                val doneCount = episodes.count { it.state == "done" }

                // Netflix-style header (Settings -> Title page) enriches itself with
                // the same TMDb metadata the share sheet uses: backdrop, genres,
                // Arabic synopsis, cast. Rows without a tmdb id resolve by title
                // search (TmdbHint); classic mode never creates this VM at all.
                val detailStyle = DetailStyleController.mode
                val sharedVm: SharedTitleViewModel? =
                    if (detailStyle == DetailStyle.NETFLIX) {
                        viewModel(
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
                    } else {
                        null
                    }
                val sharedState: SharedTitleState =
                    sharedVm?.let { it.state.collectAsState().value } ?: SharedTitleState.Loading

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "header") {
                        if (detailStyle == DetailStyle.NETFLIX) {
                            NetflixHeader(sharedState, s, episodes, doneCount)
                        } else {
                            ShowHeader(s, episodes, doneCount)
                        }
                    }

                    if (s.type == "movie") {
                        items(episodes, key = { it.key }) { ep ->
                            EpisodeCard(
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
                    } else {
                        bySeason.forEach { (season, eps) ->
                            item(key = "season-$season") {
                                SeasonHeader(
                                    season = season,
                                    eps = eps,
                                    onQueue = { vm.queueSeason(season) },
                                )
                            }
                            items(eps, key = { it.key }) { ep ->
                                EpisodeCard(
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
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove this title?") },
            text = { Text("Deletes its files on the PC and drops it from the library.") },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; vm.removeFromPc(); onBack() }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }

    confirmDelete?.let { ep ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete episode files?") },
            text = { Text("Removes the downloaded video and part files for this episode on the PC.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; vm.deleteEpisode(ep.key) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }

    sheetEpisode?.let { ep ->
        SourcePickerSheet(
            vm = vm,
            episode = ep,
            onDismiss = { sheetEpisode = null; vm.clearSourceInfo() },
        )
    }
}

// ---------------------------------------------------------------- header
// Netflix-style header (Settings -> Title page = Netflix style): a full-width
// backdrop fading into the page colour with the poster and title sitting on it,
// then genres, the TMDb Arabic synopsis and the cast — the same look as the
// share sheet. Falls back to the library's own data while TMDb loads (or when
// the show has no tmdb id), so the page is never empty.
@Composable
private fun NetflixHeader(
    state: SharedTitleState,
    show: LibraryShow,
    episodes: List<Episode>,
    doneCount: Int,
) {
    val t = (state as? SharedTitleState.Ready)?.title
    val poster = t?.posterUrl ?: show.poster
    val backdrop = t?.backdropUrl ?: show.poster
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            if (backdrop != null) {
                AsyncImage(
                    model = backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.30f),
                        0.5f to Color.Black.copy(alpha = 0.55f),
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            Row(
                Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Poster thumbnail — TMDb first, library poster as fallback.
                Box(
                    Modifier
                        .width(110.dp)
                        .height(165.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    if (poster != null) {
                        AsyncImage(
                            model = poster,
                            contentDescription = show.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                if (show.type == "movie") Icons.Rounded.Movie else Icons.Rounded.Tv,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        show.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = buildList {
                        add(if (show.type == "movie") "Movie" else "Series")
                        optStr(show.year)?.let { add(it) }
                        t?.rating?.let { add("★ ${"%.1f".format(it)}") }
                    }.joinToString("  •  ")
                    Text(meta, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                    if (episodes.isNotEmpty()) {
                        Text(
                            "$doneCount of ${episodes.size} downloaded",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                        LinearProgressIndicator(
                            progress = { if (episodes.isEmpty()) 0f else doneCount.toFloat() / episodes.size },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // Genres from TMDb once it has answered.
        val genres = t?.genres.orEmpty()
        if (genres.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                genres.forEach { MetaChip(it) }
            }
        }

        // Synopsis: TMDb Arabic like the share sheet, library overview as fallback.
        val overview = t?.overview?.ifBlank { null } ?: show.overview
        if (!overview.isNullOrBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("القصة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Cast strip — same shape as the share sheet's.
        val cast = t?.cast.orEmpty()
        if (cast.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("طاقم التمثيل", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    cast.forEach { c ->
                        Column(
                            Modifier.width(84.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            ) {
                                c.photoUrl?.let {
                                    AsyncImage(
                                        model = it,
                                        contentDescription = c.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                c.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                textAlign = TextAlign.Center,
                            )
                            if (c.character.isNotBlank()) {
                                Text(
                                    c.character,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun ShowHeader(show: LibraryShow, episodes: List<Episode>, doneCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val poster = show.poster
        if (poster != null) {
            AsyncImage(
                model = poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(104.dp)
                    .height(156.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        } else {
            Box(
                Modifier
                    .width(104.dp)
                    .height(156.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (show.type == "movie") Icons.Rounded.Movie else Icons.Rounded.Tv,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            Text(
                show.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MetaChip(if (show.type == "movie") "Movie" else "Series")
                optStr(show.year)?.let { MetaChip(it) }
                MetaChip("${show.episodes} ep")
            }
            if (episodes.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StateDot("done")
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "$doneCount of ${episodes.size} downloaded",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val totalBytes = episodes.mapNotNull { it.total }.maxOrNull()
                if (totalBytes != null) {
                    LinearProgressIndicator(
                        progress = { if (episodes.isEmpty()) 0f else doneCount.toFloat() / episodes.size },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "~${formatBytes(totalBytes)} on disk",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            optStr(show.overview)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SeasonHeader(season: Int, eps: List<Episode>, onQueue: () -> Unit) {
    val done = eps.count { it.state == "done" }
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                if (season <= 0) "Specials" else "Season $season",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "$done of ${eps.size} downloaded",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (done < eps.size) {
            TextButton(onClick = onQueue) { Text("Queue season") }
        }
    }
}

// ---------------------------------------------------------------- episode row
@Composable
private fun EpisodeCard(
    ep: Episode,
    isMovie: Boolean,
    onDownload: () -> Unit,
    onWatched: () -> Unit,
    onSubsDownload: () -> Unit,
    onSubsPublish: () -> Unit,
    onSubsClear: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val still = ep.still
                if (still != null) {
                    AsyncImage(
                        model = still,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(112.dp)
                            .height(63.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                } else {
                    Box(
                        Modifier
                            .width(112.dp)
                            .height(63.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (isMovie) ep.name.ifBlank { "Full movie" }
                        else "E${ep.episode}" + (if (ep.name.isBlank()) "" else " · ${ep.name}"),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StateDot(ep.state)
                            Spacer(Modifier.width(5.dp))
                            Text(
                                stateLabel(ep.state),
                                style = MaterialTheme.typography.labelSmall,
                                color = when (ep.state) {
                                    "done" -> MaterialTheme.colorScheme.primary
                                    "partial" -> MaterialTheme.colorScheme.tertiary
                                    "downloading" -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        ep.rating?.takeIf { it > 0 }?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Star,
                                    null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.tertiary,
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    "%.1f".format(it),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        ep.runtime?.takeIf { it > 0 }?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Schedule,
                                    null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    "${it}m",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    EpisodeSizeLine(ep)
                }
            }

            // Per-episode actions.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (ep.state) {
                    "missing", "partial" -> Button(
                        onClick = onDownload,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Icon(Icons.Rounded.Download, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (ep.state == "partial") "Resume" else "Download")
                    }

                    "downloading" -> OutlinedButton(
                        onClick = {},
                        enabled = false,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp),
                    ) { Text("Downloading…") }

                    else -> OutlinedButton(
                        onClick = onDownload,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Redownload")
                    }
                }

                if (ep.subs > 0 || ep.arSubs > 0 || ep.needsPub) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (ep.needsPub) MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Subtitles,
                                null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                when {
                                    ep.needsPub -> "publish subs"
                                    ep.pubAr > 0 -> "ar ✓"
                                    else -> "${ep.subs} sub"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                IconButton(onClick = onWatched, modifier = Modifier.size(34.dp)) {
                    Icon(
                        if (ep.watched) Icons.Rounded.CheckCircle else Icons.Rounded.Schedule,
                        contentDescription = if (ep.watched) "Watched" else "Mark watched",
                        tint = if (ep.watched) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = !menuOpen }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "Episode actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Get subtitles") },
                            leadingIcon = { Icon(Icons.Rounded.Subtitles, null) },
                            onClick = { menuOpen = false; onSubsDownload() },
                        )
                        DropdownMenuItem(
                            text = { Text("Publish subtitles next to video") },
                            leadingIcon = { Icon(Icons.Rounded.Download, null) },
                            onClick = { menuOpen = false; onSubsPublish() },
                        )
                        DropdownMenuItem(
                            text = { Text("Clear converted (Arabic)") },
                            leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                            onClick = { menuOpen = false; onSubsClear() },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete files") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeSizeLine(ep: Episode) {
    val text = when {
        ep.state == "done" && (ep.total ?: ep.downloaded) != null ->
            "${formatBytes(ep.total ?: ep.downloaded!!)} · on disk"

        ep.total != null && ep.downloaded != null ->
            "${formatBytes(ep.downloaded)} of ${formatBytes(ep.total)}"

        ep.state == "downloading" -> "Starting…"

        else -> "Not downloaded yet"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (ep.total != null && ep.downloaded != null && ep.state != "done") {
            LinearProgressIndicator(
                progress = { (ep.downloaded.toFloat() / ep.total.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.weight(1f).height(4.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- source picker
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcePickerSheet(
    vm: ShowDetailViewModel,
    episode: Episode,
    onDismiss: () -> Unit,
) {
    val state by vm.sourceInfo.collectAsState()
    val busy by vm.busy.collectAsState()
    var source by remember(episode.key) { mutableStateOf("cinejoy") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(source, episode.key) { vm.loadSource(episode.key, source) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text("Choose a source", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    episode.name.ifBlank { "Episode ${episode.episode}" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("cinejoy" to "Cinejoy", "movy" to "Movy", "akwam" to "AKWAM").forEach { (key, label) ->
                    FilterChip(
                        selected = source == key,
                        onClick = { if (source != key) source = key },
                        label = { Text(label) },
                    )
                }
            }

            when (val s = state) {
                SourceInfoState.Idle, SourceInfoState.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(
                        if (source == "cinejoy") "Capturing a fresh Cinejoy link — this can take up to a minute…"
                        else "Asking the PC for $source servers…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is SourceInfoState.Failed -> Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 12.dp),
                ) {
                    Text(
                        "Could not reach the PC: ${s.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = { vm.loadSource(episode.key, source) }) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Retry")
                    }
                }

                is SourceInfoState.Ready -> SourcePane(
                    vm = vm,
                    episode = episode,
                    source = s.source,
                    data = s.data,
                    starting = busy == "start-download",
                )
            }
        }
    }
}

@Composable
private fun SourcePane(
    vm: ShowDetailViewModel,
    episode: Episode,
    source: String,
    data: JsonObject,
    starting: Boolean,
) {
    if (data.bool("ok") == false) {
        Text(
            data.str("error") ?: "The PC could not resolve this source.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    when (source) {
        "movy" -> MovyPane(vm, episode, data, starting)
        "akwam" -> AkwamPane(vm, episode, data, starting)
        else -> CinejoyPane(vm, episode, data, starting)
    }
}

// --- Cinejoy: one captured master, choose a variant (or Auto).
@Composable
private fun CinejoyPane(
    vm: ShowDetailViewModel,
    episode: Episode,
    data: JsonObject,
    starting: Boolean,
) {
    val streamError = data.str("streamError")
    val server = data.str("server") ?: "nebula.bright67.online"
    if (streamError != null) {
        Text(
            "Link unavailable ($streamError). Try Refresh, or use Movy/AKWAM for this item.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(onClick = { vm.loadSource(episode.key, "cinejoy") }) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Refresh")
        }
        return
    }

    val variants = data.arr("variants")
    var selected by remember(episode.key) { mutableIntStateOf(-1) } // -1 = Auto
    val options = listOf(-1) + variants.mapIndexed { i, _ -> i }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Server: $server · ${if (variants.isEmpty()) "single stream" else "${variants.size} quality variant(s)"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            options.take(6).forEach { i ->
                val label = if (i < 0) "Auto" else (variants[i].int("height")?.let { "${it}p" } ?: "stream")
                FilterChip(
                    selected = selected == i,
                    onClick = { selected = i },
                    label = { Text(label) },
                )
            }
        }
        if (selected >= 0) {
            variants[selected].str("size")?.takeIf { it.isNotBlank() }?.let { size ->
                Text(
                    "Estimated size: $size",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(
            onClick = {
                val height = if (selected < 0) null else variants[selected].int("height")
                val label = if (selected < 0) "Auto (best)"
                else height?.let { "${it}p" } ?: "stream"
                vm.queueDownload(
                    episode.key,
                    buildMap {
                        put("source", "cinejoy")
                        put("qualityLabel", label)
                        height?.let { put("quality", it.toString()) }
                    },
                )
            },
            enabled = !starting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (starting) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Queueing…")
            } else {
                Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start download")
            }
        }
    }
}

// --- Movy: pick a server, then a quality from that server.
@Composable
private fun MovyPane(
    vm: ShowDetailViewModel,
    episode: Episode,
    data: JsonObject,
    starting: Boolean,
) {
    val servers = data.arr("servers")
    if (servers.isEmpty()) {
        Text("No movy servers found for this item.", style = MaterialTheme.typography.bodyMedium)
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (data.bool("rateLimited") == true) {
            Text(
                "Movy rate-limited some servers — wait a moment and Refresh.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        // The server list scrolls inside a capped-height box so the
        // quality chips and the Start download button stay visible no
        // matter how many servers the sweep returned.
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        servers.forEach { srv ->
            val key = srv.str("key") ?: return@forEach
            val ok = srv.bool("ok") == true && srv.arr("sources").isNotEmpty()
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (selectedServer == key) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = ok) { selectedServer = key },
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selectedServer == key, onClick = null, enabled = ok)
                    Column(Modifier.weight(1f)) {
                        Text(key, style = MaterialTheme.typography.titleSmall)
                        if (!ok) {
                            Text(
                                srv.str("error") ?: "no sources",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            // Quality+size badges under the name; scrollable so
                            // long lists never squeeze the server name to pieces.
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                srv.arr("sources")
                                    .mapNotNull { it.str("quality") }
                                    .distinct()
                                    .take(6)
                                    .forEach { q ->
                                        val size = srv.arr("sources").firstOrNull { it.str("quality") == q }?.str("size")
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                        ) {
                                            Text(
                                                if (size.isNullOrBlank()) q else "$q · $size",
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

        if (sources.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    labels.distinct().take(6).forEach { label ->
                        FilterChip(
                            selected = selectedLabel == label,
                            onClick = { selectedLabel = label },
                            label = { Text(label) },
                        )
                    }
                }
                sources.firstOrNull { it.str("quality") == selectedLabel }?.str("size")
                    ?.takeIf { it.isNotBlank() }?.let { size ->
                        Text(
                            "File size: $size",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
            }
        }

        Button(
            onClick = {
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
            },
            enabled = !starting && selectedServer != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (starting) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Queueing…")
            } else {
                Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start download")
            }
        }
    }
}

// --- AKWAM: every quality/server row is its own download option.
@Composable
private fun AkwamPane(
    vm: ShowDetailViewModel,
    episode: Episode,
    data: JsonObject,
    starting: Boolean,
) {
    val entries = data.arr("entries")
    if (entries.isEmpty()) {
        Text("No AKWAM qualities found for this item.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    var selectedIndex by remember(episode.key) { mutableIntStateOf(0) }
    val entry = entries.getOrNull(selectedIndex)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.forEachIndexed { i, e ->
            val label = e.str("label") ?: "Option ${i + 1}"
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (selectedIndex == i) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedIndex = i },
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selectedIndex == i, onClick = null)
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(
                                e.str("size")?.takeIf { it.isNotBlank() },
                                e.str("server")?.let { "server $it" },
                            ).joinToString(" · ").ifBlank { "quality option" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                val e = entry ?: return@Button
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
            },
            enabled = !starting && entry != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (starting) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Queueing…")
            } else {
                Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start download")
            }
        }
    }
}
