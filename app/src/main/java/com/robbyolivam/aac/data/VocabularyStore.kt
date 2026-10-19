package com.robbyolivam.aac.data

import android.content.Context
import android.util.Log
import com.robbyolivam.aac.core.DefaultVocabulary
import com.robbyolivam.aac.core.Predictor
import com.robbyolivam.aac.core.PredictorData
import com.robbyolivam.aac.core.Vocabulary
import com.robbyolivam.aac.core.VocabularyJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Owns the vocabulary on disk: atomic saves, undo/redo, and rolling snapshots so a bad edit
 * (or a curious toddler in edit mode) can always be rolled back.
 */
class VocabularyStore(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    val filesDir: File get() = context.filesDir
    private val vocabFile get() = File(filesDir, "vocabulary.json")
    private val predictorFile get() = File(filesDir, "predictor.json")
    val snapshotDir get() = File(filesDir, "snapshots").apply { mkdirs() }
    val mediaDir get() = File(filesDir, "media").apply { mkdirs() }

    private val _vocabulary = MutableStateFlow(loadVocabulary())
    val vocabulary: StateFlow<Vocabulary> = _vocabulary.asStateFlow()

    private val undoStack = ArrayDeque<Vocabulary>()
    private val redoStack = ArrayDeque<Vocabulary>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    val predictor: Predictor = Predictor(loadPredictor())

    private fun loadVocabulary(): Vocabulary {
        if (vocabFile.exists()) {
            runCatching { return DefaultVocabulary.migrate(VocabularyJson.decode(vocabFile.readText())) }
                .onFailure { Log.e(TAG, "Vocabulary file unreadable; trying latest snapshot", it) }
            latestSnapshot()?.let { snap ->
                runCatching { return DefaultVocabulary.migrate(VocabularyJson.decode(snap.readText())) }
            }
        }
        return DefaultVocabulary.create()
    }

    private fun loadPredictor(): PredictorData =
        runCatching { Predictor.decode(predictorFile.readText()) }.getOrDefault(PredictorData())

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

    /** Replaces everything (restore / import) — still undoable. */
    fun replace(vocabulary: Vocabulary) = edit { vocabulary }

    private fun publish(vocabulary: Vocabulary) {
        _vocabulary.value = vocabulary
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        scope.launch { save(vocabulary) }
    }

    private suspend fun save(vocabulary: Vocabulary) = writeLock.withLock {
        val text = VocabularyJson.encode(vocabulary)
        writeAtomically(vocabFile, text)
        val latest = latestSnapshot()
        if (latest == null || System.currentTimeMillis() - latest.lastModified() > SNAPSHOT_INTERVAL_MS) {
            val name = "vocabulary-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".json"
            writeAtomically(File(snapshotDir, name), text)
            snapshots().drop(MAX_SNAPSHOTS).forEach { it.delete() }
        }
    }

    fun savePredictor() {
        val data = predictor.snapshot()
        scope.launch { writeLock.withLock { writeAtomically(predictorFile, Predictor.encode(data)) } }
    }

    /** Newest first. */
    fun snapshots(): List<File> =
        snapshotDir.listFiles { f -> f.name.endsWith(".json") }?.sortedByDescending { it.name }.orEmpty()

    private fun latestSnapshot(): File? = snapshots().firstOrNull()

    fun restoreSnapshot(file: File): Boolean =
        runCatching { replace(DefaultVocabulary.migrate(VocabularyJson.decode(file.readText()))) }.isSuccess

    fun resetToDefault() = replace(DefaultVocabulary.create())

    fun resolve(relativePath: String): File = File(filesDir, relativePath)

    private fun writeAtomically(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    private companion object {
        const val TAG = "VocabularyStore"
        const val MAX_UNDO = 100
        const val MAX_SNAPSHOTS = 30
        const val SNAPSHOT_INTERVAL_MS = 10 * 60 * 1000L
    }
}
