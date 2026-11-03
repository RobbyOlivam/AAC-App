package com.robbyolivam.aac.shared

/**
 * What each platform gives the shared screens, besides core's [com.robbyolivam.aac.core.platform.SpeechOutput]:
 * private storage, the app's bundled files, and opening/saving files the person chooses.
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
    /** Lets the person choose a file; null if they cancel. */
    suspend fun open(accept: String): Pair<String, ByteArray>?
    /** Hands a file to the person (share sheet or download); false if it didn't happen. */
    suspend fun save(name: String, bytes: ByteArray, mimeType: String): Boolean
}

class Platform(
    val storage: AppStorage,
    val assets: AppAssets,
    val files: FileExchange,
    val speech: com.robbyolivam.aac.core.platform.SpeechOutput,
    /** How to install or update the app on this device, shown in Settings; null to hide. */
    val installHelp: String? = null,
    val appVersion: String = "",
)
