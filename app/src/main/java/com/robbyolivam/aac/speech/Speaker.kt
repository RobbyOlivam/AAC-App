package com.robbyolivam.aac.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.robbyolivam.aac.core.VoiceAge
import com.robbyolivam.aac.core.platform.AudioOutput
import com.robbyolivam.aac.core.platform.SpeechOutput
import com.robbyolivam.aac.core.platform.VoiceInfo
import com.robbyolivam.aac.core.Wav
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Offline text-to-speech plus playback of recorded button audio. Teen and kid voice ages, and
 * speech sent to a particular output (earbud or speaker), are made by rendering speech to a
 * file and playing it with AudioTrack (see [VoiceAge]); rendered words are cached, since the
 * same words get spoken over and over.
 */
class Speaker(context: Context) : TextToSpeech.OnInitListener, SpeechOutput {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)

    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pending: (() -> Unit)? = null
    private var player: MediaPlayer? = null
    private var utteranceId = 0
    private val cacheDir = File(context.applicationContext.cacheDir, "speech")
    private var track: AudioTrack? = null

    /** 0–1, applied to speech, rendered voices and recorded button audio. */
    @Volatile override var volume: Float = 1f

    private fun params(level: Float) = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, level) }

    /** The rendering we're waiting on, if any; older renderings finish silently into the cache. */
    @Volatile private var awaited: String? = null
    private val renders = java.util.concurrent.ConcurrentHashMap<String, Render>()

    private class Render(val tmp: File, val file: File, val formant: Float, val output: AudioOutput, val level: Float)

    /** The engine's voices (to switch to), and the same list as shown to the person. */
    private var engineVoices: List<Voice> = emptyList()
    private val _voices = MutableStateFlow<List<VoiceInfo>>(emptyList())
    override val voices: StateFlow<List<VoiceInfo>> = _voices.asStateFlow()

    private val _available = MutableStateFlow(true)
    override val available: StateFlow<Boolean> = _available.asStateFlow()

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TextToSpeech init failed: $status")
            _available.value = false
            return
        }
        ready = true
        tts.setAudioAttributes(attributes)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) = Unit
            override fun onDone(id: String) {
                val render = renders.remove(id) ?: return
                if (!render.tmp.renameTo(render.file)) return
                if (awaited == id) play(render.file, render.formant, render.output, render.level)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String) = onError(id, TextToSpeech.ERROR)
            override fun onError(id: String, errorCode: Int) {
                renders.remove(id)?.tmp?.delete()
            }
            override fun onStop(id: String, interrupted: Boolean) {
                renders.remove(id)?.tmp?.delete()
            }
        })
        if (tts.language == null) tts.language = Locale.getDefault()
        engineVoices = runCatching {
            tts.voices.orEmpty()
                .filter { v ->
                    // Only voices that work offline right now. A "not installed" voice makes the
                    // engine download in the background and speak with a fallback (or not at all).
                    !v.isNetworkConnectionRequired &&
                        TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in v.features.orEmpty() &&
                        v.locale.language == Locale.getDefault().language
                }
                .sortedWith(compareBy({ it.locale.toString() }, { it.name }))
        }.getOrDefault(emptyList())
        _voices.value = engineVoices.map { v ->
            VoiceInfo(id = v.name, displayName = v.name.removePrefix("${v.locale.language}-"), language = v.locale.toString())
        }
        pending?.invoke()
        pending = null
    }

    /**
     * Says [text]. [output] picks where it plays; [level] overrides the volume setting (for
     * tones such as whisper). Anything other than the default output and adult voice is rendered
     * first, which adds a short delay the first time a word is said.
     */
    override fun speak(
        text: String,
        rate: Float,
        pitch: Float,
        voiceId: String?,
        age: VoiceAge,
        output: AudioOutput,
        level: Float?,
    ) {
        if (text.isBlank()) return
        val voiceName = voiceId
        val level = level ?: volume
        stopAudio()
        val action = {
            applyVoice(voiceName)
            val target = output.takeIf { it == AudioOutput.DEFAULT || device(it) != null } ?: AudioOutput.DEFAULT
            if (age == VoiceAge.ADULT && target == AudioOutput.DEFAULT) {
                tts.setSpeechRate(rate)
                tts.setPitch(pitch)
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, params(level), "u${utteranceId++}")
            } else {
                speakRendered(text, rate, pitch, age, target, level)
            }
            Unit
        }
        if (ready) action() else pending = action
    }

    /** Whether a headset or earbud (wired, USB or Bluetooth) is connected right now. */
    override fun earbudConnected(): Boolean = device(AudioOutput.EARBUD) != null

    private fun device(output: AudioOutput): AudioDeviceInfo? {
        val devices = runCatching { audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrNull().orEmpty()
        return when (output) {
            AudioOutput.DEFAULT -> null
            AudioOutput.SPEAKER -> devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            AudioOutput.EARBUD -> devices.firstOrNull { it.type in EARBUD_TYPES }
        }
    }

    private fun speakRendered(text: String, rate: Float, pitch: Float, age: VoiceAge, output: AudioOutput, level: Float) {
        tts.stop()
        val file = File(cacheDir, cacheKey(text, rate, pitch, age, appliedVoice) + ".wav")
        if (file.exists()) {
            awaited = null
            file.setLastModified(System.currentTimeMillis())
            play(file, age.formant, output, level)
            return
        }
        cacheDir.mkdirs()
        val id = "a${utteranceId++}"
        val render = Render(File(cacheDir, "$id.tmp"), file, age.formant, output, level)
        renders[id] = render
        awaited = id
        tts.setSpeechRate(age.engineRate(rate))
        tts.setPitch(age.enginePitch(pitch))
        if (tts.synthesizeToFile(text, Bundle(), render.tmp, id) != TextToSpeech.SUCCESS) {
            renders.remove(id)
            awaited = null
            // Never go silent: say it the ordinary way instead.
            tts.setSpeechRate(rate)
            tts.setPitch(pitch)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params(level), "u${utteranceId++}")
        }
        trimCache()
    }

    /**
     * Plays a rendered WAV [speedUp] times faster (which raises pitch and formants together) on
     * [output].
     */
    private fun play(file: File, speedUp: Float, output: AudioOutput, level: Float) {
        val pcm = runCatching { Wav.parse(file.readBytes()) }.getOrNull()
        if (pcm == null || pcm.frames == 0) {
            file.delete()
            return
        }
        val newTrack = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate((pcm.sampleRate * speedUp).roundToInt())
                        .setChannelMask(if (pcm.channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.data.size)
                .build()
                .apply {
                    write(pcm.data, 0, pcm.data.size)
                    setVolume(level)
                    device(output)?.let { setPreferredDevice(it) }
                    play()
                }
        }.onFailure { Log.e(TAG, "Playback failed", it) }.getOrNull() ?: return
        synchronized(this) {
            track?.let { runCatching { it.stop() }; it.release() }
            track = newTrack
        }
    }

    private fun cacheKey(text: String, rate: Float, pitch: Float, age: VoiceAge, voice: String?): String {
        val digest = MessageDigest.getInstance("SHA-1").digest("$text|$rate|$pitch|$age|$voice".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Keeps the most recently used renderings (a few MB); words used often stay instant. */
    private fun trimCache() {
        val files = cacheDir.listFiles { f -> f.name.endsWith(".wav") } ?: return
        if (files.size <= MAX_CACHED) return
        files.sortedByDescending { it.lastModified() }.drop(MAX_CACHED).forEach { it.delete() }
    }

    /** Name of the voice the engine was last told to use; null = engine default. */
    private var appliedVoice: String? = null
    private var voiceApplied = false

    /**
     * Applies [voiceName] (null = default) before every utterance. If the chosen voice can't be
     * used, falls back to the default voice so the app never goes silent.
     */
    private fun applyVoice(voiceName: String?) {
        if (voiceApplied && voiceName == appliedVoice) return
        val voice = voiceName?.let { name -> engineVoices.firstOrNull { it.name == name } }
        val ok = voice != null && tts.setVoice(voice) == TextToSpeech.SUCCESS
        if (!ok) {
            val default = runCatching { tts.defaultVoice }.getOrNull()
            if (default == null || tts.setVoice(default) != TextToSpeech.SUCCESS) tts.language = Locale.getDefault()
        }
        appliedVoice = if (ok) voiceName else null
        voiceApplied = true
    }

    override fun playRecording(path: String, output: AudioOutput) {
        val file = File(path)
        if (!file.exists()) return
        tts.stop()
        stopAudio()
        player = MediaPlayer().apply {
            setAudioAttributes(attributes)
            setDataSource(file.absolutePath)
            setVolume(volume, volume)
            if (android.os.Build.VERSION.SDK_INT >= 28) device(output)?.let { setPreferredDevice(it) }
            setOnCompletionListener { it.release(); if (player === it) player = null }
            prepare()
            start()
        }
    }

    override fun stop() {
        tts.stop()
        stopAudio()
    }

    private fun stopAudio() {
        awaited = null
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        synchronized(this) {
            track?.let { runCatching { it.stop() }; it.release() }
            track = null
        }
    }

    override fun shutdown() {
        stop()
        tts.shutdown()
    }

    private companion object {
        const val TAG = "Speaker"
        const val MAX_CACHED = 300
        val EARBUD_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
    }
}
