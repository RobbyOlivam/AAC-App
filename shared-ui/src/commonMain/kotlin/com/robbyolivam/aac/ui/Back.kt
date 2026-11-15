package com.robbyolivam.aac.ui

import androidx.compose.runtime.Composable

/**
 * The system back gesture. Android's version lives in androidx.activity; the web and iPad have
 * no system back, so this does nothing there (every screen has its own Back or Done button).
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {}

/** The screen's smaller side in dp (Android's smallestScreenWidthDp); the app's root provides it. */
val LocalSmallestWidthDp = androidx.compose.runtime.compositionLocalOf { 800 }

/** [value] with [decimals] digits after the point ("1.5"), without platform formatting. */
fun fmt(value: Float, decimals: Int): String {
    var factor = 1
    repeat(decimals) { factor *= 10 }
    val scaled = kotlin.math.round(value * factor).toLong()
    if (decimals == 0) return scaled.toString()
    val sign = if (scaled < 0) "-" else ""
    val abs = kotlin.math.abs(scaled)
    return sign + (abs / factor) + "." + (abs % factor).toString().padStart(decimals, '0')
}
