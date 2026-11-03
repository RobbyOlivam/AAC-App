package com.robbyolivam.aac.shared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.robbyolivam.aac.core.Cell
import com.robbyolivam.aac.core.CellAction
import com.robbyolivam.aac.core.DefaultVocabulary
import com.robbyolivam.aac.core.OpenBoardFormat
import com.robbyolivam.aac.core.Settings
import com.robbyolivam.aac.core.Symbol
import com.robbyolivam.aac.core.SymbolIndex
import com.robbyolivam.aac.core.TouchChatFormat
import com.robbyolivam.aac.core.Vocabulary
import com.robbyolivam.aac.core.VocabularyJson
import com.robbyolivam.aac.core.Zip
import com.robbyolivam.aac.core.applyPronunciations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** A word in the message bar. */
data class MessageWord(val text: String, val symbol: Symbol?, val id: Long)

/**
 * The talker's state and behaviour, shared by every platform: the boards, the page shown, the
 * message being built, speaking, and bringing boards in from backups and other AAC apps.
 * Follows the Android app's rules: buttons never move, suggestions never speak by themselves.
 */
class Talker(private val platform: Platform, private val scope: CoroutineScope) {
    var vocabulary by mutableStateOf(DefaultVocabulary.create())
        private set
    var settings by mutableStateOf(Settings())
        private set
    var boardId by mutableStateOf(vocabulary.homeBoardId)
        private set
    val message = mutableStateListOf<MessageWord>()
    var symbols: SymbolIndex? by mutableStateOf(null)
        private set
    /** A short note to show the person (import results, errors); null when there's none. */
    var notice: String? by mutableStateOf(null)
    var busy: String? by mutableStateOf(null)
        private set
    var loaded by mutableStateOf(false)
        private set
    /** Bumped when pictures in storage change, so cached images reload. */
    var mediaVersion by mutableStateOf(0)
        private set

    private val backStack = ArrayDeque<String>()
    private var nextId = 0L
    private var saveJob: Job? = null
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val board get() = vocabulary.board(boardId) ?: vocabulary.home
    val atHome get() = board.id == vocabulary.homeBoardId

    suspend fun load() {
        platform.storage.read(VOCAB)?.let { bytes ->
            runCatching { DefaultVocabulary.migrate(VocabularyJson.decode(bytes.decodeToString())) }.getOrNull()
        }?.let { vocabulary = it }
        platform.storage.read(SETTINGS)?.let { bytes ->
            runCatching { json.decodeFromString(Settings.serializer(), bytes.decodeToString()) }.getOrNull()
        }?.let { settings = it }
        boardId = vocabulary.homeBoardId
        loaded = true
        platform.assets.read("symbols/index.tsv")?.let { symbols = SymbolIndex.parse(it.decodeToString()) }
    }

    // ---- Selecting buttons -------------------------------------------------------------------

    fun select(cell: Cell) {
        if (!cell.visibleAt(settings.vocabularyLevel)) return
        when (cell.action) {
            CellAction.FOLDER -> cell.linkTo?.let(::open)
            CellAction.PHRASE -> say(cell)
            CellAction.WORD -> {
                message += MessageWord(cell.text, cell.symbol, nextId++)
                if (settings.speakOnSelect) say(cell)
                // A word that opens a page (imported WordPower boards: "I" opens the verbs).
                val next = cell.linkTo?.takeIf { vocabulary.board(it) != null }
                when {
                    next != null -> open(next)
                    settings.returnHomeAfterFolderWord && !atHome && board.id != vocabulary.phrasesBoardId && !board.isScene -> home()
                }
            }
        }
    }

    fun selectStrip(cell: Cell) = say(cell)

    private fun say(cell: Cell) {
        val audio = cell.audioPath
        if (audio != null) platform.speech.playRecording(audio) else speak(cell.text)
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        val s = settings
        platform.speech.speak(vocabulary.applyPronunciations(text), s.speechRate, s.pitch, s.voiceName, s.voiceAge, level = s.volume)
    }

    fun speakMessage() = speak(message.joinToString(" ") { it.text })

    fun deleteWord() { message.removeLastOrNull() }

    fun clear() {
        message.clear()
        platform.speech.stop()
    }

    // ---- Pages -------------------------------------------------------------------------------

    fun open(id: String) {
        if (id == boardId || vocabulary.board(id) == null) return
        backStack.addLast(boardId)
        if (backStack.size > 30) backStack.removeFirst()
        boardId = id
    }

    fun back() {
        boardId = backStack.removeLastOrNull()?.takeIf { vocabulary.board(it) != null } ?: vocabulary.homeBoardId
    }

    fun home() {
        backStack.clear()
        boardId = vocabulary.homeBoardId
    }

    // ---- Settings ----------------------------------------------------------------------------

    fun updateSettings(transform: (Settings) -> Settings) {
        settings = transform(settings)
        scope.launch { platform.storage.write(SETTINGS, json.encodeToString(Settings.serializer(), settings).encodeToByteArray()) }
    }

    // ---- Boards in and out -------------------------------------------------------------------

    /** Opens a file the person picks: a backup (.zip), TouchChat (.ce) or Open Board (.obz/.obf). */
    fun importFile() = scope.launch {
        val (name, bytes) = platform.files.open(".zip,.ce,.obz,.obf,application/zip") ?: return@launch
        busy = "Reading $name…"
        delay(50) // let the busy note draw before the work starts
        val started = kotlin.time.TimeSource.Monotonic.markNow()
        notice = runCatching { importBytes(bytes) }.getOrElse { "Couldn't read $name: ${it.message ?: "unknown problem"}" }
        println("Import of $name (${bytes.size} bytes) took ${started.elapsedNow()}")
        busy = null
    }

    /** Brings in boards from [bytes]; returns what to tell the person. */
    suspend fun importBytes(bytes: ByteArray): String {
        val files = if (Zip.isZip(bytes)) Zip.read(bytes) else mapOf("board.obf" to bytes)
        val strip = vocabulary.quickStrip
        return when {
            files.containsKey(BACKUP_VOCAB) -> {
                val v = DefaultVocabulary.migrate(VocabularyJson.decode(files.getValue(BACKUP_VOCAB).decodeToString()))
                files[SETTINGS]?.let { s ->
                    runCatching { json.decodeFromString(Settings.serializer(), s.decodeToString()) }.getOrNull()?.let { restored ->
                        updateSettings { restored }
                    }
                }
                replace(v, files.filterKeys { it.startsWith("media/") })
                "Backup restored."
            }
            TouchChatFormat.isTouchChat(files) -> {
                val index = symbols
                val r = TouchChatFormat.import(files, strip, symbolFor = { index?.exact(it) })
                replace(r.vocabulary, r.media)
                "TouchChat vocabulary imported: ${r.pages} pages, ${r.buttons} buttons." +
                    if (r.symbolsMissing > 0) " ${r.symbolsMissing} buttons had TouchChat pictures with no match here; they show the word." else ""
            }
            else -> {
                val r = OpenBoardFormat.import(files, strip)
                replace(r.vocabulary, r.media)
                "Boards imported." + if (r.skippedImages > 0) " ${r.skippedImages} online-only pictures were skipped." else ""
            }
        }
    }

    private suspend fun replace(next: Vocabulary, media: Map<String, ByteArray>) {
        // Keep the previous boards, so a mistaken import can be undone from Settings.
        platform.storage.write(PREVIOUS, VocabularyJson.encode(vocabulary).encodeToByteArray())
        media.forEach { (path, data) -> platform.storage.write(path, data) }
        vocabulary = next
        mediaVersion++
        message.clear()
        home()
        save()
    }

    fun restorePrevious() = scope.launch {
        val bytes = platform.storage.read(PREVIOUS) ?: run { notice = "There are no earlier boards to go back to."; return@launch }
        val previous = DefaultVocabulary.migrate(VocabularyJson.decode(bytes.decodeToString()))
        platform.storage.write(PREVIOUS, VocabularyJson.encode(vocabulary).encodeToByteArray())
        vocabulary = previous
        home()
        save()
        notice = "Your earlier boards are back."
    }

    fun startOver() = scope.launch {
        replace(DefaultVocabulary.create(), emptyMap())
        notice = "The starter boards are back. Your previous boards can be restored from Settings."
    }

    /** A backup with the same layout as the Android app's, so boards move between devices. */
    fun saveBackup() = scope.launch {
        busy = "Making a backup…"
        val files = LinkedHashMap<String, ByteArray>()
        files[BACKUP_VOCAB] = VocabularyJson.encode(vocabulary).encodeToByteArray()
        files[SETTINGS] = json.encodeToString(Settings.serializer(), settings).encodeToByteArray()
        for (path in platform.storage.list("media/")) platform.storage.read(path)?.let { files[path] = it }
        val saved = platform.files.save("aac-backup.zip", Zip.write(files), "application/zip")
        busy = null
        if (saved) notice = "Backup saved."
    }

    private fun save() {
        saveJob?.cancel()
        val v = vocabulary
        saveJob = scope.launch { platform.storage.write(VOCAB, VocabularyJson.encode(v).encodeToByteArray()) }
    }

    companion object {
        const val VOCAB = "vocabulary.json"
        const val SETTINGS = "settings.json"
        const val PREVIOUS = "vocabulary-previous.json"
        private const val BACKUP_VOCAB = "vocabulary.json"
    }
}
