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
