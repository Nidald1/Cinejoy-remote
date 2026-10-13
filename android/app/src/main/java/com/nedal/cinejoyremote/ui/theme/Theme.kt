package com.nedal.cinejoyremote.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Teal = Color(0xFF0F766E)
private val TealBright = Color(0xFF2DD4BF)
private val Mint = Color(0xFF80CBC4)
private val Background = Color(0xFF0A0F12)
private val Surface1 = Color(0xFF111A1F)
private val Surface2 = Color(0xFF17242B)
private val Surface3 = Color(0xFF1E2F38)
private val OnBackground = Color(0xFFE7F1F1)
private val Muted = Color(0xFF9DB2B6)
private val Outline = Color(0xFF3A4E56)
private val ErrorTint = Color(0xFFFFB4AB)

// Dark-only app: on Android 12+ we follow the wallpaper (Material You),
// elsewhere the branded teal scheme below.
private val Fallback = darkColorScheme(
    primary = TealBright,
    onPrimary = Color(0xFF00201C),
    primaryContainer = Color(0xFF0B5D55),
    onPrimaryContainer = Color(0xFFC7FFF7),
    secondary = Teal,
    onSecondary = Color(0xFF00201C),
    tertiary = Mint,
    background = Background,
    onBackground = OnBackground,
    surface = Surface1,
    onSurface = OnBackground,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface3,
    outline = Outline,
    outlineVariant = Color(0xFF2A3C44),
    error = ErrorTint,
    onError = Color(0xFF410002),
)

@Composable
fun CinejoyTheme(content: @Composable () -> Unit) {
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        Fallback
    }
    MaterialTheme(colorScheme = colors, content = content)
}
