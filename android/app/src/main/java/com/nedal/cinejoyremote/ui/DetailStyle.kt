package com.nedal.cinejoyremote.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Which layout a library title opens with: the classic detail page (unchanged
// default) or the Netflix-style hero copied from the share sheet. Persisted
// like ThemeController so the choice survives restarts; SettingsScreen writes
// it, ShowDetailScreen reads it.
enum class DetailStyle { CLASSIC, NETFLIX }

object DetailStyleController {
    var mode by mutableStateOf(DetailStyle.CLASSIC)
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences("cinejoy_detail_style", Context.MODE_PRIVATE)
        mode = runCatching { DetailStyle.valueOf(prefs.getString("mode", "CLASSIC") ?: "CLASSIC") }
            .getOrDefault(DetailStyle.CLASSIC)
    }

    fun set(context: Context, newMode: DetailStyle) {
        mode = newMode
        context.getSharedPreferences("cinejoy_detail_style", Context.MODE_PRIVATE)
            .edit()
            .putString("mode", newMode.name)
            .apply()
    }
}

// One-shot handoff: NotificationsScreen drops the accepted showId here before
// navigating, and ShowDetailScreen opens the sources window for that show as
// soon as its episodes are in — pressing Accept goes straight to choosing the
// source and quality instead of starting a random download. Same pattern as
// SharedLinkInbox.
object DetailPickInbox {
    private var pending: String? = null

    fun offer(showId: String) {
        pending = showId
    }

    fun take(showId: String): Boolean {
        if (pending != showId) return false
        pending = null
        return true
    }
}
