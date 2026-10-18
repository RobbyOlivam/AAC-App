package com.robbyolivam.aac.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robbyolivam.aac.data.KeyboardLayout
import com.robbyolivam.aac.data.Settings
import com.robbyolivam.aac.data.SettingsLock
import com.robbyolivam.aac.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: AacViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val voices by vm.speaker.voices.collectAsStateWithLifecycle()
    val ttsAvailable by vm.speaker.available.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSnapshots by remember { mutableStateOf(false) }
    var showPronunciations by remember { mutableStateOf(false) }
    var settingPin by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    fun update(transform: (Settings) -> Settings) = vm.updateSettings(transform)
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { vm.backup.export(it) } }.isSuccess
            }
            toast(if (ok) "Backup saved" else "Couldn't save the backup")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { vm.backup.import(it) } }
            }
            toast(if (result.isSuccess) "Backup restored" else "That file isn't a backup from this app")
        }
    }

    BackHandler { vm.screen = Screen.MAIN }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.screen = Screen.MAIN }) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Button(onClick = vm::enterEditMode, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Icon(Icons.Default.Edit, null)
                Text("  Edit boards")
            }

            Header("Voice")
            if (!ttsAvailable) {
                Text(
                    "No text-to-speech engine found. Install “Speech Services by Google” from the Play Store.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            SliderSetting("Speed", settings.speechRate, 0.5f..2.0f, "%.1f×".format(settings.speechRate)) { v -> update { it.copy(speechRate = v) } }
            SliderSetting("Pitch", settings.pitch, 0.5f..2.0f, "%.1f".format(settings.pitch)) { v -> update { it.copy(pitch = v) } }
            if (voices.isNotEmpty()) {
                Text("Voice", fontWeight = FontWeight.SemiBold)
                ChipFlow {
                    FilterChip(settings.voiceName == null, { update { it.copy(voiceName = null) } }, { Text("Default") })
                    voices.forEach { voice ->
                        FilterChip(settings.voiceName == voice.name, {
                            update { it.copy(voiceName = voice.name) }
                            vm.speaker.speak("Hi, this is my voice", settings.speechRate, settings.pitch, voice.name)
                        }, { Text(voice.name.removePrefix("${voice.locale.language}-").take(28)) })
                    }
                }
            }
            OutlinedButton(onClick = { vm.speak("Hello! This is how I sound.") }) { Text("Test voice") }
            ToggleSetting("Speak each word when tapped", settings.speakOnSelect) { v -> update { it.copy(speakOnSelect = v) } }
            ToggleSetting("Speak letters and words while typing", settings.speakLettersWhileTyping) { v -> update { it.copy(speakLettersWhileTyping = v) } }
            OutlinedButton(onClick = { showPronunciations = true }) { Text("Pronunciations…") }

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
            ToggleSetting("Vibrate on tap", settings.haptics) { v -> update { it.copy(haptics = v) } }

            Header("Board")
            ToggleSetting("Go back to Core after picking a folder word", settings.returnHomeAfterFolderWord) { v -> update { it.copy(returnHomeAfterFolderWord = v) } }
            ToggleSetting("Show word predictions", settings.showPredictions) { v -> update { it.copy(showPredictions = v) } }
            ToggleSetting("Suggest full sentences (grammar helper)", settings.grammarHelper) { v -> update { it.copy(grammarHelper = v) } }
            ToggleSetting("Show labels", settings.showLabels) { v -> update { it.copy(showLabels = v) } }
            SliderSetting("Label size", settings.labelScale, 0.7f..1.6f, "%.0f%%".format(settings.labelScale * 100)) { v -> update { it.copy(labelScale = v) } }
            Text("Keyboard", fontWeight = FontWeight.SemiBold)
            ChipFlow {
                KeyboardLayout.entries.forEach { k ->
                    FilterChip(settings.keyboardLayout == k, { update { it.copy(keyboardLayout = k) } }, { Text(k.name) })
                }
            }
            Text("Theme", fontWeight = FontWeight.SemiBold)
            ChipFlow {
                ThemeMode.entries.forEach { t ->
                    FilterChip(settings.themeMode == t, { update { it.copy(themeMode = t) } }, { Text(t.name.lowercase().replaceFirstChar(Char::uppercase)) })
                }
            }

            Header("Device")
            Text("Protect settings and editing", fontWeight = FontWeight.SemiBold)
            ChipFlow {
                FilterChip(settings.settingsLock == SettingsLock.NONE, { update { it.copy(settingsLock = SettingsLock.NONE) } }, { Text("Off") })
                FilterChip(settings.settingsLock == SettingsLock.HOLD, { update { it.copy(settingsLock = SettingsLock.HOLD) } }, { Text("Hold 1.5 s") })
                FilterChip(settings.settingsLock == SettingsLock.PIN, { settingPin = true }, { Text("PIN") })
            }
            ToggleSetting("Keep screen on", settings.keepScreenOn) { v -> update { it.copy(keepScreenOn = v) } }
            ToggleSetting("Show over the lock screen", settings.showOnLockScreen) { v -> update { it.copy(showOnLockScreen = v) } }
            Text(
                "Tip: add the “Talk” tile to Quick Settings (swipe down twice → edit) to open this app from anywhere.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.outline,
            )

            Header("Backup")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    exportLauncher.launch("aac-backup-$stamp.zip")
                }) { Text("Save backup") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Restore backup") }
            }
            OutlinedButton(onClick = { showSnapshots = true }) { Text("Restore an earlier version…") }
            TextButton(onClick = { confirmReset = true }) { Text("Reset to starter vocabulary", color = MaterialTheme.colorScheme.error) }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(
                "Speech always works offline. Your words stay on this device unless you save a backup.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.outline,
            )
            Text("Version 0.1.0 (MVP)", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(bottom = 24.dp))
        }
    }

    if (showSnapshots) SnapshotDialog(vm, onDismiss = { showSnapshots = false }, onRestored = { toast("Restored. Use Undo in edit mode to go back.") })
    if (showPronunciations) PronunciationDialog(vm, onDismiss = { showPronunciations = false })
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
    val format = remember { SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Earlier versions") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (snapshots.isEmpty()) Text("No saved versions yet. A version is kept every 10 minutes while you edit.")
                snapshots.forEach { file: File ->
                    Text(
                        format.format(Date(file.lastModified())),
                        Modifier.fillMaxWidth().clickable {
                            if (vm.store.restoreSnapshot(file)) onRestored()
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

@Composable
private fun PronunciationDialog(vm: AacViewModel, onDismiss: () -> Unit) {
    val vocabulary by vm.vocabulary.collectAsStateWithLifecycle()
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
