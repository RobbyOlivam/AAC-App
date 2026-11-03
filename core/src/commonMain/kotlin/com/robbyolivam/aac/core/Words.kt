package com.robbyolivam.aac.core

/**
 * Runs of letters, digits and apostrophes in [text], as index ranges. Written by hand because
 * Kotlin/Native's regex engine (iOS) does not accept the Unicode classes `\p{L}` / `\p{N}`.
 */
internal fun wordRanges(text: String): List<IntRange> {
    val out = ArrayList<IntRange>()
    var start = -1
    for (i in text.indices) {
        val c = text[i]
        val inWord = c.isLetterOrDigit() || c == '\''
        if (inWord && start < 0) start = i
        if (!inWord && start >= 0) { out += start until i; start = -1 }
    }
    if (start >= 0) out += start until text.length
    return out
}

/** The words in [text] (letters, digits and apostrophes), in order. */
internal fun wordTokens(text: String): List<String> = wordRanges(text).map { text.substring(it) }
