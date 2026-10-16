package com.nedal.cinejoyremote.ui.v3

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nedal.cinejoyremote.presentation.RequestsViewModel
import com.nedal.cinejoyremote.ui.SeerrSession
import kotlin.math.roundToInt

/**
 * V3 Seerr tab — full-screen web UI (spec §3.5, mockup 08).
 *
 * The shell hides BOTH app bars for this route (V3MainShell), the page draws
 * edge-to-edge behind the system bars, and the ONE WebView lives in the
 * Activity-scoped [SeerrSession] so switching tabs or rotating never reloads
 * it. Back is always handled here first: WebView history → previous app tab.
 * A draggable edge handle is the way back to the app while the bar is gone.
 */
@Composable
fun V3SeerrScreen(
    vm: RequestsViewModel,
    snackbar: SnackbarHostState,
    session: SeerrSession,
    selectedDevice: String?,
    onGoHome: () -> Unit,
) {
    val url by vm.seerrUrl.collectAsState()
    val loading by vm.seerrLoading.collectAsState()
    val vmError by vm.error.collectAsState()

    // Ask the PC where Seerr runs — but only once a PC is actually selected.
    LaunchedEffect(selectedDevice) {
        if (selectedDevice != null && session.url == null && !loading) vm.openSeerr()
    }
    val fallbackUrl by vm.seerrFallback.collectAsState()
    LaunchedEffect(url, fallbackUrl) { url?.let { session.open(it, fallbackUrl) } }
    LaunchedEffect(vmError) { vmError?.let { msg -> snackbar.showSnackbar(msg); vm.clearError() } }

    // Persist the WebView when the app stops so process death can restore it.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, session) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) session.persist()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ALWAYS active while this tab is visible; canGoBack stays fresh from
    // doUpdateVisitedHistory, so a SPA back always lands inside Seerr first.
    BackHandler(enabled = session.canGoBack) { session.goBack() }

    // No pull-to-refresh gesture: the WebView consumes its own drags and a
    // Compose gesture above it fights every in-page scroll. Refresh is one
    // tap on the handle (the thin progress bar shows it working).
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0D12)) // Seerr navy — no white flash
            .nestedScroll(
                object : NestedScrollConnection {
                    override fun onPostScroll(
                        available: Offset,
                        consumed: Offset,
                        source: NestedScrollSource,
                    ): Offset = Offset.Zero
                },
            ),
    ) {
        // Keyed on the session's generation: after a renderer crash the
        // session builds a fresh WebView and this swaps the dead one out.
        androidx.compose.runtime.key(session.generation) {
            AndroidView(
                factory = { session.webView },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (session.loading) {
            LinearProgressIndicator(
                progress = { session.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .height(2.dp),
            )
        }

        session.error?.let { msg ->
            Column(
                Modifier.fillMaxSize().background(Color(0xFF0B0D12)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Can't reach Seerr", style = com.nedal.cinejoyremote.ui.v3.V3Type.sectionTitle, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(
                    msg,
                    style = com.nedal.cinejoyremote.ui.v3.V3Type.body,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(Modifier.height(14.dp))
                TextButton(onClick = { session.reload() }) {
                    Text("Retry", color = Color(0xFF4C8FF7))
                }
            }
        }

        // URL not resolved yet: spinner, or a retry when the command failed.
        if (session.url == null && session.error == null) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        "Waiting for the PC to say where Seerr runs.",
                        style = com.nedal.cinejoyremote.ui.v3.V3Type.body14,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                    TextButton(onClick = { vm.openSeerr() }) { Text("Try again", color = Color(0xFF4C8FF7)) }
                }
            }
        }

        V3SeerrHandle(session = session, onGoHome = onGoHome)
    }
}

/**
 * Floating semi-transparent handle, draggable anywhere, collapsed to a sliver
 * at the screen edge (mockup 08). Tapping expands the four app actions
 * (Home, Reload, Open in browser, Close Seerr); the position is remembered
 * across rotation in dp so it stays valid across densities.
 */
@Composable
private fun V3SeerrHandle(session: SeerrSession, onGoHome: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var rawX by rememberSaveable { mutableFloatStateOf(Float.NaN) }
    var rawY by rememberSaveable { mutableFloatStateOf(Float.NaN) }
    val density = LocalDensity.current.density
    val config = LocalConfiguration.current
    val screenW = config.screenWidthDp.toFloat()
    val screenH = config.screenHeightDp.toFloat()
    val xDp = (if (rawX.isNaN()) screenW - 34f else rawX).coerceIn(0f, (screenW - 28f).coerceAtLeast(0f))
    val yDp = (if (rawY.isNaN()) (screenH - 96f) / 2f else rawY).coerceIn(0f, (screenH - 56f).coerceAtLeast(0f))

    Box(Modifier.fillMaxSize()) {
        val handle = Modifier
            .offset(x = xDp.dp, y = yDp.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    rawX = (if (rawX.isNaN()) xDp else rawX) + drag.x / density
                    rawY = (if (rawY.isNaN()) yDp else rawY) + drag.y / density
                }
            }
            .clip(RoundedCornerShape(50))
            .background(Color(0x6612151C))
            .clickable { expanded = !expanded }

        if (expanded) {
            androidx.compose.material3.Surface(
                color = Color(0xF0171B24),
                shape = RoundedCornerShape(14.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 44.dp),
            ) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    V3HandleRow(Icons.Rounded.Home, "App home") { expanded = false; onGoHome() }
                    V3HandleRow(Icons.Rounded.Refresh, "Reload") { expanded = false; session.reload() }
                    V3HandleRow(Icons.Rounded.OpenInBrowser, "Open in browser") { expanded = false; session.openInBrowser() }
                    V3HandleRow(Icons.Rounded.Close, "Close Seerr") { expanded = false; onGoHome() }
                }
            }
        }

        Box(
            handle
                .width(if (expanded) 40.dp else 26.dp)
                .height(if (expanded) 96.dp else 56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (expanded) Icons.Rounded.ChevronRight else Icons.Rounded.ChevronLeft,
                contentDescription = "App menu",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun V3HandleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color.White, style = com.nedal.cinejoyremote.ui.v3.V3Type.body14)
    }
}
