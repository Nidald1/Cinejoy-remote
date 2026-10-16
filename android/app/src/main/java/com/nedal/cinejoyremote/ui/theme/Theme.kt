package com.nedal.cinejoyremote.ui.theme

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// Morphe-style Material 3 brand: a blue → teal gradient identity over a
// near-black (#1A1C1E) surface. Accent #A4C9FF keeps buttons and highlights
// readable in dark mode. Light and OLED variants keep the same identity.

// Brand gradient endpoints — use for headers, update dialog, FABs.
val MorpheBrandBlue = Color(0xFF1E5AA8)
val MorpheBrandTeal = Color(0xFF00AFAE)

private val Background = Color(0xFF1A1C1E)
private val Surface1 = Color(0xFF1F2124)
private val Surface2 = Color(0xFF23262B)
private val Surface3 = Color(0xFF2B2E35)
private val OnBackground = Color(0xFFE3E2E6)
private val Muted = Color(0xFFC3C6CF)
private val Outline = Color(0xFF43474E)
private val ErrorTint = Color(0xFFFFB4AB)

private val MorpheDark = darkColorScheme(
    primary = Color(0xFFA4C9FF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = MorpheBrandBlue,
    onPrimaryContainer = Color(0xFFD4E3FF),
    secondary = Color(0xFFBEC7DC),
    onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3E4758),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = MorpheBrandTeal,
    onTertiary = Color(0xFF003737),
    tertiaryContainer = Color(0xFF004F4F),
    onTertiaryContainer = Color(0xFF6FF7F5),
    background = Background,
    onBackground = OnBackground,
    surface = Background,
    onSurface = OnBackground,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Surface1,
    surfaceContainerLow = Surface1,
    surfaceContainer = Surface2,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface3,
    outline = Outline,
    outlineVariant = Color(0xFF43474E),
    error = ErrorTint,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

// Pure-black OLED variant: backgrounds/surfaces go to #000 so AMOLED pixels
// switch fully off; cards stay slightly raised so they remain visible.
private val MorpheOled = MorpheDark.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color(0xFF0B0B0D),
    surfaceContainerHigh = Color(0xFF121215),
    surfaceContainerHighest = Color(0xFF1B1B1F),
)

// Light variant of the same brand identity.
private val MorpheLight = lightColorScheme(
    primary = MorpheBrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4E3FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Color(0xFF43566B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E3F8),
    onSecondaryContainer = Color(0xFF112C40),
    tertiary = Color(0xFF006A6A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF6FF7F5),
    onTertiaryContainer = Color(0xFF002020),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFF6F7FB),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE1E3EA),
    onSurfaceVariant = Color(0xFF43474E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEEF0F5),
    surfaceContainerHigh = Color(0xFFE8EAF0),
    surfaceContainerHighest = Color(0xFFDDE0E7),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C6CF),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

enum class ThemeMode { DARK, LIGHT, OLED }

// The shape scale the whole app shares: controls 12, cards 16, dialogs 28.
val CinejoyShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.xs),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl),
)

// Process-wide theme choice, persisted in SharedPreferences so the mode
// survives restarts. SettingsScreen writes it; MainActivity reads it.
object ThemeController {
    var mode by mutableStateOf(ThemeMode.DARK)
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences("cinejoy_theme", Context.MODE_PRIVATE)
        mode = runCatching { ThemeMode.valueOf(prefs.getString("mode", "DARK") ?: "DARK") }
            .getOrDefault(ThemeMode.DARK)
    }

    fun set(context: Context, newMode: ThemeMode) {
        mode = newMode
        context.getSharedPreferences("cinejoy_theme", Context.MODE_PRIVATE)
            .edit()
            .putString("mode", newMode.name)
            .apply()
    }
}

@Composable
fun CinejoyTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    // Fixed brand identity per mode — no Material You sampling, so the Morphe
    // look is identical on every device. Typography and shapes come from the
    // shared design system (Type.kt / Tokens.kt) so every screen reads the
    // same hierarchy and corner language.
    val scheme = when (mode) {
        ThemeMode.LIGHT -> MorpheLight
        ThemeMode.OLED -> MorpheOled
        ThemeMode.DARK -> MorpheDark
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = CinejoyTypography,
        shapes = CinejoyShapes,
    ) {
        // Surface seeds LocalContentColor (onBackground), so bare Text()
        // without an explicit colour renders readable instead of black —
        // the AuthScreen title bug that shipped in v5.0.148.
        Surface(color = scheme.background) {
            content()
        }
    }
}
