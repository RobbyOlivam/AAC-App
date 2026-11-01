package com.robbyolivam.aac.core

/**
 * Suggestions for the "Add a word" wizard: what kind of word it is (which sets its color),
 * which folder it belongs in, and the first empty spot there. Hints come from the symbol
 * library's grammar and category tags; everything can be changed by the person adding the word.
 */
object WordWizard {

    data class Plan(val type: WordType, val boardId: String?, val index: Int?)

    /** Category words in symbol tags → the starter folder they belong in. Checked in order. */
    private val folderHints = listOf(
        "feelings" to listOf("feelings", "emotion"),
        "health" to listOf("healthcare", "medical", "medicine", "illness"),
        "body" to listOf("body", "grooming"),
        "food" to listOf("food", "meals", "snacks", "drink", "fruit", "vegetables", "breads", "desserts", "baking", "salads", "eating"),
        "animals" to listOf("animal", "mammal", "bird", "insect", "pets", "fish", "reptile"),
        "people" to listOf("people", "profession", "family", "relationship"),
        "weather" to listOf("weather"),
        "time" to listOf("time", "calendar", "days", "months", "seasons"),
        "school" to listOf("school", "stationery", "alphabet", "number", "science", "computer", "classroom"),
        "activities" to listOf("sport", "leisure", "games", "hobbies", "music", "celebration"),
        "places" to listOf("building", "country", "maps", "places", "habitat", "shops"),
        "describe" to listOf("descriptive", "shape", "colour", "color", "state", "position", "quantity", "size"),
        "things" to listOf("clothes", "accessories", "furniture", "kitchen", "tools", "electrical", "toys", "items", "transport", "item"),
    )

    /** Guesses the kind of word from the symbol library's [grammar] and category [tags]. */
    fun guessType(word: String, grammar: String?, tags: String?): WordType {
        val t = tags.orEmpty().lowercase()
        fun tagged(vararg keys: String) = keys.any { Regex("\\b$it\\b").containsMatchIn(t) }
        return when {
            grammar.equals("Verb", true) || grammar.equals("VerbComplex", true) -> WordType.VERB
            grammar.equals("Interjection", true) -> WordType.SOCIAL
            tagged("descriptive", "state", "feelings", "shape", "colour", "color", "size") -> WordType.ADJECTIVE
            tagged("profession", "people", "family", "relationship") -> WordType.PERSON
            tagged("building", "country", "maps", "places", "habitat") -> WordType.PLACE
            grammar != null -> WordType.NOUN
            word.trim().lowercase().endsWith("ly") && word.length > 4 -> WordType.ADVERB
            else -> WordType.NOUN
        }
    }

    /** The folder for a new word: from its tags, else from its kind. Only folders that exist. */
    fun guessFolder(type: WordType, tags: String?, vocabulary: Vocabulary): String? {
        val t = tags.orEmpty().lowercase()
        val byTags = folderHints.firstOrNull { (_, keys) -> keys.any { Regex("\\b$it\\b").containsMatchIn(t) } }?.first
        val byType = when (type) {
            WordType.VERB -> listOf("actions", "more-actions")
            WordType.ADJECTIVE, WordType.ADVERB -> listOf("describe")
            WordType.PERSON -> listOf("people")
            WordType.PLACE -> listOf("places")
            WordType.NOUN -> listOf("things")
            else -> listOf("little-words")
        }
        val candidates = listOfNotNull(byTags) + byType
        val existing = candidates.filter { vocabulary.board(it)?.isScene == false }
        // A full folder is still the right home; prefer one with room among the good matches.
        return existing.firstOrNull { firstEmptySlot(vocabulary.board(it)!!) != null } ?: existing.firstOrNull()
    }

    fun firstEmptySlot(board: Board): Int? = if (board.isScene) null else board.cells.indexOfFirst { it == null }.takeIf { it >= 0 }

    fun plan(word: String, grammar: String?, tags: String?, vocabulary: Vocabulary): Plan {
        val type = guessType(word, grammar, tags)
        val boardId = guessFolder(type, tags, vocabulary)
        return Plan(type, boardId, boardId?.let { vocabulary.board(it) }?.let(::firstEmptySlot))
    }
}

/**
 * Checks an AI-written sentence before it's offered: it must keep every word the person chose
 * (allowing other forms, like "want" → "wants"), stay short, and be a single plain sentence.
 * The AI may add little words, never new ideas.
 */
object ExpansionCheck {
    private const val MAX_LENGTH = 140

    fun accept(input: String, output: String?): String? {
        if (output == null) return null
        val sentence = output.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?.trim('"', '“', '”', '\'', '*', ' ')
            ?.removePrefix("Sentence:")?.trim()
            ?: return null
        if (sentence.length > MAX_LENGTH || sentence.isEmpty()) return null
        val chosen = UsageEvent.wordsIn(input)
        if (chosen.isEmpty()) return null
        val said = UsageEvent.wordsIn(sentence)
        if (said.size < chosen.size) return null
        // Each chosen word must appear (or a form sharing its first letters: go/went is allowed by
        // the irregular check below).
        val keepsWords = chosen.all { w -> said.any { s -> sameWord(w, s) } }
        if (!keepsWords) return null
        // No runaway sentences: at most a few added words per chosen word.
        if (said.size > chosen.size * 3 + 4) return null
        if (sentence.equals(input.trim(), ignoreCase = true)) return null
        return sentence.replaceFirstChar { it.uppercase() }
    }

    private fun sameWord(chosen: String, said: String): Boolean {
        if (chosen == said) return true
        if (chosen == "i" && said in setOf("i'm", "i'd", "i'll", "i've", "me", "my")) return true
        if (chosen == "me" && said in setOf("i", "my")) return true
        val forms = EnglishMorphology.forms(chosen, WordType.VERB) + EnglishMorphology.forms(chosen, WordType.NOUN)
        if (said in forms) return true
        val stem = chosen.take(maxOf(3, chosen.length - 2))
        return chosen.length >= 4 && said.startsWith(stem)
    }
}
