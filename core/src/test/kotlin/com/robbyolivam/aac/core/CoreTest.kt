package com.robbyolivam.aac.core

import org.junit.Test
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
