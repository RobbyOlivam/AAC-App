package com.robbyolivam.aac.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.robbyolivam.aac.core.Symbol
import com.robbyolivam.aac.data.MediaFiles
import com.robbyolivam.aac.data.SymbolLibrary
import com.robbyolivam.aac.core.SymbolStyle
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.runtime.collectAsState

val LocalSymbolLibrary = staticCompositionLocalOf<SymbolLibrary?> { null }
val LocalSymbolStyle = staticCompositionLocalOf { SymbolStyle.LIBRARY }
/** Whether emoji can be drawn as text (the web has no emoji font, so it uses OpenMoji pictures only). */
val LocalHasEmojiFont = staticCompositionLocalOf { true }

/** Which library symbol to draw for [symbol], or null to use its emoji/photo. */
fun SymbolLibrary.libraryIdFor(symbol: Symbol, style: SymbolStyle): String? {
    if (style == SymbolStyle.EMOJI) return null
    return symbol.libraryId?.takeIf { contains(it) } ?: forEmoji(symbol.emoji)
}

/** Loads a library symbol (an SVG) once; shows nothing until it's ready. */
@Composable
fun rememberLibraryPainter(libraryId: String?): Painter? {
    val library = LocalSymbolLibrary.current ?: return null
    if (libraryId == null) return null
    val density = LocalDensity.current
    val painter by produceState(initialValue = library.cached(libraryId), libraryId) {
        // A reused button slot keeps the previous symbol in `value`; always replace it.
        value = library.cached(libraryId) ?: library.painter(libraryId, density)
    }
    return painter
}

/**
 * A button's picture: the person's own photo first, then the bundled library symbol, then
 * the emoji, and finally (if nothing else) the word itself.
 */
@Composable
fun SymbolView(
    symbol: Symbol?,
    media: MediaFiles,
    modifier: Modifier = Modifier,
    fallbackText: String? = null,
    textColor: Color = Color.Unspecified,
) {
    val library = LocalSymbolLibrary.current
    val style = LocalSymbolStyle.current
    val imagePath = symbol?.imagePath
    val mediaVersion by media.version.collectAsState()
    val photo by produceState(imagePath?.let { media.cached(it) }, imagePath, mediaVersion) { value = imagePath?.let { media.load(it) } }
    // The symbol index loads after the first frame: recompute once it's there.
    val index = library?.index?.collectAsState()?.value
    val libraryId = remember(symbol, style, library, index, photo == null) { if (photo == null && symbol != null) library?.libraryIdFor(symbol, style) else null }
    val libraryImage = rememberLibraryPainter(libraryId)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        when {
            photo != null -> Image(
                bitmap = photo!!,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
            )
            libraryImage != null -> Image(
                painter = libraryImage,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            libraryId != null -> Unit // still rendering; avoid flashing the emoji first
            !symbol?.emoji.isNullOrBlank() && LocalHasEmojiFont.current -> {
                val size = with(LocalDensity.current) { (minOf(maxHeight, maxWidth) * 0.72f).toSp() }
                Text(symbol?.emoji.orEmpty(), fontSize = size, lineHeight = size, textAlign = TextAlign.Center)
            }
            fallbackText != null -> Text(fallbackText, color = textColor, fontWeight = FontWeight.Bold)
        }
    }
}
