package com.robbyolivam.aac.speech

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Records short clips (a parent's voice, a favorite sound) for a button. */
class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null

    val isRecording: Boolean get() = recorder != null

    fun start(output: File) {
        stop()
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(44_100)
        r.setAudioEncodingBitRate(96_000)
        r.setOutputFile(output.absolutePath)
        r.prepare()
        r.start()
        recorder = r
    }

    /** Returns true if a usable clip was written. */
    fun stop(): Boolean {
        val r = recorder ?: return false
        recorder = null
        return try {
            r.stop()
            true
        } catch (e: RuntimeException) {
            // Thrown when stop() is called before any audio was captured.
            false
        } finally {
            r.release()
        }
    }
}
