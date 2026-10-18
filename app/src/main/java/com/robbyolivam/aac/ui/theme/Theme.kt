package com.robbyolivam.aac.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.robbyolivam.aac.core.WordType
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

/** Modified Fitzgerald Key colors, the convention most AAC systems and SLPs use. */
fun wordColor(type: WordType): Color = when (type) {
    WordType.PRONOUN, WordType.PERSON -> Color(0xFFFFF176)
    WordType.VERB -> Color(0xFFA5D6A7)
    WordType.ADJECTIVE, WordType.ADVERB -> Color(0xFF90CAF9)
    WordType.NOUN, WordType.PLACE -> Color(0xFFFFCC80)
    WordType.QUESTION -> Color(0xFFCE93D8)
    WordType.NEGATION -> Color(0xFFEF9A9A)
    WordType.SOCIAL -> Color(0xFFF8BBD0)
    WordType.PREPOSITION, WordType.DETERMINER, WordType.CONJUNCTION -> Color(0xFFE0E0E0)
    WordType.FOLDER -> Color(0xFFD7CCC8)
    WordType.OTHER -> Color(0xFFF5F5F5)
}

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
