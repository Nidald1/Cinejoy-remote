package com.nedal.cinejoyremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import coil.compose.AsyncImage
import com.nedal.cinejoyremote.domain.LibraryShow
import com.nedal.cinejoyremote.presentation.DevicesViewModel
import com.nedal.cinejoyremote.presentation.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    devicesVm: DevicesViewModel,
    vm: LibraryViewModel,
    factory: androidx.lifecycle.ViewModelProvider.Factory,
    snackbar: SnackbarHostState,
    onOpenShow: (String) -> Unit,
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

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add link") },
            )
        },
    ) { inner ->
        LazyVerticalGrid(
            // The wall is tighter and the tiles a touch bigger, like the app
            // it borrows its look from; classic keeps the old metrics exactly.
            columns = GridCells.Adaptive(minSize = if (netflix) 142.dp else 124.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            horizontalArrangement = Arrangement.spacedBy(if (netflix) 10.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (netflix) 10.dp else 14.dp),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 96.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(Modifier.weight(1f)) {
                        DeviceRow(devices, selected, onSelect = devicesVm::select)
                    }
                    var sortMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }) {
                        Icon(
                            if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = "Search library",
                        )
                    }
                    Box {
                        IconButton(onClick = { sortMenu = !sortMenu }) {
                            Icon(Icons.Rounded.Sort, contentDescription = "Sort library")
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf(
                                "newest" to "Newest first",
                                "oldest" to "Oldest first",
                                "name" to "Name A→Z",
                                "name_desc" to "Name Z→A",
                            ).forEach { (mode, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (sortMode == mode) {
                                                Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                            }
                                            Text(label)
                                        }
                                    },
                                    onClick = { sortMode = mode; sortMenu = false },
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { selected?.let { vm.refresh(it) } },
                        enabled = !refreshing && selected != null,
                    ) {
                        if (refreshing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh library")
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                // Movies | Series | All — two shelves instead of one mixed grid.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "all" to "All",
                        "movie" to "Movies",
                        "tv" to "Series",
                    ).forEach { (mode, label) ->
                        val selected = typeFilter == mode
                        val count = typeCounts.firstOrNull { it.first == mode }?.second ?: 0
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { typeFilter = mode },
                        ) {
                            Text(
                                "$label · $count",
                                Modifier.fillMaxWidth().padding(vertical = 9.dp),
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (searchOpen) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search library") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (filtered.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (shows.isEmpty()) EmptyLibrary(hasDevice = selected != null)
                    else Column(
                        Modifier.fillMaxWidth().padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("🔍", style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("No matches", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Nothing in your library matches “$query”.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(filtered, key = { it.showId }) { show ->
                if (netflix) {
                    NetflixPoster(show) { onOpenShow(show.showId) }
                } else {
                    PosterCard(show) { onOpenShow(show.showId) }
                }
            }
        }
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

// ---------------------------------------------------------------- poster grid
// Netflix-style wall tile: bare artwork only — no type badge, no title over a
// scrim, no meta row — the clean poster look the Netflix app uses. Tapping a
// tile still opens the same title page (whose style is Settings -> Title page).
@Composable
private fun NetflixPoster(show: LibraryShow, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        val poster = show.poster
        if (poster != null) {
            AsyncImage(
                model = poster,
                contentDescription = show.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // No artwork: keep the tile readable instead of a blank square.
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    if (show.type == "movie") Icons.Rounded.Movie else Icons.Rounded.Tv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    show.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PosterCard(show: LibraryShow, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            val poster = show.poster
            if (poster != null) {
                AsyncImage(
                    model = poster,
                    contentDescription = show.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Bottom scrim keeps the title readable over any artwork.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                            ),
                        ),
                )
            } else {
                Icon(
                    if (show.type == "movie") Icons.Rounded.Movie else Icons.Rounded.Tv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            ) {
                Text(
                    if (show.type == "movie") "Movie" else "Series",
                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (poster != null) {
                Text(
                    show.title,
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (show.poster == null) {
            Text(
                show.title,
                Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            Modifier.padding(top = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            show.year?.takeIf { it.isNotBlank() }?.let { MetaChip(it) }
            MetaChip("${show.episodes} ep")
        }
    }
}

@Composable
private fun EmptyLibrary(hasDevice: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🎬", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(8.dp))
        Text("Your library is empty", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            if (hasDevice) "Tap “Add link” and paste any watch link — cinejoy.pk, movy.sx, akwam, themoviedb.org or imdb.com. The PC will fetch it and you pick the download source right after."
            else "Sign the PC app in first — then add movies and series from here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun MetaChip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
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
