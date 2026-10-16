package com.nedal.cinejoyremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

// The app's one image path: Coil with a 250ms crossfade (posters fade in
// instead of popping while scrolling), the surface colour as the placeholder,
// and a fallback slot so every screen degrades the same way when artwork is
// missing.
@Composable
fun Artwork(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(model)
                    .crossfade(250)
                    .build(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            fallback()
        }
    }
}
