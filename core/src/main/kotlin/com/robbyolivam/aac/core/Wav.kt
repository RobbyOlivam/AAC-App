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

    /** A 16-bit PCM WAV file holding [pcm]. */
    fun write(pcm: Pcm): ByteArray {
        val out = java.io.ByteArrayOutputStream(44 + pcm.data.size)
        fun int16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        fun int32(v: Int) { int16(v and 0xFFFF); int16((v ushr 16) and 0xFFFF) }
        out.write("RIFF".toByteArray()); int32(36 + pcm.data.size); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); int32(16); int16(1); int16(pcm.channels); int32(pcm.sampleRate)
        int32(pcm.sampleRate * pcm.channels * 2); int16(pcm.channels * 2); int16(16)
        out.write("data".toByteArray()); int32(pcm.data.size); out.write(pcm.data)
        return out.toByteArray()
    }

    private fun ByteArray.ascii(at: Int) = String(this, at, 4, Charsets.US_ASCII)
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
