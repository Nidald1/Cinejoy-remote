package com.nedal.cinejoyremote.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// How library tiles are drawn: the classic card (type badge, title over a
// scrim, meta row) or the Netflix-style poster wall (bare artwork only, like
// the Netflix app). Persisted like ThemeController so the choice survives
// restarts; SettingsScreen writes it, LibraryScreen reads it.
enum class LibraryStyle { CLASSIC, NETFLIX }

object LibraryStyleController {
    var mode by mutableStateOf(LibraryStyle.CLASSIC)
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences("cinejoy_library_style", Context.MODE_PRIVATE)
        mode = runCatching { LibraryStyle.valueOf(prefs.getString("mode", "CLASSIC") ?: "CLASSIC") }
            .getOrDefault(LibraryStyle.CLASSIC)
    }

    fun set(context: Context, newMode: LibraryStyle) {
        mode = newMode
        context.getSharedPreferences("cinejoy_library_style", Context.MODE_PRIVATE)
            .edit()
            .putString("mode", newMode.name)
            .apply()
    }
}
