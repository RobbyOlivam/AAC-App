package com.robbyolivam.aac.core

/** One searchable symbol from the bundled library. */
data class LibrarySymbol(val libraryId: String, val name: String, val grammar: String, val tags: String) {
    val set: String get() = libraryId.substringBefore(':')
}

/**
 * The bundled symbol library's index (symbols/index.tsv: set, id, name, grammar, tags), and
 * searching it. Shared by every platform; each platform only loads the file and draws the SVGs.
 */
class SymbolIndex(val symbols: List<LibrarySymbol>) {
    private val ids: Set<String> = symbols.mapTo(HashSet()) { it.libraryId }

    fun contains(libraryId: String): Boolean = libraryId in ids

    /** The OpenMoji version of [emoji], so emoji look the same on every device. */
    fun forEmoji(emoji: String?): String? {
        if (emoji.isNullOrBlank()) return null
        val points = codePoints(emoji)
        val withSelector = points.joinToString("-") { it.toString(16).uppercase() }
        val withoutSelector = points.filter { it != 0xFE0F }.joinToString("-") { it.toString(16).uppercase() }
        return listOf("o:$withSelector", "o:$withoutSelector").firstOrNull { it in ids }
    }

    /**
     * Symbols matching [query]: exact name first, then names starting with it, then names
     * containing it, then tags. Within each, symbols whose tags fit [context] (the folder's topic)
     * come first; then Mulberry (made for AAC) and this app's own symbols ahead of OpenMoji.
     */
    private val byName: Map<String, List<LibrarySymbol>> by lazy { symbols.groupBy { it.name.lowercase() } }

    /** The symbol named exactly [word] (Mulberry first), or null. Fast, for importing many words. */
    fun exact(word: String): String? =
        byName[word.trim().lowercase()]?.minByOrNull { if (it.set == "o") 1 else 0 }?.libraryId

    fun search(query: String, limit: Int = 60, context: List<String> = emptyList()): List<LibrarySymbol> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val words = Regex("\\b${Regex.escape(q)}")
        val topic = context.map { Regex("\\b${Regex.escape(it.lowercase())}") }
        return symbols.mapNotNull { s ->
            val name = s.name.lowercase()
            val rank = when {
                name == q -> 0
                name.startsWith("$q ") || name.startsWith(q) -> 1
                words.containsMatchIn(name) -> 2
                words.containsMatchIn(s.tags.lowercase()) -> 3
                else -> return@mapNotNull null
            }
            // Among equally good name matches, pictures from the folder's topic come first
            // (in Things, "tablet" should be a computer, not pills).
            val offTopic = if (topic.isEmpty() || topic.any { it.containsMatchIn(s.tags.lowercase()) }) 0 else 1
            Triple(rank * 2 + offTopic, if (s.set == "o") 1 else 0, s)
        }.sortedWith(compareBy({ it.first }, { it.second }, { it.third.name.length }))
            .take(limit)
            .map { it.third }
    }

    companion object {
        fun parse(tsv: String): SymbolIndex = SymbolIndex(
            tsv.lineSequence().mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 5) null else LibrarySymbol("${p[0]}:${p[1]}", p[2], p[3], p[4])
            }.toList(),
        )

        /** Unicode code points of [text] (surrogate pairs joined), without platform APIs. */
        fun codePoints(text: String): List<Int> {
            val out = ArrayList<Int>(text.length)
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) {
                    out += ((c.code - 0xD800) shl 10) + (text[i + 1].code - 0xDC00) + 0x10000
                    i += 2
                } else {
                    out += c.code
                    i++
                }
            }
            return out
        }
    }
}
