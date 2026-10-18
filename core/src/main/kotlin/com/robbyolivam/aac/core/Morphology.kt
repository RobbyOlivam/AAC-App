package com.robbyolivam.aac.core

/** English word forms (go → goes/going/went/gone, dog → dogs, big → bigger/biggest…). */
object EnglishMorphology {

    private data class Verb(val past: String, val participle: String)

    private val irregularVerbs: Map<String, Verb> = listOf(
        "go went gone", "eat ate eaten", "drink drank drunk", "see saw seen", "do did done",
        "make made made", "have had had", "get got gotten", "put put put", "feel felt felt",
        "come came come", "give gave given", "take took taken", "run ran run", "sit sat sat",
        "sleep slept slept", "read read read", "swim swam swum", "draw drew drawn", "sing sang sung",
        "think thought thought", "know knew known", "say said said", "tell told told", "find found found",
        "build built built", "cut cut cut", "throw threw thrown", "buy bought bought", "bring brought brought",
        "catch caught caught", "fall fell fallen", "fly flew flown", "hear heard heard", "hold held held",
        "hurt hurt hurt", "leave left left", "lose lost lost", "meet met met", "pay paid paid",
        "ride rode ridden", "sell sold sold", "send sent sent", "shake shook shaken", "speak spoke spoken",
        "stand stood stood", "teach taught taught", "wake woke woken", "wear wore worn", "win won won",
        "write wrote written", "begin began begun", "break broke broken", "choose chose chosen",
        "forget forgot forgotten", "hide hid hidden", "let let let", "keep kept kept", "feed fed fed",
        "spend spent spent", "understand understood understood", "blow blew blown", "grow grew grown",
        "drive drove driven", "bite bit bitten", "hit hit hit", "shut shut shut", "dig dug dug",
        "can could could", "will would would",
    ).associate { line -> line.split(' ').let { it[0] to Verb(it[1], it[2]) } }

    private val irregularPlurals = mapOf(
        "child" to "children", "person" to "people", "man" to "men", "woman" to "women",
        "foot" to "feet", "tooth" to "teeth", "mouse" to "mice", "fish" to "fish", "sheep" to "sheep",
        "knife" to "knives", "leaf" to "leaves", "wolf" to "wolves", "shelf" to "shelves",
        "deer" to "deer", "goose" to "geese", "potato" to "potatoes", "tomato" to "tomatoes",
    )

    /** Nouns that are already plural or uncountable. */
    private val noPlural = setOf(
        "grapes", "fries", "pants", "socks", "shoes", "glasses", "noodles", "crayons", "blocks",
        "bubbles", "headphones", "rice", "milk", "water", "juice", "bread", "cereal", "popcorn",
        "music", "corn", "medicine", "candy", "pasta", "soup", "cheese", "chicken", "breakfast",
        "lunch", "dinner", "people", "family", "everyone", "somebody", "home", "outside", "inside",
        "time",
    )

    private val irregularAdjectives = mapOf(
        "good" to ("better" to "best"), "bad" to ("worse" to "worst"), "far" to ("farther" to "farthest"),
        "many" to ("more" to "most"), "much" to ("more" to "most"), "little" to ("littler" to "littlest"),
        "fun" to ("more fun" to "most fun"),
    )

    private val pronounForms = mapOf(
        "i" to listOf("I", "me", "my", "mine", "myself", "I'm"),
        "me" to listOf("me", "I", "my", "mine", "myself"),
        "my" to listOf("my", "mine", "I", "me", "myself"),
        "you" to listOf("you", "your", "yours", "yourself", "you're"),
        "he" to listOf("he", "him", "his", "himself", "he's"),
        "she" to listOf("she", "her", "hers", "herself", "she's"),
        "we" to listOf("we", "us", "our", "ours", "ourselves", "we're"),
        "they" to listOf("they", "them", "their", "theirs", "themselves", "they're"),
        "it" to listOf("it", "its", "itself", "it's"),
    )

    private val specialForms = mapOf(
        "is" to listOf("is", "be", "am", "are", "was", "were", "being", "been"),
        "be" to listOf("be", "am", "is", "are", "was", "were", "being", "been"),
        "not" to listOf("not", "don't", "can't", "won't", "isn't", "didn't", "doesn't", "aren't", "no"),
        "that" to listOf("that", "this", "these", "those", "the", "a", "an"),
        "what" to listOf("what", "what's", "which", "why", "how", "when"),
        "where" to listOf("where", "where's", "there", "here"),
        "who" to listOf("who", "who's", "whose", "whom"),
        "can" to listOf("can", "could", "can't", "couldn't"),
        "will" to listOf("will", "would", "won't", "going to"),
        "all done" to listOf("all done", "done", "finished"),
        "more" to listOf("more", "most", "less", "some", "all", "another"),
        "little" to listOf("little", "littler", "littlest", "small", "smaller", "smallest"),
    )

    private const val VOWELS = "aeiou"

    private fun isConsonant(c: Char) = c.isLetter() && c.lowercaseChar() !in VOWELS

    /** Short consonant-vowel-consonant words double their final consonant: stop → stopping, big → bigger. */
    private fun doublesFinal(w: String): Boolean {
        if (w.length < 3) return false
        val (a, b, c) = Triple(w[w.length - 3], w[w.length - 2], w[w.length - 1])
        val cvc = isConsonant(a) && !isConsonant(b) && isConsonant(c) && c !in "wxy"
        if (!cvc) return false
        // Only one vowel group: run, stop, swim, big — but not open, visit.
        return w.count { it.lowercaseChar() in VOWELS } == 1
    }

    fun thirdPerson(verb: String): String = when {
        verb == "have" -> "has"
        verb == "do" -> "does"
        verb == "go" -> "goes"
        verb == "is" || verb == "be" -> "is"
        verb == "can" || verb == "will" -> verb
        verb.endsWith("y") && verb.length > 1 && isConsonant(verb[verb.length - 2]) -> verb.dropLast(1) + "ies"
        verb.endsWith("s") || verb.endsWith("x") || verb.endsWith("z") || verb.endsWith("ch") ||
            verb.endsWith("sh") || verb.endsWith("o") -> verb + "es"
        else -> verb + "s"
    }

    fun progressive(verb: String): String = when {
        verb == "be" || verb == "is" -> "being"
        verb.endsWith("ie") -> verb.dropLast(2) + "ying"
        verb.endsWith("ee") || verb.endsWith("ye") || verb.endsWith("oe") -> verb + "ing"
        verb.endsWith("e") && verb.length > 2 -> verb.dropLast(1) + "ing"
        doublesFinal(verb) -> verb + verb.last() + "ing"
        else -> verb + "ing"
    }

    fun past(verb: String): String {
        irregularVerbs[verb]?.let { return it.past }
        return when {
            verb == "is" -> "was"
            verb.endsWith("e") -> verb + "d"
            verb.endsWith("y") && verb.length > 1 && isConsonant(verb[verb.length - 2]) -> verb.dropLast(1) + "ied"
            doublesFinal(verb) -> verb + verb.last() + "ed"
            else -> verb + "ed"
        }
    }

    fun pastParticiple(verb: String): String = irregularVerbs[verb]?.participle ?: if (verb == "is") "been" else past(verb)

    fun plural(noun: String): String {
        val lower = noun.lowercase()
        irregularPlurals[lower]?.let { return it }
        return when {
            lower in noPlural -> noun
            lower.endsWith("y") && lower.length > 1 && isConsonant(lower[lower.length - 2]) -> noun.dropLast(1) + "ies"
            lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("z") || lower.endsWith("ch") ||
                lower.endsWith("sh") -> noun + "es"
            else -> noun + "s"
        }
    }

    fun comparative(adj: String): Pair<String, String> {
        irregularAdjectives[adj]?.let { return it }
        // A final silent "e" (nice, cute) doesn't add a syllable.
        val stem = if (adj.endsWith("e") && !adj.endsWith("ee")) adj.dropLast(1) else adj
        val syllables = Regex("[aeiouy]+").findAll(stem).count()
        return when {
            syllables >= 3 || (syllables == 2 && !adj.endsWith("y")) || adj.endsWith("ed") ->
                "more $adj" to "most $adj"
            adj.endsWith("e") -> adj + "r" to adj + "st"
            adj.endsWith("y") && adj.length > 1 && isConsonant(adj[adj.length - 2]) ->
                adj.dropLast(1) + "ier" to adj.dropLast(1) + "iest"
            doublesFinal(adj) -> adj + adj.last() + "er" to adj + adj.last() + "est"
            else -> adj + "er" to adj + "est"
        }
    }

    /**
     * Returns the useful forms of [word] (the word itself first), inflecting the last word of a
     * multi-word noun/adjective and the first word of a multi-word verb.
     */
    fun forms(word: String, type: WordType): List<String> {
        val lower = word.lowercase()
        specialForms[lower]?.let { return it }
        if (type == WordType.PRONOUN) pronounForms[lower]?.let { return it }
        if (word.contains(' ')) {
            val parts = word.split(' ')
            return when (type) {
                WordType.VERB -> forms(parts.first(), type).map { (listOf(it) + parts.drop(1)).joinToString(" ") }
                WordType.NOUN, WordType.PLACE, WordType.PERSON ->
                    forms(parts.last(), type).map { (parts.dropLast(1) + it).joinToString(" ") }
                else -> listOf(word)
            }
        }
        val result = when (type) {
            WordType.VERB -> listOf(word, thirdPerson(lower), progressive(lower), past(lower), pastParticiple(lower), "to $lower")
            WordType.NOUN -> listOf(word, plural(word), "$word's")
            WordType.PERSON -> listOf(word, "$word's", plural(word))
            WordType.PLACE -> listOf(word, plural(word))
            WordType.ADJECTIVE -> comparative(lower).let { (er, est) -> listOf(word, er, est, "not $lower", "very $lower") }
            else -> listOf(word)
        }
        return result.distinct()
    }

    /** True when [forms] offers more than the word itself. */
    fun hasForms(word: String, type: WordType): Boolean = forms(word, type).size > 1
}
