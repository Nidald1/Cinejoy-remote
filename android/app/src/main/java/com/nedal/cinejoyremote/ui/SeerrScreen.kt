package com.nedal.cinejoyremote.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nedal.cinejoyremote.presentation.RequestsViewModel

// Seerr's own web UI inside the app: browse, request, and manage everything
// Seerr manages — not just the requests the bridge mirrored. The URL comes
// from the PC (seerr-info, rewritten for the LAN); the session is the
// WebView's own, so signing in once keeps you signed in. As a bottom-nav tab
// it carries no chrome of its own — the shell's top bar stays put and the
// WebView takes the whole area. Back walks Seerr's history first and only
// leaves the tab at its start.
@Composable
fun SeerrScreen(
    vm: RequestsViewModel,
    snackbar: SnackbarHostState,
) {
    val url by vm.seerrUrl.collectAsState()
    val loading by vm.seerrLoading.collectAsState()
    val error by vm.error.collectAsState()
    var webView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(Unit) { if (url == null && !loading) vm.openSeerr() }
    LaunchedEffect(error) { error?.let { msg -> snackbar.showSnackbar(msg); vm.clearError() } }

    // Inside the WebView, back walks Seerr's own history; once at its start
    // the shell's back handler takes over (leaves the tab).
    BackHandler(enabled = webView?.canGoBack() == true) { webView?.goBack() }

    val target = url
    when {
        target == null -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        "Waiting for the PC to say where Seerr runs.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = { vm.openSeerr() }) {
                        Text("Try again", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        else -> AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Keep navigation inside the view — Seerr is a SPA and
                    // its links must not leak into the system browser.
                    webViewClient = WebViewClient()
                    webView = this
                    loadUrl(target)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
