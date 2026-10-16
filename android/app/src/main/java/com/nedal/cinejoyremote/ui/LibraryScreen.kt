package com.nedal.cinejoyremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nedal.cinejoyremote.domain.LibraryShow
import com.nedal.cinejoyremote.presentation.DevicesViewModel
import com.nedal.cinejoyremote.presentation.LibraryViewModel
import com.nedal.cinejoyremote.ui.components.Artwork
import com.nedal.cinejoyremote.ui.components.BarePosterTile
import com.nedal.cinejoyremote.ui.components.ClassicPosterTile
import com.nedal.cinejoyremote.ui.components.EmptyState
import com.nedal.cinejoyremote.ui.theme.Spacing
import com.nedal.cinejoyremote.share.TmdbFetcher
import com.nedal.cinejoyremote.share.TmdbRef
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Link

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    devicesVm: DevicesViewModel,
    vm: LibraryViewModel,
    factory: androidx.lifecycle.ViewModelProvider.Factory,
    snackbar: SnackbarHostState,
    onOpenShow: (String) -> Unit,
    onOpenSearch: () -> Unit = {},
) {
    val devices by devicesVm.devices.collectAsState()
    val selected by devicesVm.selectedDeviceId.collectAsState()
    val shows by vm.shows.collectAsState()
    val error by vm.error.collectAsState()
    val adding by vm.adding.collectAsState()
    val addedShowId by vm.addedShowId.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val notice by vm.notice.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("newest") }
    // Movies and series are separate shelves: "all" keeps the old combined view.
    var typeFilter by remember { mutableStateOf("all") }
    // Long-press on a poster proposes removal; the dialog confirms first so a
    // stray press can never drop a title from the PC catalog.
    var pendingRemove by remember { mutableStateOf<LibraryShow?>(null) }
    // Settings -> Library: classic card vs Netflix-style bare-poster wall.
    val netflix = LibraryStyleController.mode == LibraryStyle.NETFLIX

    val filtered = remember(shows, query, sortMode, typeFilter) {
        val q = query.trim()
        val searched = if (q.isEmpty()) shows else shows.filter { it.title.contains(q, ignoreCase = true) }
        val base = if (typeFilter == "all") searched else searched.filter { it.type == typeFilter }
        when (sortMode) {
            "name" -> base.sortedBy { it.title.lowercase() }
            "name_desc" -> base.sortedByDescending { it.title.lowercase() }
            "oldest" -> base.sortedBy { it.addedAt ?: "" }
            else -> base.sortedByDescending { it.addedAt ?: "" } // newest first
        }
    }
    val typeCounts = remember(shows) {
        listOf(
            "all" to shows.size,
            "movie" to shows.count { it.type == "movie" },
            "tv" to shows.count { it.type == "tv" },
        )
    }
    // Hero rotation pool: up to 8 newest titles with movies and series
    // interleaved, so the banner always changes between DIFFERENT kinds of
    // titles (spec: min 6, no back-to-back repeats — a distinct-items list
    // can't repeat).
    val heroItems = remember(filtered) {
        val movies = filtered.filter { it.type == "movie" }
        val series = filtered.filter { it.type == "tv" }
        val mixed = ArrayList<LibraryShow>(8)
        var m = 0
        var s = 0
        while (mixed.size < 8 && (m < movies.size || s < series.size)) {
            if (m < movies.size) mixed.add(movies[m++])
            if (mixed.size < 8 && s < series.size) mixed.add(series[s++])
        }
        mixed
    }

    // Design v2: search / sort / filter / add / refresh are top-bar icons —
    // the old DeviceRow pill, the type pill farm and the inline search field
    // are gone from the layout entirely.
    var sortSheet by remember { mutableStateOf(false) }
    var filterSheet by remember { mutableStateOf(false) }
    ProvideTopBar(
        title = "Library",
        actions = listOf(
            ChromeAction(Icons.Rounded.Search, "Search library") { onOpenSearch() },
            ChromeAction(Icons.Rounded.Sort, "Sort library") { sortSheet = true },
            ChromeAction(Icons.Rounded.FilterList, "Filter library") { filterSheet = true },
            ChromeAction(Icons.Rounded.Link, "Add by link") { showAdd = true },
            ChromeAction(Icons.Rounded.Refresh, "Refresh library") {
                selected?.let { vm.refresh(it) }
            },
        ),
    )
    if (sortSheet) {
        CineSheet(title = "Sort by", onDismiss = { sortSheet = false }) {
            listOf(
                "newest" to "Newest first",
                "oldest" to "Oldest first",
                "name" to "Name A→Z",
                "name_desc" to "Name Z→A",
            ).forEach { (mode, label) ->
                SheetOption(label, selected = sortMode == mode, onClick = { sortMode = mode; sortSheet = false })
            }
        }
    }
    if (filterSheet) {
        CineSheet(title = "Show", onDismiss = { filterSheet = false }) {
            listOf("all" to "All", "movie" to "Movies", "tv" to "Series").forEach { (mode, label) ->
                val count = typeCounts.firstOrNull { it.first == mode }?.second ?: 0
                SheetOption("$label  ·  $count", selected = typeFilter == mode, onClick = { typeFilter = mode; filterSheet = false })
            }
        }
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

    Column(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            // The wall is tighter and the tiles a touch bigger, like the app
            // it borrows its look from; classic keeps the old metrics exactly.
            columns = GridCells.Adaptive(minSize = if (netflix) 142.dp else 124.dp),
            modifier = Modifier
                .fillMaxSize(),
            // On the 4/8 grid: the Netflix wall packs tighter than classic.
            horizontalArrangement = Arrangement.spacedBy(if (netflix) Spacing.sm else Spacing.md),
            verticalArrangement = Arrangement.spacedBy(if (netflix) Spacing.md else Spacing.lg),
            contentPadding = PaddingValues(
                start = Spacing.gutter,
                end = Spacing.gutter,
                top = Spacing.xs,
                bottom = Spacing.bottomClearance,
            ),
        ) {
            if (filtered.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (shows.isEmpty()) {
                        EmptyState(
                            emoji = "🎬",
                            title = "Your library is empty",
                            message = if (selected != null) {
                                "Tap “Add link” and paste any watch link — cinejoy.pk, movy.sx, akwam, themoviedb.org or imdb.com. The PC will fetch it and you pick the download source right after."
                            } else {
                                "Sign the PC app in first — then add movies and series from here."
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        EmptyState(
                            emoji = "🔍",
                            title = "No matches",
                            message = "Nothing in your library matches “$query”.",
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        )
                    }
                }
            }
            // Auto-rotating cinematic hero — up to 8 titles, movies and
            // series interleaved, no back-to-back repeats (a static list of
            // distinct items can't repeat). Hidden while filtering shelves.
            if (heroItems.isNotEmpty() && typeFilter == "all") {
                item(span = { GridItemSpan(maxLineSpan) }, key = "hero") {
                    HeroPager(items = heroItems, onOpenShow = onOpenShow)
                }
            }
            items(filtered, key = { it.showId }) { show ->
                val open = { onOpenShow(show.showId); Unit }
                val proposeRemove = { pendingRemove = show; Unit }
                if (netflix) {
                    BarePosterTile(show, onClick = open, onLongClick = proposeRemove)
                } else {
                    ClassicPosterTile(show, onClick = open, onLongClick = proposeRemove)
                }
            }
        }
    }

    pendingRemove?.let { show ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove from library?") },
            text = {
                Text(
                    "“${show.title}” will be removed from the PC catalog. " +
                        "Downloaded files stay on disk.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    selected?.let { vm.removeShow(it, show.showId) }
                    pendingRemove = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("Cancel") }
            },
        )
    }

    if (showAdd) {
        AddLinkSheet(
            adding = adding,
            onDismiss = { showAdd = false },
            onAdd = { url ->
                selected?.let { vm.addShow(it, url) } ?: run { showAdd = false }
            },
        )
    }
}

// ---------------------------------------------------------------- hero pager
// Design-v2 hero: auto-rotates every 6s through up to 8 different titles
// (movies and series interleaved), crossfades via the pager, swipeable, with
// page dots. Auto-advance pauses while the user is dragging, for 4s after a
// swipe, and whenever the app is not in the foreground.
@Composable
private fun HeroPager(
    items: List<LibraryShow>,
    onOpenShow: (String) -> Unit,
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState { items.size }

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
            if (items.size < 2 || !inForeground || dragging ||
                System.currentTimeMillis() < holdUntil
            ) continue
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val show = items[page]
            HeroSlide(show = show) { onOpenShow(show.showId) }
        }
        // Page dots.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            items.forEachIndexed { i, _ ->
                val active = i == pagerState.currentPage
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .clip(RoundedCornerShape(50))
                        .size(width = if (active) 14.dp else 5.dp, height = 5.dp)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.35f),
                        ),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- poster grid
// One hero slide: full-bleed backdrop under a scrim with the type chip, year,
// title, one-line synopsis and a compact Details action.
@Composable
private fun HeroSlide(show: LibraryShow, onClick: () -> Unit) {
    // Start with the mirrored poster so the hero paints instantly; upgrade to
    // the real backdrop once TMDb answers (rows without a TMDb id keep the
    // poster — search-per-row would hammer TMDb from a scrolling grid).
    var art by remember(show.showId, show.poster) { mutableStateOf(show.poster) }
    LaunchedEffect(show.showId, show.tmdb) {
        val tmdb = show.tmdb ?: return@LaunchedEffect
        TmdbFetcher.fetch(TmdbRef(show.type, tmdb)).getOrNull()
            ?.backdropUrl?.let { art = it }
    }
    Box(
        Modifier
            // Escape the grid's gutters so the art touches both edges.
            // Modifier.padding rejects negative values ("Padding must be
            // non-negative" — it crashed every Netflix-wall library open), so
            // measure at full screen width (requiredWidth ignores the grid's
            // item constraints) and shift left by the gutter instead.
            .requiredWidth(LocalConfiguration.current.screenWidthDp.dp)
            .offset(x = -Spacing.gutter)
            .height(220.dp)
            .clip(RoundedCornerShape(0.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Artwork(
            model = art,
            contentDescription = show.title,
            modifier = Modifier.fillMaxSize(),
        )
        // Bottom scrim keeps title + synopsis readable over any artwork.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(150.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 26.dp, vertical = 14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        if (show.type == "movie") "MOVIE" else "SERIES",
                        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                show.year?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                show.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            show.overview?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- add link
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLinkSheet(adding: Boolean, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { if (!adding) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    "Add movie or series",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Paste any watch link — cinejoy.pk, movy.sx, akwam, themoviedb.org or imdb.com. You will pick the download source next.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("https://cinejoy.pk/watch/…  ·  https://akwam.…  ·  imdb.com/title/tt…") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            if (adding) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    "The PC is fetching show info…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(
                    enabled = !adding && url.isNotBlank(),
                    onClick = { onAdd(url.trim()) },
                ) { Text(if (adding) "Adding…" else "Add") }
            }
        }
    }
}

// ------------------------------------------------------------------- search
// Full search screen opened from the Library top bar: one focused field over
// a poster grid of live results. (The old inline field that squeezed the
// grid is gone.)
@Composable
fun LibrarySearchScreen(
    devicesVm: DevicesViewModel,
    vm: LibraryViewModel,
    onOpenShow: (String) -> Unit,
) {
    val shows by vm.shows.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val netflix = LibraryStyleController.mode == LibraryStyle.NETFLIX

    ProvideTopBar(title = "Search", actions = emptyList())

    val results = remember(shows, query) {
        val q = query.trim()
        if (q.isEmpty()) emptyList()
        else shows.filter { it.title.contains(q, ignoreCase = true) }
            .sortedByDescending { it.addedAt ?: "" }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.gutter)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search library") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )
        if (query.isBlank()) {
            CineEmpty(
                icon = Icons.Rounded.Search,
                message = "Type a title to search the PC library.",
            )
        } else if (results.isEmpty()) {
            CineEmpty(
                icon = Icons.Rounded.Search,
                message = "Nothing in your library matches \u201C$query\u201D.",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if (netflix) 142.dp else 124.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(if (netflix) Spacing.sm else Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                contentPadding = PaddingValues(top = Spacing.md, bottom = Spacing.bottomClearance),
            ) {
                items(results, key = { "s-" + it.showId }) { show ->
                    if (netflix) {
                        BarePosterTile(show, onClick = { onOpenShow(show.showId) }, onLongClick = {})
                    } else {
                        ClassicPosterTile(show, onClick = { onOpenShow(show.showId) }, onLongClick = {})
                    }
                }
            }
        }
    }
}
