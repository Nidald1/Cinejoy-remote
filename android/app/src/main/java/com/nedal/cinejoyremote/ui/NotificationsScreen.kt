package com.nedal.cinejoyremote.ui

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
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.SnackbarHostState
import coil.compose.AsyncImage
import com.nedal.cinejoyremote.domain.SeerrRequest
import com.nedal.cinejoyremote.presentation.RequestsViewModel

// Notifications — Seerr requests waiting for a decision.
//
// The PC holds each request instead of downloading it straight away, so this
// screen is the approval step: Accept imports the title and approves the
// request in Seerr, then opens the title's page so the source and quality
// are chosen in the normal download picker. Reject declines it in Seerr too
// so whoever asked can request it again. The row disappears as soon as it is
// decided, because the PC deletes the mirrored event.
@Composable
fun NotificationsScreen(
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

    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); vm.clearError() } }
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); vm.clearNotice() } }
    LaunchedEffect(acceptedShow) {
        acceptedShow?.let { onAcceptedShow(it); vm.clearAcceptedShow() }
    }

    if (requests.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
            ) {
                Text("🔔", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(8.dp))
                Text("Nothing waiting", style = MaterialTheme.typography.titleMedium)
                Text(
                    "When someone requests a movie or a series through Seerr it\n" +
                        "appears here, and you decide whether to download it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                SeerrOpenButton(onOpenSeerr)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "seerr-header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Seerr requests",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                SeerrOpenButton(onOpenSeerr)
            }
        }
        items(requests, key = { it.rowId }) { req ->
            RequestCard(
                req = req,
                busy = busy == req.reqId,
                onAccept = { vm.accept(req) },
                onReject = { vm.reject(req) },
            )
        }
    }
}

// Entry point to Seerr itself (its own web UI in a WebView): manage every
// request, browse, and clean up stuck titles — not just what the bridge
// mirrored here.
@Composable
private fun SeerrOpenButton(onOpenSeerr: () -> Unit) {
    TextButton(onClick = onOpenSeerr) {
        Icon(Icons.Rounded.Language, contentDescription = null, Modifier.padding(end = 6.dp))
        Text("Open Seerr", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RequestCard(
    req: SeerrRequest,
    busy: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (req.poster != null) {
                AsyncImage(
                    model = req.poster,
                    contentDescription = req.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 58.dp, height = 86.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (req.type == "tv") {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                    ) {
                        Text(
                            if (req.type == "tv") "SERIES" else "MOVIE",
                            Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        listOfNotNull(req.year, req.seasons).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(req.title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAccept, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text(if (busy) "Working…" else "Accept")
                    }
                    OutlinedButton(onClick = onReject, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text("Reject")
                    }
                }
            }
            if (busy) {
                Spacer(Modifier.width(10.dp))
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}
