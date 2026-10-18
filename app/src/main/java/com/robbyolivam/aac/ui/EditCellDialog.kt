package com.robbyolivam.aac.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.robbyolivam.aac.core.Cell
import com.robbyolivam.aac.core.CellAction
import com.robbyolivam.aac.core.Symbol
import com.robbyolivam.aac.core.WordType
import com.robbyolivam.aac.ui.theme.wordColor

private val typeLabels = listOf(
    WordType.PRONOUN to "Pronoun", WordType.PERSON to "Person", WordType.VERB to "Action",
    WordType.ADJECTIVE to "Describing", WordType.ADVERB to "Adverb", WordType.NOUN to "Thing",
    WordType.PLACE to "Place", WordType.QUESTION to "Question", WordType.NEGATION to "Negation",
    WordType.SOCIAL to "Social", WordType.PREPOSITION to "Little word", WordType.OTHER to "Other",
)

@Composable
fun EditCellDialog(vm: AacViewModel, target: EditTarget) {
    val context = LocalContext.current
    val original = remember(target) { vm.cellFor(target) }
    val isStrip = target is EditTarget.Strip

    var label by remember { mutableStateOf(original?.label.orEmpty()) }
    var spoken by remember { mutableStateOf(original?.spoken.orEmpty()) }
    var emoji by remember { mutableStateOf(original?.symbol?.emoji.orEmpty()) }
    var imagePath by remember { mutableStateOf(original?.symbol?.imagePath) }
    var type by remember { mutableStateOf(original?.type ?: WordType.NOUN) }
    var action by remember { mutableStateOf(original?.action ?: if (isStrip) CellAction.PHRASE else CellAction.WORD) }
    var linkTo by remember { mutableStateOf(original?.linkTo) }
    var hidden by remember { mutableStateOf(original?.hidden ?: false) }
    var audioPath by remember { mutableStateOf(original?.audioPath) }
    var recording by remember { mutableStateOf(false) }
    var pendingAudio by remember { mutableStateOf<String?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.media.importImage(it)?.let { path -> imagePath = path } }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let { imagePath = vm.media.saveBitmap(it) }
    }

    fun startRecording() {
        val (relative, file) = vm.media.newAudioFile()
        runCatching { vm.recorder.start(file) }.onSuccess {
            pendingAudio = relative
            recording = true
        }
    }

    fun stopRecording() {
        if (vm.recorder.stop()) audioPath = pendingAudio
        recording = false
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
    }
    DisposableEffect(Unit) { onDispose { if (vm.recorder.isRecording) vm.recorder.stop() } }

    fun build(): Cell? {
        if (label.isBlank()) return null
        return Cell(
            id = original?.id ?: vm.newCellId(),
            label = label.trim(),
            spoken = spoken.trim().takeIf { it.isNotEmpty() && it != label.trim() },
            symbol = Symbol(emoji = emoji.trim().ifEmpty { null }, imagePath = imagePath),
            type = if (action == CellAction.FOLDER) WordType.FOLDER else type,
            action = action,
            linkTo = if (action == CellAction.FOLDER) linkTo else null,
            hidden = hidden,
            audioPath = audioPath,
        )
    }

    Dialog(onDismissRequest = { vm.editing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { vm.editing = null }) { Icon(Icons.Default.Close, null); Text("Cancel") }
                    Text(
                        if (original == null) "New button" else "Edit button",
                        Modifier.weight(1f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Button(
                        onClick = { build()?.let { vm.saveCell(target, it) } },
                        enabled = label.isNotBlank() && (action != CellAction.FOLDER || linkTo != null),
                    ) { Text("Save") }
                }

                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Preview + picture
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(96.dp)) {
                            CellButton(
                                cell = build() ?: Cell("preview", "…", type = type),
                                settings = vm.settings.value,
                                editMode = false,
                                highlighted = false,
                                moving = false,
                                media = vm.media,
                                onSelect = { build()?.let(vm::speakCell) },
                                onLongPress = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = {
                                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(6.dp)); Text("Photo") }
                            OutlinedButton(onClick = { takePhoto.launch(null) }) {
                                Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text("Camera")
                            }
                            if (imagePath != null) TextButton(onClick = { imagePath = null }) { Text("Remove picture") }
                        }
                    }

                    OutlinedTextField(label, { label = it }, label = { Text("Label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        spoken, { spoken = it },
                        label = { Text(if (action == CellAction.FOLDER) "Spoken text (not used for folders)" else "Say instead (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        emoji, { emoji = it.take(16) },
                        label = { Text("Emoji symbol") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val suggestions = remember(label) { emojiSuggestions(vm, label) }
                    if (suggestions.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            suggestions.forEach { e ->
                                Box(
                                    Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                        .clickable { emoji = e },
                                    contentAlignment = Alignment.Center,
                                ) { Text(e, fontSize = 26.sp) }
                            }
                        }
                    }

                    if (!isStrip) {
                        SectionTitle("What it does")
                        ChipRow {
                            FilterChip(action == CellAction.WORD, { action = CellAction.WORD }, { Text("Add word") })
                            FilterChip(action == CellAction.PHRASE, { action = CellAction.PHRASE }, { Text("Speak right away") })
                            FilterChip(action == CellAction.FOLDER, { action = CellAction.FOLDER }, { Text("Open folder") })
                        }
                    }

                    if (action == CellAction.FOLDER) {
                        SectionTitle("Folder opens")
                        val boards = vm.vocabulary.value.boards.filter { it.id != vm.vocabulary.value.homeBoardId }
                        ChipRow {
                            boards.forEach { b -> FilterChip(linkTo == b.id, { linkTo = b.id }, { Text(b.name) }) }
                            FilterChip(false, { linkTo = vm.createBoard(label.ifBlank { "New board" }) }, { Text("+ New board") })
                        }
                    } else {
                        SectionTitle("Word type (sets the color)")
                        ChipRow {
                            typeLabels.forEach { (t, name) ->
                                FilterChip(
                                    selected = type == t,
                                    onClick = { type = t },
                                    label = { Text(name) },
                                    leadingIcon = { Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(wordColor(t))) },
                                )
                            }
                        }
                    }

                    if (action != CellAction.FOLDER) {
                        SectionTitle("Recorded voice")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (recording) {
                                Button(onClick = { stopRecording() }) { Icon(Icons.Default.Stop, null); Text("Stop") }
                                Text("Recording…", color = MaterialTheme.colorScheme.error)
                            } else {
                                OutlinedButton(onClick = {
                                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                        PackageManager.PERMISSION_GRANTED
                                    if (granted) startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }) { Icon(Icons.Default.Mic, null); Text(if (audioPath == null) "Record" else "Re-record") }
                                if (audioPath != null) {
                                    OutlinedButton(onClick = { vm.speaker.playFile(vm.store.resolve(audioPath!!)) }) {
                                        Icon(Icons.Default.PlayArrow, null); Text("Play")
                                    }
                                    TextButton(onClick = { audioPath = null }) { Text("Use voice instead") }
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Hide button", fontWeight = FontWeight.SemiBold)
                            Text("Keeps its spot so nothing else moves", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(hidden, { hidden = it })
                    }

                    if (original != null && target is EditTarget.Grid) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.startMove(target) }) { Text("Move…") }
                            OutlinedButton(onClick = { vm.saveCell(target, null) }) {
                                Icon(Icons.Default.Delete, null); Text("Delete")
                            }
                        }
                    }
                    Spacer(Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

private val commonEmoji = listOf(
    "😀", "😊", "😢", "😠", "❤️", "👍", "👎", "🙏", "🏠", "🏫", "🚗", "🍎", "🍕", "🥤", "🎮", "📺",
    "🎵", "📚", "⚽", "🐕", "🐈", "🌳", "☀️", "🌙", "⭐", "🎉", "🎁", "🛁", "🛏️", "🚽", "👕", "🧸",
)

private fun emojiSuggestions(vm: AacViewModel, label: String): List<String> {
    val q = label.trim().lowercase()
    if (q.isEmpty()) return commonEmoji
    val matches = vm.wordIndex().filterKeys { it.startsWith(q) || q.startsWith(it) && it.length > 2 }
        .values.mapNotNull { it.symbol.emoji }
    return (matches + commonEmoji).distinct().take(24)
}
