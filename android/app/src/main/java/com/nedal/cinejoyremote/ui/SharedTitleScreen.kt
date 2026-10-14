package com.nedal.cinejoyremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nedal.cinejoyremote.presentation.DevicesViewModel
import com.nedal.cinejoyremote.presentation.SharedAction
import com.nedal.cinejoyremote.presentation.SharedTitleState
import com.nedal.cinejoyremote.presentation.SharedTitleViewModel
import com.nedal.cinejoyremote.share.CastMember
import com.nedal.cinejoyremote.share.SharedTitle
import com.nedal.cinejoyremote.share.TmdbRef

// Netflix-style details page for a title shared into the app (SeriesGuide -> CineJoy).
// Arabic is the primary language: the whole screen lays out right-to-left.
@Composable
fun SharedTitleScreen(
    devicesVm: DevicesViewModel,
    ref: TmdbRef,
    snackbar: SnackbarHostState,
) {
    val vm: SharedTitleViewModel = viewModel(
        key = "shared-${ref.type}-${ref.id}",
        factory = remember(ref) { SharedTitleViewModel.factory(ref, devicesVm) },
    )
    val state by vm.state.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.clearMessage()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (val s = state) {
                is SharedTitleState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is SharedTitleState.Error -> Text(
                    "تعذّر تحميل التفاصيل: ${s.message}",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                )
                is SharedTitleState.Ready -> TitleDetails(s.title, busy) { action ->
                    if (action == SharedAction.DOWNLOAD) vm.download() else vm.addToLibrary()
                }
            }
        }
    }
}

@Composable
private fun TitleDetails(
    t: SharedTitle,
    busy: SharedAction?,
    onAction: (SharedAction) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Full-bleed backdrop with a dark gradient fading into the page colour.
        Box(Modifier.fillMaxWidth().height(380.dp)) {
            val backdrop = t.backdropUrl ?: t.posterUrl
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
                        0f to Color.Black.copy(alpha = 0.25f),
                        0.45f to Color.Black.copy(alpha = 0.55f),
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            Row(
                Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Large poster thumbnail.
                Box(
                    Modifier
                        .width(120.dp)
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    t.posterUrl?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = t.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        t.title,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 3,
                    )
                    val meta = buildList {
                        add(if (t.ref.type == "tv") "مسلسل" else "فيلم")
                        if (t.year.isNotBlank()) add(t.year)
                        t.rating?.let { add("★ ${"%.1f".format(it)}") }
                    }.joinToString("  •  ")
                    Text(meta, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }

        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // Primary actions side by side.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onAction(SharedAction.DOWNLOAD) },
                    enabled = busy == null,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (busy == SharedAction.DOWNLOAD) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("تحميل", fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(
                    onClick = { onAction(SharedAction.LIBRARY) },
                    enabled = busy == null,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (busy == SharedAction.LIBRARY) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("إضافة للمكتبة", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (t.genres.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    t.genres.forEach { MetaChip(it) }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("القصة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    t.overview.ifBlank { "لا يوجد وصف متوفر لهذا العمل." },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (t.cast.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("طاقم التمثيل", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(t.cast) { CastCard(it) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CastCard(c: CastMember) {
    Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
