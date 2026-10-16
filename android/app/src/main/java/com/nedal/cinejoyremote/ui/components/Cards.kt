package com.nedal.cinejoyremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nedal.cinejoyremote.domain.LibraryShow
import com.nedal.cinejoyremote.ui.theme.pressScale

// ---------------------------------------------------------------- poster tiles
// Classic library tile: artwork with a bottom scrim, title over it, type
// badge on top and a small meta row under it. Shared (Library grid now,
// related rows later). Long-press proposes removal.

@Composable
fun ClassicPosterTile(
    show: LibraryShow,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .pressScale(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            val poster = show.poster
            if (poster != null) {
                Artwork(
                    model = poster,
                    contentDescription = show.title,
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

// Bare Netflix-style wall tile: artwork only — no badge, no title overlay.
@Composable
fun BarePosterTile(
    show: LibraryShow,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pressScale(),
    ) {
        val poster = show.poster
        if (poster != null) {
            Artwork(
                model = poster,
                contentDescription = show.title,
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
