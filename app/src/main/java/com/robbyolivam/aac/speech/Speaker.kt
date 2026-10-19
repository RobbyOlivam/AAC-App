package com.robbyolivam.aac.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale

/** Offline text-to-speech plus playback of recorded button audio. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pending: (() -> Unit)? = null
    private var player: MediaPlayer? = null
    private var utteranceId = 0

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

    fun speak(text: String, rate: Float, pitch: Float, voiceName: String?) {
        if (text.isBlank()) return
        stopAudio()
        val action = {
            tts.setSpeechRate(rate)
            tts.setPitch(pitch)
            applyVoice(voiceName)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "u${utteranceId++}")
            Unit
        }
        if (ready) action() else pending = action
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
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    private companion object {
        const val TAG = "Speaker"
    }
}
