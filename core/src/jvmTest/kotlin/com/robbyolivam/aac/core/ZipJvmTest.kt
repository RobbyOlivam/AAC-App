package com.robbyolivam.aac.core

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** The plain-Kotlin zip code (used on web and iOS) checked against Java's own zip library. */
class ZipJvmTest {
    private fun samples(): Map<String, ByteArray> {
        val r = Random(7)
        return mapOf(
            "empty.txt" to ByteArray(0),
            "text.json" to ("{\"words\": [" + (1..5000).joinToString { "\"word$it\"" } + "]}").encodeToByteArray(),
            "random.bin" to ByteArray(200_000) { r.nextInt().toByte() },
            "repeats.bin" to ByteArray(300_000) { (it % 7).toByte() },
            "media/picture.png" to ByteArray(70_000) { (r.nextInt(4) * 60).toByte() },
            "names/ünïcode.txt" to "héllo".encodeToByteArray(),
        )
    }

    @Test
    fun readsWhatJavaWrites() {
        for (level in listOf(0, 1, 6, 9)) {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { z ->
                z.setLevel(level)
                z.putNextEntry(ZipEntry("folder/")); z.closeEntry()
                samples().forEach { (name, data) -> z.putNextEntry(ZipEntry(name)); z.write(data); z.closeEntry() }
            }
            val read = Zip.read(out.toByteArray())
            assertEquals(samples().keys, read.keys, "level $level")
            samples().forEach { (name, data) -> assertContentEquals(data, read[name], "$name at level $level") }
        }
    }

    @Test
    fun javaReadsWhatWeWrite() {
        val zip = Zip.write(samples())
        val read = LinkedHashMap<String, ByteArray>()
        ZipInputStream(zip.inputStream()).use { z ->
            while (true) { val e = z.nextEntry ?: break; read[e.name] = z.readBytes() }
        }
        samples().forEach { (name, data) -> assertContentEquals(data, read[name], name) }
        assertContentEquals(samples()["text.json"], Zip.read(zip)["text.json"])
    }

    @Test
    fun crcMatchesJava() {
        val data = samples().getValue("random.bin")
        assertEquals(CRC32().apply { update(data) }.value, Zip.crc32(data))
    }
}
