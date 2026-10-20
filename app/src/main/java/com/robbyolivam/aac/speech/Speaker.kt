package com.robbyolivam.aac.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.robbyolivam.aac.core.VoiceAge
import com.robbyolivam.aac.core.Wav
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Offline text-to-speech plus playback of recorded button audio. Teen and kid voice ages are
 * made by rendering speech to a file and playing it back faster (see [VoiceAge]); rendered
 * words are cached, since the same words get spoken over and over.
 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pending: (() -> Unit)? = null
    private var player: MediaPlayer? = null
    private var utteranceId = 0
    private val cacheDir = File(context.applicationContext.cacheDir, "speech")
    private var track: AudioTrack? = null

    /** The rendering we're waiting on, if any; older renderings finish silently into the cache. */
    @Volatile private var awaited: String? = null
    private val renders = java.util.concurrent.ConcurrentHashMap<String, Render>()

    private class Render(val tmp: File, val file: File, val formant: Float)

    private val _voices = MutableStateFlow<List<Voice>>(emptyList())
    val voices: StateFlow<List<Voice>> = _voices.asStateFlow()

    private val _available = MutableStateFlow(true)
    val available: StateFlow<Boolean> = _available.asStateFlow()

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
                if (awaited == id) play(render.file, render.formant)
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
        _voices.value = runCatching {
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
        pending?.invoke()
        pending = null
    }

    fun speak(text: String, rate: Float, pitch: Float, voiceName: String?, age: VoiceAge = VoiceAge.ADULT) {
        if (text.isBlank()) return
        stopAudio()
        val action = {
            applyVoice(voiceName)
            if (age == VoiceAge.ADULT) {
                tts.setSpeechRate(rate)
                tts.setPitch(pitch)
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "u${utteranceId++}")
            } else {
                speakAged(text, rate, pitch, age)
            }
            Unit
        }
        if (ready) action() else pending = action
    }

    private fun speakAged(text: String, rate: Float, pitch: Float, age: VoiceAge) {
        tts.stop()
        val file = File(cacheDir, cacheKey(text, rate, pitch, age, appliedVoice) + ".wav")
        if (file.exists()) {
            awaited = null
            file.setLastModified(System.currentTimeMillis())
            play(file, age.formant)
            return
        }
        cacheDir.mkdirs()
        val id = "a${utteranceId++}"
        val render = Render(File(cacheDir, "$id.tmp"), file, age.formant)
        renders[id] = render
        awaited = id
        tts.setSpeechRate(age.engineRate(rate))
        tts.setPitch(age.enginePitch(pitch))
        if (tts.synthesizeToFile(text, Bundle(), render.tmp, id) != TextToSpeech.SUCCESS) {
            renders.remove(id)
            awaited = null
            // Never go silent: say it in the normal voice instead.
            tts.setSpeechRate(rate)
            tts.setPitch(pitch)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "u${utteranceId++}")
        }
        trimCache()
    }

    /** Plays a rendered WAV [speedUp] times faster, which raises pitch and formants together. */
    private fun play(file: File, speedUp: Float) {
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
        val voice = voiceName?.let { name -> _voices.value.firstOrNull { it.name == name } }
        val ok = voice != null && tts.setVoice(voice) == TextToSpeech.SUCCESS
        if (!ok) {
            val default = runCatching { tts.defaultVoice }.getOrNull()
            if (default == null || tts.setVoice(default) != TextToSpeech.SUCCESS) tts.language = Locale.getDefault()
        }
        appliedVoice = if (ok) voiceName else null
        voiceApplied = true
    }

    fun playFile(file: File) {
        if (!file.exists()) return
        tts.stop()
        stopAudio()
        player = MediaPlayer().apply {
            setAudioAttributes(attributes)
            setDataSource(file.absolutePath)
            setOnCompletionListener { it.release(); if (player === it) player = null }
            prepare()
            start()
        }
    }

    fun stop() {
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

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    private companion object {
        const val TAG = "Speaker"
        const val MAX_CACHED = 300
    }
}
