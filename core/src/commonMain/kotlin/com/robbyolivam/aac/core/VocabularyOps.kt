package com.robbyolivam.aac.core

import kotlinx.serialization.json.Json

/** One step on the way to a word: on [boardId], select the cell at [cellIndex]. */
data class PathStep(val boardId: String, val cellIndex: Int)

data class WordLocation(val cell: Cell, val path: List<PathStep>) {
    val target: PathStep get() = path.last()
}

object VocabularyJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        prettyPrint = false
    }

    fun encode(vocabulary: Vocabulary): String = json.encodeToString(Vocabulary.serializer(), vocabulary)

    fun decode(text: String): Vocabulary = json.decodeFromString(Vocabulary.serializer(), text)
}

fun Vocabulary.updateBoard(boardId: String, transform: (Board) -> Board): Vocabulary =
    copy(boards = boards.map { if (it.id == boardId) transform(it) else it })

fun Vocabulary.setCell(boardId: String, index: Int, cell: Cell?): Vocabulary =
    updateBoard(boardId) { it.withCell(index, cell) }

/** Swaps two slots on the same board. The only way positions change is through an explicit move. */
fun Vocabulary.swapCells(boardId: String, from: Int, to: Int): Vocabulary =
    updateBoard(boardId) { board ->
        val cells = board.cells.toMutableList()
        val tmp = cells[from]
        cells[from] = cells[to]
        cells[to] = tmp
        board.copy(cells = cells)
    }

fun Vocabulary.addBoard(board: Board): Vocabulary = copy(boards = boards + board)

fun Vocabulary.updateQuickStrip(index: Int, cell: Cell): Vocabulary =
    copy(quickStrip = quickStrip.toMutableList().also { it[index] = cell })

/** Boards whose paths start from a dedicated navigation button rather than from a folder. */
val Vocabulary.rootBoardIds: List<String> get() = listOf(homeBoardId, phrasesBoardId)

/**
 * Finds every visible word cell matching [query] (case-insensitive prefix on any word of the
 * label or spoken text) with the shortest folder path from a root board.
 */
fun Vocabulary.search(query: String, limit: Int = 30, level: Int = Cell.MAX_LEVEL): List<WordLocation> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    val paths = shortestPaths(level)
    val results = mutableListOf<Pair<Int, WordLocation>>()
    for (board in boards) {
        val prefix = paths[board.id] ?: continue
        board.indexedCells().forEach { (index, cell) ->
            if (!cell.visibleAt(level) || cell.action == CellAction.FOLDER) return@forEach
            val label = cell.label.lowercase()
            val spoken = cell.text.lowercase()
            val rank = when {
                label == q || spoken == q -> 0
                label.startsWith(q) || spoken.startsWith(q) -> 1
                label.split(' ').any { it.startsWith(q) } || spoken.split(' ').any { it.startsWith(q) } -> 2
                else -> return@forEach
            }
            results += (rank * 100 + prefix.size) to WordLocation(cell, prefix + PathStep(board.id, index))
        }
    }
    return results.sortedBy { it.first }.map { it.second }.distinctBy { it.cell.text.lowercase() to it.path }.take(limit)
}

/** Breadth-first search from the root boards; value is the folder steps needed to reach each board. */
fun Vocabulary.shortestPaths(level: Int = Cell.MAX_LEVEL): Map<String, List<PathStep>> {
    val paths = LinkedHashMap<String, List<PathStep>>()
    val queue = ArrayDeque<String>()
    for (root in rootBoardIds) {
        if (board(root) != null && root !in paths) {
            paths[root] = emptyList()
            queue += root
        }
    }
    while (queue.isNotEmpty()) {
        val board = board(queue.removeFirst()) ?: continue
        board.indexedCells().forEach { (index, cell) ->
            val target = cell.takeIf { it.action == CellAction.FOLDER && it.visibleAt(level) }?.linkTo ?: return@forEach
            if (target !in paths && board(target) != null) {
                paths[target] = paths.getValue(board.id) + PathStep(board.id, index)
                queue += target
            }
        }
    }
    return paths
}

/** Finds a cell by its label or spoken text (case-insensitive), preferring the shortest path. */
fun Vocabulary.findWord(word: String, level: Int = Cell.MAX_LEVEL): WordLocation? {
    val w = word.trim().lowercase()
    return search(w, limit = 50, level = level).firstOrNull { it.cell.label.lowercase() == w || it.cell.text.lowercase() == w }
}

/** Every visible word cell, for prediction and lookup. */
fun Vocabulary.allWordCells(level: Int = Cell.MAX_LEVEL): List<Cell> =
    boards.flatMap { b -> b.indexedCells().map { it.second } }.filter { it.action != CellAction.FOLDER && it.visibleAt(level) }

/** Applies whole-word pronunciation overrides before text goes to the speech engine. */
fun Vocabulary.applyPronunciations(text: String): String {
    if (pronunciations.isEmpty()) return text
    val lookup = pronunciations.mapKeys { it.key.lowercase() }
    return Regex("[\\p{L}\\p{N}']+").replace(text) { m -> lookup[m.value.lowercase()] ?: m.value }
}

/** The expansion for a typed shortcut (case-insensitive), or null if [word] isn't one. */
fun Vocabulary.expandAbbreviation(word: String): String? {
    val key = word.trim().lowercase()
    if (key.isEmpty()) return null
    return abbreviations.entries.firstOrNull { it.key.lowercase() == key }?.value
}

/**
 * Copies a page (grid or photo scene) under [newId]. Buttons get fresh ids from [newCellId];
 * folder buttons still open the same pages as the original.
 */
fun Vocabulary.duplicateBoard(boardId: String, newId: String, name: String, newCellId: () -> String): Vocabulary {
    val original = board(boardId) ?: return this
    val copy = original.copy(
        id = newId,
        name = name,
        cells = original.cells.map { it?.copy(id = newCellId()) },
        scene = original.scene?.let { s -> s.copy(hotspots = s.hotspots.map { h -> h.copy(cell = h.cell.copy(id = newCellId())) }) },
    )
    return addBoard(copy)
}

fun Vocabulary.resizeBoard(boardId: String, rows: Int, cols: Int): Vocabulary =
    updateBoard(boardId) { it.resized(rows, cols) }

fun Vocabulary.addHotspot(boardId: String, hotspot: Hotspot): Vocabulary =
    updateBoard(boardId) { b -> b.scene?.let { s -> b.copy(scene = s.copy(hotspots = s.hotspots + hotspot)) } ?: b }

fun Vocabulary.updateHotspot(boardId: String, index: Int, transform: (Hotspot) -> Hotspot): Vocabulary =
    updateBoard(boardId) { b ->
        b.scene?.let { s -> b.copy(scene = s.copy(hotspots = s.hotspots.toMutableList().also { it[index] = transform(it[index]) })) } ?: b
    }

fun newSceneBoard(id: String, name: String, imagePath: String): Board =
    Board(id = id, name = name, rows = 0, cols = 0, cells = emptyList(), scene = Scene(imagePath))

fun newBoard(id: String, name: String, rows: Int, cols: Int): Board =
    Board(id = id, name = name, rows = rows, cols = cols, cells = List(rows * cols) { null })
