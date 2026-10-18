package com.robbyolivam.aac.core

import kotlinx.serialization.Serializable

/** Grammatical role of a word. Drives Fitzgerald-key colors, morphology and grammar help. */
@Serializable
enum class WordType {
    PRONOUN, PERSON, VERB, ADJECTIVE, ADVERB, NOUN, PLACE, PREPOSITION, QUESTION,
    NEGATION, SOCIAL, DETERMINER, CONJUNCTION, FOLDER, OTHER
}

/** What happens when a cell is selected. */
@Serializable
enum class CellAction {
    /** Adds the word to the message bar (and speaks it if "speak on select" is on). */
    WORD,
    /** Speaks immediately without building a message (quick phrases, emergency strip). */
    PHRASE,
    /** Opens another board. */
    FOLDER,
}

@Serializable
data class Symbol(
    val emoji: String? = null,
    /** Path relative to the app's files dir, e.g. "media/abc.jpg". */
    val imagePath: String? = null,
)

@Serializable
data class Cell(
    val id: String,
    val label: String,
    /** Text to speak / add to the message if it differs from the label. */
    val spoken: String? = null,
    val symbol: Symbol = Symbol(),
    val type: WordType = WordType.OTHER,
    val action: CellAction = CellAction.WORD,
    /** Target board for [CellAction.FOLDER]. */
    val linkTo: String? = null,
    /** Hidden cells keep their slot so the motor plan never shifts. */
    val hidden: Boolean = false,
    /** Recorded audio clip, relative to the app's files dir. */
    val audioPath: String? = null,
) {
    val text: String get() = spoken?.takeIf { it.isNotBlank() } ?: label
}

/**
 * A fixed grid of slots. A cell's index is its motor-plan position and must never change
 * unless a person moves it explicitly.
 */
@Serializable
data class Board(
    val id: String,
    val name: String,
    val rows: Int,
    val cols: Int,
    val cells: List<Cell?>,
) {
    init {
        require(cells.size == rows * cols) { "Board $id has ${cells.size} cells, expected ${rows * cols}" }
    }

    fun cellAt(row: Int, col: Int): Cell? = cells[row * cols + col]

    fun withCell(index: Int, cell: Cell?): Board =
        copy(cells = cells.toMutableList().also { it[index] = cell })
}

@Serializable
data class Vocabulary(
    val version: Int = CURRENT_VERSION,
    val homeBoardId: String,
    val phrasesBoardId: String,
    val boards: List<Board>,
    /** Always-visible strip (yes/no/stop/help…), reachable from every page. */
    val quickStrip: List<Cell>,
    /** Whole-word pronunciation overrides applied before speaking, e.g. "Siobhan" -> "shiv awn". */
    val pronunciations: Map<String, String> = emptyMap(),
) {
    fun board(id: String): Board? = boards.firstOrNull { it.id == id }

    val home: Board get() = board(homeBoardId) ?: boards.first()

    companion object {
        const val CURRENT_VERSION = 1
    }
}
