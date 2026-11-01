package com.robbyolivam.aac.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where a logged word or message came from. */
@Serializable
enum class UsageSource {
    /** A word button on the Core (home) page. */
    @SerialName("core") CORE,
    /** A word button on any other page (folders, photo scenes). */
    @SerialName("folder") FOLDER,
    @SerialName("keyboard") KEYBOARD,
    @SerialName("prediction") PREDICTION,
    /** A ready-made phrase button. */
    @SerialName("phrase") PHRASE,
    /** The always-visible quick strip (yes, no, help…). */
    @SerialName("strip") STRIP,
    /** The message bar, spoken with the speak button. */
    @SerialName("message") MESSAGE,
}

/**
 * One logged event: a word added to the message ([words] = 1), or something spoken as a whole
 * ([utterance] = true) — the message bar, a phrase or a quick-strip button.
 */
@Serializable
data class UsageEvent(
    @SerialName("t") val time: Long,
    @SerialName("x") val text: String,
    @SerialName("s") val source: UsageSource,
    @SerialName("u") val utterance: Boolean = false,
    @SerialName("b") val boardId: String? = null,
    @SerialName("i") val index: Int? = null,
) {
    val words: List<String> get() = wordsIn(text)

    companion object {
        fun wordsIn(text: String): List<String> =
            Regex("[\\p{L}\\p{N}']+").findAll(text.lowercase()).map { it.value.trim('\'') }.filter { it.isNotEmpty() }.toList()
    }
}

/** Usage numbers for a period, for parents, teachers and speech therapists. */
data class UsageReport(
    /** Words added to messages (each tap or typed word counts). */
    val totalWords: Int,
    /** Different words used. */
    val uniqueWords: Int,
    /** Messages spoken from the message bar. */
    val messages: Int,
    /** Mean length of utterance: average words per spoken message (0 if none). */
    val meanLengthOfUtterance: Double,
    /** Ready-made phrases and quick-strip buttons spoken. */
    val phrasesSpoken: Int,
    /** Words chosen on the Core page, and everywhere else (folders, keyboard, predictions). */
    val coreWords: Int,
    val fringeWords: Int,
    /** Words used for the first time ever during this period. */
    val newWords: List<String>,
    /** Most used words, most first. */
    val topWords: List<Pair<String, Int>>,
    /** Words per day for every day in the period, oldest first (day = days since 1970, local time). */
    val wordsPerDay: List<Pair<Long, Int>>,
    /** Taps per button, by (page id, button position). */
    val buttonTaps: Map<Pair<String, Int>, Int>,
    val activeDays: Int,
) {
    val corePercent: Int get() = if (coreWords + fringeWords == 0) 0 else (100.0 * coreWords / (coreWords + fringeWords)).toInt()
}

object UsageLog {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private const val DAY = 24 * 60 * 60 * 1000L

    fun encode(event: UsageEvent): String = json.encodeToString(UsageEvent.serializer(), event)

    /** Null for a damaged line, so one bad line never loses the rest of the log. */
    fun decode(line: String): UsageEvent? =
        if (line.isBlank()) null else runCatching { json.decodeFromString(UsageEvent.serializer(), line) }.getOrNull()

    fun day(time: Long, utcOffsetMillis: Long): Long = Math.floorDiv(time + utcOffsetMillis, DAY)

    /**
     * Builds the report for events from [from] (inclusive) to [to] (exclusive). Events before
     * [from] are only used to decide which words are new.
     */
    fun report(events: List<UsageEvent>, from: Long, to: Long, utcOffsetMillis: Long, topCount: Int = 20): UsageReport {
        val inPeriod = events.filter { it.time in from until to }
        val wordEvents = inPeriod.filter { !it.utterance }
        val words = wordEvents.flatMap { it.words }
        val counts = words.groupingBy { it }.eachCount()
        val messages = inPeriod.filter { it.utterance && it.source == UsageSource.MESSAGE }
        val messageLengths = messages.map { it.words.size }.filter { it > 0 }

        val usedBefore = events.filter { it.time < from && !it.utterance }.flatMap { it.words }.toSet()
        val firstUse = LinkedHashMap<String, Long>()
        wordEvents.sortedBy { it.time }.forEach { e -> e.words.forEach { w -> firstUse.putIfAbsent(w, e.time) } }

        val perDayCounts = wordEvents.groupBy { day(it.time, utcOffsetMillis) }.mapValues { (_, list) -> list.sumOf { it.words.size } }
        val firstDay = day(from, utcOffsetMillis)
        val lastDay = day(to - 1, utcOffsetMillis)
        val wordsPerDay = (firstDay..lastDay).map { it to (perDayCounts[it] ?: 0) }

        val taps = HashMap<Pair<String, Int>, Int>()
        wordEvents.forEach { e ->
            if (e.boardId != null && e.index != null) taps.merge(e.boardId to e.index, 1, Int::plus)
        }
        val core = wordEvents.filter { it.source == UsageSource.CORE }.sumOf { it.words.size }

        return UsageReport(
            totalWords = words.size,
            uniqueWords = counts.size,
            messages = messages.size,
            meanLengthOfUtterance = if (messageLengths.isEmpty()) 0.0 else messageLengths.average(),
            phrasesSpoken = inPeriod.count { it.utterance && it.source != UsageSource.MESSAGE },
            coreWords = core,
            fringeWords = words.size - core,
            newWords = firstUse.keys.filter { it !in usedBefore },
            topWords = counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(topCount).map { it.key to it.value },
            wordsPerDay = wordsPerDay,
            buttonTaps = taps,
            activeDays = inPeriod.map { day(it.time, utcOffsetMillis) }.distinct().size,
        )
    }

    /** Spreadsheet-friendly export: one row per event. Times are ISO-8601 in UTC. */
    fun toCsv(events: List<UsageEvent>): String = buildString {
        appendLine("time_utc,kind,source,text,words,page,position")
        events.forEach { e ->
            val time = java.time.Instant.ofEpochMilli(e.time).toString()
            val kind = if (e.utterance) "spoken" else "word"
            val text = "\"" + e.text.replace("\"", "\"\"") + "\""
            appendLine("$time,$kind,${e.source.name.lowercase()},$text,${e.words.size},${e.boardId.orEmpty()},${e.index?.toString().orEmpty()}")
        }
    }
}
