package com.robbyolivam.aac.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.caverock.androidsvg.SVG

/** One searchable symbol from the bundled library. */
data class LibrarySymbol(val libraryId: String, val name: String, val grammar: String, val tags: String) {
    val set: String get() = libraryId.substringBefore(':')
}

/**
 * The bundled symbol library: Mulberry (AAC-specific, CC BY-SA 4.0) and OpenMoji
 * (CC BY-SA 4.0), shipped as SVGs in assets/symbols. Symbols render to bitmaps on demand
 * and are cached, so boards stay fast on cheap tablets.
 */
class SymbolLibrary(private val context: Context) {

    private val index: List<LibrarySymbol> by lazy {
        context.assets.open("symbols/index.tsv").bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 5) null else LibrarySymbol("${p[0]}:${p[1]}", p[2], p[3], p[4])
            }.toList()
        }
    }

    private val ids: Set<String> by lazy { index.mapTo(HashSet()) { it.libraryId } }

    private val cache = object : LruCache<String, ImageBitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun contains(libraryId: String): Boolean = libraryId in ids

    /** The OpenMoji version of [emoji], so emoji look the same on every device. */
    fun forEmoji(emoji: String?): String? {
        if (emoji.isNullOrBlank()) return null
        val withSelector = emoji.codePoints().toArray().joinToString("-") { Integer.toHexString(it).uppercase() }
        val withoutSelector = emoji.codePoints().toArray().filter { it != 0xFE0F }
            .joinToString("-") { Integer.toHexString(it).uppercase() }
        return listOf("o:$withSelector", "o:$withoutSelector").firstOrNull { it in ids }
    }

    /**
     * Symbols matching [query]: exact name first, then names starting with it, then names
     * containing it, then tags. Mulberry (made for AAC) ranks ahead of OpenMoji on ties.
     */
    fun search(query: String, limit: Int = 60): List<LibrarySymbol> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val words = Regex("\\b${Regex.escape(q)}")
        return index.mapNotNull { s ->
            val name = s.name.lowercase()
            val rank = when {
                name == q -> 0
                name.startsWith("$q ") || name.startsWith(q) -> 1
                words.containsMatchIn(name) -> 2
                words.containsMatchIn(s.tags.lowercase()) -> 3
                else -> return@mapNotNull null
            }
            Triple(rank, if (s.set == "m") 0 else 1, s)
        }.sortedWith(compareBy({ it.first }, { it.second }, { it.third.name.length }))
            .take(limit)
            .map { it.third }
    }

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
