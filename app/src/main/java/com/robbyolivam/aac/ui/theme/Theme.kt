package com.robbyolivam.aac.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.robbyolivam.aac.core.WordType
import com.robbyolivam.aac.core.fitzgeraldRgb
import com.robbyolivam.aac.data.ThemeMode

private val Teal = Color(0xFF2E7D6B)

private val LightColors = lightColorScheme(
    primary = Teal,
    secondary = Color(0xFF5C6BC0),
    background = Color(0xFFF4F6F5),
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD3BE),
    secondary = Color(0xFF9FA8DA),
    background = Color(0xFF101413),
    surface = Color(0xFF1B2120),
)

/** Modified Fitzgerald Key colors, shared with exports via the core module. */
fun wordColor(type: WordType): Color = Color(0xFF000000.toInt() or fitzgeraldRgb(type))

/** Text on word buttons is always dark: the Fitzgerald colors are light in both themes. */
val OnWordColor = Color(0xFF1A1A1A)

@Composable
fun AacTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
