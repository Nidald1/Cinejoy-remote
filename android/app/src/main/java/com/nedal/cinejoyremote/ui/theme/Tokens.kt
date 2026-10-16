package com.nedal.cinejoyremote.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// The 4/8dp grid. Screens pull spacing from here instead of scattering
// literals — the old UI mixed 10/12/14/18dp for the same job.
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp

    /** Screen-edge inset every list and grid uses (was 14dp — now on the grid). */
    val gutter = 16.dp

    /** Gap between cards inside a list or grid. */
    val listGap = 12.dp

    /** Gap between major blocks / sections. */
    val sectionGap = 24.dp

    /** Extra content padding so a list's last card clears the bottom nav. */
    val bottomClearance = 96.dp
}

// Corner radii: cards 16, controls 12, dialogs 28 — the spec's 12–16 for
// cards, applied everywhere (episode cards, task cards, settings cards…).
object Radius {
    val xs = 8.dp   // thumbnails, stills, small art
    val sm = 12.dp  // buttons, fields, pills
    val md = 16.dp  // cards, panels
    val lg = 20.dp
    val xl = 28.dp  // dialogs
}

// Status colors shared by downloads, badges and dots — previously magic
// hex literals living inside DownloadsScreen.
object StatusColors {
    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)
    val Info = Color(0xFF38BDF8)
    val Neutral = Color(0xFF94A3B8)
}
