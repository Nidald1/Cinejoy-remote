package com.nedal.cinejoyremote.ui.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nedal.cinejoyremote.domain.SeerrRequest
import com.nedal.cinejoyremote.presentation.RequestsViewModel
import com.nedal.cinejoyremote.ui.v3.components.EmptyState

/**
 * V3 Notifications (spec mockup 09): Seerr requests waiting for a decision —
 * the PC holds each request instead of downloading straight away, so this is
 * the approval step. Accept imports + opens the title (source picker raises
 * via DetailPickInbox); Reject declines in Seerr too, and the row disappears
 * because the PC deletes the mirrored event. Pull to refresh re-scans now.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V3NotificationsScreen(
    vm: RequestsViewModel,
    snackbar: SnackbarHostState,
    onAcceptedShow: (String) -> Unit,
    onOpenSeerr: () -> Unit,
) {
    val requests by vm.requests.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val notice by vm.notice.collectAsState()
    val acceptedShow by vm.acceptedShow.collectAsState()
    val refreshing by vm.refreshing.collectAsState()

    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); vm.clearError() } }
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); vm.clearNotice() } }
    // The PC only mirrors a Seerr request when its scan runs — ask for a scan
    // as soon as this screen appears (throttled inside the VM) so a just-made
    // request shows up now, not on the PC's next background pass.
    LaunchedEffect(Unit) { vm.autoScan() }
    LaunchedEffect(acceptedShow) {
        acceptedShow?.let { onAcceptedShow(it); vm.clearAcceptedShow() }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { vm.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        if (requests.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.Download,
                    title = "Nothing waiting",
                    body = "When someone requests a movie or a series through Seerr it " +
                        "appears here, and you decide whether to download it.",
                    actions = {
                        TextButton(onClick = onOpenSeerr) {
                            Icon(Icons.Rounded.Language, contentDescription = null, tint = v3.accent)
                            Spacer(Modifier.width(V3Space.xs))
                            Text("Open Seerr", color = v3.accent, style = V3Type.chip)
                        }
                    },
                )
            }
            return@PullToRefreshBox
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = V3Space.gutter,
                end = V3Space.gutter,
                top = V3Space.sm,
                bottom = V3Space.bottomClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(V3Space.listGap),
        ) {
            item(key = "seerr-header") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Seerr requests", style = V3Type.sectionTitle, color = v3.text, modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenSeerr) {
                        Icon(Icons.Rounded.Language, contentDescription = null, tint = v3.accent)
                        Spacer(Modifier.width(6.dp))
                        Text("Open Seerr", color = v3.accent, style = V3Type.chip)
                    }
                }
            }
            items(requests, key = { it.rowId }) { req ->
                V3RequestCard(
                    req = req,
                    busy = busy == req.reqId,
                    onAccept = { vm.accept(req) },
                    onReject = { vm.reject(req) },
                )
            }
        }
    }
}

@Composable
private fun V3RequestCard(req: SeerrRequest, busy: Boolean, onAccept: () -> Unit, onReject: () -> Unit) {
    val c = v3
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(V3Radius.card))
            .background(c.raised)
            .padding(V3Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (req.poster != null) {
            Box(
                Modifier
                    .size(width = 58.dp, height = 86.dp)
                    .clip(RoundedCornerShape(V3Radius.poster))
                    .background(c.surface),
            ) {
                AsyncImage(
                    model = req.poster,
                    contentDescription = req.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(V3Space.md))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(V3Radius.pill))
                        .background(c.accent.copy(alpha = 0.18f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        if (req.type == "tv") "SERIES" else "MOVIE",
                        style = V3Type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                        color = c.accent,
                    )
                }
                Spacer(Modifier.width(V3Space.sm))
                Text(
                    listOfNotNull(req.year, req.seasons).joinToString(" · "),
                    style = V3Type.caption,
                    color = c.textTertiary,
                )
            }
            Spacer(Modifier.height(V3Space.xs))
            Text(req.title, style = V3Type.itemTitle, color = c.text)
            Spacer(Modifier.height(V3Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(V3Space.sm)) {
                Button(
                    onClick = onAccept,
                    enabled = !busy,
                    shape = RoundedCornerShape(V3Radius.sm),
                    modifier = Modifier.weight(1f).height(40.dp),
                ) { Text(if (busy) "Working…" else "Accept", style = V3Type.chip) }
                OutlinedButton(
                    onClick = onReject,
                    enabled = !busy,
                    shape = RoundedCornerShape(V3Radius.sm),
                    modifier = Modifier.weight(1f).height(40.dp),
                ) { Text("Reject", style = V3Type.chip, color = c.textSecondary) }
            }
        }
        if (busy) {
            Spacer(Modifier.width(V3Space.sm))
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.accent)
        }
    }
}
