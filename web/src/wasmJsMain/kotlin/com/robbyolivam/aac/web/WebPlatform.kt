package com.robbyolivam.aac.web

import com.robbyolivam.aac.core.VoiceAge
import com.robbyolivam.aac.core.platform.AudioOutput
import com.robbyolivam.aac.core.platform.SpeechOutput
import com.robbyolivam.aac.core.platform.VoiceInfo
import com.robbyolivam.aac.shared.AppAssets
import com.robbyolivam.aac.shared.AppStorage
import com.robbyolivam.aac.shared.FileExchange
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

// The browser side lives in platform.js (window.aac); these are its Kotlin declarations.

external interface AacJs : JsAny {
    val store: StoreJs
    val speech: SpeechJs
    val sounds: SoundsJs
    val files: FilesJs
    fun fetchBytes(url: String): Promise<Uint8Array?>
    val bytes: BytesJs
    fun isStandalone(): Boolean
}

external interface BytesJs : JsAny {
    fun toLatin1(bytes: Uint8Array): String
    fun fromLatin1(text: String): Uint8Array
}

external interface StoreJs : JsAny {
    fun get(key: String): Promise<Uint8Array?>
    fun put(key: String, bytes: Uint8Array): Promise<JsAny?>
    fun remove(key: String): Promise<JsAny?>
    fun keys(prefix: String): Promise<JsString>
}

external interface SpeechJs : JsAny {
    fun available(): Boolean
    fun voices(): String
    fun onVoicesChanged(callback: () -> Unit)
    fun speak(text: String, rate: Double, pitch: Double, volume: Double, voiceId: String)
    fun stop()
}

external interface SoundsJs : JsAny {
    fun play(key: String, volume: Double): Promise<JsAny?>
}

external interface PickedFile : JsAny {
    val name: String
    val bytes: Uint8Array
}

external interface FilesJs : JsAny {
    fun pick(accept: String): Promise<PickedFile?>
    fun save(name: String, bytes: Uint8Array, mime: String): Promise<JsBoolean>
}

external val aac: AacJs

// ---- Bytes between Kotlin and the browser ----------------------------------------------------
// Passed as Latin-1 strings: the runtime copies strings in bulk, where byte-by-byte calls
// across the boundary would take seconds for a large TouchChat file.

fun Uint8Array.toByteArray(): ByteArray {
    val s = aac.bytes.toLatin1(this)
    return ByteArray(s.length) { s[it].code.toByte() }
}

fun ByteArray.toUint8Array(): Uint8Array =
    aac.bytes.fromLatin1(CharArray(size) { (this[it].toInt() and 0xFF).toChar() }.concatToString())

// ---- Implementations -------------------------------------------------------------------------

class WebStorage : AppStorage {
    override suspend fun read(path: String): ByteArray? = aac.store.get(path).await<Uint8Array?>()?.toByteArray()
    override suspend fun write(path: String, bytes: ByteArray) { aac.store.put(path, bytes.toUint8Array()).await<JsAny?>() }
    override suspend fun delete(path: String) { aac.store.remove(path).await<JsAny?>() }
    override suspend fun list(prefix: String): List<String> =
        aac.store.keys(prefix).await<JsString>().toString().split('\n').filter { it.isNotEmpty() }
}

/** The app's own files, served next to it and kept offline by the service worker. */
class WebAssets : AppAssets {
    override suspend fun read(path: String): ByteArray? = aac.fetchBytes(path).await<Uint8Array?>()?.toByteArray()
}

class WebFiles : FileExchange {
    override suspend fun open(accept: String): Pair<String, ByteArray>? =
        aac.files.pick(accept).await<PickedFile?>()?.let { it.name to it.bytes.toByteArray() }

    override suspend fun save(name: String, bytes: ByteArray, mimeType: String): Boolean =
        aac.files.save(name, bytes.toUint8Array(), mimeType).await<JsBoolean>().toBoolean()
}

/** Speech with the device's voices (Web Speech). Voice age is approximated with pitch. */
class WebSpeech : SpeechOutput {
    private val _voices = MutableStateFlow(parseVoices())
    override val voices: StateFlow<List<VoiceInfo>> = _voices
    override val available: StateFlow<Boolean> = MutableStateFlow(aac.speech.available())
    override var volume: Float = 1f

    init {
        aac.speech.onVoicesChanged { _voices.value = parseVoices() }
    }

    private fun parseVoices(): List<VoiceInfo> =
        aac.speech.voices().split('\n').mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 3) null else VoiceInfo(p[0], p[1], p[2])
        }

    override fun speak(text: String, rate: Float, pitch: Float, voiceId: String?, age: VoiceAge, output: AudioOutput, level: Float?) {
        val agePitch = when (age) {
            VoiceAge.ADULT -> 1f
            VoiceAge.TEEN -> 1.15f
            VoiceAge.KID -> 1.35f
        }
        aac.speech.speak(text, rate.toDouble(), (pitch * agePitch).coerceIn(0.1f, 2f).toDouble(), (level ?: volume).toDouble(), voiceId ?: "")
    }

    override fun playRecording(path: String, output: AudioOutput) {
        aac.sounds.play(path, volume.toDouble())
    }

    override fun earbudConnected(): Boolean = false
    override fun stop() = aac.speech.stop()
    override fun shutdown() = aac.speech.stop()
}
