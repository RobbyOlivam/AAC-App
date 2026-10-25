package com.robbyolivam.aac.speech

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * A downloadable neural voice (Piper, run on the phone by sherpa-onnx). Only voices trained on
 * public-domain recordings are listed, so they are safe to use anywhere.
 */
data class VoicePack(
    val id: String,
    val name: String,
    val description: String,
    val repo: String,
    val revision: String,
    val sha256: String,
    val bytes: Long,
) {
    val voiceName: String get() = PREFIX + id
    val url: String get() = "https://huggingface.co/csukuangfj/$repo/resolve/$revision/${repo.removePrefix("vits-piper-")}.onnx"

    companion object {
        /** Voice settings use this prefix to pick a voice pack instead of a system voice. */
        const val PREFIX = "pack:"
    }
}

val voicePackCatalog = listOf(
    VoicePack(
        "kristin", "Kristin", "US English · female", "vits-piper-en_US-kristin-medium",
        "6fdc68249ba2b1b41c1c42ec74341d1599e2c96e", "f9a5c375c70ddc33ccdb3969c76a4d7cc5255ac08119a312b0034a1d07041be6", 63_531_507,
    ),
    VoicePack(
        "ljspeech", "Linda", "US English · female", "vits-piper-en_US-ljspeech-medium",
        "34f390d13b9edbaa30c339127de0f9c4c5948c90", "8ceba58a4b540d4e7e7e24ad079cf0d92762a4fd334e059f12940787c6c37b3d", 63_531_507,
    ),
    VoicePack(
        "cori", "Cori", "UK English · female", "vits-piper-en_GB-cori-medium",
        "e304c95c578725ba9cab0cff451c4e5d9aaf889e", "8b0d3cdd77f2878e0aa2048103eabb4d01b334783f99629f042bcf703aeba487", 63_531_507,
    ),
    VoicePack(
        "john", "John", "US English · male", "vits-piper-en_US-john-medium",
        "adfff0e2951b85fafe20b582a8961a956ba926bd", "7ab60237f77e06c8f32b811080b7ad82d23bc5bacc4bba2c35e58aeae6833364", 63_531_507,
    ),
    VoicePack(
        "norman", "Norman", "US English · male", "vits-piper-en_US-norman-medium",
        "96632d35050997005e69857cbdf5352ed329bb45", "fa06b6b8b280e176b17f8424e05ca12d0223eeac9a31c87230937fe7120fbe2a", 63_531_507,
    ),
    VoicePack(
        "bryce", "Bryce", "US English · male", "vits-piper-en_US-bryce-medium",
        "b7f24892d9291522cf27e7cbfe34f17fa1cf4b17", "f9ad3c773102fc27b13f48e5a743bbc75de6036b07dfb8a5c84ec2cc24153a28", 63_531_507,
    ),
)

/** Tests flip this so the voice pack list shows on the JVM, where the ARM engine can't load. */
@Volatile
var assumeSupported = false

/** Download state of one pack: not installed, downloading (0–1), or installed. */
sealed interface PackState {
    data object NotInstalled : PackState
    data class Downloading(val progress: Float) : PackState
    data object Installed : PackState
    data class Failed(val message: String) : PackState
}

/**
 * Downloads voice packs (only when the user taps Download) and runs them. The shared
 * pronunciation data (espeak-ng, English only) and token table ship in the APK.
 */
class VoicePacks(context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, "voices")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = HashMap<String, Job>()

    private val _states = MutableStateFlow(voicePackCatalog.associate { it.id to initialState(it) })
    val states: StateFlow<Map<String, PackState>> = _states.asStateFlow()

    /** False when the engine can't load on this device (e.g. an x86 emulator). */
    val supported: Boolean by lazy {
        assumeSupported || runCatching { System.loadLibrary("sherpa-onnx-jni"); true }
            .onFailure { Log.w(TAG, "Voice engine not available", it) }
            .getOrDefault(false)
    }

    private fun modelFile(pack: VoicePack) = File(dir, "${pack.id}.onnx")

    private fun initialState(pack: VoicePack): PackState =
        if (modelFile(pack).length() == pack.bytes) PackState.Installed else PackState.NotInstalled

    fun installed(): List<VoicePack> = voicePackCatalog.filter { _states.value[it.id] == PackState.Installed }

    fun find(voiceName: String?): VoicePack? =
        voiceName?.takeIf { it.startsWith(VoicePack.PREFIX) }?.let { name -> voicePackCatalog.firstOrNull { it.voiceName == name } }

    fun download(pack: VoicePack) {
        if (jobs[pack.id]?.isActive == true) return
        jobs[pack.id] = scope.launch {
            setState(pack, PackState.Downloading(0f))
            val tmp = File(dir, "${pack.id}.part")
            val result = runCatching {
                dir.mkdirs()
                val connection = URL(pack.url).openConnection() as HttpURLConnection
                connection.connectTimeout = 20_000
                connection.readTimeout = 30_000
                val digest = MessageDigest.getInstance("SHA-256")
                connection.inputStream.use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        var lastShown = 0L
                        while (isActive) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            total += n
                            if (total - lastShown > 512 * 1024) {
                                lastShown = total
                                setState(pack, PackState.Downloading(total.toFloat() / pack.bytes))
                            }
                        }
                    }
                }
                connection.disconnect()
                check(isActive) { "Cancelled" }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                check(hash == pack.sha256) { "Download was damaged — please try again" }
                check(tmp.renameTo(modelFile(pack))) { "Couldn't save the voice" }
            }
            tmp.delete()
            setState(
                pack,
                result.fold(
                    { PackState.Installed },
                    { if (isActive) PackState.Failed(it.message ?: "Download failed") else PackState.NotInstalled },
                ),
            )
        }
    }

    fun cancel(pack: VoicePack) {
        jobs.remove(pack.id)?.cancel()
        setState(pack, PackState.NotInstalled)
    }

    fun delete(pack: VoicePack) {
        cancel(pack)
        synchronized(engineLock) {
            if (loadedId == pack.id) {
                engine?.release()
                engine = null
                loadedId = null
            }
        }
        modelFile(pack).delete()
        setState(pack, PackState.NotInstalled)
    }

    private fun setState(pack: VoicePack, state: PackState) = _states.update { it + (pack.id to state) }

    private val engineLock = Any()
    private var engine: OfflineTts? = null
    private var loadedId: String? = null

    /**
     * Speaks [text] with [pack] into 16-bit PCM. [speed] 1.0 = normal. Slow (tens to hundreds of
     * milliseconds): call off the main thread. Returns null if the pack can't be used.
     */
    fun synthesize(pack: VoicePack, text: String, speed: Float): Pair<Int, ByteArray>? = synchronized(engineLock) {
        if (!supported || _states.value[pack.id] != PackState.Installed) return null
        runCatching {
            val tts = if (loadedId == pack.id) engine!! else {
                engine?.release()
                engine = null
                OfflineTts(
                    null, // load from files, not the APK
                    OfflineTtsConfig(
                        model = OfflineTtsModelConfig(
                            vits = OfflineTtsVitsModelConfig(
                                model = modelFile(pack).path,
                                tokens = sharedData().resolve("piper-tokens.txt").path,
                                dataDir = sharedData().resolve("espeak-ng-data").path,
                            ),
                            numThreads = 2,
                        ),
                    ),
                ).also { engine = it; loadedId = pack.id }
            }
            val audio = tts.generate(text, 0, speed)
            audio.sampleRate to toPcm16(audio.samples)
        }.onFailure { Log.e(TAG, "Voice pack failed", it) }.getOrNull()
    }

    /** Copies the bundled pronunciation data out of the APK once (the engine needs real files). */
    private fun sharedData(): File {
        val root = File(app.filesDir, "voice-data-v1")
        if (File(root, ".done").exists()) return root
        fun copy(path: String) {
            val children = app.assets.list(path).orEmpty()
            if (children.isEmpty()) {
                File(root, path).apply { parentFile?.mkdirs() }.outputStream().use { out -> app.assets.open(path).use { it.copyTo(out) } }
            } else {
                children.forEach { copy("$path/$it") }
            }
        }
        copy("espeak-ng-data")
        copy("piper-tokens.txt")
        File(root, ".done").createNewFile()
        return root
    }

    private companion object {
        const val TAG = "VoicePacks"

        fun toPcm16(samples: FloatArray): ByteArray {
            val out = ByteArray(samples.size * 2)
            for (i in samples.indices) {
                val v = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt()
                out[2 * i] = v.toByte()
                out[2 * i + 1] = (v shr 8).toByte()
            }
            return out
        }
    }
}
