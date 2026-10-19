package com.robbyolivam.aac.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.robbyolivam.aac.AacApplication
import com.robbyolivam.aac.core.Board
import com.robbyolivam.aac.core.Cell
import com.robbyolivam.aac.core.CellAction
import com.robbyolivam.aac.core.EnglishMorphology
import com.robbyolivam.aac.core.GrammarHelper
import com.robbyolivam.aac.core.Hotspot
import com.robbyolivam.aac.core.addHotspot
import com.robbyolivam.aac.core.newSceneBoard
import com.robbyolivam.aac.core.resizeBoard
import com.robbyolivam.aac.core.updateHotspot
import com.robbyolivam.aac.core.Symbol
import com.robbyolivam.aac.core.Token
import com.robbyolivam.aac.core.Vocabulary
import com.robbyolivam.aac.core.WordLocation
import com.robbyolivam.aac.core.WordType
import com.robbyolivam.aac.core.addBoard
import com.robbyolivam.aac.core.allWordCells
import com.robbyolivam.aac.core.applyPronunciations
import com.robbyolivam.aac.core.newBoard
import com.robbyolivam.aac.core.search
import com.robbyolivam.aac.core.setCell
import com.robbyolivam.aac.core.updateBoard
import com.robbyolivam.aac.core.updateQuickStrip
import com.robbyolivam.aac.data.Settings
import java.util.UUID

/** A word in the message bar, with the picture it came from. */
data class MessageToken(val text: String, val type: WordType?, val symbol: Symbol?, val id: Long = nextTokenId++)

private var nextTokenId = 0L

enum class Panel { BOARD, KEYBOARD }

enum class Screen { MAIN, SETTINGS }

sealed interface EditTarget {
    data class Grid(val boardId: String, val index: Int) : EditTarget
    data class Strip(val index: Int) : EditTarget
    /** A hotspot that doesn't exist yet, centered where the person tapped on a scene photo. */
    data class NewHotspot(val boardId: String, val x: Float, val y: Float) : EditTarget
}

/** The cell a person picked up in edit mode and is about to drop somewhere else. */
data class MoveSource(val boardId: String, val index: Int, val cell: Cell)

class AacViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AacApplication
    val store = app.vocabulary
    val settingsStore = app.settings
    val media = app.media
    val speaker = app.speaker
    val recorder = app.recorder
    val backup = app.backup
    val openBoard = app.openBoard
    val pdf = app.pdf

    val vocabulary get() = store.vocabulary
    val settings get() = settingsStore.settings
    private val s: Settings get() = settingsStore.settings.value
    private val v: Vocabulary get() = store.vocabulary.value
    private val level: Int get() = s.vocabularyLevel

    val scanner = Scanner(viewModelScope) { speak(it) }

    init {
        viewModelScope.launch { settingsStore.settings.collect { scanner.configure(it) } }
    }

    var currentBoardId by mutableStateOf(v.homeBoardId)
        private set
    private val backStack = ArrayDeque<String>()

    val message = mutableStateListOf<MessageToken>()
    var typing by mutableStateOf("")
        private set

    var panel by mutableStateOf(Panel.BOARD)
    var screen by mutableStateOf(Screen.MAIN)

    var editMode by mutableStateOf(false)
        private set
    var editing by mutableStateOf<EditTarget?>(null)
    var moveSource by mutableStateOf<MoveSource?>(null)
        private set

    var formsMode by mutableStateOf(false)
    var formsFor by mutableStateOf<Cell?>(null)

    var guide by mutableStateOf<WordLocation?>(null)
        private set
    var showPartner by mutableStateOf(false)
    var searchOpen by mutableStateOf(false)

    private var lastActivation = 0L

    val currentBoard: Board get() = v.board(currentBoardId) ?: v.home

    // ---- Lookups ---------------------------------------------------------------------------

    private var indexedVocabulary: Vocabulary? = null
    private var indexedLevel = -1
    private var wordIndex: Map<String, Cell> = emptyMap()

    /** Lower-case word → its button, for symbols in predictions and types for grammar help. */
    fun wordIndex(): Map<String, Cell> {
        val current = v
        if (indexedVocabulary !== current || indexedLevel != level) {
            wordIndex = current.allWordCells(level).filter { it.action == CellAction.WORD }
                .reversed().associateBy { it.text.lowercase() }
            indexedVocabulary = current
            indexedLevel = level
        }
        return wordIndex
    }

    fun search(query: String): List<WordLocation> = v.search(query, level = level)

    /** Name of the board the in-app Back button returns to. */
    fun backTargetName(): String =
        if (panel == Panel.KEYBOARD) currentBoard.name
        else backStack.lastOrNull()?.let { v.board(it)?.name } ?: v.home.name

    private fun cellAt(boardId: String, index: Int): Cell? {
        val board = v.board(boardId) ?: return null
        return board.scene?.hotspots?.getOrNull(index)?.cell ?: board.cells.getOrNull(index)
    }

    // ---- Selection -------------------------------------------------------------------------

    private fun debounced(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (s.debounceMs > 0 && now - lastActivation < s.debounceMs) return true
        lastActivation = now
        return false
    }

    fun onCellSelected(boardId: String, index: Int, cell: Cell?) {
        if (editMode) {
            onEditTap(boardId, index, cell)
            return
        }
        if (cell == null || !cell.visibleAt(level) || debounced()) return
        advanceGuide(boardId, index)
        if (formsMode && cell.action == CellAction.WORD && EnglishMorphology.hasForms(cell.text, cell.type)) {
            formsMode = false
            formsFor = cell
            return
        }
        activate(cell, boardId)
    }

    fun onStripSelected(index: Int, cell: Cell) {
        if (editMode) {
            editing = EditTarget.Strip(index)
            return
        }
        if (debounced()) return
        speakCell(cell)
    }

    fun onCellLongPress(cell: Cell?) {
        if (editMode || cell == null || !cell.visibleAt(level) || cell.action != CellAction.WORD) return
        if (EnglishMorphology.hasForms(cell.text, cell.type)) formsFor = cell
    }

    private fun activate(cell: Cell, boardId: String) {
        when (cell.action) {
            CellAction.FOLDER -> cell.linkTo?.let { navigate(it) }
            CellAction.PHRASE -> speakCell(cell)
            CellAction.WORD -> {
                addToken(MessageToken(cell.text, cell.type, cell.symbol))
                if (s.speakOnSelect) speakCell(cell)
                afterWord(boardId)
            }
        }
    }

    fun selectForm(cell: Cell, form: String) {
        formsFor = null
        addToken(MessageToken(form, cell.type, cell.symbol))
        if (s.speakOnSelect) speak(form)
        afterWord(currentBoardId)
    }

    private fun afterWord(boardId: String) {
        val v = v
        val isScene = v.board(boardId)?.isScene == true
        if (s.returnHomeAfterFolderWord && !isScene && boardId != v.homeBoardId && boardId != v.phrasesBoardId) goHome()
    }

    private fun addToken(token: MessageToken) {
        commitTyping()
        message.add(token)
    }

    // ---- Navigation ------------------------------------------------------------------------

    fun navigate(boardId: String) {
        if (boardId == currentBoardId) return
        backStack.addLast(currentBoardId)
        if (backStack.size > 30) backStack.removeFirst()
        currentBoardId = boardId
        panel = Panel.BOARD
    }

    fun goHome() {
        backStack.clear()
        currentBoardId = v.homeBoardId
        panel = Panel.BOARD
    }

    fun goBack() {
        if (panel == Panel.KEYBOARD) {
            panel = Panel.BOARD
            return
        }
        currentBoardId = backStack.removeLastOrNull() ?: v.homeBoardId
    }

    fun openPhrases() = navigate(v.phrasesBoardId)

    // ---- "Where is it?" guide -------------------------------------------------------------

    fun startGuide(location: WordLocation) {
        guide = location
        searchOpen = false
        panel = Panel.BOARD
        goHome()
        if (location.path.first().boardId != v.homeBoardId) navigate(location.path.first().boardId)
    }

    fun cancelGuide() {
        guide = null
    }

    /** Index to highlight on [boardId] while guiding, if any. */
    fun guideIndex(boardId: String): Int? = guide?.path?.firstOrNull { it.boardId == boardId }?.cellIndex

    private fun advanceGuide(boardId: String, index: Int) {
        val g = guide ?: return
        if (g.target.boardId == boardId && g.target.cellIndex == index) guide = null
    }

    // ---- Message bar -----------------------------------------------------------------------

    fun messageText(): String =
        (message.map { it.text } + listOfNotNull(typing.takeIf { it.isNotBlank() })).joinToString(" ")

    fun speakMessage() {
        val text = messageText()
        if (text.isBlank()) return
        speak(text)
        store.predictor.learn(text)
        store.savePredictor()
    }

    fun speakCell(cell: Cell) {
        val audio = cell.audioPath
        if (audio != null) speaker.playFile(store.resolve(audio)) else speak(cell.text)
    }

    fun speak(text: String) {
        val s = s
        speaker.speak(v.applyPronunciations(text), s.speechRate, s.pitch, s.voiceName)
    }

    fun backspace() {
        if (typing.isNotEmpty()) typing = typing.dropLast(1) else message.removeLastOrNull()
    }

    fun clearMessage() {
        message.clear()
        typing = ""
        speaker.stop()
    }

    fun removeToken(token: MessageToken) {
        message.remove(token)
    }

    private fun previousWord(): String? = message.lastOrNull()?.text

    /** Next-word predictions for the symbol board, limited to words that have a button. */
    fun boardPredictions(limit: Int = 8): List<Cell> {
        val index = wordIndex()
        return store.predictor.predictNext(previousWord(), limit, allowed = index.keys).mapNotNull { index[it] }
    }

    fun keyboardPredictions(limit: Int = 6): List<String> =
        store.predictor.complete(typing, previousWord(), limit)

    fun polishSuggestion(): String? {
        if (!s.grammarHelper || message.size < 2) return null
        return GrammarHelper.polish(message.map { Token(it.text, it.type) })
    }

    fun acceptSuggestion(sentence: String) {
        val index = wordIndex()
        val words = sentence.split(' ').filter { it.isNotBlank() }
        message.clear()
        words.forEach { w ->
            val cell = index[w.lowercase().trim('.', '?', '!', ',')]
            message.add(MessageToken(w, cell?.type, cell?.symbol))
        }
    }

    // ---- Keyboard --------------------------------------------------------------------------

    fun typeKey(key: String) {
        typing += key
        if (s.speakLettersWhileTyping) speak(key)
    }

    fun typeSpace() {
        if (typing.isBlank()) return
        commitTyping()
    }

    fun commitTyping() {
        val word = typing.trim()
        typing = ""
        if (word.isEmpty()) return
        val cell = wordIndex()[word.lowercase()]
        message.add(MessageToken(word, cell?.type, cell?.symbol))
        if (s.speakLettersWhileTyping) speak(word)
    }

    fun choosePrediction(word: String) {
        typing = ""
        val cell = wordIndex()[word.lowercase()]
        message.add(MessageToken(word, cell?.type, cell?.symbol))
        if (s.speakOnSelect && panel == Panel.BOARD) speak(word)
    }

    // ---- Edit mode -------------------------------------------------------------------------

    fun enterEditMode() {
        editMode = true
        screen = Screen.MAIN
        panel = Panel.BOARD
    }

    fun exitEditMode() {
        editMode = false
        editing = null
        moveSource = null
    }

    private fun onEditTap(boardId: String, index: Int, cell: Cell?) {
        val source = moveSource
        if (source != null && v.board(boardId)?.isScene != true) {
            moveSource = null
            if (source.boardId == boardId && source.index == index) return
            store.edit { voc ->
                voc.setCell(source.boardId, source.index, cell).setCell(boardId, index, source.cell)
            }
            return
        }
        editing = EditTarget.Grid(boardId, index)
    }

    fun startMove(target: EditTarget.Grid) {
        val cell = cellAt(target.boardId, target.index) ?: return
        editing = null
        moveSource = MoveSource(target.boardId, target.index, cell)
    }

    fun cancelMove() {
        moveSource = null
    }

    fun cellFor(target: EditTarget): Cell? = when (target) {
        is EditTarget.Grid -> cellAt(target.boardId, target.index)
        is EditTarget.Strip -> v.quickStrip.getOrNull(target.index)
        is EditTarget.NewHotspot -> null
    }

    fun hotspotFor(target: EditTarget): Hotspot? =
        (target as? EditTarget.Grid)?.let { v.board(it.boardId)?.scene?.hotspots?.getOrNull(it.index) }

    fun saveCell(target: EditTarget, cell: Cell?) {
        when (target) {
            is EditTarget.Grid -> store.edit { it.setCell(target.boardId, target.index, cell) }
            is EditTarget.Strip -> if (cell != null) store.edit { it.updateQuickStrip(target.index, cell) }
            is EditTarget.NewHotspot -> if (cell != null) addNewHotspot(target, cell, 0.24f, 0.24f)
        }
        editing = null
    }

    /** Creates an empty board with the same grid as the current one and returns its id. */
    fun createBoard(name: String): String {
        val id = "board-" + UUID.randomUUID().toString().take(8)
        val template = currentBoard
        store.edit { it.addBoard(newBoard(id, name.ifBlank { "New board" }, template.rows, template.cols)) }
        return id
    }

    /** Creates a photo scene board and returns its id. */
    fun createSceneBoard(name: String, imagePath: String): String {
        val id = "scene-" + UUID.randomUUID().toString().take(8)
        store.edit { it.addBoard(newSceneBoard(id, name.ifBlank { "Scene" }, imagePath)) }
        return id
    }

    /** Adds a hotspot centered where the person tapped, kept inside the photo. */
    fun addNewHotspot(target: EditTarget.NewHotspot, cell: Cell, width: Float, height: Float) {
        val x = (target.x - width / 2).coerceIn(0f, 1f - width)
        val y = (target.y - height / 2).coerceIn(0f, 1f - height)
        store.edit { it.addHotspot(target.boardId, Hotspot(x, y, width, height, cell)) }
        editing = null
    }

    fun onSceneBackgroundTap(boardId: String, x: Float, y: Float) {
        if (editMode && moveSource == null) editing = EditTarget.NewHotspot(boardId, x, y)
    }

    fun updateHotspot(boardId: String, index: Int, transform: (Hotspot) -> Hotspot) {
        store.edit { it.updateHotspot(boardId, index, transform) }
    }

    fun resizeBoard(boardId: String, rows: Int, cols: Int) {
        store.edit { it.resizeBoard(boardId, rows, cols) }
    }

    fun renameBoard(boardId: String, name: String) {
        if (name.isBlank()) return
        store.edit { it.updateBoard(boardId) { b -> b.copy(name = name) } }
    }

    fun newCellId(): String = "cell-" + UUID.randomUUID().toString().take(12)

    fun undo() = store.undo()
    fun redo() = store.redo()

    // ---- Settings --------------------------------------------------------------------------

    fun updateSettings(transform: (Settings) -> Settings) = settingsStore.update(transform)

    fun setPronunciation(word: String, sayAs: String?) {
        if (word.isBlank()) return
        store.edit { voc ->
            val map = voc.pronunciations.toMutableMap()
            if (sayAs.isNullOrBlank()) map.remove(word) else map[word] = sayAs
            voc.copy(pronunciations = map)
        }
    }
}
