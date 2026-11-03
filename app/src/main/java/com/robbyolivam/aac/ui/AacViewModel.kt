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
import com.robbyolivam.aac.core.Tone
import com.robbyolivam.aac.core.UsageEvent
import com.robbyolivam.aac.core.UsageSource
import com.robbyolivam.aac.core.applyPronunciations
import com.robbyolivam.aac.core.duplicateBoard
import com.robbyolivam.aac.core.expandAbbreviation
import com.robbyolivam.aac.core.newBoard
import com.robbyolivam.aac.core.search
import com.robbyolivam.aac.core.setCell
import com.robbyolivam.aac.core.updateBoard
import com.robbyolivam.aac.core.updateQuickStrip
import com.robbyolivam.aac.core.ScanMode
import com.robbyolivam.aac.core.Settings
import com.robbyolivam.aac.core.platform.AudioOutput
import java.util.UUID

/** A word in the message bar, with the picture it came from. */
/** A word in the message. [base] is the button's word before any form was chosen ("want" for "wanted"). */
data class MessageToken(
    val text: String,
    val type: WordType?,
    val symbol: Symbol?,
    val id: Long = nextTokenId++,
    val base: String? = null,
)

private var nextTokenId = 0L

enum class Panel { BOARD, KEYBOARD }

enum class Screen { MAIN, SETTINGS, REPORT, HELP }

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
    val symbols = app.symbols
    val usage = app.usage

    val vocabulary get() = store.vocabulary
    val settings get() = settingsStore.settings
    private val s: Settings get() = settingsStore.settings.value
    private val v: Vocabulary get() = store.vocabulary.value
    private val level: Int get() = s.vocabularyLevel

    val scanner = Scanner(viewModelScope) { preview(it) }

    init {
        viewModelScope.launch {
            settingsStore.settings.collect {
                scanner.configure(it)
                speaker.volume = it.volume
            }
        }
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

    /** Explore mode: words hidden by the vocabulary level (or hidden by hand) show and work, just for now. */
    var exploring by mutableStateOf(false)

    /** A button copied in edit mode, ready to paste into an empty spot. */
    var clipboard by mutableStateOf<Cell?>(null)
        private set
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

    /** The button last previewed with "preview on first tap" ("board#index"). */
    private var previewed: String? = null

    /** Whether a button responds: visible at this level, or anything while exploring. */
    fun usable(cell: Cell): Boolean = exploring || cell.visibleAt(level)

    fun onCellSelected(boardId: String, index: Int, cell: Cell?) {
        if (editMode) {
            onEditTap(boardId, index, cell)
            return
        }
        if (cell == null || !usable(cell) || debounced()) return
        if (s.previewTaps && s.scanMode == ScanMode.OFF) {
            val key = "$boardId#$index"
            if (previewed != key) {
                // First tap: say it privately so the person can check before using it.
                previewed = key
                preview(if (cell.action == CellAction.FOLDER) cell.label else cell.text)
                return
            }
            previewed = null
        }
        advanceGuide(boardId, index)
        if (formsMode && cell.action == CellAction.WORD && EnglishMorphology.hasForms(cell.text, cell.type)) {
            formsMode = false
            formsFor = cell
            return
        }
        activate(cell, boardId, index.takeIf { v.board(boardId)?.isScene != true })
    }

    fun onStripSelected(index: Int, cell: Cell) {
        if (editMode) {
            editing = EditTarget.Strip(index)
            return
        }
        if (debounced()) return
        logSpoken(cell.text, UsageSource.STRIP)
        speakCell(cell)
    }

    fun onCellLongPress(cell: Cell?) {
        if (editMode || cell == null || !usable(cell) || cell.action != CellAction.WORD) return
        if (EnglishMorphology.hasForms(cell.text, cell.type)) formsFor = cell
    }

    private fun activate(cell: Cell, boardId: String, index: Int?) {
        when (cell.action) {
            CellAction.FOLDER -> cell.linkTo?.let { navigate(it) }
            CellAction.PHRASE -> {
                logSpoken(cell.text, UsageSource.PHRASE)
                speakCell(cell)
            }
            CellAction.WORD -> {
                addToken(MessageToken(cell.text, cell.type, cell.symbol, base = cell.text))
                logWord(cell.text, boardId, index)
                if (s.speakOnSelect) speakCell(cell)
                // A word that opens a page (imported WordPower boards: "I" opens the verbs).
                val next = cell.linkTo?.takeIf { v.board(it) != null }
                if (next != null) navigate(next) else afterWord(boardId)
            }
        }
    }

    fun selectForm(cell: Cell, form: String) {
        formsFor = null
        if (formsReplaceLast) {
            // Forms pressed after a word: change that word ("want" → "wanted") in place.
            formsReplaceLast = false
            val last = message.lastOrNull()
            if (last != null) message[message.lastIndex] = last.copy(text = form, base = cell.text)
            if (s.speakOnSelect) speak(form)
            return
        }
        addToken(MessageToken(form, cell.type, cell.symbol, base = cell.text))
        logWord(form, currentBoardId, null)
        if (s.speakOnSelect) speak(form)
        afterWord(currentBoardId)
    }

    /** True while the forms picker is changing the last word of the message (not adding one). */
    private var formsReplaceLast = false

    /**
     * The Forms button. With a word in the message that has other forms, shows them for that
     * word right away; otherwise turns on "next word" mode, where the next word tapped shows its
     * forms (the message bar says so).
     */
    fun formsPressed() {
        if (formsMode) {
            formsMode = false
            return
        }
        commitTyping()
        val last = message.lastOrNull()
        if (last != null) {
            val word = last.base ?: last.text
            val type = last.type ?: wordIndex()[word.lowercase()]?.type
            if (type != null && EnglishMorphology.hasForms(word, type)) {
                formsReplaceLast = true
                formsFor = Cell(id = "message-word", label = word, symbol = last.symbol ?: Symbol(), type = type)
                return
            }
        }
        formsMode = true
    }

    fun dismissForms() {
        formsFor = null
        formsReplaceLast = false
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

    // ---- Usage log (opt-in, pausable by the user) --------------------------------------------

    private val logging: Boolean get() = s.usageLogging && !s.usagePaused && !editMode

    private fun logWord(text: String, boardId: String, index: Int?) {
        val source = if (boardId == v.homeBoardId) UsageSource.CORE else UsageSource.FOLDER
        if (logging) usage.append(UsageEvent(System.currentTimeMillis(), text, source, boardId = boardId, index = index))
    }

    private fun logWord(text: String, source: UsageSource) {
        if (logging) usage.append(UsageEvent(System.currentTimeMillis(), text, source))
    }

    private fun logSpoken(text: String, source: UsageSource) {
        if (logging) usage.append(UsageEvent(System.currentTimeMillis(), text, source, utterance = true))
    }

    /** The user's own privacy switch: pause or resume logging without opening settings. */
    fun toggleUsagePause() = settingsStore.update { it.copy(usagePaused = !it.usagePaused) }

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

    /** Says the message in a [tone] (question, excited, whisper…). */
    fun speakMessage(tone: Tone) {
        val text = tone.apply(messageText())
        if (text.isBlank()) return
        logSpoken(text, UsageSource.MESSAGE)
        val s = s
        speaker.speak(
            v.applyPronunciations(text), s.speechRate * tone.rate, s.pitch * tone.pitch, s.voiceName, s.voiceAge,
            output = speechOutput, level = tone.volume(s.volume),
        )
        store.predictor.learn(messageText())
        store.savePredictor()
    }

    fun speakMessage() {
        val text = messageText()
        if (text.isBlank()) return
        logSpoken(text, UsageSource.MESSAGE)
        speak(text)
        store.predictor.learn(text)
        store.savePredictor()
    }

    fun speakCell(cell: Cell) {
        val audio = cell.audioPath
        if (audio != null) speaker.playRecording(store.resolve(audio).absolutePath, speechOutput) else speak(cell.text)
    }

    /** Real speech: out loud. With the private earbud on, forced to the phone speaker. */
    private val speechOutput: AudioOutput get() = if (s.earbudPreview) AudioOutput.SPEAKER else AudioOutput.DEFAULT

    fun speak(text: String) = speak(text, speechOutput)

    /** A private preview (scanning cues, first-tap previews): in the earbud when that's on. */
    fun preview(text: String) = speak(text, if (s.earbudPreview) AudioOutput.EARBUD else AudioOutput.DEFAULT)

    private fun speak(text: String, output: AudioOutput) {
        val s = s
        speaker.speak(v.applyPronunciations(text), s.speechRate, s.pitch, s.voiceName, s.voiceAge, output = output)
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

    fun keyboardPredictions(limit: Int = 6): List<String> {
        val words = store.predictor.complete(typing, previousWord(), limit)
        // A typed shortcut offers its full words first.
        val expansion = v.expandAbbreviation(typing) ?: return words
        return (listOf(expansion) + words).distinct().take(limit)
    }

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
        if (s.speakLettersWhileTyping) preview(key)
    }

    fun typeSpace() {
        if (typing.isBlank()) return
        commitTyping()
    }

    fun commitTyping() {
        val word = typing.trim()
        typing = ""
        if (word.isEmpty()) return
        val expansion = v.expandAbbreviation(word)
        if (expansion != null) {
            val index = wordIndex()
            expansion.split(' ').filter { it.isNotBlank() }.forEach { w ->
                val cell = index[w.lowercase()]
                message.add(MessageToken(w, cell?.type, cell?.symbol))
                logWord(w, UsageSource.KEYBOARD)
            }
            if (s.speakLettersWhileTyping) preview(expansion)
            return
        }
        val cell = wordIndex()[word.lowercase()]
        message.add(MessageToken(word, cell?.type, cell?.symbol))
        logWord(word, UsageSource.KEYBOARD)
        if (s.speakLettersWhileTyping) preview(word)
    }

    fun choosePrediction(word: String) {
        typing = ""
        val index = wordIndex()
        word.split(' ').filter { it.isNotBlank() }.forEach { w ->
            val cell = index[w.lowercase()]
            message.add(MessageToken(w, cell?.type, cell?.symbol))
            logWord(w, UsageSource.PREDICTION)
        }
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

    /** The guided tour's current step, or null when it isn't running. */
    var tutorialStep by mutableStateOf<Int?>(null)
        private set

    /** Starts the tour on the main screen, from the Core page. */
    fun startTutorial() {
        exitEditMode()
        screen = Screen.MAIN
        goHome()
        tutorialStep = 0
        settingsStore.update { it.copy(tutorialOffered = true) }
    }

    fun tutorialNext() {
        val step = tutorialStep ?: return
        tutorialStep = if (step >= tutorialSteps.lastIndex) null else step + 1
    }

    fun tutorialBack() {
        tutorialStep = tutorialStep?.let { (it - 1).coerceAtLeast(0) }
    }

    fun endTutorial() {
        tutorialStep = null
    }

    fun declineTutorial() = settingsStore.update { it.copy(tutorialOffered = true) }

    /** Whether the "Add a word" wizard is open. */
    var addingWord by mutableStateOf(false)

    /** Adds a word from the wizard and shows its page, so the person sees where it went. */
    fun addWord(word: String, type: WordType, libraryId: String?, boardId: String, index: Int) {
        val cell = Cell(id = newCellId(), label = word, symbol = Symbol(libraryId = libraryId), type = type)
        store.edit { it.setCell(boardId, index, cell) }
        navigate(boardId)
    }

    fun copyCell(cell: Cell) {
        clipboard = cell
        editing = null
    }

    /** Pastes the copied button into [target] as a new button (its own id). */
    fun pasteInto(target: EditTarget.Grid) {
        val cell = clipboard ?: return
        store.edit { it.setCell(target.boardId, target.index, cell.copy(id = newCellId())) }
        editing = null
    }

    /** Copies a page and opens the copy, so it can be changed without touching the original. */
    fun duplicateBoard(boardId: String) {
        val original = v.board(boardId) ?: return
        val id = (if (original.isScene) "scene-" else "board-") + UUID.randomUUID().toString().take(8)
        store.edit { it.duplicateBoard(boardId, id, "${original.name} (copy)", ::newCellId) }
        navigate(id)
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

    fun setAbbreviation(shortcut: String, expansion: String?) {
        val key = shortcut.trim().lowercase()
        if (key.isEmpty() || key.contains(' ')) return
        store.edit { voc ->
            val map = voc.abbreviations.toMutableMap()
            if (expansion.isNullOrBlank()) map.remove(key) else map[key] = expansion.trim()
            voc.copy(abbreviations = map)
        }
    }

    fun setPronunciation(word: String, sayAs: String?) {
        if (word.isBlank()) return
        store.edit { voc ->
            val map = voc.pronunciations.toMutableMap()
            if (sayAs.isNullOrBlank()) map.remove(word) else map[word] = sayAs
            voc.copy(pronunciations = map)
        }
    }
}
