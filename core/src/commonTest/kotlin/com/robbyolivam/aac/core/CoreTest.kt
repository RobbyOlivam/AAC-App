package com.robbyolivam.aac.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultVocabularyTest {
    private val vocab = DefaultVocabulary.create()

    @Test
    fun boardsAreValid() {
        val ids = vocab.boards.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "board ids must be unique")
        for (board in vocab.boards) {
            val cellIds = board.cells.filterNotNull().map { it.id }
            assertEquals(cellIds.size, cellIds.toSet().size, "cell ids must be unique on ${board.id}")
            board.cells.filterNotNull().filter { it.action == CellAction.FOLDER }.forEach {
                assertNotNull(vocab.board(it.linkTo!!), "folder ${it.id} links to a missing board")
            }
        }
    }

    @Test
    fun coreBoardIsFullAndEveryBoardReachable() {
        assertTrue(vocab.home.cells.all { it != null })
        assertEquals(vocab.boards.map { it.id }.toSet(), vocab.shortestPaths().keys)
    }

    @Test
    fun jsonRoundTrip() {
        val decoded = VocabularyJson.decode(VocabularyJson.encode(vocab))
        assertEquals(vocab, decoded)
    }
}

class VocabularyOpsTest {
    private val vocab = DefaultVocabulary.create()

    @Test
    fun findsPathToFringeWord() {
        val pizza = assertNotNull(vocab.findWord("pizza"))
        assertEquals(2, pizza.path.size)
        assertEquals(DefaultVocabulary.HOME, pizza.path[0].boardId)
        assertEquals("food", pizza.path[1].boardId)
    }

    @Test
    fun coreWordHasSingleStep() {
        val want = assertNotNull(vocab.findWord("want"))
        assertEquals(listOf(PathStep(DefaultVocabulary.HOME, 1)), want.path)
    }

    @Test
    fun phrasesAreSearchable() {
        val result = vocab.search("bathroom")
        assertTrue(result.any { it.path.first().boardId == DefaultVocabulary.PHRASES })
    }

    @Test
    fun swapOnlyMovesTheTwoCells() {
        val swapped = vocab.swapCells(DefaultVocabulary.HOME, 0, 1)
        val before = vocab.home.cells
        val after = swapped.home.cells
        assertEquals(before[0], after[1])
        assertEquals(before[1], after[0])
        assertEquals(before.drop(2), after.drop(2))
    }

    @Test
    fun hiddenWordsAreNotFound() {
        val idx = vocab.home.cells.indexOfFirst { it?.label == "want" }
        val hidden = vocab.setCell(DefaultVocabulary.HOME, idx, vocab.home.cells[idx]!!.copy(hidden = true))
        assertNull(hidden.findWord("want"))
    }

    @Test
    fun pronunciationOverrides() {
        val v = vocab.copy(pronunciations = mapOf("Siobhan" to "shiv awn"))
        assertEquals("Hi shiv awn!", v.applyPronunciations("Hi Siobhan!"))
    }
}

class MorphologyTest {
    private val m = EnglishMorphology

    @Test
    fun verbs() {
        assertEquals(listOf("go", "goes", "going", "went", "gone", "to go"), m.forms("go", WordType.VERB))
        assertEquals("stopping", m.progressive("stop"))
        assertEquals("stopped", m.past("stop"))
        assertEquals("opening", m.progressive("open"))
        assertEquals("making", m.progressive("make"))
        assertEquals("cries", m.thirdPerson("cry"))
        assertEquals("washes", m.thirdPerson("wash"))
        assertEquals("played", m.past("play"))
        assertEquals("tried", m.past("try"))
    }

    @Test
    fun nouns() {
        assertEquals("dogs", m.plural("dog"))
        assertEquals("babies", m.plural("baby"))
        assertEquals("sandwiches", m.plural("sandwich"))
        assertEquals("children", m.plural("child"))
        assertEquals("ice creams", m.forms("ice cream", WordType.NOUN)[1])
    }

    @Test
    fun adjectives() {
        assertEquals("bigger" to "biggest", m.comparative("big"))
        assertEquals("happier" to "happiest", m.comparative("happy"))
        assertEquals("better" to "best", m.comparative("good"))
        assertEquals("more different" to "most different", m.comparative("different"))
        assertEquals("nicer" to "nicest", m.comparative("nice"))
    }

    @Test
    fun pronouns() {
        assertTrue("me" in m.forms("I", WordType.PRONOUN))
        assertTrue("their" in m.forms("they", WordType.PRONOUN))
    }
}

class PredictorTest {
    @Test
    fun predictsCommonContinuations() {
        val p = Predictor()
        assertTrue("to" in p.predictNext("want"))
        assertTrue(p.complete("pl", "want to").contains("play"))
    }

    @Test
    fun learnsFromUser() {
        val p = Predictor()
        repeat(3) { p.learn("I want minecraft") }
        assertEquals("minecraft", p.predictNext("want").first())
        val restored = Predictor(Predictor.decode(Predictor.encode(p.snapshot())))
        assertEquals("minecraft", restored.predictNext("want").first())
    }

    @Test
    fun respectsAllowedWords() {
        val p = Predictor()
        val allowed = setOf("more", "go")
        assertTrue(p.predictNext("want", allowed = allowed).all { it in allowed })
    }
}

class GrammarHelperTest {
    private fun t(vararg words: Pair<String, WordType?>) = words.map { Token(it.first, it.second) }

    @Test
    fun expandsTelegraphicMessage() {
        val tokens = t("want" to WordType.VERB, "go" to WordType.VERB, "park" to WordType.PLACE)
        assertEquals("I want to go to the park.", GrammarHelper.polish(tokens))
    }

    @Test
    fun subjectVerbAgreement() {
        val tokens = t("he" to WordType.PRONOUN, "want" to WordType.VERB, "more" to WordType.ADJECTIVE)
        assertEquals("He wants more.", GrammarHelper.polish(tokens))
    }

    @Test
    fun insertsCopula() {
        assertEquals("I am happy.", GrammarHelper.polish(t("I" to WordType.PRONOUN, "happy" to WordType.ADJECTIVE)))
        assertEquals(
            "I am not tired.",
            GrammarHelper.polish(t("I" to WordType.PRONOUN, "not" to WordType.NEGATION, "tired" to WordType.ADJECTIVE)),
        )
    }

    @Test
    fun negation() {
        val tokens = t("she" to WordType.PRONOUN, "not" to WordType.NEGATION, "like" to WordType.VERB, "it" to WordType.PRONOUN)
        assertEquals("She doesn't like it.", GrammarHelper.polish(tokens))
    }

    @Test
    fun questions() {
        assertEquals("Where is mom?", GrammarHelper.polish(t("where" to WordType.QUESTION, "mom" to WordType.PERSON)))
    }

    @Test
    fun goHomeHasNoArticle() {
        assertEquals("I want to go home.", GrammarHelper.polish(t("want" to WordType.VERB, "go" to WordType.VERB, "home" to WordType.PLACE)))
    }

    @Test
    fun nothingToPolish() {
        assertNull(GrammarHelper.polish(t("hi" to WordType.SOCIAL)))
        assertNull(GrammarHelper.polish(t("I" to WordType.PRONOUN, "like" to WordType.VERB, "it" to WordType.PRONOUN)))
    }
}

class LevelsAndResizeTest {
    private val vocab = DefaultVocabulary.create()

    @Test
    fun levelsHideWithoutMoving() {
        val home = vocab.home
        val level1 = home.cells.count { it?.visibleAt(1) == true }
        assertEquals(12, level1)
        assertTrue(home.cells.count { it?.visibleAt(2) == true } > level1)
        assertEquals(56, home.cells.count { it?.visibleAt(Cell.MAX_LEVEL) == true })
        // Words hidden at a level are not found by search at that level.
        assertNull(vocab.findWord("who", level = 1))
        assertNotNull(vocab.findWord("who"))
        assertNull(vocab.findWord("pizza", level = 1), "Food folder is level 2")
        assertNotNull(vocab.findWord("pizza", level = 2))
    }

    @Test
    fun migrationAddsLevels() {
        val old = vocab.copy(version = 1, boards = vocab.boards.map { b -> b.copy(cells = b.cells.map { it?.copy(level = 1) }) })
        val migrated = DefaultVocabulary.migrate(old)
        assertEquals(Vocabulary.CURRENT_VERSION, migrated.version)
        assertEquals(vocab.home, migrated.home)
    }

    @Test
    fun migrationAddsSeventhColumnWithoutMovingAnything() {
        val newIds = setOf("body", "school", "animals", "activities", "little-words", "more-actions", "health", "weather")
        val oldHome = vocab.home.resized(8, 6)
        val old = vocab.copy(version = 2, boards = vocab.boards.filter { it.id !in newIds }.map { if (it.id == vocab.homeBoardId) oldHome else it })
        val migrated = DefaultVocabulary.migrate(old)
        assertEquals(7, migrated.home.cols)
        for (r in 0 until 8) for (c in 0 until 6) assertEquals(oldHome.cellAt(r, c), migrated.home.cellAt(r, c))
        assertTrue(newIds.all { migrated.board(it) != null })
        assertNotNull(migrated.findWord("elephant"))
        assertEquals(vocab.home, migrated.home)
    }

    @Test
    fun vocabularyIsLarge() {
        val words = vocab.allWordCells().map { it.text.lowercase() }.toSet()
        assertTrue(words.size > 600, "only ${words.size} distinct words")
    }

    @Test
    fun resizeKeepsRowAndColumn() {
        val board = vocab.home
        val bigger = board.resized(10, 8)
        assertEquals(board.cellAt(3, 2), bigger.cellAt(3, 2))
        assertEquals(board.cellAt(7, 5), bigger.cellAt(7, 5))
        assertEquals(0, board.cellsLostByResize(10, 8))
        assertEquals(board.cols * 2, board.cellsLostByResize(6, board.cols))
        assertEquals(board.cellAt(1, 1), board.resized(4, 3).cellAt(1, 1))
    }

    @Test
    fun sceneHotspotsAreSearchable() {
        val cell = Cell("seesaw", "seesaw", type = WordType.NOUN)
        val v = vocab.addBoard(newSceneBoard("park-scene", "Park", "media/park.jpg"))
            .addHotspot("park-scene", Hotspot(0.1f, 0.2f, 0.3f, 0.3f, cell))
            .setCell(DefaultVocabulary.HOME, 0, Cell("f", "Park", type = WordType.FOLDER, action = CellAction.FOLDER, linkTo = "park-scene"))
        val found = assertNotNull(v.findWord("seesaw"))
        assertEquals(PathStep("park-scene", 0), found.target)
        val removed = v.setCell("park-scene", 0, null)
        assertTrue(removed.board("park-scene")!!.scene!!.hotspots.isEmpty())
    }
}

class OpenBoardFormatTest {
    @Test
    fun roundTripKeepsBoardsPositionsAndLinks() {
        val vocab = DefaultVocabulary.create()
        val files = OpenBoardFormat.export(vocab) { null }
        assertTrue("manifest.json" in files)
        val imported = OpenBoardFormat.import(files, fallbackStrip = emptyList()).vocabulary
        assertEquals(vocab.homeBoardId, imported.homeBoardId)
        assertEquals(vocab.phrasesBoardId, imported.phrasesBoardId)
        assertEquals(vocab.quickStrip.map { it.label to it.text }, imported.quickStrip.map { it.label to it.text })
        for (board in vocab.boards) {
            val other = assertNotNull(imported.board(board.id))
            assertEquals(board.rows to board.cols, other.rows to other.cols)
            board.cells.zip(other.cells).forEach { (a, b) ->
                assertEquals(a?.label, b?.label)
                assertEquals(a?.linkTo, b?.linkTo)
                assertEquals(a?.type, b?.type)
                assertEquals(a?.symbol?.emoji, b?.symbol?.emoji)
                assertEquals(a?.symbol?.libraryId, b?.symbol?.libraryId)
                assertEquals(a?.level, b?.level)
                assertEquals(a?.action, b?.action)
            }
        }
    }

    @Test
    fun importsForeignBoardWithEmbeddedImage() {
        val png = byteArrayOf(1, 2, 3)
        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class) val b64 = kotlin.io.encoding.Base64.Default.encode(png)
        val home = """
            {"format":"open-board-0.1","id":"1_2","name":"Home",
             "buttons":[
               {"id":"1","label":"want","background_color":"rgb(0, 255, 0)"},
               {"id":"2","label":"Animals","load_board":{"path":"boards/animals.obf"}},
               {"id":"3","label":"happy","image_id":"i1","background_color":"rgb(0,0,255)"},
               {"id":"4","label":"web","image_id":"i2"}],
             "images":[{"id":"i1","data":"data:image/png;base64,$b64","content_type":"image/png"},
                       {"id":"i2","url":"https://example.com/x.png"}],
             "grid":{"rows":2,"columns":2,"order":[["1","2"],["3",null]]}}
        """.trimIndent()
        val animals = """
            {"format":"open-board-0.1","id":"animals","name":"Animals",
             "buttons":[{"id":"a","label":"dog","vocalization":"I see a dog"}],
             "grid":{"rows":1,"columns":3,"order":[[null,"a",null]]}}
        """.trimIndent()
        val manifest = """{"format":"open-board-0.1","root":"boards/home.obf","paths":{"boards":{"1_2":"boards/home.obf","animals":"boards/animals.obf"}}}"""
        val result = OpenBoardFormat.import(
            mapOf(
                "manifest.json" to manifest.encodeToByteArray(),
                "boards/home.obf" to home.encodeToByteArray(),
                "boards/animals.obf" to animals.encodeToByteArray(),
            ),
            fallbackStrip = DefaultVocabulary.create().quickStrip,
        )
        val v = result.vocabulary
        val homeBoard = v.home
        assertEquals("Home", homeBoard.name)
        assertEquals(WordType.VERB, homeBoard.cells[0]!!.type)
        assertEquals(CellAction.FOLDER, homeBoard.cells[1]!!.action)
        assertEquals("animals", homeBoard.cells[1]!!.linkTo)
        assertEquals(WordType.ADJECTIVE, homeBoard.cells[2]!!.type)
        val imagePath = assertNotNull(homeBoard.cells[2]!!.symbol.imagePath)
        assertTrue(png.contentEquals(result.media.getValue(imagePath)))
        assertNull(homeBoard.cells[3])
        assertEquals(1, result.skippedImages)
        val dog = v.board("animals")!!.cells[1]!!
        assertEquals("I see a dog", dog.text)
        assertNotNull(v.board(v.phrasesBoardId))
        assertNotNull(v.findWord("dog"))
    }
}

class WavTest {
    private fun wav(sampleRate: Int, samples: ShortArray, extraChunk: Boolean = false): ByteArray {
        val out = mutableListOf<Byte>()
        fun bytes(b: ByteArray) { out += b.toList() }
        fun int16(v: Int) { out += (v and 0xFF).toByte(); out += ((v shr 8) and 0xFF).toByte() }
        fun int32(v: Int) { int16(v and 0xFFFF); int16((v shr 16) and 0xFFFF) }
        bytes("RIFF".encodeToByteArray()); int32(0); bytes("WAVE".encodeToByteArray())
        bytes("fmt ".encodeToByteArray()); int32(16); int16(1); int16(1); int32(sampleRate); int32(sampleRate * 2); int16(2); int16(16)
        if (extraChunk) { bytes("LIST".encodeToByteArray()); int32(3); bytes(byteArrayOf(1, 2, 3, 0)) }
        bytes("data".encodeToByteArray()); int32(samples.size * 2)
        samples.forEach { int16(it.toInt()) }
        return out.toByteArray()
    }

    @Test
    fun parsesTtsOutput() {
        val pcm = assertNotNull(Wav.parse(wav(22050, shortArrayOf(1, -2, 300), extraChunk = true)))
        assertEquals(22050, pcm.sampleRate)
        assertEquals(1, pcm.channels)
        assertEquals(3, pcm.frames)
        assertEquals(-2, (pcm.data[2].toInt() and 0xFF) or (pcm.data[3].toInt() shl 8))
    }

    @Test
    fun rejectsNonWav() {
        assertNull(Wav.parse("hello world, not audio".encodeToByteArray()))
        assertNull(Wav.parse(ByteArray(0)))
    }

    @Test
    fun voiceAgeKeepsSpeedAndRaisesPitch() {
        for (age in VoiceAge.entries) {
            // Playback runs `formant` times faster, so the result must match what was asked for.
            assertEquals(1.0f, age.engineRate(1.0f) * age.formant, 1e-4f)
            assertEquals(age.pitch, age.enginePitch(1.0f) * age.formant, 1e-4f)
        }
        assertTrue(VoiceAge.KID.pitch > VoiceAge.TEEN.pitch && VoiceAge.TEEN.pitch > VoiceAge.ADULT.pitch)
    }
}

class BatchATest {
    @Test
    fun abbreviationsExpandIgnoringCase() {
        val v = DefaultVocabulary.create().copy(abbreviations = mapOf("hru" to "how are you"))
        assertEquals("how are you", v.expandAbbreviation("HRU"))
        assertNull(v.expandAbbreviation("hello"))
        assertNull(v.expandAbbreviation(" "))
    }

    @Test
    fun duplicatePageCopiesButtonsWithNewIds() {
        val v = DefaultVocabulary.create()
        var n = 0
        val copy = v.duplicateBoard(v.homeBoardId, "home-copy", "Core copy") { "new-${n++}" }
        val original = copy.board(v.homeBoardId)!!
        val dup = copy.board("home-copy")!!
        assertEquals("Core copy", dup.name)
        assertEquals(original.rows to original.cols, dup.rows to dup.cols)
        // Same words in the same places, different ids.
        assertEquals(original.cells.map { it?.label }, dup.cells.map { it?.label })
        assertEquals(original.cells.map { it?.linkTo }, dup.cells.map { it?.linkTo })
        val ids = original.cells.mapNotNull { it?.id }.toSet()
        assertTrue(dup.cells.filterNotNull().none { it.id in ids })
        assertEquals(v, copy.copy(boards = copy.boards.filter { it.id != "home-copy" }))
    }
}

class UsageTest {
    private val day = 24 * 60 * 60 * 1000L
    private fun word(t: Long, w: String, s: UsageSource = UsageSource.CORE, i: Int? = null) =
        UsageEvent(t, w, s, boardId = if (i != null) "home" else null, index = i)

    @Test
    fun encodeDecodeRoundTripsAndSkipsDamagedLines() {
        val e = UsageEvent(123, "I want \"more\"", UsageSource.MESSAGE, utterance = true)
        assertEquals(e, UsageLog.decode(UsageLog.encode(e)))
        assertNull(UsageLog.decode("{not json"))
        assertNull(UsageLog.decode(""))
    }

    @Test
    fun reportCountsWordsMessagesAndCoreShare() {
        val start = 10 * day
        val events = listOf(
            word(start - day, "want"), // before the period: "want" isn't new
            word(start + 1, "I", i = 0),
            word(start + 2, "want", i = 1),
            word(start + 3, "pizza", UsageSource.FOLDER),
            UsageEvent(start + 4, "I want pizza", UsageSource.MESSAGE, utterance = true),
            word(start + day + 5, "want", i = 1),
            word(start + day + 6, "go", UsageSource.KEYBOARD),
            UsageEvent(start + day + 7, "want go", UsageSource.MESSAGE, utterance = true),
            UsageEvent(start + day + 8, "Help", UsageSource.STRIP, utterance = true),
        )
        val r = UsageLog.report(events, from = start, to = start + 7 * day, utcOffsetMillis = 0)
        assertEquals(5, r.totalWords)
        assertEquals(4, r.uniqueWords)
        assertEquals(2, r.messages)
        assertEquals(2.5, r.meanLengthOfUtterance, 1e-9)
        assertEquals(1, r.phrasesSpoken)
        assertEquals(3, r.coreWords)
        assertEquals(2, r.fringeWords)
        assertEquals(60, r.corePercent)
        assertEquals(listOf("i", "pizza", "go"), r.newWords)
        assertEquals("want" to 2, r.topWords.first())
        assertEquals(7, r.wordsPerDay.size)
        assertEquals(listOf(3, 2, 0, 0, 0, 0, 0), r.wordsPerDay.map { it.second })
        assertEquals(2, r.buttonTaps["home" to 1])
        assertEquals(2, r.activeDays)
    }

    @Test
    fun daysFollowLocalTime() {
        // 23:30 UTC is the next day at UTC+1.
        val t = 5 * day - 30 * 60 * 1000L
        assertEquals(4, UsageLog.day(t, 0))
        assertEquals(5, UsageLog.day(t, 60 * 60 * 1000L))
    }

    @Test
    fun isoTimesMatchTheCalendar() {
        assertEquals("1970-01-01T00:00:00Z", UsageLog.isoUtc(0))
        assertEquals("2000-02-29T23:59:59Z", UsageLog.isoUtc(951868799000))
        assertEquals("2026-11-02T12:33:05Z", UsageLog.isoUtc(1793622785000))
        assertEquals("1969-12-31T23:59:59Z", UsageLog.isoUtc(-1000))
    }

    @Test
    fun csvQuotesText() {
        val csv = UsageLog.toCsv(listOf(UsageEvent(0, "say \"hi\", ok", UsageSource.MESSAGE, utterance = true)))
        assertTrue(csv.lines()[1].contains("\"say \"\"hi\"\", ok\""))
        assertTrue(csv.startsWith("time_utc,kind,source,text"))
    }
}

class ToneTest {
    @Test
    fun toneSetsTheEnding() {
        assertEquals("you want juice?", Tone.QUESTION.apply("you want juice."))
        assertEquals("I did it!", Tone.EXCITED.apply(" I did it "))
        assertEquals("", Tone.LOUD.apply("  "))
    }

    @Test
    fun loudIsFullVolumeAndWhisperIsQuiet() {
        assertEquals(1f, Tone.LOUD.volume(0.3f))
        assertTrue(Tone.WHISPER.volume(1f) < 0.5f)
        assertEquals(0.8f, Tone.QUESTION.volume(0.8f))
    }
}

class WordWizardTest {
    private val v = DefaultVocabulary.create()

    @Test
    fun guessesTypeFromSymbolHints() {
        assertEquals(WordType.VERB, WordWizard.guessType("bake", "Verb", "cooking Kitchen"))
        assertEquals(WordType.NOUN, WordWizard.guessType("banana", "Noun", "fruit Food Fruit"))
        assertEquals(WordType.ADJECTIVE, WordWizard.guessType("shiny", "Noun", "Descriptive State"))
        assertEquals(WordType.PERSON, WordWizard.guessType("nurse", "Noun", "job Profession Healthcare"))
        assertEquals(WordType.ADVERB, WordWizard.guessType("quickly", null, null))
    }

    @Test
    fun plansFolderAndEmptySlot() {
        val banana = WordWizard.plan("banana", "Noun", "fruit Food Fruit", v)
        assertEquals("food", banana.boardId)
        val food = v.board("food")!!
        assertEquals(food.cells.indexOfFirst { it == null }.takeIf { it >= 0 }, banana.index)
        assertEquals("actions", WordWizard.plan("bake", "Verb", "cooking", v).boardId)
        assertEquals("things", WordWizard.plan("gizmo", null, null, v).boardId)
        assertEquals("health", WordWizard.plan("plaster", "Noun", "Healthcare First aid", v).boardId)
    }

    @Test
    fun fullFolderFallsBackToOneWithRoom() {
        var n = 0
        fun fill(b: Board) = b.copy(cells = b.cells.map { it ?: Cell("x-${n++}", "x") })
        val bothFull = v.updateBoard("actions", ::fill).updateBoard("more-actions", ::fill)
        // Nowhere free: still the right folder, but no spot, so the wizard asks.
        assertEquals("actions", WordWizard.plan("bake", "Verb", null, bothFull).boardId)
        assertNull(WordWizard.plan("bake", "Verb", null, bothFull).index)
        val roomInMore = bothFull.setCell("more-actions", 5, null)
        val plan = WordWizard.plan("bake", "Verb", null, roomInMore)
        assertEquals("more-actions", plan.boardId)
        assertEquals(5, plan.index)
    }
}


class SymbolContextTest {
    @Test
    fun tabletIsAComputerNotPills() {
        val tablet = DefaultVocabulary.create().board("things")!!.cells.filterNotNull().single { it.label == "tablet" }
        assertEquals("c:tablet", tablet.symbol.libraryId)
    }

    @Test
    fun upgradeFixesUnchangedPicturesOnly() {
        val fresh = DefaultVocabulary.create()
        fun Vocabulary.withSymbol(board: String, label: String, id: String) = updateBoard(board) { b ->
            b.copy(cells = b.cells.map { c -> if (c?.label == label) c.copy(symbol = c.symbol.copy(libraryId = id)) else c })
        }
        val old = fresh.withSymbol("things", "tablet", "m:2768")      // the old pills picture
            .withSymbol("body", "nails", "m:1111")                      // a person chose their own
            .copy(version = 3)
        val migrated = DefaultVocabulary.migrate(old)
        fun Vocabulary.symbolOf(board: String, label: String) = board(board)!!.cells.filterNotNull().single { it.label == label }.symbol.libraryId
        assertEquals("c:tablet", migrated.symbolOf("things", "tablet"))
        assertEquals("m:1111", migrated.symbolOf("body", "nails"))
        assertEquals(Vocabulary.CURRENT_VERSION, migrated.version)
    }
}

class SymbolIndexTest {
    private val index = SymbolIndex.parse(
        listOf(
            "m\t1\ttablet\tNoun\tclinic health Healthcare Medical items",
            "c\ttablet\ttablet\tNoun\tipad device Electrical Computer",
            "o\t1F4F1\tmobile phone\t\tphone cell",
            "o\t2764-FE0F\tred heart\t\tlove",
            "o\t1F44D\tthumbs up\t\tyes",
            "bad line",
        ).joinToString("\n"),
    )

    @Test
    fun parsesAndSkipsBadLines() {
        assertEquals(5, index.symbols.size)
        assertTrue(index.contains("c:tablet"))
    }

    @Test
    fun searchPrefersTheFoldersTopic() {
        assertEquals("c:tablet", index.search("tablet", context = listOf("computer"))[0].libraryId)
        assertEquals("m:1", index.search("tablet", context = listOf("medical"))[0].libraryId)
        // With no folder topic, Mulberry/custom symbols come before OpenMoji.
        assertTrue(index.search("phone").all { it.set == "o" })
    }

    @Test
    fun emojiMapToOpenMoji() {
        assertEquals("o:1F44D", index.forEmoji("👍"))
        assertEquals("o:2764-FE0F", index.forEmoji("❤️"))
        assertNull(index.forEmoji("🦄"))
        assertEquals(listOf(0x1F44D), SymbolIndex.codePoints("👍"))
    }
}

class WordsTest {
    @Test
    fun splitsLettersDigitsAndApostrophes() {
        assertEquals(listOf("I'm", "café", "2", "niño"), wordTokens("I'm at the café, 2 niño!").filter { it != "at" && it != "the" })
        assertEquals(listOf("hi", "you"), UsageEvent.wordsIn("  'Hi'... you?"))
    }
}
