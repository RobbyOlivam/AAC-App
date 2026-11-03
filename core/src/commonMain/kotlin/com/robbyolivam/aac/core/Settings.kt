package com.robbyolivam.aac.core

import kotlinx.serialization.Serializable

// Settings shared by every version of the app (Android, web, iOS); each platform stores them
// as JSON, and backups carry the same JSON between devices.

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
    /** 0–1, on top of the phone's media volume. */
    val volume: Float = 1.0f,
    val voiceName: String? = null,
    /** Makes any voice sound like a teen or a kid. */
    val voiceAge: VoiceAge = VoiceAge.ADULT,
    val speakOnSelect: Boolean = true,
    val speakLettersWhileTyping: Boolean = false,
    /** A row of tone buttons (question, excited, whisper…) that say the message in that tone. */
    val showToneButtons: Boolean = false,
    /**
     * Private earbud: scanning cues and previews go to a connected headset or earbud, while real
     * speech always comes out of the phone's speaker.
     */
    val earbudPreview: Boolean = false,
    /** First tap on a board button says it privately (preview); a second tap uses it. */
    val previewTaps: Boolean = false,
    // Touch access
    val holdTimeMs: Int = 0,
    val acceptOnRelease: Boolean = true,
    val debounceMs: Int = 0,
    /** Slide a finger over the board; a button is chosen after resting on it this long. 0 = off. */
    val dwellMs: Int = 0,
    /** The small gaps between buttons count as touches on the nearest button. */
    val touchGaps: Boolean = false,
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
    /** Adds an Explore button to the menu that shows hidden words for a while. */
    val showExploreButton: Boolean = false,
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
    // Usage log (opt-in; stays on this device)
    val usageLogging: Boolean = false,
    /** Privacy pause: the user can stop logging any time from the main screen. */
    val usagePaused: Boolean = false,
    /** Whether the first-run "take the tour?" offer has been shown. */
    val tutorialOffered: Boolean = false,
    // Device
    val settingsLock: SettingsLock = SettingsLock.HOLD,
    val pin: String = "",
    val keepScreenOn: Boolean = false,
    val showOnLockScreen: Boolean = false,
)
