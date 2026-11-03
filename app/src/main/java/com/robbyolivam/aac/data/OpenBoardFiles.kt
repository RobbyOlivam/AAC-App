package com.robbyolivam.aac.data

import com.robbyolivam.aac.core.OpenBoardFormat
import com.robbyolivam.aac.core.TouchChatFormat
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Reads and writes board files from other AAC apps: Open Board Format (.obz zip or a single
 * .obf board), and imports TouchChat vocabularies (.ce).
 */
class OpenBoardFiles(
    private val store: VocabularyStore,
    private val media: MediaFiles,
    private val symbols: SymbolLibrary,
) {

    fun export(output: OutputStream) {
        val files = OpenBoardFormat.export(store.vocabulary.value) { path ->
            store.resolve(path).takeIf { it.exists() }?.readBytes()
        }
        ZipOutputStream(output).use { zip ->
            files.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    /** Replaces the vocabulary (undoable). Returns a short summary to show the person. */
    fun import(input: InputStream): String {
        val bytes = input.readBytes()
        val isZip = bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        val files = if (isZip) unzip(bytes) else mapOf("board.obf" to bytes)
        val strip = store.vocabulary.value.quickStrip
        if (TouchChatFormat.isTouchChat(files)) {
            val result = TouchChatFormat.import(files, strip, symbolFor = symbols::exact)
            save(result.media, result.vocabulary)
            return "TouchChat vocabulary imported: ${result.pages} pages, ${result.buttons} buttons." +
                (if (result.symbolsMissing > 0) " ${result.symbolsMissing} buttons had TouchChat pictures with no match here; they show the word." else "") +
                " Undo in edit mode brings back your old boards."
        }
        val result = OpenBoardFormat.import(files, fallbackStrip = strip)
        save(result.media, result.vocabulary)
        return "Boards imported. Undo in edit mode brings back your old ones." +
            if (result.skippedImages > 0) " ${result.skippedImages} online-only pictures were skipped." else ""
    }

    private fun save(files: Map<String, ByteArray>, vocabulary: com.robbyolivam.aac.core.Vocabulary) {
        files.forEach { (path, data) -> store.resolve(path).also { it.parentFile?.mkdirs() }.writeBytes(data) }
        media.clearCache()
        store.replace(vocabulary)
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val files = LinkedHashMap<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var total = 0L
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || entry.name.contains("..")) continue
                val data = zip.readBytes()
                total += data.size
                require(total < MAX_TOTAL_BYTES) { "Board file is too large" }
                files[entry.name.removePrefix("/")] = data
            }
        }
        return files
    }

    private companion object {
        const val MAX_TOTAL_BYTES = 300L * 1024 * 1024
    }
}
