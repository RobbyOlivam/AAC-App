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
import com.robbyolivam.aac.data.SymbolStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalSymbolLibrary = staticCompositionLocalOf<SymbolLibrary?> { null }
val LocalSymbolStyle = staticCompositionLocalOf { SymbolStyle.LIBRARY }

/** Which library symbol to draw for [symbol], or null to use its emoji/photo. */
fun SymbolLibrary.libraryIdFor(symbol: Symbol, style: SymbolStyle): String? {
    if (style == SymbolStyle.EMOJI) return null
    return symbol.libraryId?.takeIf { contains(it) } ?: forEmoji(symbol.emoji)
}

/** Renders a library symbol off the main thread; shows nothing until it's ready. */
@Composable
fun rememberLibraryBitmap(libraryId: String?): ImageBitmap? {
    val library = LocalSymbolLibrary.current ?: return null
    if (libraryId == null) return null
    val bitmap by produceState(initialValue = library.cached(libraryId), libraryId) {
        // A reused button slot keeps the previous symbol in `value`; always replace it.
        value = library.cached(libraryId)
            ?: if (renderSymbolsSynchronously) library.render(libraryId)
            else withContext(Dispatchers.Default) { library.render(libraryId) }
    }
    return bitmap
}

/** Tests flip this so screenshots include symbols without waiting on background threads. */
@Volatile
var renderSymbolsSynchronously = false

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
    val photo = remember(imagePath) { imagePath?.let { media.load(it) } }
    val libraryId = remember(symbol, style, library) { if (photo == null && symbol != null) library?.libraryIdFor(symbol, style) else null }
    val libraryImage = rememberLibraryBitmap(libraryId)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        when {
            photo != null -> Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
            )
            libraryImage != null -> Image(
                bitmap = libraryImage,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            libraryId != null -> Unit // still rendering; avoid flashing the emoji first
            !symbol?.emoji.isNullOrBlank() -> {
                val size = with(LocalDensity.current) { (minOf(maxHeight, maxWidth) * 0.72f).toSp() }
                Text(symbol?.emoji.orEmpty(), fontSize = size, lineHeight = size, textAlign = TextAlign.Center)
            }
            fallbackText != null -> Text(fallbackText, color = textColor, fontWeight = FontWeight.Bold)
        }
    }
}
