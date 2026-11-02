package com.robbyolivam.aac.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Counts learned from what the user actually says. Persisted between sessions. */
@Serializable
data class PredictorData(
    val unigrams: Map<String, Int> = emptyMap(),
    val bigrams: Map<String, Map<String, Int>> = emptyMap(),
)

/**
 * Small on-device word predictor: seeded unigram/bigram model plus user-adaptive counts.
 * User counts are weighted heavily so the user's own phrasing wins quickly.
 */
class Predictor(learned: PredictorData = PredictorData()) {

    private val unigrams = HashMap<String, Double>()
    private val bigrams = HashMap<String, HashMap<String, Double>>()
    private val userUnigrams = HashMap<String, Int>(learned.unigrams)
    private val userBigrams = HashMap<String, HashMap<String, Int>>().apply {
        learned.bigrams.forEach { (k, v) -> put(k, HashMap(v)) }
    }

    init {
        COMMON_WORDS.split(Regex("\\s+")).filter { it.isNotBlank() }.forEachIndexed { rank, w ->
            unigrams[w] = (unigrams[w] ?: 0.0) + 1000.0 / (rank + 10)
        }
        SEED_SENTENCES.forEach { sentence -> addSeed(tokenize(sentence)) }
    }

    private fun addSeed(words: List<String>) {
        var prev = START
        for (w in words) {
            unigrams[w] = (unigrams[w] ?: 0.0) + 1.0
            bigrams.getOrPut(prev) { HashMap() }.addTo(w, 1.0)
            prev = w
        }
    }

    /** Records a spoken utterance so future predictions adapt to the user. */
    fun learn(text: String) {
        var prev = START
        for (w in tokenize(text)) {
            userUnigrams.addTo(w, 1)
            userBigrams.getOrPut(prev) { HashMap() }.addTo(w, 1)
            prev = w
        }
    }

    fun snapshot(): PredictorData = PredictorData(HashMap(userUnigrams), userBigrams.mapValues { HashMap(it.value) })

    /**
     * Next-word suggestions after [previous] (null = start of message), optionally limited to
     * words present in [allowed] (e.g. words that have a button).
     */
    fun predictNext(previous: String?, limit: Int = 6, allowed: Set<String>? = null): List<String> =
        rank(previous, prefix = "", limit = limit, allowed = allowed)

    /** Completes a partially typed word. */
    fun complete(prefix: String, previous: String?, limit: Int = 6): List<String> {
        if (prefix.isBlank()) return predictNext(previous, limit)
        return rank(previous, prefix.lowercase(), limit, allowed = null).filter { it != prefix.lowercase() }
    }

    private fun rank(previous: String?, prefix: String, limit: Int, allowed: Set<String>?): List<String> {
        val prev = previous?.lowercase()?.let { tokenize(it).lastOrNull() } ?: START
        val scores = HashMap<String, Double>()
        fun add(word: String, score: Double) {
            if (!word.startsWith(prefix)) return
            if (allowed != null && word !in allowed) return
            scores.addTo(word, score)
        }
        unigrams.forEach { (w, c) -> add(w, c * 0.01) }
        userUnigrams.forEach { (w, c) -> add(w, c * 2.0) }
        bigrams[prev]?.forEach { (w, c) -> add(w, c * 5.0) }
        userBigrams[prev]?.forEach { (w, c) -> add(w, c * 20.0) }
        if (prev != START) scores.remove(prev)
        return scores.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }

    companion object {
        private const val START = "<s>"

        fun tokenize(text: String): List<String> =
            Regex("[\\p{L}\\p{N}']+").findAll(text.lowercase()).map { it.value }.toList()

        private val json = Json { ignoreUnknownKeys = true }

        fun decode(text: String): PredictorData = json.decodeFromString(PredictorData.serializer(), text)

        fun encode(data: PredictorData): String = json.encodeToString(PredictorData.serializer(), data)

        // Roughly frequency-ordered conversational English.
        private const val COMMON_WORDS = """
            i you the a to it is and not that what my me we want go do like can have is this in on
            no yes he she they get more help stop be are was all done here there with for of your
            mom dad play eat drink see look need make put come give take feel good bad big little
            now later where who why how when please thank thanks okay hi bye one two some again
            home school outside car water food toy book tv music friend happy sad mad tired sick
            hurt hungry thirsty break turn wait finished different same up down off out open close
            what's i'm don't can't it's that's let's going will would could should did does has had
            know think say tell love hate fun funny favorite today tomorrow yesterday morning night
            bathroom later soon very really too also but because so if then or an at by from about
            him her them us our their his its am were been being got went saw made said told came
            game park store lunch dinner breakfast snack pizza juice milk cookie apple banana
            run walk jump sit sleep read watch listen hug kiss sing dance draw swim ride build
            something nothing everything everyone someone time day week minute hot cold fast slow
        """

        private val SEED_SENTENCES = listOf(
            "I want more", "I want to go", "I want to play", "I want to eat", "I want to drink",
            "I want that", "I want help", "I like it", "I like that", "I don't like it",
            "I don't want it", "I need help", "I need a break", "I need to go to the bathroom",
            "I am hungry", "I am thirsty", "I am tired", "I am happy", "I am sad", "I feel sick",
            "I feel happy", "I feel sad", "you are funny", "you go", "my turn", "your turn",
            "let's go", "let's play", "go home", "go outside", "go to school", "go to the park",
            "can I have more", "can I have a turn", "can you help me", "what is that",
            "what is it", "where is mom", "where is dad", "where are we going", "who is that",
            "it is hot", "it is cold", "it is big", "it is little", "it is broken", "all done",
            "stop it", "not now", "not that", "I love you", "help me", "look at that",
            "see you later", "thank you", "how are you", "I am good", "I don't know",
            "I want to watch tv", "I want to listen to music", "turn it on", "turn it off",
            "put it in", "put it on", "get it", "give me that", "eat lunch", "drink water",
            "play with me", "come here", "I want to go home", "I want to go outside",
        )
    }
}

/** Adds [amount] to the count for [key] (platform-neutral stand-in for Java's Map.merge). */
internal fun <K> MutableMap<K, Int>.addTo(key: K, amount: Int) {
    this[key] = (this[key] ?: 0) + amount
}

internal fun <K> MutableMap<K, Double>.addTo(key: K, amount: Double) {
    this[key] = (this[key] ?: 0.0) + amount
}
