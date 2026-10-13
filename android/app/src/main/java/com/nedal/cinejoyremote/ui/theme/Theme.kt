package com.nedal.cinejoyremote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF0F766E)
private val TealBright = Color(0xFF2DD4BF)

private val Scheme = darkColorScheme(
    primary = TealBright,
    secondary = Teal,
    background = Color(0xFF0B1114),
    surface = Color(0xFF11191D),
)

@Composable
fun CinejoyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
