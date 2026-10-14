package com.nedal.cinejoyremote

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nedal.cinejoyremote.share.SharedLinkInbox
import com.nedal.cinejoyremote.ui.CinejoyApp
import com.nedal.cinejoyremote.ui.DetailStyleController
import com.nedal.cinejoyremote.ui.LibraryStyleController
import com.nedal.cinejoyremote.ui.theme.CinejoyTheme
import com.nedal.cinejoyremote.ui.theme.ThemeController

class CinejoyApplication : Application()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeController.load(applicationContext)
        DetailStyleController.load(applicationContext)
        LibraryStyleController.load(applicationContext)
        // A rotation re-delivers the same intent; only a fresh launch is a share.
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            CinejoyTheme(ThemeController.mode) { CinejoyApp() }
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
