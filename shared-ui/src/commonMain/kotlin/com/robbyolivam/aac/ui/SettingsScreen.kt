package com.robbyolivam.aac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.robbyolivam.aac.core.VoiceAge
import com.robbyolivam.aac.core.KeyboardLayout
import com.robbyolivam.aac.core.OrientationMode
import com.robbyolivam.aac.core.ScanMode
import com.robbyolivam.aac.core.UsageLog
import com.robbyolivam.aac.core.Zip
import com.robbyolivam.aac.data.BackupManager
import com.robbyolivam.aac.core.Settings
import com.robbyolivam.aac.core.SettingsLock
import com.robbyolivam.aac.core.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: AacViewModel) {
    val settings by vm.settings.collectAsState()
    val voices by vm.speaker.voices.collectAsState()
    val ttsAvailable by vm.speaker.available.collectAsState()
    val scope = rememberCoroutineScope()
    val platform = vm.platform
    var showSnapshots by remember { mutableStateOf(false) }
    var showPronunciations by remember { mutableStateOf(false) }
    var showAbbreviations by remember { mutableStateOf(false) }
    var settingPin by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    fun update(transform: (Settings) -> Settings) = vm.updateSettings(transform)
    fun toast(msg: String) { vm.notice = msg }
    fun today() = UsageLog.isoUtc(platform.device.nowMillis() + platform.device.utcOffsetMillis()).take(10)

    fun saveBackup() = vm.runBusy("Making a backup…", "Couldn't save the backup") {
        if (platform.files.save("aac-backup-${today()}.zip", vm.backup.export(), "application/zip")) "Backup saved" else null
    }
    fun restoreBackup() = scope.launch {
        val (name, bytes) = platform.files.open(".zip,application/zip") ?: return@launch
        vm.runBusy("Reading $name…", "That file isn't a backup from this app") {
            vm.backup.import(Zip.read(bytes))
            "Backup restored"
        }
    }
    fun exportOpenBoard() = vm.runBusy("Making the Open Board file…", "Couldn't save the file") {
        if (platform.files.save("aac-boards.obz", vm.openBoard.export(), "application/zip")) "Open Board file saved" else null
    }
    fun importBoards() = scope.launch {
        val (name, bytes) = platform.files.open(".zip,.ce,.obz,.obf,application/zip") ?: return@launch
        vm.runBusy("Reading $name…", "That file isn't a board file this app can read (.obz, .obf, TouchChat .ce or a backup)") {
            val files = if (Zip.isZip(bytes)) Zip.read(bytes) else mapOf("board.obf" to bytes)
            // A backup made on any device restores here too.
            if (BackupManager.isBackup(files)) { vm.backup.import(files); "Backup restored" } else vm.openBoard.import(files)
        }
    }

    BackHandler { vm.screen = Screen.MAIN }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { vm.screen = Screen.MAIN }) {
                Icon(Icons.Default.ArrowBack, null)
                Text("  Back to talking")
            }
            Text("  Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureCard(
                    icon = Icons.Default.Edit,
                    title = "Edit boards",
                    subtitle = "Add, change, move or hide buttons and pages",
                    onClick = vm::enterEditMode,
                    modifier = Modifier.weight(1.3f),
                    primary = true,
                )
                FeatureCard(
                    icon = Icons.Default.HelpOutline,
                    title = "How to use",
                    subtitle = "Take the tour or read the guide",
                    onClick = { vm.screen = Screen.HELP },
                    modifier = Modifier.weight(1f),
                )
            }

            Header("Voice")
            if (!ttsAvailable) {
                Text("This browser can't speak. Use Safari on iPad, or Chrome or Edge on a computer.", color = MaterialTheme.colorScheme.error)
            }
            SliderSetting("Speed", settings.speechRate, 0.5f..2.0f, fmt(settings.speechRate, 1) + "×") { v -> update { it.copy(speechRate = v) } }
            SliderSetting("Pitch", settings.pitch, 0.5f..2.0f, fmt(settings.pitch, 1)) { v -> update { it.copy(pitch = v) } }
            SliderSetting("Volume", settings.volume, 0.1f..1.0f, fmt(settings.volume * 100, 0) + "%") { v -> update { it.copy(volume = v) } }
            Text(
                "On top of the device's volume. If speech is quiet at 100%, turn up the device's volume.",
                style = MaterialTheme.typography.bodySmall,
            )
            // The device's voices, English first (an iPad lists dozens of languages).
            val shownVoices = voices.filter { it.language.startsWith("en") }.sortedBy { it.displayName }.ifEmpty { voices }
            if (shownVoices.isNotEmpty()) {
                Text("Voice", fontWeight = FontWeight.SemiBold)
                ChipFlow {
                    FilterChip(settings.voiceName == null, { update { it.copy(voiceName = null) } }, { Text("Default") })
                    shownVoices.forEach { voice ->
                        FilterChip(settings.voiceName == voice.id, {
                            update { it.copy(voiceName = voice.id) }
                            vm.speaker.speak("Hi, this is my voice", settings.speechRate, settings.pitch, voice.id, settings.voiceAge)
                        }, { Text(voice.displayName.take(28)) })
                    }
                }
            }
            Text("Voice age", fontWeight = FontWeight.SemiBold)
            ChipFlow {
                VoiceAge.entries.forEach { age ->
                    FilterChip(settings.voiceAge == age, {
                        update { it.copy(voiceAge = age) }
                        vm.speaker.speak("Hi, this is my voice", settings.speechRate, settings.pitch, settings.voiceName, age)
                    }, { Text(age.label) })
                }
            }
            Text(
                "Teen and Kid make the voice sound younger by raising its pitch (the iPad's voices can't be reshaped further).",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "More voices: iPad Settings → Accessibility → Spoken Content → Voices (download an Enhanced or Premium voice).",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.outline,
            )
            OutlinedButton(onClick = { vm.speak("Hello! This is how I sound.") }) { Text("Test voice") }
            ToggleSetting("Speak each word when tapped", settings.speakOnSelect) { v -> update { it.copy(speakOnSelect = v) } }
            ToggleSetting("Speak letters and words while typing", settings.speakLettersWhileTyping) { v -> update { it.copy(speakLettersWhileTyping = v) } }
            ToggleSetting("Show tone buttons (question, excited, whisper…)", settings.showToneButtons) { v -> update { it.copy(showToneButtons = v) } }
            Text(
                "A row under the message: tap a tone to say the message that way. Tones change speed, pitch and loudness; " +
                    "the device's voices can't truly whisper or sound sarcastic, so these are approximations.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.outline,
            )
            ToggleSetting("Preview on first tap (tap again to use)", settings.previewTaps) { v -> update { it.copy(previewTaps = v) } }
            Text(
                "The first tap on a board button says it; a second tap on the same button uses it. " +
                    "The quick strip (yes, no, help…) always works on the first tap.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.outline,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showPronunciations = true }) { Text("Pronunciations…") }
                OutlinedButton(onClick = { showAbbreviations = true }) { Text("Abbreviations…") }
            }

            Header("Touch")
            SliderSetting(
                "Hold time (ignore quick brushes)", settings.holdTimeMs.toFloat(), 0f..2000f,
                if (settings.holdTimeMs == 0) "Off" else "${settings.holdTimeMs} ms", steps = 19,
            ) { v -> update { it.copy(holdTimeMs = (v / 100).roundToInt() * 100) } }
            ToggleSetting("Select when finger lifts (not when it lands)", settings.acceptOnRelease) { v -> update { it.copy(acceptOnRelease = v) } }
            SliderSetting(
                "Ignore repeated taps for", settings.debounceMs.toFloat(), 0f..2000f,
                if (settings.debounceMs == 0) "Off" else "${settings.debounceMs} ms", steps = 19,
            ) { v -> update { it.copy(debounceMs = (v / 100).roundToInt() * 100) } }
            ToggleSetting("Spaces between buttons count as touches", settings.touchGaps) { v -> update { it.copy(touchGaps = v) } }
            SliderSetting(
                "Slide to select (rest on a button)", settings.dwellMs.toFloat(), 0f..3000f,
                if (settings.dwellMs == 0) "Off" else "${settings.dwellMs} ms", steps = 29,
            ) { v -> update { it.copy(dwellMs = (v / 100).roundToInt() * 100) } }
            Text(
                "With slide to select on, keep a finger on the board and slide; a button is chosen after the finger rests on it this long. " +
                    "Lifting the finger cancels. Works on word pages (not photo scenes or the menu).",
                style = MaterialTheme.typography.bodySmall,
            )
            if (platform.canVibrate) ToggleSetting("Vibrate on tap", settings.haptics) { v -> update { it.copy(haptics = v) } }

            Header("Vocabulary level")
            ChipFlow {
                listOf(1 to "1 · First words", 2 to "2 · Early", 3 to "3 · Growing", 4 to "4 · All").forEach { (l, name) ->
                    FilterChip(settings.vocabularyLevel == l, { update { it.copy(vocabularyLevel = l) } }, { Text(name) })
                }
            }
            Text(
                "Lower levels hide words in place. Nothing moves when you go up a level, so motor memory carries over.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.outline,
            )
            ToggleSetting("Show Explore button", settings.showExploreButton) { v -> update { it.copy(showExploreButton = v) } }
            Text(
                "Explore shows hidden words (faded) for a while so they can be tried or modelled, without changing the level.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.outline,
            )

            Header("Board")
            ToggleSetting("Go back to Core after picking a folder word", settings.returnHomeAfterFolderWord) { v -> update { it.copy(returnHomeAfterFolderWord = v) } }
            ToggleSetting("Show word predictions", settings.showPredictions) { v -> update { it.copy(showPredictions = v) } }
            ToggleSetting("Suggest full sentences (grammar helper)", settings.grammarHelper) { v -> update { it.copy(grammarHelper = v) } }
            ToggleSetting("Show labels", settings.showLabels) { v -> update { it.copy(showLabels = v) } }
            SliderSetting("Label size", settings.labelScale, 0.7f..1.6f, fmt(settings.labelScale * 100, 0) + "%") { v -> update { it.copy(labelScale = v) } }
            Text("Keyboard", fontWeight = FontWeight.SemiBold)
            ChipFlow {
                KeyboardLayout.entries.forEach { k ->
                    FilterChip(settings.keyboardLayout == k, { update { it.copy(keyboardLayout = k) } }, { Text(k.name) })
                }
            }
            Text(
                "To keep the screen one way round, use the iPad's rotation lock (Control Center).",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.outline,
            )
            ToggleSetting("High contrast buttons (low vision / CVI)", settings.highContrast) { v -> update { it.copy(highContrast = v) } }
            Text("Theme", fontWeight = FontWeight.SemiBold)
            ChipFlow {
                ThemeMode.entries.forEach { t ->
                    FilterChip(settings.themeMode == t, { update { it.copy(themeMode = t) } }, { Text(t.name.lowercase().replaceFirstChar(Char::uppercase)) })
                }
            }

            Header("Switch scanning")
            ChipFlow {
                listOf(ScanMode.OFF to "Off", ScanMode.AUTO to "1 switch (auto)", ScanMode.STEP to "2 switches (step)").forEach { (m, name) ->
                    FilterChip(settings.scanMode == m, { update { it.copy(scanMode = m) } }, { Text(name) })
                }
            }
            if (settings.scanMode != ScanMode.OFF) {
                Text(
                    if (settings.scanMode == ScanMode.AUTO) "The highlight moves by itself. Press the switch to pick a row, then a button."
                    else "Switch 1 (Space or 1) moves the highlight. Switch 2 (Enter or 2) selects.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.outline,
                )
                if (settings.scanMode == ScanMode.AUTO) {
                    SliderSetting(
                        "Scan speed", settings.scanIntervalMs.toFloat(), 500f..4000f,
                        fmt(settings.scanIntervalMs / 1000f, 1) + " s", steps = 34,
                    ) { v -> update { it.copy(scanIntervalMs = (v / 100).roundToInt() * 100) } }
                    SliderSetting("Stop after this many loops", settings.scanLoops.toFloat(), 1f..10f, "${settings.scanLoops}", steps = 8) { v ->
                        update { it.copy(scanLoops = v.roundToInt()) }
                    }
                }
                ToggleSetting("Speak each row and button as it lights up", settings.scanSpeakItems) { v -> update { it.copy(scanSpeakItems = v) } }
                Text(
                    "Bluetooth switches that act as a keyboard (Space/Enter or 1/2) work here. On iPad you can also use the iPad's own Switch Control.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.outline,
                )
                ToggleSetting("Tap anywhere on screen as a switch", settings.scanTouchAnywhere) { v -> update { it.copy(scanTouchAnywhere = v) } }
            }

            Header("Usage log")
            ToggleSetting("Keep a usage log on this device", settings.usageLogging) { v -> update { it.copy(usageLogging = v, usagePaused = false) } }
            Text(
                "Records words and messages (with times) so you can see progress. It stays on this device, " +
                    "and the person can pause it any time with the dot next to the suggestions.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.outline,
            )
            OutlinedButton(onClick = { vm.screen = Screen.REPORT }) { Text("Usage report…") }

            Header("Device")
            Text("Protect settings and editing", fontWeight = FontWeight.SemiBold)
            ChipFlow {
                FilterChip(settings.settingsLock == SettingsLock.NONE, { update { it.copy(settingsLock = SettingsLock.NONE) } }, { Text("Off") })
                FilterChip(settings.settingsLock == SettingsLock.HOLD, { update { it.copy(settingsLock = SettingsLock.HOLD) } }, { Text("Hold 1.5 s") })
                FilterChip(settings.settingsLock == SettingsLock.PIN, { settingPin = true }, { Text("PIN") })
            }
            ToggleSetting("Keep screen on", settings.keepScreenOn) { v -> update { it.copy(keepScreenOn = v) } }
            Text("Keep someone in the talker", fontWeight = FontWeight.SemiBold)
            Text(
                "Use Guided Access: iPad Settings → Accessibility → Guided Access, then triple-click the top (or Home) button while the talker is open.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.outline,
            )
            platform.installHelp?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline) }

            Header("Backup")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { saveBackup() }) { Text("Save backup") }
                OutlinedButton(onClick = { restoreBackup() }) { Text("Restore backup") }
            }
            OutlinedButton(onClick = { showSnapshots = true }) { Text("Restore an earlier version…") }

            Header("Share with other AAC apps & print")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportOpenBoard() }) { Text("Export .obz") }
                OutlinedButton(onClick = { importBoards() }) { Text("Import boards") }
            }
            Text(
                "Export uses Open Board Format (.obz), which CoughDrop, Cboard and other apps read. Import reads .obz and .obf files, TouchChat vocabularies (.ce) and backups from this app on any device. Importing replaces your boards (Undo brings them back).",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.outline,
            )
            TextButton(onClick = { confirmReset = true }) { Text("Reset to starter vocabulary", color = MaterialTheme.colorScheme.error) }

            Header("Credits")
            Text(
                "Symbols: Mulberry Symbols © Steve Lee (mulberrysymbols.org) and OpenMoji (openmoji.org), " +
                    "both licensed CC BY-SA 4.0 (creativecommons.org/licenses/by-sa/4.0).",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.outline,
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(
                "Works offline once installed. Your words stay on this device unless you save a backup.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.outline,
            )
            if (platform.appVersion.isNotEmpty()) Text("Version ${platform.appVersion}", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(bottom = 24.dp))
        }
    }

    if (showSnapshots) SnapshotDialog(vm, onDismiss = { showSnapshots = false }, onRestored = { toast("Restored. Use Undo in edit mode to go back.") })
    if (showPronunciations) PronunciationDialog(vm, onDismiss = { showPronunciations = false })
    if (showAbbreviations) AbbreviationDialog(vm, onDismiss = { showAbbreviations = false })
    if (settingPin) {
        TextInputDialog(
            title = "Choose a PIN (4–8 digits)",
            initial = "",
            onConfirm = { pin ->
                val digits = pin.filter(Char::isDigit)
                if (digits.length in 4..8) update { it.copy(settingsLock = SettingsLock.PIN, pin = digits) } else toast("PIN must be 4 to 8 digits")
                settingPin = false
            },
            onDismiss = { settingPin = false },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset vocabulary?") },
            text = { Text("All boards go back to the starter set. You can still undo this or restore an earlier version.") },
            confirmButton = { TextButton(onClick = { vm.store.resetToDefault(); confirmReset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ToggleSetting(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
private fun SliderSetting(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column {
        Row {
            Text(title, Modifier.weight(1f))
            Text(display, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun SnapshotDialog(vm: AacViewModel, onDismiss: () -> Unit, onRestored: () -> Unit) {
    val snapshots = remember { vm.store.snapshots() }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Earlier versions") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (snapshots.isEmpty()) Text("No saved versions yet. A version is kept every 10 minutes while you edit.")
                snapshots.forEach { name ->
                    Text(
                        vm.store.snapshotLabel(name),
                        Modifier.fillMaxWidth().clickable {
                            scope.launch { if (vm.store.restoreSnapshot(name)) onRestored() }
                            onDismiss()
                        }.padding(vertical = 12.dp),
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** A large, tappable card for the most important destinations in settings. */
@Composable
private fun FeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val container = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val content = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 112.dp),
        shape = RoundedCornerShape(18.dp),
        color = container,
        contentColor = content,
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, Modifier.size(34.dp))
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 13.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun AbbreviationDialog(vm: AacViewModel, onDismiss: () -> Unit) {
    val vocabulary by vm.vocabulary.collectAsState()
    var shortcut by remember { mutableStateOf("") }
    var expansion by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Abbreviations") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Type a shortcut on the keyboard, then space: it becomes the full words.", fontSize = 13.sp)
                vocabulary.abbreviations.entries.sortedBy { it.key }.forEach { (short, full) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$short → $full", Modifier.weight(1f))
                        TextButton(onClick = { vm.setAbbreviation(short, null) }) { Text("Remove") }
                    }
                }
                OutlinedTextField(shortcut, { shortcut = it.replace(" ", "") }, label = { Text("Shortcut (e.g. hru)") }, singleLine = true)
                OutlinedTextField(expansion, { expansion = it }, label = { Text("Full words (e.g. how are you)") }, singleLine = true)
                Button(
                    onClick = { vm.setAbbreviation(shortcut, expansion); shortcut = ""; expansion = "" },
                    enabled = shortcut.isNotBlank() && expansion.isNotBlank(),
                ) { Text("Add") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun PronunciationDialog(vm: AacViewModel, onDismiss: () -> Unit) {
    val vocabulary by vm.vocabulary.collectAsState()
    var word by remember { mutableStateOf("") }
    var sayAs by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pronunciations") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fix how the voice says a word — names especially.", fontSize = 13.sp)
                vocabulary.pronunciations.forEach { (w, s) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$w → $s", Modifier.weight(1f).clickable { vm.speak(w) })
                        TextButton(onClick = { vm.setPronunciation(w, null) }) { Text("Remove") }
                    }
                }
                OutlinedTextField(word, { word = it }, label = { Text("Word") }, singleLine = true)
                OutlinedTextField(sayAs, { sayAs = it }, label = { Text("Say it like") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.speak(sayAs.ifBlank { word }) }) { Text("Test") }
                    Button(onClick = { vm.setPronunciation(word.trim(), sayAs.trim()); word = ""; sayAs = "" }, enabled = word.isNotBlank() && sayAs.isNotBlank()) {
                        Text("Add")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
