package com.robbyolivam.aac.core

/** Modified Fitzgerald Key colors (0xRRGGBB), the convention most AAC systems and SLPs use. */
fun fitzgeraldRgb(type: WordType): Int = when (type) {
    WordType.PRONOUN, WordType.PERSON -> 0xFFF176
    WordType.VERB -> 0xA5D6A7
    WordType.ADJECTIVE, WordType.ADVERB -> 0x90CAF9
    WordType.NOUN, WordType.PLACE -> 0xFFCC80
    WordType.QUESTION -> 0xCE93D8
    WordType.NEGATION -> 0xEF9A9A
    WordType.SOCIAL -> 0xF8BBD0
    WordType.PREPOSITION, WordType.DETERMINER, WordType.CONJUNCTION -> 0xE0E0E0
    WordType.FOLDER -> 0xD7CCC8
    WordType.OTHER -> 0xF5F5F5
}

/** Best-guess word type for a button color from another AAC app; null if nothing is close. */
fun wordTypeForColor(rgb: Int): WordType? {
    fun dist(a: Int, b: Int): Int {
        val dr = (a shr 16 and 0xFF) - (b shr 16 and 0xFF)
        val dg = (a shr 8 and 0xFF) - (b shr 8 and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return dr * dr + dg * dg + db * db
    }
    val candidates = listOf(
        WordType.PRONOUN, WordType.VERB, WordType.ADJECTIVE, WordType.NOUN,
        WordType.QUESTION, WordType.NEGATION, WordType.SOCIAL, WordType.PREPOSITION,
    )
    // Also match the saturated Fitzgerald variants many apps use.
    val saturated = mapOf(
        0xFFFF00 to WordType.PRONOUN, 0x00FF00 to WordType.VERB, 0x008000 to WordType.VERB,
        0x0000FF to WordType.ADJECTIVE, 0xFFA500 to WordType.NOUN, 0x800080 to WordType.QUESTION,
        0xFF0000 to WordType.NEGATION, 0xFFC0CB to WordType.SOCIAL,
    )
    val all = candidates.map { fitzgeraldRgb(it) to it } + saturated.toList()
    val best = all.minBy { dist(it.first, rgb) }
    return best.second.takeIf { dist(best.first, rgb) < 60 * 60 * 3 }
}

/** Parses "rgb(1, 2, 3)", "rgba(1,2,3,0.5)" or "#RRGGBB" into 0xRRGGBB. */
fun parseCssColor(css: String?): Int? {
    val s = css?.trim()?.lowercase() ?: return null
    if (s.startsWith("#") && s.length == 7) return s.substring(1).toIntOrNull(16)
    val m = Regex("rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)").find(s) ?: return null
    val (r, g, b) = m.destructured
    return (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
}

fun cssColor(rgb: Int): String = "rgb(${rgb shr 16 and 0xFF}, ${rgb shr 8 and 0xFF}, ${rgb and 0xFF})"
