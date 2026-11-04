package com.robbyolivam.aac.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robbyolivam.aac.core.SettingsLock
import com.robbyolivam.aac.core.ThemeMode
import com.robbyolivam.aac.core.VoiceAge
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(talker: Talker, platform: Platform, onClose: () -> Unit) {
    val s = talker.settings
    val voices by platform.speech.voices.collectAsState()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Button(onClick = onClose) { Text("Done", fontSize = 18.sp) }
            }
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)
                    .widthIn(max = 900.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Section("Boards and backups")
                Text(
                    "Import boards opens a TouchChat vocabulary (.ce), a backup from this app on any device (.zip), " +
                        "or Open Board files (.obz, .obf). Importing replaces the boards; \"Go back to the previous boards\" undoes it.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { talker.importFile() }) { Text("Import boards") }
                    OutlinedButton(onClick = { talker.saveBackup() }) { Text("Save backup") }
                    OutlinedButton(onClick = { talker.restorePrevious() }) { Text("Go back to the previous boards") }
                    OutlinedButton(onClick = { talker.startOver() }) { Text("Use the starter boards") }
                }

                Section("Voice")
                val english = voices.filter { it.language.startsWith("en") }.ifEmpty { voices }
                if (english.isEmpty()) {
                    Text("No voices yet. On iPad: Settings → Accessibility → Spoken Content → Voices to download one.")
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(s.voiceName == null, { talker.updateSettings { it.copy(voiceName = null) } }, { Text("Device default") })
                        english.sortedBy { it.displayName }.forEach { v ->
                            FilterChip(s.voiceName == v.id, { talker.updateSettings { it.copy(voiceName = v.id) } }, { Text("${v.displayName} (${v.language})") })
                        }
                    }
                }
                Labeled("Speed ${(s.speechRate * 100).roundToInt()}%") {
                    Slider(s.speechRate, { v -> talker.updateSettings { it.copy(speechRate = v) } }, valueRange = 0.5f..1.6f)
                }
                Labeled("Pitch ${(s.pitch * 100).roundToInt()}%") {
                    Slider(s.pitch, { v -> talker.updateSettings { it.copy(pitch = v) } }, valueRange = 0.6f..1.6f)
                }
                Labeled("Volume ${(s.volume * 100).roundToInt()}%") {
                    Slider(s.volume, { v -> talker.updateSettings { it.copy(volume = v) } }, valueRange = 0.1f..1f)
                }
                Text("Voice age")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(VoiceAge.ADULT to "Adult", VoiceAge.TEEN to "Teen", VoiceAge.KID to "Kid").forEach { (age, name) ->
                        FilterChip(s.voiceAge == age, { talker.updateSettings { it.copy(voiceAge = age) } }, { Text(name) })
                    }
                }
                OutlinedButton(onClick = { talker.speak("Hello, this is my voice.") }) { Text("Test the voice") }
                Toggle("Speak each word when tapped", s.speakOnSelect) { v -> talker.updateSettings { it.copy(speakOnSelect = v) } }

                Section("Board")
                Toggle("Go back home after a word from a folder", s.returnHomeAfterFolderWord) { v -> talker.updateSettings { it.copy(returnHomeAfterFolderWord = v) } }
                Toggle("Show words under pictures", s.showLabels) { v -> talker.updateSettings { it.copy(showLabels = v) } }
                Toggle("High contrast buttons", s.highContrast) { v -> talker.updateSettings { it.copy(highContrast = v) } }
                Text("Theme")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ThemeMode.SYSTEM to "Match device", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark").forEach { (mode, name) ->
                        FilterChip(s.themeMode == mode, { talker.updateSettings { it.copy(themeMode = mode) } }, { Text(name) })
                    }
                }

                Section("Device")
                Toggle("Protect settings (hold the Settings button to open)", s.settingsLock != SettingsLock.NONE) { v ->
                    talker.updateSettings { it.copy(settingsLock = if (v) SettingsLock.HOLD else SettingsLock.NONE) }
                }
                platform.installHelp?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (platform.appVersion.isNotEmpty()) Text("Version ${platform.appVersion}", color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 12.dp))
    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Column { Text(label); content() }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 17.sp)
        Switch(checked, onChange)
    }
}
