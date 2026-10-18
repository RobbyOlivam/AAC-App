package com.robbyolivam.aac.core

/** A word in the message bar. [type] is null for typed words that have no button. */
data class Token(val text: String, val type: WordType? = null)

/**
 * Rule-based "polish" for telegraphic messages: "want go park" → "I want to go to the park."
 * Runs fully offline. It only ever produces a *suggestion*; the user decides whether to use it.
 * An on-device LLM can implement [UtteranceExpander] later and fall back to this.
 */
interface UtteranceExpander {
    fun suggestions(tokens: List<Token>): List<String>
}

object GrammarHelper : UtteranceExpander {

    private val thirdPersonSubjects = setOf("he", "she", "it", "that", "this")
    private val pluralSubjects = setOf("you", "we", "they")
    private val subjects = setOf("i") + thirdPersonSubjects + pluralSubjects
    private val toVerbs = setOf("want", "need", "like", "try", "have", "going", "love", "hate", "wants", "needs", "likes", "tries", "has", "loves", "hates")
    private val firstPersonStarters = setOf("want", "like", "need", "have", "feel", "see", "know", "think", "love", "hate")
    private val motionVerbs = setOf("go", "come", "walk", "run", "drive", "ride", "goes", "comes", "walks", "runs", "going", "went")
    private val placesWithoutTo = setOf("home", "outside", "inside", "here", "there", "upstairs", "downstairs")
    private val placesWithoutThe = setOf("school", "work", "church", "bed", "grandma's house")
    private val modals = setOf("can", "will", "could", "would", "should", "don't", "doesn't", "didn't", "can't", "won't", "to")

    private class W(var text: String, val type: WordType?) {
        val lower get() = text.lowercase()
    }

    override fun suggestions(tokens: List<Token>): List<String> {
        val polished = polish(tokens) ?: return emptyList()
        return listOf(polished)
    }

    /** Returns a polished sentence, or null when there is nothing to improve. */
    fun polish(tokens: List<Token>): String? {
        if (tokens.isEmpty()) return null
        val plain = tokens.joinToString(" ") { it.text }
        val words = tokens.map { W(it.text, it.type) }.toMutableList()

        // 1. "want cookie" → "I want cookie"
        val first = words.first()
        if (first.type == WordType.VERB && first.lower in firstPersonStarters) {
            words.add(0, W("I", WordType.PRONOUN))
        }

        var i = 0
        while (i < words.size) {
            val w = words[i]
            val next = words.getOrNull(i + 1)
            val isSubject = w.lower in subjects || w.type == WordType.PERSON
            if (isSubject && next != null) {
                when {
                    // 2. "I not like" → "I don't like", "he not want" → "he doesn't want"
                    next.type == WordType.NEGATION && next.lower == "not" && words.getOrNull(i + 2)?.type == WordType.VERB &&
                        words[i + 2].lower != "is" -> {
                        next.text = if (isThirdPerson(w)) "doesn't" else "don't"
                    }
                    // 3. "I happy" → "I am happy", "I not happy" → "I am not happy"
                    next.type == WordType.ADJECTIVE ||
                        (next.type == WordType.NEGATION && words.getOrNull(i + 2)?.type == WordType.ADJECTIVE) -> {
                        words.add(i + 1, W(copula(w), WordType.VERB))
                    }
                    // 4. "he want" → "he wants", "I is" → "I am"
                    next.type == WordType.VERB -> {
                        next.text = if (next.lower == "is") copula(w) else if (isThirdPerson(w)) EnglishMorphology.thirdPerson(next.lower) else next.text
                    }
                }
            }
            // 5. "want go" → "want to go"
            if (w.lower in toVerbs && next?.type == WordType.VERB && next.lower !in modals) {
                words.add(i + 1, W("to", WordType.PREPOSITION))
            }
            // 6. "go park" → "go to the park", "go school" → "go to school", "go home" stays
            if (w.lower in motionVerbs && next?.type == WordType.PLACE) {
                val place = next.lower
                when {
                    place in placesWithoutTo -> Unit
                    place in placesWithoutThe -> words.add(i + 1, W("to", WordType.PREPOSITION))
                    else -> {
                        words.add(i + 1, W("the", WordType.DETERMINER))
                        words.add(i + 1, W("to", WordType.PREPOSITION))
                    }
                }
            }
            // 7. "where mom" → "where is mom", "what that" → "what is that"
            if (w.type == WordType.QUESTION && i == 0 && next != null &&
                (next.type in setOf(WordType.NOUN, WordType.PERSON, WordType.PLACE, WordType.PRONOUN, WordType.DETERMINER))
            ) {
                words.add(i + 1, W(if (next.lower in pluralSubjects) "are" else "is", WordType.VERB))
            }
            i++
        }

        val isQuestion = words.first().type == WordType.QUESTION || words.first().lower in setOf("can", "do", "is", "are", "will")
        val sentence = words.joinToString(" ") { if (it.lower == "i" || it.lower.startsWith("i'")) it.text.replaceFirstChar { c -> c.uppercase() } else it.text }
            .replaceFirstChar { it.uppercase() }
            .trimEnd('.', '?', '!') + if (isQuestion) "?" else "."
        val unchanged = sentence.trimEnd('.', '?').equals(plain, ignoreCase = true)
        return if (unchanged || words.size < 2) null else sentence
    }

    private fun isThirdPerson(w: W) = w.lower in thirdPersonSubjects || w.type == WordType.PERSON

    private fun copula(subject: W) = when {
        subject.lower == "i" -> "am"
        subject.lower in pluralSubjects -> "are"
        else -> "is"
    }
}
