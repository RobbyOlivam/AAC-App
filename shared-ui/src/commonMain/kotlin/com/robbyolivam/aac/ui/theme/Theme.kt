package com.robbyolivam.aac.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.robbyolivam.aac.shared.resources.Res
import com.robbyolivam.aac.shared.resources.inter_bold
import com.robbyolivam.aac.shared.resources.inter_regular
import org.jetbrains.compose.resources.Font

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.robbyolivam.aac.core.WordType
import com.robbyolivam.aac.core.fitzgeraldRgb
import com.robbyolivam.aac.core.ThemeMode

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
    // The web has no system fonts, so the app ships its own (Inter).
    val inter = FontFamily(Font(Res.font.inter_regular), Font(Res.font.inter_bold, FontWeight.Bold), Font(Res.font.inter_bold, FontWeight.SemiBold), Font(Res.font.inter_regular, FontWeight.Medium))
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = inter), displayMedium = base.displayMedium.copy(fontFamily = inter),
        displaySmall = base.displaySmall.copy(fontFamily = inter), headlineLarge = base.headlineLarge.copy(fontFamily = inter),
        headlineMedium = base.headlineMedium.copy(fontFamily = inter), headlineSmall = base.headlineSmall.copy(fontFamily = inter),
        titleLarge = base.titleLarge.copy(fontFamily = inter), titleMedium = base.titleMedium.copy(fontFamily = inter),
        titleSmall = base.titleSmall.copy(fontFamily = inter), bodyLarge = base.bodyLarge.copy(fontFamily = inter),
        bodyMedium = base.bodyMedium.copy(fontFamily = inter), bodySmall = base.bodySmall.copy(fontFamily = inter),
        labelLarge = base.labelLarge.copy(fontFamily = inter), labelMedium = base.labelMedium.copy(fontFamily = inter),
        labelSmall = base.labelSmall.copy(fontFamily = inter),
    )
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = typography) {
        // Plain Text without a style uses LocalTextStyle: give it the font too.
        CompositionLocalProvider(LocalTextStyle provides typography.bodyLarge, content = content)
    }
}
