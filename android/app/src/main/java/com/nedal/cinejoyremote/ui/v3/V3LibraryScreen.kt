package com.nedal.cinejoyremote.ui.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.nedal.cinejoyremote.domain.LibraryShow
import com.nedal.cinejoyremote.presentation.DevicesViewModel
import com.nedal.cinejoyremote.presentation.LibraryViewModel
import com.nedal.cinejoyremote.share.TmdbFetcher
import com.nedal.cinejoyremote.share.TmdbHint
import com.nedal.cinejoyremote.share.TmdbRef
import com.nedal.cinejoyremote.ui.theme.pressScale
import com.nedal.cinejoyremote.ui.v3.components.EmptyState
import com.nedal.cinejoyremote.ui.v3.components.SheetRow
import com.nedal.cinejoyremote.ui.v3.components.V3IconAction
import com.nedal.cinejoyremote.ui.v3.components.V3Pill
import com.nedal.cinejoyremote.ui.v3.components.V3Sheet
import kotlinx.coroutines.delay

private val SORT_OPTIONS = listOf(
    "newest" to "Newest first",
    "oldest" to "Oldest first",
    "name" to "Name A→Z",
    "name_desc" to "Name Z→A",
)

/**
 * V3 Library — mockup 01: full-bleed cinematic hero (auto-rotating pager),
 * search pill, type chips, curated section rows with See All, then the full
 * poster grid. Long-press a poster to propose removal; "Add by link" lives
 * in the more-menu with refresh; sort/filter as sheets.
 */
@Composable
fun V3LibraryScreen(
    devicesVm: DevicesViewModel,
    vm: LibraryViewModel,
    snackbar: SnackbarHostState,
    onOpenShow: (String) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val c = v3
    val devices by devicesVm.devices.collectAsState()
    val selected by devicesVm.selectedDeviceId.collectAsState()
    val shows by vm.shows.collectAsState()
    val error by vm.error.collectAsState()
    val adding by vm.adding.collectAsState()
    val addedShowId by vm.addedShowId.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val notice by vm.notice.collectAsState()

    var sortMode by rememberSaveable { mutableStateOf("newest") }
    var typeFilter by rememberSaveable { mutableStateOf("all") }
    var showAdd by remember { mutableStateOf(false) }
    var sortSheet by remember { mutableStateOf(false) }
    var moreSheet by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<LibraryShow?>(null) }
    val gridState = rememberLazyGridState()

    val filtered = remember(shows, sortMode, typeFilter) {
        val searched = shows
        val base = if (typeFilter == "all") searched else searched.filter { it.type == typeFilter }
        when (sortMode) {
            "name" -> base.sortedBy { it.title.lowercase() }
            "name_desc" -> base.sortedByDescending { it.title.lowercase() }
            "oldest" -> base.sortedWith(compareBy { it.addedAt ?: "" })
            else -> base.sortedWith(compareByDescending { it.addedAt ?: "" })
        }
    }
    val typeCounts = remember(shows) {
        listOf(
            "all" to shows.size,
            "movie" to shows.count { it.type == "movie" },
            "tv" to shows.count { it.type == "tv" },
        )
    }
    val recent = remember(shows) { shows.sortedByDescending { it.addedAt ?: "" }.take(12) }
    val movies = remember(shows) { shows.filter { it.type == "movie" }.sortedByDescending { it.addedAt ?: "" }.take(12) }
    val series = remember(shows) { shows.filter { it.type == "tv" }.sortedByDescending { it.addedAt ?: "" }.take(12) }
    // Hero rotation pool: up to 8 newest titles with movies and series
    // interleaved so the banner always changes between DIFFERENT kinds.
    val heroItems = remember(shows) {
        val m = shows.filter { it.type == "movie" }.sortedByDescending { it.addedAt ?: "" }
        val s = shows.filter { it.type == "tv" }.sortedByDescending { it.addedAt ?: "" }
        val mixed = ArrayList<LibraryShow>(8)
        var mi = 0
        var si = 0
        while (mixed.size < 8 && (mi < m.size || si < s.size)) {
            if (mi < m.size) mixed.add(m[mi++])
            if (mixed.size < 8 && si < s.size) mixed.add(s[si++])
        }
        mixed
    }

    LaunchedEffect(devices) { devicesVm.autoSelect(devices) }
    LaunchedEffect(addedShowId) {
        addedShowId?.let { id -> vm.clearAdded(); onOpenShow(id) }
    }
    LaunchedEffect(error) {
        error?.let { msg -> snackbar.showSnackbar(msg); vm.clearError() }
    }
    LaunchedEffect(notice) {
        notice?.let { msg -> snackbar.showSnackbar(msg); vm.clearNotice() }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(V3Space.listGap),
        verticalArrangement = Arrangement.spacedBy(V3Space.lg),
        contentPadding = PaddingValues(
            start = V3Space.gutter,
            end = V3Space.gutter,
            top = V3Space.xs,
            bottom = V3Space.bottomClearance,
        ),
    ) {
        // ---- full-bleed hero (escapes the grid's gutters like the v2 fix).
        if (heroItems.isNotEmpty() && typeFilter == "all") {
            item(span = { GridItemSpan(maxLineSpan) }, key = "hero") {
                V3HeroPager(items = heroItems, onOpenShow = onOpenShow)
            }
        }

        // ---- search pill (mockup 01).
        item(span = { GridItemSpan(maxLineSpan) }, key = "search") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = V3Space.sm)
                    .clip(RoundedCornerShape(V3Radius.field))
                    .background(c.raised)
                    .clickable(onClick = onOpenSearch)
                    .padding(horizontal = V3Space.md, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(V3Space.sm))
                Text("Search movies and series", style = V3Type.body14, color = c.textTertiary)
            }
        }

        // ---- type chips + sort/more (mockup 01 chip row).
        item(span = { GridItemSpan(maxLineSpan) }, key = "chips") {
            Row(
                Modifier.fillMaxWidth().padding(top = V3Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("all" to "All", "movie" to "Movies", "tv" to "Series").forEachIndexed { i, (mode, label) ->
                    if (i > 0) Spacer(Modifier.width(V3Space.sm))
                    V3Pill(label = label, selected = typeFilter == mode, onClick = { typeFilter = mode })
                }
                Spacer(Modifier.weight(1f))
                V3IconAction(Icons.Rounded.Sort, "Sort library") { sortSheet = true }
                V3IconAction(Icons.Rounded.FilterList, "Filter library") { sortSheet = true }
                V3IconAction(Icons.Rounded.Link, "Add by link") { showAdd = true }
                V3IconAction(Icons.Rounded.Refresh, "Refresh library") {
                    selected?.let { vm.refresh(it) }
                }
            }
        }

        if (refreshing) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "refresh") {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            }
        }

        if (filtered.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                if (shows.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.Movie,
                        title = "Your library is empty",
                        body = if (selected != null) {
                            "Tap “Add by link” and paste any watch link — cinejoy.pk, movy.sx, akwam, themoviedb.org or imdb.com. The PC will fetch it and you pick the download source right after."
                        } else {
                            "Sign the PC app in first — then add movies and series from here."
                        },
                        actions = if (selected != null) {
                            { Button(onClick = { showAdd = true }) { Text("Add by link") } }
                        } else null,
                    )
                } else {
                    EmptyState(
                        icon = Icons.Rounded.Search,
                        title = "No matches",
                        body = "No titles match the current filter.",
                        actions = {
                            Button(onClick = { typeFilter = "all"; sortMode = "newest" }) {
                                Text("Reset filters")
                            }
                        },
                    )
                }
            }
        } else {
            // ---- section rows (mockup 01: sections with See All links).
            if (typeFilter == "all" && recent.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "sec-recent") {
                    V3SectionHeader("Recently Added") { /* the grid below already shows all */ }
                }
                item(span = { GridItemSpan(maxLineSpan) }, key = "row-recent") {
                    V3PosterRow(recent, onOpenShow, onLongPress = { pendingRemove = it })
                }
                if (movies.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "sec-movies") {
                        V3SectionHeader("Movies") { typeFilter = "movie" }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }, key = "row-movies") {
                        V3PosterRow(movies, onOpenShow, onLongPress = { pendingRemove = it })
                    }
                }
                if (series.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "sec-series") {
                        V3SectionHeader("New TV Series") { typeFilter = "tv" }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }, key = "row-series") {
                        V3PosterRow(series, onOpenShow, onLongPress = { pendingRemove = it })
                    }
                }
            }

            // ---- full poster grid (the spec's 112x168dp poster tiles).
            item(span = { GridItemSpan(maxLineSpan) }, key = "sec-all") {
                V3SectionHeader("All Titles") { typeFilter = "all" }
            }
            items(filtered, key = { it.showId }) { show ->
                V3PosterTile(
                    show = show,
                    onClick = { onOpenShow(show.showId) },
                    onLongClick = { pendingRemove = show },
                )
            }
        }
    }

    if (sortSheet) {
        V3Sheet(title = "Sort by", onDismiss = { sortSheet = false }) {
            SORT_OPTIONS.forEach { (mode, label) ->
                SheetRow(label, selected = sortMode == mode, onClick = { sortMode = mode; sortSheet = false })
            }
        }
    }
    if (moreSheet) {
        V3Sheet(title = "Library", onDismiss = { moreSheet = false }) {
            SheetRow("Add by link", onClick = { moreSheet = false; showAdd = true })
            SheetRow("Refresh library", onClick = { moreSheet = false; selected?.let { vm.refresh(it) } })
        }
    }

    pendingRemove?.let { show ->
        val cc = v3
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingRemove = null },
            containerColor = cc.sheet,
            title = { Text("Remove from library?", color = cc.text) },
            text = {
                Text(
                    "“${show.title}” will be removed from the PC catalog. Downloaded files stay on disk.",
                    color = cc.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    selected?.let { vm.removeShow(it, show.showId) }
                    pendingRemove = null
                }) { Text("Remove", color = cc.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("Cancel", color = cc.textSecondary) }
            },
        )
    }

    if (showAdd) {
        V3AddLinkSheet(
            adding = adding,
            onDismiss = { showAdd = false },
            onAdd = { url ->
                selected?.let { vm.addShow(it, url) } ?: run { showAdd = false }
            },
        )
    }
}

private fun jumpToAll(gridState: androidx.compose.foundation.lazy.grid.LazyGridState) {
    // kept for future deep-link: scroll the "All Titles" header into view.
    gridState.requestScrollToItem(0)
}

// ---------------------------------------------------------------- hero pager

/** Auto-rotating cinematic hero — 6s interval, pauses while dragging/hidden. */
@Composable
private fun V3HeroPager(items: List<LibraryShow>, onOpenShow: (String) -> Unit) {
    if (items.isEmpty()) return
    val pagerState = androidx.compose.foundation.pager.rememberPagerState { items.size }

    val lifecycleOwner = LocalLifecycleOwner.current
    var inForeground by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            inForeground = event == Lifecycle.Event.ON_RESUME
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val dragging by pagerState.interactionSource.collectIsDraggedAsState()
    var holdUntil by remember { mutableStateOf(0L) }
    LaunchedEffect(dragging) {
        if (!dragging) holdUntil = System.currentTimeMillis() + 4000
    }
    LaunchedEffect(pagerState, items.size) {
        while (true) {
            delay(6_000)
            if (items.size < 2 || !inForeground || dragging || System.currentTimeMillis() < holdUntil) continue
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val show = items[page]
            V3HeroSlide(show = show) { onOpenShow(show.showId) }
        }
        // Page dots.
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            items.forEachIndexed { i, _ ->
                val active = i == pagerState.currentPage
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .clip(RoundedCornerShape(50))
                        .size(width = if (active) 14.dp else 5.dp, height = 5.dp)
                        .background(if (active) v3.accent else Color.White.copy(alpha = 0.35f)),
                )
            }
        }
    }
}

/** One hero slide: backdrop under a scrim, chip+year, 24sp title, synopsis, Details. */
@Composable
private fun V3HeroSlide(show: LibraryShow, onClick: () -> Unit) {
    val c = v3
    // Paint the mirrored poster instantly; upgrade to the real backdrop when
    // TMDb answers (rows without a TMDb id keep the poster).
    var art by remember(show.showId, show.poster) { mutableStateOf(show.poster) }
    LaunchedEffect(show.showId, show.tmdb) {
        val tmdb = show.tmdb ?: return@LaunchedEffect
        TmdbFetcher.fetch(TmdbRef(show.type, tmdb)).getOrNull()
            ?.backdropUrl?.let { art = it }
    }
    Box(
        Modifier
            // Escape the grid gutters: measure at full screen width (the
            // negative-padding crash fix) and shift left by the gutter.
            .requiredWidth(LocalConfiguration.current.screenWidthDp.dp)
            .offset(x = -V3Space.gutter)
            .height(300.dp)
            .clip(RoundedCornerShape(0.dp))
            .clickable(onClick = onClick)
            .background(c.surface),
    ) {
        if (art.isNullOrBlank()) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(Color(0xFF16233B), Color(0xFF0B0D12))),
                ),
            )
        } else {
            AsyncImage(
                model = art,
                contentDescription = show.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Bottom scrim keeps title + synopsis readable over any artwork.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(170.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = V3Space.gutter, vertical = V3Space.md),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(V3Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(V3Radius.pill))
                        .background(c.accent)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        if (show.type == "movie") "MOVIE" else "SERIES",
                        style = V3Type.caption.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                }
                show.year?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = V3Type.caption12, color = Color.White.copy(alpha = 0.85f))
                }
            }
            Spacer(Modifier.height(V3Space.xs))
            Text(
                show.title,
                style = V3Type.heroTitle.copy(fontSize = 24.sp, lineHeight = 28.sp),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            show.overview?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    style = V3Type.body,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(V3Space.md))
            Box(
                Modifier
                    .clip(RoundedCornerShape(V3Radius.pill))
                    .background(Color.White.copy(alpha = 0.16f))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 18.dp, vertical = 7.dp),
            ) {
                Text("Details", style = V3Type.chip, color = Color.White)
            }
        }
    }
}

// --------------------------------------------------------------- section row

@Composable
private fun V3SectionHeader(title: String, onSeeAll: () -> Unit) {
    val c = v3
    Row(
        Modifier.fillMaxWidth().padding(top = V3Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = V3Type.sectionTitle, color = c.text, modifier = Modifier.weight(1f))
        Text(
            "See All",
            style = V3Type.caption12,
            color = c.activeText,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onSeeAll)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

/** Horizontal poster row: 104x156dp tiles per the mockup. */
@Composable
private fun V3PosterRow(
    items: List<LibraryShow>,
    onOpenShow: (String) -> Unit,
    onLongPress: (LibraryShow) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(V3Space.md)) {
        items(items, key = { "r-" + it.showId }) { show ->
            V3PosterTile(
                show = show,
                onClick = { onOpenShow(show.showId) },
                onLongClick = { onLongPress(show) },
                fixedWidth = 104.dp,
                showMeta = false,
            )
        }
    }
}

// ---------------------------------------------------------------- poster tile

// cj_library rows can carry poster=NULL (mirrored before the PC scraped art —
// The Sopranos' 86-episode row is exactly that) or a picsum.photos seed URL
// (a placeholder image, not the show). Both render as a blank or foreign tile,
// so resolve real art phone-side: straight from the row's TMDb id when it has
// one, otherwise by a title+year search. "" in the cache means "we looked and
// there is nothing" so a bad row costs two TMDb calls once, not per scroll.
private val posterArtCache =
    java.util.Collections.synchronizedMap(mutableMapOf<String, String>())

private fun isRealPoster(url: String?): Boolean =
    !url.isNullOrBlank() && url != "null" && "picsum.photos" !in url

private suspend fun resolvePosterArt(show: LibraryShow): String? {
    show.tmdb?.let { tmdb ->
        TmdbFetcher.fetch(TmdbRef(show.type, tmdb)).getOrNull()?.posterUrl?.let { return it }
    }
    val ref = TmdbFetcher.searchRef(TmdbHint(show.type, show.title, show.year)) ?: return null
    return TmdbFetcher.fetch(ref).getOrNull()?.posterUrl
}

@Composable
private fun rememberPosterArt(show: LibraryShow): String? {
    var art by remember(show.showId) {
        mutableStateOf(
            show.poster.takeIf { isRealPoster(it) }
                ?: posterArtCache[show.showId]?.ifBlank { null },
        )
    }
    LaunchedEffect(show.showId) {
        if (art != null || posterArtCache.containsKey(show.showId)) return@LaunchedEffect
        val resolved = runCatching { resolvePosterArt(show) }.getOrNull()
        posterArtCache[show.showId] = resolved ?: ""
        art = resolved
    }
    return art
}

@Composable
private fun V3PosterTile(
    show: LibraryShow,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    fixedWidth: Dp? = null,
    showMeta: Boolean = true,
) {
    val c = v3
    Column(
        modifier
            .then(if (fixedWidth != null) Modifier.width(fixedWidth) else Modifier.fillMaxWidth())
            .clip(RoundedCornerShape(V3Radius.poster))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .pressScale(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(V3Radius.poster))
                .background(c.raised),
        ) {
            val poster = rememberPosterArt(show)
            if (poster != null) {
                AsyncImage(
                    model = poster,
                    contentDescription = show.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    Modifier.align(Alignment.Center).padding(horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        if (show.type == "movie") Icons.Rounded.Movie else Icons.Rounded.Tv,
                        contentDescription = null,
                        tint = c.textTertiary,
                    )
                    Spacer(Modifier.height(V3Space.xs))
                    Text(
                        show.title,
                        style = V3Type.caption,
                        color = c.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (showMeta) {
            Text(
                show.title,
                Modifier.padding(top = 5.dp),
                style = V3Type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                show.year?.takeIf { it.isNotBlank() },
                "${show.episodes} ep",
            ).joinToString(" · ")
            Text(meta, style = V3Type.caption, color = c.textTertiary, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- add link

@Composable
private fun V3AddLinkSheet(adding: Boolean, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    val c = v3
    var url by remember { mutableStateOf("") }
    V3Sheet(title = "Add movie or series", onDismiss = { if (!adding) onDismiss() }) {
        Text(
            "Paste any watch link — cinejoy.pk, movy.sx, akwam, themoviedb.org or imdb.com. " +
                "You will pick the download source next.",
            style = V3Type.body,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(V3Space.md))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("https://cinejoy.pk/watch/…") },
            singleLine = true,
            shape = RoundedCornerShape(V3Radius.field),
            modifier = Modifier.fillMaxWidth(),
        )
        if (adding) {
            Spacer(Modifier.height(V3Space.md))
            LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp))
            Spacer(Modifier.height(V3Space.xs))
            Text("The PC is fetching show info…", style = V3Type.caption, color = c.textSecondary)
        }
        Row(Modifier.fillMaxWidth().padding(top = V3Space.sm), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Cancel", color = c.textSecondary) }
            TextButton(enabled = !adding && url.isNotBlank(), onClick = { onAdd(url.trim()) }) {
                Text(if (adding) "Adding…" else "Add", color = c.accent)
            }
        }
    }
}
