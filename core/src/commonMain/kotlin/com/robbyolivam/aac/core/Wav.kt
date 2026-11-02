package com.robbyolivam.aac.core

/** 16-bit PCM audio read from a WAV file. */
class Pcm(val sampleRate: Int, val channels: Int, val data: ByteArray) {
    val frames: Int get() = data.size / (2 * channels)
}

/**
 * Minimal WAV reader for the files text-to-speech engines write: RIFF/WAVE, 16-bit PCM.
 * Returns null for anything else so callers can fall back to normal speech.
 */
object Wav {
    fun parse(bytes: ByteArray): Pcm? {
        if (bytes.size < 12 || bytes.ascii(0) != "RIFF" || bytes.ascii(8) != "WAVE") return null
        var pos = 12
        var sampleRate = 0
        var channels = 0
        var bits = 0
        while (pos + 8 <= bytes.size) {
            val id = bytes.ascii(pos)
            // Engines that stream sometimes leave the size as 0 or 0xFFFFFFFF; treat as "to end".
            val declared = bytes.int32(pos + 4).toLong() and 0xFFFFFFFFL
            val start = pos + 8
            val end = if (declared == 0L || start + declared > bytes.size) bytes.size else (start + declared).toInt()
            when (id) {
                "fmt " -> {
                    if (end - start < 16 || bytes.int16(start) != 1) return null // PCM only
                    channels = bytes.int16(start + 2)
                    sampleRate = bytes.int32(start + 4)
                    bits = bytes.int16(start + 14)
                }
                "data" -> {
                    if (bits != 16 || channels !in 1..2 || sampleRate <= 0) return null
                    val length = (end - start) / (2 * channels) * (2 * channels)
                    return Pcm(sampleRate, channels, bytes.copyOfRange(start, start + length))
                }
            }
            pos = end + ((end - start) and 1) // chunks are word-aligned
        }
        return null
    }

    private fun ByteArray.ascii(at: Int) = decodeToString(at, at + 4)
    private fun ByteArray.int16(at: Int) = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)
    private fun ByteArray.int32(at: Int) = int16(at) or (int16(at + 2) shl 16)
}

/**
 * Makes a voice sound younger. [formant] scales the whole voice up (vocal-tract size — the
 * biggest cue for a child's voice) by playing the audio faster; the engine speaks slower by the
 * same amount first, so the speed doesn't change. [pitch] is the overall rise in voice pitch.
 */
enum class VoiceAge(val label: String, val formant: Float, val pitch: Float) {
    ADULT("Adult", 1.0f, 1.0f),
    TEEN("Teen", 1.08f, 1.15f),
    KID("Kid", 1.2f, 1.45f);

    /** Rate to ask the engine for, so the sped-up playback lands on [rate]. */
    fun engineRate(rate: Float) = rate / formant

    /** Pitch to ask the engine for; playback raises it by [formant] on top. */
    fun enginePitch(pitch: Float) = pitch * this.pitch / formant
}

/**
 * Ways to say a message. The phone's speech engines don't take emotion directly, so each tone
 * is speed, pitch, loudness and end punctuation (which changes the engine's intonation).
 */
enum class Tone(
    val label: String,
    val emoji: String,
    val rate: Float,
    val pitch: Float,
    /** Multiplies the volume setting; [LOUD] always plays at full volume. */
    val gain: Float,
    private val ending: String,
) {
    QUESTION("Question", "❓", 1.0f, 1.08f, 1.0f, "?"),
    EXCITED("Excited", "😃", 1.12f, 1.2f, 1.0f, "!"),
    WHISPER("Whisper", "🤫", 0.9f, 0.92f, 0.35f, "."),
    LOUD("Loud", "📢", 1.0f, 1.1f, 1.0f, "!"),
    SAD("Sad", "😢", 0.8f, 0.85f, 0.8f, "."),
    SARCASTIC("Sarcastic", "🙄", 0.8f, 0.95f, 1.0f, "."),
    ;

    /** The message with this tone's ending (replacing any end punctuation already there). */
    fun apply(text: String): String {
        val body = text.trim().trimEnd('.', '!', '?', ',', ';', ':')
        return if (body.isEmpty()) "" else body + ending
    }

    fun volume(setting: Float): Float = if (this == LOUD) 1f else (setting * gain).coerceIn(0f, 1f)
}
