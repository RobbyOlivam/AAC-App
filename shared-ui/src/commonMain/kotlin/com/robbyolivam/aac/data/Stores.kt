package com.robbyolivam.aac.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import com.robbyolivam.aac.core.DefaultVocabulary
import com.robbyolivam.aac.core.LibrarySymbol
import com.robbyolivam.aac.core.OpenBoardFormat
import com.robbyolivam.aac.core.Predictor
import com.robbyolivam.aac.core.PredictorData
import com.robbyolivam.aac.core.Settings
import com.robbyolivam.aac.core.SymbolIndex
import com.robbyolivam.aac.core.TouchChatFormat
import com.robbyolivam.aac.core.UsageEvent
import com.robbyolivam.aac.core.UsageLog
import com.robbyolivam.aac.core.Vocabulary
import com.robbyolivam.aac.core.VocabularyJson
import com.robbyolivam.aac.core.Zip
import com.robbyolivam.aac.shared.CropRect
import com.robbyolivam.aac.shared.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.decodeToSvgPainter
import kotlin.random.Random

/** A short random id for new buttons, pages and files. */
fun newId(prefix: String): String = prefix + "-" + Random.nextLong().toULong().toString(36).take(10)

/**
 * The boards: undo/redo, saving, and rolling snapshots so a bad edit can always be rolled back.
 * Everything is kept in memory and saved in the background (storage may be slow on the web).
 */
class VocabularyStore(private val platform: Platform, private val scope: CoroutineScope) {
    private val storage get() = platform.storage
    private val writeLock = Mutex()

    private val _vocabulary = MutableStateFlow(DefaultVocabulary.create())
    val vocabulary: StateFlow<Vocabulary> = _vocabulary.asStateFlow()

    private val undoStack = ArrayDeque<Vocabulary>()
    private val redoStack = ArrayDeque<Vocabulary>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    var predictor: Predictor = Predictor()
        private set

    private var snapshotNames: List<String> = emptyList()
    private var lastSnapshotAt = 0L

    suspend fun load() {
        snapshotNames = storage.list(SNAPSHOTS).sortedDescending()
        val loaded = storage.read(VOCAB)?.let { decode(it) }
            ?: snapshotNames.firstNotNullOfOrNull { name -> storage.read(name)?.let { decode(it) } }
        if (loaded != null) _vocabulary.value = loaded
        storage.read(PREDICTOR)?.let { bytes ->
            runCatching { Predictor.decode(bytes.decodeToString()) }.getOrNull()?.let { predictor = Predictor(it) }
        }
    }

    private fun decode(bytes: ByteArray): Vocabulary? =
        runCatching { DefaultVocabulary.migrate(VocabularyJson.decode(bytes.decodeToString())) }.getOrNull()

    /** Applies an edit, recording it for undo. */
    fun edit(transform: (Vocabulary) -> Vocabulary) {
        val before = _vocabulary.value
        val after = transform(before)
        if (after == before) return
        undoStack.addLast(before)
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()
        publish(after)
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_vocabulary.value)
        publish(previous)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_vocabulary.value)
        publish(next)
    }

    /** Replaces everything (restore / import): still undoable. */
    fun replace(vocabulary: Vocabulary) = edit { vocabulary }

    private fun publish(vocabulary: Vocabulary) {
        _vocabulary.value = vocabulary
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        scope.launch { save(vocabulary) }
    }

    private suspend fun save(vocabulary: Vocabulary) = writeLock.withLock {
        val bytes = VocabularyJson.encode(vocabulary).encodeToByteArray()
        storage.write(VOCAB, bytes)
        val now = platform.device.nowMillis()
        if (snapshotNames.isEmpty() || now - lastSnapshotAt > SNAPSHOT_INTERVAL_MS) {
            lastSnapshotAt = now
            val name = SNAPSHOTS + UsageLog.isoUtc(now).replace(":", "") + ".json"
            storage.write(name, bytes)
            snapshotNames = (listOf(name) + snapshotNames).distinct()
            snapshotNames.drop(MAX_SNAPSHOTS).forEach { storage.delete(it) }
            snapshotNames = snapshotNames.take(MAX_SNAPSHOTS)
        }
    }

    fun savePredictor() {
        val data: PredictorData = predictor.snapshot()
        scope.launch { writeLock.withLock { storage.write(PREDICTOR, Predictor.encode(data).encodeToByteArray()) } }
    }

    /** Snapshot names, newest first ("snapshots/2026-11-15T120000Z.json"). */
    fun snapshots(): List<String> = snapshotNames

    /** "2026-11-15 12:00 UTC" for a snapshot name. */
    fun snapshotLabel(name: String): String {
        val stamp = name.removePrefix(SNAPSHOTS).removeSuffix(".json")
        return if (stamp.length >= 15) "${stamp.substring(0, 10)} ${stamp.substring(11, 13)}:${stamp.substring(13, 15)} UTC" else stamp
    }

    suspend fun restoreSnapshot(name: String): Boolean {
        val v = storage.read(name)?.let { decode(it) } ?: return false
        replace(v)
        return true
    }

    fun resetToDefault() = replace(DefaultVocabulary.create())

    companion object {
        const val VOCAB = "vocabulary.json"
        const val PREDICTOR = "predictor.json"
        const val SNAPSHOTS = "snapshots/"
        const val MAX_UNDO = 100
        const val MAX_SNAPSHOTS = 30
        const val SNAPSHOT_INTERVAL_MS = 10 * 60 * 1000L
    }
}

class SettingsStore(private val platform: Platform, private val scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    suspend fun load() {
        platform.storage.read(KEY)?.let { bytes ->
            runCatching { json.decodeFromString(Settings.serializer(), bytes.decodeToString()) }.getOrNull()
        }?.let { _settings.value = it }
    }

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        val text = json.encodeToString(Settings.serializer(), next)
        scope.launch { platform.storage.write(KEY, text.encodeToByteArray()) }
    }

    fun exportJson(): String = json.encodeToString(Settings.serializer(), _settings.value)

    fun importJson(text: String) {
        val imported = json.decodeFromString(Settings.serializer(), text)
        update { imported }
    }

    companion object {
        const val KEY = "settings.json"
    }
}

/** Button pictures: stored small (≤512 px JPEG) so boards stay fast. */
@OptIn(ExperimentalResourceApi::class)
class MediaFiles(private val platform: Platform) {
    private val cache = LinkedHashMap<String, ImageBitmap>()
    private val _version = MutableStateFlow(0)
    /** Bumped when stored pictures change (restore, import), so screens reload them. */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun cached(path: String): ImageBitmap? = cache[path]

    suspend fun load(path: String): ImageBitmap? {
        cache[path]?.let { return it }
        val bitmap = runCatching { platform.storage.read(path)?.decodeToImageBitmap() }.getOrNull() ?: return null
        cache[path] = bitmap
        if (cache.size > 300) cache.remove(cache.keys.first())
        return bitmap
    }

    /** Saves a picked picture (cropped, scaled down); returns its path, or null if it can't be read. */
    suspend fun saveImage(bytes: ByteArray, crop: CropRect? = null, maxSize: Int = MAX_SIZE): String? {
        val jpeg = platform.images.jpeg(bytes, maxSize, crop) ?: return null
        val path = "media/" + newId("img") + ".jpg"
        platform.storage.write(path, jpeg)
        return path
    }

    suspend fun saveAudio(bytes: ByteArray): String {
        val path = "media/" + newId("audio") + ".m4a"
        platform.storage.write(path, bytes)
        return path
    }

    fun clearCache() {
        cache.clear()
        _version.value++
    }

    companion object {
        const val MAX_SIZE = 512
    }
}

/**
 * The opt-in usage log: one JSON line per event, kept per month so writes stay small. It never
 * leaves the device unless someone exports it.
 */
class UsageStore(private val platform: Platform, private val scope: CoroutineScope) {
    private val lock = Mutex()
    private val pending = ArrayList<UsageEvent>()

    private fun keyFor(time: Long) = PREFIX + UsageLog.isoUtc(time).take(7) + ".jsonl"

    fun append(event: UsageEvent) {
        pending += event
        scope.launch { flush() }
    }

    private suspend fun flush() = lock.withLock {
        if (pending.isEmpty()) return@withLock
        val batch = pending.toList()
        pending.clear()
        batch.groupBy { keyFor(it.time) }.forEach { (key, events) ->
            val before = platform.storage.read(key)?.decodeToString().orEmpty()
            platform.storage.write(key, (before + events.joinToString("") { UsageLog.encode(it) + "\n" }).encodeToByteArray())
        }
    }

    /** Every event, oldest first. */
    suspend fun readAll(): List<UsageEvent> {
        flush()
        return lock.withLock {
            platform.storage.list(PREFIX).sorted().flatMap { key ->
                platform.storage.read(key)?.decodeToString().orEmpty().lineSequence().mapNotNull(UsageLog::decode).toList()
            }
        }
    }

    suspend fun csv(): ByteArray = UsageLog.toCsv(readAll()).encodeToByteArray()

    suspend fun clear() = lock.withLock {
        pending.clear()
        platform.storage.list(PREFIX).forEach { platform.storage.delete(it) }
    }

    /** Drops months older than about [keepDays]. */
    suspend fun prune(keepDays: Int = 400) = lock.withLock {
        val cutoff = UsageLog.isoUtc(platform.device.nowMillis() - keepDays * 24L * 60 * 60 * 1000).take(7)
        platform.storage.list(PREFIX).filter { it.removePrefix(PREFIX).take(7) < cutoff }.forEach { platform.storage.delete(it) }
    }

    companion object {
        const val PREFIX = "usage/"
    }
}

/**
 * The bundled symbol library (Mulberry and OpenMoji, CC BY-SA 4.0) shipped as SVGs. Searching
 * is core's [SymbolIndex]; this loads the index and draws symbols, keeping recent ones.
 */
@OptIn(ExperimentalResourceApi::class)
class SymbolLibrary(private val platform: Platform) {
    private val _index = MutableStateFlow<SymbolIndex?>(null)
    val index: StateFlow<SymbolIndex?> = _index.asStateFlow()
    private val cache = LinkedHashMap<String, Painter?>()

    suspend fun load() {
        platform.assets.read("symbols/index.tsv")?.let { _index.value = SymbolIndex.parse(it.decodeToString()) }
    }

    fun contains(libraryId: String): Boolean = _index.value?.contains(libraryId) ?: true
    fun forEmoji(emoji: String?): String? = _index.value?.forEmoji(emoji)
    fun exact(word: String): String? = _index.value?.exact(word)
    fun search(query: String, limit: Int = 60, context: List<String> = emptyList()): List<LibrarySymbol> =
        _index.value?.search(query, limit, context).orEmpty()

    fun cached(libraryId: String): Painter? = cache[libraryId]
    fun isCached(libraryId: String): Boolean = cache.containsKey(libraryId)

    suspend fun painter(libraryId: String, density: Density): Painter? {
        if (cache.containsKey(libraryId)) return cache[libraryId]
        val parts = libraryId.split(':', limit = 2)
        val painter = if (parts.size < 2) null else runCatching {
            platform.assets.read("symbols/${parts[0]}/${parts[1]}.svg")?.decodeToSvgPainter(density)
        }.getOrNull()
        cache[libraryId] = painter
        if (cache.size > 800) cache.remove(cache.keys.first())
        return painter
    }
}

/** Single-file backup (.zip) of boards, settings, learned predictions and pictures: the same layout as Android's. */
class BackupManager(
    private val platform: Platform,
    private val store: VocabularyStore,
    private val settings: SettingsStore,
    private val media: MediaFiles,
) {
    suspend fun export(): ByteArray {
        val files = LinkedHashMap<String, ByteArray>()
        files[VOCAB] = VocabularyJson.encode(store.vocabulary.value).encodeToByteArray()
        files[SETTINGS] = settings.exportJson().encodeToByteArray()
        files[PREDICTOR] = Predictor.encode(store.predictor.snapshot()).encodeToByteArray()
        for (path in platform.storage.list("media/")) platform.storage.read(path)?.let { files[path] = it }
        return Zip.write(files)
    }

    /** Restores a backup. Throws if it has no readable boards; nothing is changed then. */
    suspend fun import(entries: Map<String, ByteArray>) {
        val vocabulary = DefaultVocabulary.migrate(VocabularyJson.decode(
            entries[VOCAB]?.decodeToString() ?: throw IllegalArgumentException("Not an AAC backup file"),
        ))
        entries.filterKeys { it.startsWith("media/") && !it.contains("..") }.forEach { (name, bytes) -> platform.storage.write(name, bytes) }
        entries[SETTINGS]?.let { runCatching { settings.importJson(it.decodeToString()) } }
        entries[PREDICTOR]?.let { platform.storage.write(VocabularyStore.PREDICTOR, it) }
        media.clearCache()
        store.replace(vocabulary)
    }

    companion object {
        const val VOCAB = "vocabulary.json"
        const val SETTINGS = "settings.json"
        const val PREDICTOR = "predictor.json"
        fun isBackup(entries: Map<String, ByteArray>) = VOCAB in entries
    }
}

/** Board files from other AAC apps: Open Board Format (.obz/.obf) in and out, TouchChat (.ce) in. */
class OpenBoardFiles(
    private val platform: Platform,
    private val store: VocabularyStore,
    private val media: MediaFiles,
    private val symbols: SymbolLibrary,
) {
    /** An Open Board (.obz) file of every board, with the pictures. */
    suspend fun export(): ByteArray {
        val v = store.vocabulary.value
        // Storage is asynchronous, so the pictures the boards use are read up front.
        val paths = v.boards.flatMap { b -> b.indexedCells().mapNotNull { it.second.symbol.imagePath } + listOfNotNull(b.scene?.imagePath) }
        val pictures = paths.distinct().mapNotNull { p -> platform.storage.read(p)?.let { p to it } }.toMap()
        return Zip.write(OpenBoardFormat.export(v) { path -> pictures[path] })
    }

    /** Replaces the boards (undoable). Returns a short summary to show the person. */
    suspend fun import(files: Map<String, ByteArray>): String {
        val strip = store.vocabulary.value.quickStrip
        if (TouchChatFormat.isTouchChat(files)) {
            val r = TouchChatFormat.import(files, strip, symbolFor = symbols::exact)
            save(r.media, r.vocabulary)
            return "TouchChat vocabulary imported: ${r.pages} pages, ${r.buttons} buttons." +
                (if (r.symbolsMissing > 0) " ${r.symbolsMissing} buttons had TouchChat pictures with no match here; they show the word." else "") +
                " Undo in edit mode brings back your old boards."
        }
        val r = OpenBoardFormat.import(files, fallbackStrip = strip)
        save(r.media, r.vocabulary)
        return "Boards imported. Undo in edit mode brings back your old ones." +
            if (r.skippedImages > 0) " ${r.skippedImages} online-only pictures were skipped." else ""
    }

    private suspend fun save(files: Map<String, ByteArray>, vocabulary: Vocabulary) {
        files.forEach { (path, data) -> platform.storage.write(path, data) }
        media.clearCache()
        store.replace(vocabulary)
    }
}
