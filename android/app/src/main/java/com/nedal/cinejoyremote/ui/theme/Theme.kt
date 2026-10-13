package com.nedal.cinejoyremote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Morphe-style Material 3 brand: a blue → teal gradient identity over a
// near-black (#1A1C1E) surface. Accent #A4C9FF keeps buttons and highlights
// readable in the dark-only app.

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

@Composable
fun CinejoyTheme(content: @Composable () -> Unit) {
    // Dark-only app with a fixed brand identity — no Material You sampling,
    // so the Morphe look is identical on every device.
    MaterialTheme(colorScheme = MorpheDark, content = content)
}
