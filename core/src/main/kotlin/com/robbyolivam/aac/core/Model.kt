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
    /**
     * A bundled library symbol: "m:<id>" for Mulberry, "o:<hexcode>" for OpenMoji.
     * Shown when there's no photo; the emoji is the fallback.
     */
    val libraryId: String? = null,
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
    /**
     * Vocabulary level at which this cell appears (1 = first words … [MAX_LEVEL] = everything).
     * Lower levels hide cells in place, so the motor plan stays the same as vocabulary grows.
     */
    val level: Int = 1,
) {
    val text: String get() = spoken?.takeIf { it.isNotBlank() } ?: label

    fun visibleAt(vocabularyLevel: Int): Boolean = !hidden && level <= vocabularyLevel

    companion object {
        const val MAX_LEVEL = 4
    }
}

/** A tappable region on a photo, in fractions (0..1) of the photo's width and height. */
@Serializable
data class Hotspot(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val cell: Cell,
)

/** Visual scene display: a photo with hotspots instead of a grid. */
@Serializable
data class Scene(
    val imagePath: String,
    val hotspots: List<Hotspot> = emptyList(),
)

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
    /** When set, this board is a visual scene and [cells] is unused. */
    val scene: Scene? = null,
) {
    init {
        require(cells.size == rows * cols) { "Board $id has ${cells.size} cells, expected ${rows * cols}" }
    }

    val isScene: Boolean get() = scene != null

    fun cellAt(row: Int, col: Int): Cell? = cells[row * cols + col]

    /** Every cell with its index: grid slot for grids, hotspot index for scenes. */
    fun indexedCells(): List<Pair<Int, Cell>> =
        scene?.hotspots?.mapIndexed { i, h -> i to h.cell }
            ?: cells.mapIndexedNotNull { i, c -> c?.let { i to it } }

    fun withCell(index: Int, cell: Cell?): Board {
        val s = scene
        if (s != null) {
            val hotspots = s.hotspots.toMutableList()
            if (cell == null) hotspots.removeAt(index) else hotspots[index] = hotspots[index].copy(cell = cell)
            return copy(scene = s.copy(hotspots = hotspots))
        }
        return copy(cells = cells.toMutableList().also { it[index] = cell })
    }

    /** Number of filled cells that would fall outside a [newRows]×[newCols] grid. */
    fun cellsLostByResize(newRows: Int, newCols: Int): Int =
        cells.withIndex().count { (i, c) -> c != null && (i / cols >= newRows || i % cols >= newCols) }

    /** Changes the grid size, keeping every cell at the same row and column. */
    fun resized(newRows: Int, newCols: Int): Board {
        val next = MutableList<Cell?>(newRows * newCols) { null }
        cells.forEachIndexed { i, c ->
            val r = i / cols
            val col = i % cols
            if (c != null && r < newRows && col < newCols) next[r * newCols + col] = c
        }
        return copy(rows = newRows, cols = newCols, cells = next)
    }
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
    /** Typed shortcuts that expand when the word is finished, e.g. "hru" -> "how are you". */
    val abbreviations: Map<String, String> = emptyMap(),
) {
    fun board(id: String): Board? = boards.firstOrNull { it.id == id }

    val home: Board get() = board(homeBoardId) ?: boards.first()

    companion object {
        const val CURRENT_VERSION = 3
    }
}
