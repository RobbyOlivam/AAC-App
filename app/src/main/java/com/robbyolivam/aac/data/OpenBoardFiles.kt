package com.robbyolivam.aac.data

import com.robbyolivam.aac.core.OpenBoardFormat
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Reads and writes Open Board Format files (.obz zip or a single .obf board). */
class OpenBoardFiles(private val store: VocabularyStore, private val media: MediaFiles) {

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

    /** Replaces the vocabulary (undoable). Returns how many pictures couldn't be brought over. */
    fun import(input: InputStream): Int {
        val bytes = input.readBytes()
        val isZip = bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        val files = if (isZip) unzip(bytes) else mapOf("board.obf" to bytes)
        val result = OpenBoardFormat.import(files, fallbackStrip = store.vocabulary.value.quickStrip)
        result.media.forEach { (path, data) -> store.resolve(path).also { it.parentFile?.mkdirs() }.writeBytes(data) }
        media.clearCache()
        store.replace(result.vocabulary)
        return result.skippedImages
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
