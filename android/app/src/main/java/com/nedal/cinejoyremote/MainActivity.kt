package com.nedal.cinejoyremote

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import com.nedal.cinejoyremote.share.SharedLinkInbox
import com.nedal.cinejoyremote.ui.DetailStyleController
import com.nedal.cinejoyremote.ui.LibraryStyleController
import com.nedal.cinejoyremote.ui.theme.CinejoyTheme
import com.nedal.cinejoyremote.ui.v3.V3App
import com.nedal.cinejoyremote.ui.v3.V3Theme
import com.nedal.cinejoyremote.ui.theme.ThemeController
import com.nedal.cinejoyremote.ui.theme.ThemeMode

class CinejoyApplication : Application()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge: the window draws behind the system bars and the
        // Scaffold bars own the insets — the top bar can then collapse all
        // the way up under the status bar on scroll (hide-on-scroll).
        enableEdgeToEdge()
        ThemeController.load(applicationContext)
        DetailStyleController.load(applicationContext)
        LibraryStyleController.load(applicationContext)
        // A rotation re-delivers the same intent; only a fresh launch is a share.
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            // The in-app theme can be lighter than the system night mode, so
            // the status/navigation bar icon contrast follows ThemeController,
            // not the OS setting.
            val mode = ThemeController.mode
            SideEffect {
                val light = mode == ThemeMode.LIGHT
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = light
                    isAppearanceLightNavigationBars = light
                }
            }
            // v3 shell inside the legacy theme: CinejoyTheme seeds the app's
            // typography baseline; V3Theme applies the v3 palette (dark-first,
            // Appearance toggle intact) for the shell and its Material components.
            CinejoyTheme(mode) {
                V3Theme { V3App() }
            }
        }
    }

    // launchMode is singleTop, so a share into an already-open app lands here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        SharedLinkInbox.offer(text)
    }
}
