package com.nedal.cinejoyremote.ui.v3

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.nedal.cinejoyremote.domain.LibraryShow
import com.nedal.cinejoyremote.presentation.DevicesViewModel
import com.nedal.cinejoyremote.presentation.LibraryViewModel
import com.nedal.cinejoyremote.ui.v3.components.EmptyState

/**
 * V3 search (spec mockup 02, search view): one focused field over a poster
 * grid of live results from the PC library.
 */
@Composable
fun V3SearchScreen(
    devicesVm: DevicesViewModel,
    vm: LibraryViewModel,
    onOpenShow: (String) -> Unit,
) {
    val c = v3
    val shows by vm.shows.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }

    val results = remember(shows, query) {
        val q = query.trim()
        if (q.isEmpty()) emptyList()
        else shows.filter { it.title.contains(q, ignoreCase = true) }
            .sortedByDescending { it.addedAt ?: "" }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search library", style = V3Type.body) },
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = c.textSecondary)
            },
            shape = RoundedCornerShape(V3Radius.field),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = V3Space.gutter, vertical = V3Space.sm),
        )

        if (query.isBlank()) {
            EmptyState(
                icon = Icons.Rounded.Search,
                title = "Search your library",
                body = "Type a title to search the PC library.",
            )
        } else if (results.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Search,
                title = "No matches",
                body = "Nothing in your library matches “$query”.",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(V3Space.listGap),
                verticalArrangement = Arrangement.spacedBy(V3Space.lg),
                contentPadding = PaddingValues(
                    start = V3Space.gutter,
                    end = V3Space.gutter,
                    top = V3Space.sm,
                    bottom = V3Space.bottomClearance,
                ),
            ) {
                items(results, key = { "s-" + it.showId }) { show ->
                    V3SearchTile(show) { onOpenShow(show.showId) }
                }
            }
        }
    }
}

/** Result tile: 2:3 poster with the title under it (like the library grid). */
@Composable
private fun V3SearchTile(show: LibraryShow, onClick: () -> Unit) {
    val c = v3
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(V3Radius.poster))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(V3Radius.poster))
                .background(c.raised),
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
                Icon(
                    if (show.type == "movie") Icons.Rounded.Movie else Icons.Rounded.Tv,
                    contentDescription = null,
                    tint = c.textTertiary,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Text(
            show.title,
            Modifier.padding(top = 5.dp),
            style = V3Type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
