package com.nedal.cinejoyremote.ui.v3

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nedal.cinejoyremote.ui.theme.CinejoyShapes
import com.nedal.cinejoyremote.ui.theme.CinejoyTypography
import com.nedal.cinejoyremote.ui.theme.ThemeController
import com.nedal.cinejoyremote.ui.theme.ThemeMode

/**
 * CineJoy v3 design system — the palette, type scale, spacing grid and shape
 * language from the redesign spec. Dark-first (Light/OLED keep working through
 * the existing Appearance toggle). Every v3 component pulls from here; no
 * component defines its own raw hex except artwork gradients.
 */
data class V3Colors(
    val background: Color,
    val surface: Color,
    val raised: Color,
    val sheet: Color,
    val divider: Color,
    val accent: Color,          // buttons / indicators
    val activeText: Color,      // active tab + link text
    val success: Color,
    val warning: Color,
    val error: Color,
    val text: Color,            // primary
    val textSecondary: Color,
    val textTertiary: Color,
    val onAccent: Color = Color.White,
    val badge: Color = Color(0xFFEF4444),
    val unreadTint: Color = Color(0xFF18243B),
)

private val V3Dark = V3Colors(
    background = Color(0xFF0B0D12),
    surface = Color(0xFF12151C),
    raised = Color(0xFF1A1F2B),
    sheet = Color(0xFF141821),
    divider = Color(0x12FFFFFF), // white @ 7%
    accent = Color(0xFF3B82F6),
    activeText = Color(0xFF4C8FF7),
    success = Color(0xFF22C55E),
    warning = Color(0xFFF59E0B),
    error = Color(0xFFEF4444),
    text = Color(0xFFF2F4F8),
    textSecondary = Color(0xFF8B93A3),
    textTertiary = Color(0xFF7D8596),
)

// OLED: same identity, backgrounds go pure-black so AMOLED pixels switch off.
private val V3Oled = V3Dark.copy(background = Color.Black, surface = Color(0xFF0A0A0C), sheet = Color(0xFF0E0E12))

// Light: inverted surfaces, same accent. The Appearance toggle keeps working.
private val V3Light = V3Colors(
    background = Color(0xFFF6F7FB),
    surface = Color.White,
    raised = Color(0xFFEEF0F5),
    sheet = Color.White,
    divider = Color(0x14000000),
    accent = Color(0xFF2563EB),
    activeText = Color(0xFF2563EB),
    success = Color(0xFF16A34A),
    warning = Color(0xFFD97706),
    error = Color(0xFFDC2626),
    text = Color(0xFF12151C),
    textSecondary = Color(0xFF5A6272),
    textTertiary = Color(0xFF6B7280),
    unreadTint = Color(0xFFE4EDFB),
)

val LocalV3 = staticCompositionLocalOf { V3Dark }

/** Type scale from the spec. Sans-serif ships with Noto fallbacks → Arabic/RTL works. */
object V3Type {
    val screenTitle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
    val heroTitle = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 44.sp)
    val sectionTitle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
    val itemTitle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
    val body = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val body14 = TextStyle(fontSize = 14.sp, lineHeight = 19.sp)
    val caption = TextStyle(fontSize = 11.sp, lineHeight = 15.sp)
    val caption12 = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val chip = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
    val tabLabel = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 13.sp)
    val mono = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp)
}

/** 4/8dp grid; 16dp screen gutters. */
object V3Space {
    val gutter = androidx.compose.ui.unit.Dp(16f)
    val xs = androidx.compose.ui.unit.Dp(4f)
    val sm = androidx.compose.ui.unit.Dp(8f)
    val md = androidx.compose.ui.unit.Dp(12f)
    val lg = androidx.compose.ui.unit.Dp(16f)
    val xl = androidx.compose.ui.unit.Dp(24f)
    val xxl = androidx.compose.ui.unit.Dp(32f)
    val listGap = androidx.compose.ui.unit.Dp(12f)
    val bottomClearance = androidx.compose.ui.unit.Dp(96f)
}

object V3Radius {
    val sm = androidx.compose.ui.unit.Dp(12f) // buttons / small controls
    val poster = androidx.compose.ui.unit.Dp(12f)
    val card = androidx.compose.ui.unit.Dp(16f)
    val chip = androidx.compose.ui.unit.Dp(14f)
    val sheet = androidx.compose.ui.unit.Dp(24f)
    val field = androidx.compose.ui.unit.Dp(14f)
    val pill = androidx.compose.ui.unit.Dp(999f) // fully rounded
}

/** Hard layout limits from the spec — measured in the phase reports. */
object V3Limits {
    const val TOP_BAR_DP = 56
    const val TABS_DP = 40
    const val BOTTOM_BAR_DP = 80       // including gesture inset; 64dp touch area
    const val BOTTOM_TOUCH_DP = 64
    const val POSTER_W_DP = 112
    const val POSTER_H_DP = 168
}

/** Reusable color palette for the current Appearance mode. */
@Composable
fun v3Colors(): V3Colors = when (ThemeController.mode) {
    ThemeMode.LIGHT -> V3Light
    ThemeMode.OLED -> V3Oled
    ThemeMode.DARK -> V3Dark
}

/**
 * V3 theme wrapper: seeds MaterialTheme (so Material components such as
 * AlertDialog/Switch stay consistent) and provides [LocalV3] for the design
 * system's own tokens. Dark-first per the spec.
 */
@Composable
fun V3Theme(content: @Composable () -> Unit) {
    val c = v3Colors()
    val scheme = androidx.compose.material3.darkColorScheme(
        primary = c.accent,
        onPrimary = c.onAccent,
        background = c.background,
        onBackground = c.text,
        surface = c.surface,
        onSurface = c.text,
        surfaceVariant = c.raised,
        onSurfaceVariant = c.textSecondary,
        surfaceContainerLowest = c.background,
        surfaceContainerLow = c.surface,
        surfaceContainer = c.raised,
        surfaceContainerHigh = c.raised,
        surfaceContainerHighest = c.raised,
        outline = c.divider,
        outlineVariant = c.divider,
        error = c.error,
        onError = Color.White,
    )
    CompositionLocalProvider(LocalV3 provides c) {
        MaterialTheme(
            colorScheme = scheme,
            // Shared app typography + shape scale: legacy screens rendered
            // inside the v3 shell keep the app's text hierarchy, and v3's
            // Material components (dialogs, sheets) match the type scale.
            typography = CinejoyTypography,
            shapes = CinejoyShapes,
        ) {
            Surface(color = c.background) { content() }
        }
    }
}

/** Convenience accessors. */
val v3: V3Colors @Composable @ReadOnlyComposable get() = LocalV3.current
