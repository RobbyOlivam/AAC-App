package com.robbyolivam.aac.shared

import com.robbyolivam.aac.core.platform.SpeechOutput

/**
 * What each platform gives the shared app, besides core's [SpeechOutput]: private storage, the
 * app's bundled files, files the person opens or saves, pictures, recordings and a few device
 * services. The web app implements these in web/ (platform.js); a native iPad app would use
 * Apple's APIs; Android has its own data layer today and can move onto these.
 */
interface AppStorage {
    /** Files the app keeps (vocabulary.json, settings.json, media/…), by relative path. */
    suspend fun read(path: String): ByteArray?
    suspend fun write(path: String, bytes: ByteArray)
    suspend fun delete(path: String)
    suspend fun list(prefix: String): List<String>
}

interface AppAssets {
    /** A file shipped with the app, e.g. "symbols/index.tsv" or "symbols/m/apple.svg". */
    suspend fun read(path: String): ByteArray?
}

interface FileExchange {
    /** Lets the person choose a file; null if they cancel. [camera] asks for a new photo instead. */
    suspend fun open(accept: String, camera: Boolean = false): Pair<String, ByteArray>?
    /** Hands a file to the person (share sheet or download); false if it didn't happen. */
    suspend fun save(name: String, bytes: ByteArray, mimeType: String): Boolean
}

/** A part of a picture, in fractions (0..1) of its width and height. */
data class CropRect(val left: Float, val top: Float, val width: Float, val height: Float)

interface ImageTools {
    /** The picture's size in pixels, or null if it isn't a picture this device can read. */
    suspend fun size(bytes: ByteArray): Pair<Int, Int>?
    /** A JPEG of [crop] of the picture (all of it when null), no bigger than [maxSize] on its long side. */
    suspend fun jpeg(bytes: ByteArray, maxSize: Int, crop: CropRect? = null): ByteArray?
}

interface Recorder {
    /** Starts recording from the microphone (asking permission first); false if it can't. */
    suspend fun start(): Boolean
    /** Stops and returns the recording, or null if nothing was recorded. */
    suspend fun stop(): ByteArray?
    val isRecording: Boolean
}

interface DeviceServices {
    fun nowMillis(): Long
    /** The device's time zone offset from UTC right now, in milliseconds. */
    fun utcOffsetMillis(): Long
    fun keepScreenOn(on: Boolean)
    /** Shares text (share sheet); false where that isn't possible. */
    fun shareText(text: String): Boolean
    /** A short vibration, where the device can. */
    fun vibrate() {}
}

class Platform(
    val storage: AppStorage,
    val assets: AppAssets,
    val files: FileExchange,
    val speech: SpeechOutput,
    val images: ImageTools,
    val recorder: Recorder?,
    val device: DeviceServices,
    /** How to install or update the app on this device, shown in Settings; null to hide. */
    val installHelp: String? = null,
    val appVersion: String = "",
    /** Things this platform can't do, so Settings can leave them out. */
    val canVibrate: Boolean = false,
    /** Whether the device can draw emoji as text (the web can't: no emoji font). */
    val hasEmojiFont: Boolean = false,
    val canPrintPdf: Boolean = false,
)
