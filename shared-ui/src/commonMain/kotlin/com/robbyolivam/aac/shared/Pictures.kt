package com.robbyolivam.aac.shared

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.robbyolivam.aac.core.Symbol
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.decodeToSvgPainter

/**
 * Loads and keeps button pictures: the person's own (from storage) and library symbols (SVGs
 * shipped with the app). Keeps the most recent few hundred so pages switch instantly.
 */
@OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
class Pictures(private val platform: Platform) {
    private val cache = LinkedHashMap<String, Painter?>()
    private val max = 600
    /** Turns emoji (the quick strip's, older boards') into the library's OpenMoji pictures. */
    var index: com.robbyolivam.aac.core.SymbolIndex? by mutableStateOf(null)

    fun libraryIdFor(symbol: Symbol): String? = symbol.libraryId ?: index?.forEmoji(symbol.emoji)

    fun cached(key: String): Painter? = cache[key]

    suspend fun load(symbol: Symbol, density: Density, version: Int): Painter? {
        val image = symbol.imagePath
        val library = libraryIdFor(symbol)
        val key = when {
            image != null -> "img:$version:$image"
            library != null -> "lib:$library"
            else -> return null
        }
        if (cache.containsKey(key)) return cache[key]
        val painter = runCatching {
            when {
                image != null -> platform.storage.read(image)?.let { BitmapPainter(it.decodeToImageBitmap()) }
                else -> {
                    val (set, id) = library!!.split(':', limit = 2)
                    platform.assets.read("symbols/$set/$id.svg")?.decodeToSvgPainter(density)
                }
            }
        }.getOrNull()
        cache[key] = painter
        if (cache.size > max) cache.remove(cache.keys.first())
        return painter
    }

    suspend fun bitmap(path: String): ImageBitmap? = runCatching { platform.storage.read(path)?.decodeToImageBitmap() }.getOrNull()
}

/** A button's picture, or nothing while it loads or if there's none. */
@Composable
fun SymbolImage(symbol: Symbol, pictures: Pictures, version: Int, modifier: Modifier = Modifier) {
    val library = pictures.libraryIdFor(symbol)
    if (symbol.imagePath == null && library == null) return
    val density = LocalDensity.current
    val key = symbol.imagePath?.let { "img:$version:$it" } ?: "lib:$library"
    var painter by remember(key) { mutableStateOf(pictures.cached(key)) }
    LaunchedEffect(key) { if (painter == null) painter = pictures.load(symbol, density, version) }
    painter?.let { Image(it, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit) }
}
