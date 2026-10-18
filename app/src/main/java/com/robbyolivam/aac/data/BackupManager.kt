package com.robbyolivam.aac.data

import com.robbyolivam.aac.core.VocabularyJson
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Single-file backup (.zip) of vocabulary, settings, learned predictions and media. */
class BackupManager(
    private val store: VocabularyStore,
    private val settings: SettingsStore,
    private val media: MediaFiles,
) {
    fun export(output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            put(VOCAB, VocabularyJson.encode(store.vocabulary.value).toByteArray())
            put(SETTINGS, settings.exportJson().toByteArray())
            put(PREDICTOR, com.robbyolivam.aac.core.Predictor.encode(store.predictor.snapshot()).toByteArray())
            store.mediaDir.listFiles()?.forEach { file -> put("media/${file.name}", file.readBytes()) }
        }
    }

    /** Restores a backup. Throws if the archive has no readable vocabulary; nothing is changed in that case. */
    fun import(input: InputStream) {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                val safe = !name.contains("..") && (name in setOf(VOCAB, SETTINGS, PREDICTOR) || name.startsWith("media/"))
                if (!entry.isDirectory && safe) entries[name] = zip.readBytes()
            }
        }
        val vocabulary = VocabularyJson.decode(
            entries[VOCAB]?.decodeToString() ?: throw IllegalArgumentException("Not an AAC backup file"),
        )
        entries.filterKeys { it.startsWith("media/") }.forEach { (name, bytes) ->
            store.resolve(name).also { it.parentFile?.mkdirs() }.writeBytes(bytes)
        }
        media.clearCache()
        entries[SETTINGS]?.let { runCatching { settings.importJson(it.decodeToString()) } }
        store.replace(vocabulary)
    }

    private companion object {
        const val VOCAB = "vocabulary.json"
        const val SETTINGS = "settings.json"
        const val PREDICTOR = "predictor.json"
    }
}
