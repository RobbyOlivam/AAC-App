package com.robbyolivam.aac.core.platform

import com.robbyolivam.aac.core.VoiceAge
import kotlinx.coroutines.flow.StateFlow

/**
 * What each platform provides to the shared app. Android implements these today; the iOS app
 * implements the same interfaces (see docs/iOS-port.md), so the screens and logic above them
 * don't change between platforms.
 */

/** Where sound goes: wherever the system routes it, the device's own speaker, or a private earbud. */
enum class AudioOutput { DEFAULT, SPEAKER, EARBUD }

/** A voice the device can speak with. [id] is what gets saved in settings. */
data class VoiceInfo(val id: String, val displayName: String, val language: String)

/**
 * Speech and recorded-audio playback.
 * Android: TextToSpeech + AudioTrack/MediaPlayer (`Speaker`). iOS: AVSpeechSynthesizer + AVAudioPlayer.
 */
interface SpeechOutput {
    /** Offline voices for the device's language, ready to use now. */
    val voices: StateFlow<List<VoiceInfo>>

    /** False when the device has no working speech engine (the app shows how to get one). */
    val available: StateFlow<Boolean>

    /** 0–1, on top of the device's media volume. */
    var volume: Float

    /**
     * Says [text]. [age] makes the voice sound younger; [output] picks where it plays; [level]
     * overrides [volume] for this one utterance (tones such as whisper).
     */
    fun speak(
        text: String,
        rate: Float,
        pitch: Float,
        voiceId: String?,
        age: VoiceAge = VoiceAge.ADULT,
        output: AudioOutput = AudioOutput.DEFAULT,
        level: Float? = null,
    )

    /** Plays a button's recorded audio from [path] (an absolute file path). */
    fun playRecording(path: String, output: AudioOutput = AudioOutput.DEFAULT)

    /** Whether a headset or earbud is connected right now. */
    fun earbudConnected(): Boolean

    fun stop()

    fun shutdown()
}

/**
 * Keeps a child in the app.
 * Android: screen pinning (lock task). iOS: Guided Access (`UIAccessibility.requestGuidedAccessSession`).
 */
interface DeviceLock {
    val isLocked: Boolean

    /** Asks the system to lock to this app. It may need a system setting turned on first ([setupHint]). */
    fun lock()

    fun unlock()

    /** Where to turn the system feature on if locking doesn't start. */
    val setupHint: String
}
