package com.robbyolivam.aac.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class KeyboardLayout { QWERTY, ABC }

@Serializable
enum class OrientationMode { AUTO, PORTRAIT, LANDSCAPE }

/** LIBRARY = bundled Mulberry/OpenMoji symbols; EMOJI = the phone's own emoji font. */
@Serializable
enum class SymbolStyle { LIBRARY, EMOJI }

/** OFF, one-switch automatic scanning, or two-switch step scanning (move + select). */
@Serializable
enum class ScanMode { OFF, AUTO, STEP }

/** How settings and edit mode are protected from accidental (or curious) taps. */
@Serializable
enum class SettingsLock { NONE, HOLD, PIN }

@Serializable
data class Settings(
    // Voice
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val voiceName: String? = null,
    val speakOnSelect: Boolean = true,
    val speakLettersWhileTyping: Boolean = false,
    // Touch access
    val holdTimeMs: Int = 0,
    val acceptOnRelease: Boolean = true,
    val debounceMs: Int = 0,
    val haptics: Boolean = true,
    // Board
    val returnHomeAfterFolderWord: Boolean = true,
    val showPredictions: Boolean = true,
    val showLabels: Boolean = true,
    val labelScale: Float = 1.0f,
    val grammarHelper: Boolean = true,
    val keyboardLayout: KeyboardLayout = KeyboardLayout.QWERTY,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 1–4; words above this level are hidden in place. */
    val vocabularyLevel: Int = 4,
    /** Black buttons with colored borders, for low vision / cortical visual impairment. */
    val highContrast: Boolean = false,
    val symbolStyle: SymbolStyle = SymbolStyle.LIBRARY,
    val orientation: OrientationMode = OrientationMode.AUTO,
    // Switch scanning
    val scanMode: ScanMode = ScanMode.OFF,
    val scanIntervalMs: Int = 1500,
    val scanLoops: Int = 3,
    val scanSpeakItems: Boolean = false,
    /** Treat a tap anywhere on the screen as a switch press (no Bluetooth switch needed). */
    val scanTouchAnywhere: Boolean = true,
    // Device
    val settingsLock: SettingsLock = SettingsLock.HOLD,
    val pin: String = "",
    val keepScreenOn: Boolean = false,
    val showOnLockScreen: Boolean = false,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private fun load(): Settings =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(Settings.serializer(), it) }.getOrNull() }
            ?: Settings()

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit().putString(KEY, json.encodeToString(Settings.serializer(), next)).apply()
    }

    fun exportJson(): String = json.encodeToString(Settings.serializer(), _settings.value)

    fun importJson(text: String) {
        val imported = json.decodeFromString(Settings.serializer(), text)
        update { imported }
    }

    private companion object {
        const val KEY = "settings_json"
    }
}
