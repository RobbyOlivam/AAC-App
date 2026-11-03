package com.robbyolivam.aac.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.caverock.androidsvg.SVG
import com.robbyolivam.aac.core.LibrarySymbol
import com.robbyolivam.aac.core.SymbolIndex

/**
 * The bundled symbol library: Mulberry (AAC-specific, CC BY-SA 4.0) and OpenMoji
 * (CC BY-SA 4.0), shipped as SVGs in assets/symbols. Symbols render to bitmaps on demand
 * and are cached, so boards stay fast on cheap tablets.
 */
class SymbolLibrary(private val context: Context) {

    /** Searching and emoji lookup are shared with iOS (core's SymbolIndex); this class loads and draws. */
    private val index: SymbolIndex by lazy {
        SymbolIndex.parse(context.assets.open("symbols/index.tsv").bufferedReader().use { it.readText() })
    }

    private val cache = object : LruCache<String, ImageBitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun contains(libraryId: String): Boolean = index.contains(libraryId)

    /** The OpenMoji version of [emoji], so emoji look the same on every device. */
    fun forEmoji(emoji: String?): String? = index.forEmoji(emoji)

    /** See [SymbolIndex.search]. */
    fun exact(word: String): String? = index.exact(word)

    fun search(query: String, limit: Int = 60, context: List<String> = emptyList()): List<LibrarySymbol> =
        index.search(query, limit, context)

    /** Renders a library symbol to a square bitmap (cached). Call off the main thread. */
    fun render(libraryId: String, size: Int = RENDER_SIZE): ImageBitmap? {
        val key = "$libraryId@$size"
        cache.get(key)?.let { return it }
        return renderBitmap(libraryId, size)?.asImageBitmap()?.also { cache.put(key, it) }
    }

    fun cached(libraryId: String, size: Int = RENDER_SIZE): ImageBitmap? = cache.get("$libraryId@$size")

    /** Plain Android bitmap, for PDF printing. */
    fun renderBitmap(libraryId: String, size: Int = RENDER_SIZE): Bitmap? = runCatching {
        val (set, id) = libraryId.split(':', limit = 2)
        val svg = context.assets.open("symbols/$set/$id.svg").use { SVG.getFromInputStream(it) }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        svg.renderToCanvas(Canvas(bitmap), RectF(0f, 0f, size.toFloat(), size.toFloat()))
        bitmap
    }.getOrNull()

    companion object {
        const val RENDER_SIZE = 256
    }
}
