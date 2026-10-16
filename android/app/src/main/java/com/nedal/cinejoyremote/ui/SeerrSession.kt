package com.nedal.cinejoyremote.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

/**
 * ONE WebView for the whole app session (spec B4.4).
 *
 * The old screen created its WebView in the AndroidView factory, so every tab
 * switch disposed it and Seerr reloaded from scratch. This ViewModel outlives
 * tab switches AND rotation (the Activity's ViewModel scope), so the page,
 * its scroll position and its history survive. Full state is written into the
 * SavedStateHandle on stop so it also comes back after process death.
 */
@SuppressLint("SetJavaScriptEnabled")
class SeerrSession(
    private val app: Application,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    var url by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var progress by mutableIntStateOf(0)
        private set
    /** Updated from doUpdateVisitedHistory — the usual source of the "stale canGoBack" back-button bug (B4.3). */
    var canGoBack by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /**
     * The live WebView. Mutable because a Chromium renderer crash kills the
     * hosting app process unless the app takes over (onRenderProcessGone
     * below): there we destroy the dead view, build a fresh one and bump
     * [generation] so the screen swaps it in.
     */
    var webView: WebView = createWebView()
        private set
    var generation by mutableIntStateOf(0)
        private set

    init {
        savedState.get<Bundle>(KEY_STATE)?.let { webView.restoreState(it) }
        // restoreState re-loads the page asynchronously; mirror its URL.
        savedState.get<String>(KEY_URL)?.let { restored ->
            if (restored.isNotBlank()) {
                url = restored
                loading = true
            }
        }
    }

    private fun createWebView(): WebView {
        val w = WebView(app)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        // No white flash while Seerr (navy) loads.
        w.setBackgroundColor(0xFF0B0D12.toInt())
        w.webViewClient = SeerrWebViewClient()
        w.webChromeClient = SessionChromeClient()
        return w
    }

    private inner class SessionChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            progress = newProgress
        }
    }

    private fun handleRendererDeath(dead: WebView) {
        runCatching { dead.destroy() }
        webView = createWebView()
        generation += 1
        error = null
        url?.let {
            loading = true
            progress = 0
            webView.loadUrl(it)
        }
    }

    // The PC sends the Tailscale address first (works on mobile data) and the
    // LAN address as fallback (works at home without Tailscale). If the
    // primary cannot be reached, the fallback is tried once automatically.
    private var fallbackUrl: String? = null

    /** Load Seerr (idempotent — same URL is never re-loaded on tab switches). */
    fun open(target: String, fallback: String? = null) {
        if (url == target && error == null) return
        fallbackUrl = fallback?.takeIf { it != target }
        url = target
        error = null
        loading = true
        webView.loadUrl(target)
    }

    fun reload() {
        error = null
        loading = true
        webView.reload()
    }

    fun goBack(): Boolean {
        if (webView.canGoBack()) {
            webView.goBack()
            return true
        }
        canGoBack = false
        return false
    }

    fun openInBrowser() {
        url?.let { runCatching { app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }
    }

    fun persist() {
        val bundle = Bundle().also { webView.saveState(it) }
        savedState[KEY_STATE] = bundle
        savedState[KEY_URL] = url
    }

    override fun onCleared() {
        webView.destroy()
    }

    private inner class SeerrWebViewClient : WebViewClient() {
        /**
         * Seerr is a heavy SPA; on a memory-tight phone its renderer can
         * die. Default behaviour (return false) kills the whole app — that
         * is the "opening the Seerr tab crashes the app" bug. Take
         * ownership instead: drop the dead view, build a fresh one and
         * reload where the user was.
         */
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            handleRendererDeath(view)
            return true
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val host = request.url.host ?: return false
            val seerrHost = runCatching { Uri.parse(url ?: return false).host }.getOrNull()
            val allowed = seerrHost != null && (host == seerrHost || host.endsWith(".$seerrHost"))
            return if (allowed) {
                false
            } else {
                // Anything that leaves Seerr opens in the system browser (B4.7).
                runCatching { app.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                true
            }
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            // Seerr is a SPA using the History API — this is the ONLY reliable
            // place to keep canGoBack fresh (B4.3).
            canGoBack = view.canGoBack()
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            loading = true
            error = null
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            loading = false
            canGoBack = view?.canGoBack() == true
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error_: WebResourceError?) {
            if (request?.isForMainFrame != true) return
            // Unreachable primary (e.g. the Tailscale address while the VPN is
            // off): switch once to the LAN URL before showing the error page.
            val fb = fallbackUrl
            if (fb != null && url != fb) {
                fallbackUrl = null
                url = fb
                error = null
                loading = true
                view?.loadUrl(fb)
                return
            }
            error = error_?.description?.toString() ?: "Seerr is unreachable"
            loading = false
        }
    }

    private companion object {
        const val KEY_STATE = "seerr_webview_state"
        const val KEY_URL = "seerr_url"
    }
}
