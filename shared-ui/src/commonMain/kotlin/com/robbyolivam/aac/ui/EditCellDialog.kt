package com.robbyolivam.aac.ui

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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.robbyolivam.aac.core.Cell
import com.robbyolivam.aac.core.CellAction
import com.robbyolivam.aac.core.Symbol
import com.robbyolivam.aac.core.WordType
import com.robbyolivam.aac.core.WordWizard
import com.robbyolivam.aac.ui.theme.wordColor

private val typeLabels = listOf(
    WordType.PRONOUN to "Pronoun", WordType.PERSON to "Person", WordType.VERB to "Action",
    WordType.ADJECTIVE to "Describing", WordType.ADVERB to "Adverb", WordType.NOUN to "Thing",
    WordType.PLACE to "Place", WordType.QUESTION to "Question", WordType.NEGATION to "Negation",
    WordType.SOCIAL to "Social", WordType.PREPOSITION to "Little word", WordType.OTHER to "Other",
)

@OptIn(ExperimentalResourceApi::class)
@Composable
fun EditCellDialog(vm: AacViewModel, target: EditTarget) {
    val scope = rememberCoroutineScope()
    val original = remember(target) { vm.cellFor(target) }
    val isStrip = target is EditTarget.Strip

    var label by remember { mutableStateOf(original?.label.orEmpty()) }
    var spoken by remember { mutableStateOf(original?.spoken.orEmpty()) }
    var emoji by remember { mutableStateOf(original?.symbol?.emoji.orEmpty()) }
    var imagePath by remember { mutableStateOf(original?.symbol?.imagePath) }
    var libraryId by remember { mutableStateOf(original?.symbol?.libraryId) }
    var symbolQuery by remember { mutableStateOf(original?.label.orEmpty()) }
    var type by remember { mutableStateOf(original?.type ?: WordType.NOUN) }
    var action by remember { mutableStateOf(original?.action ?: if (isStrip) CellAction.PHRASE else CellAction.WORD) }
    var linkTo by remember { mutableStateOf(original?.linkTo) }
    var hidden by remember { mutableStateOf(original?.hidden ?: false) }
    var audioPath by remember { mutableStateOf(original?.audioPath) }
    var level by remember { mutableStateOf(original?.level ?: 1) }
    val hotspot = remember(target) { vm.hotspotFor(target) }
    var hotspotWidth by remember { mutableStateOf(hotspot?.width ?: 0.24f) }
    var hotspotHeight by remember { mutableStateOf(hotspot?.height ?: 0.24f) }
    var recording by remember { mutableStateOf(false) }
    var pendingAudio by remember { mutableStateOf<String?>(null) }

    // New pictures go through the crop step before they're saved.
    var cropping by remember { mutableStateOf<Pair<ByteArray, ImageBitmap>?>(null) }
    fun pickPicture(camera: Boolean) = scope.launch {
        val (_, bytes) = vm.platform.files.open("image/*", camera) ?: return@launch
        // Scaled down first, so a big camera photo is quick to crop.
        val scaled = vm.platform.images.jpeg(bytes, 1600) ?: run { vm.notice = "That picture can't be read"; return@launch }
        val image = runCatching { scaled.decodeToImageBitmap() }.getOrNull() ?: return@launch
        cropping = scaled to image
    }
    fun pickScenePhoto() = scope.launch {
        val (_, bytes) = vm.platform.files.open("image/*") ?: return@launch
        val path = vm.media.saveImage(bytes, maxSize = 1600) ?: run { vm.notice = "That picture can't be read"; return@launch }
        linkTo = vm.createSceneBoard(label.ifBlank { "Scene" }, path)
    }

    val recorder = vm.recorder
    fun startRecording() = scope.launch {
        if (recorder != null && recorder.start()) recording = true
        else vm.notice = "The microphone isn't available (check the browser's microphone permission)"
    }

    fun stopRecording() = scope.launch {
        val bytes = recorder?.stop()
        recording = false
        if (bytes != null && bytes.isNotEmpty()) audioPath = vm.media.saveAudio(bytes)
    }

    DisposableEffect(Unit) { onDispose { if (recorder?.isRecording == true) scope.launch { recorder.stop() } } }

    fun build(): Cell? {
        if (label.isBlank()) return null
        return Cell(
            id = original?.id ?: vm.newCellId(),
            label = label.trim(),
            spoken = spoken.trim().takeIf { it.isNotEmpty() && it != label.trim() },
            symbol = Symbol(emoji = emoji.trim().ifEmpty { null }, imagePath = imagePath, libraryId = libraryId),
            type = if (action == CellAction.FOLDER) WordType.FOLDER else type,
            action = action,
            // Words can open a page too (WordPower-style: "I" opens the verbs that follow it).
            linkTo = if (action == CellAction.PHRASE) null else linkTo,
            hidden = hidden,
            audioPath = audioPath,
            level = level,
        )
    }

    fun save() {
        val cell = build() ?: return
        if (target is EditTarget.NewHotspot) {
            vm.addNewHotspot(target, cell, hotspotWidth, hotspotHeight)
            return
        }
        val spot = hotspot
        if (spot != null && target is EditTarget.Grid && (spot.width != hotspotWidth || spot.height != hotspotHeight)) {
            vm.updateHotspot(target.boardId, target.index) {
                it.copy(
                    width = hotspotWidth,
                    height = hotspotHeight,
                    x = it.x.coerceAtMost(1f - hotspotWidth),
                    y = it.y.coerceAtMost(1f - hotspotHeight),
                )
            }
        }
        vm.saveCell(target, cell)
    }

    cropping?.let { (bytes, image) ->
        CropDialog(
            image = image,
            onDone = { crop ->
                cropping = null
                scope.launch { vm.media.saveImage(bytes, crop)?.let { imagePath = it } }
            },
            onCancel = { cropping = null },
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
                        onClick = { save() },
                        enabled = label.isNotBlank() && (action != CellAction.FOLDER || linkTo != null),
                    ) { Text("Save") }
                }

                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val copied = vm.clipboard
                    if (copied != null && original == null && target is EditTarget.Grid && hotspot == null) {
                        Button(onClick = { vm.pasteInto(target) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.ContentPaste, null); Text("  Paste “${copied.label}” here")
                        }
                    }
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
                                pickPicture(camera = false)
                            }) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(6.dp)); Text("Photo") }
                            OutlinedButton(onClick = { pickPicture(camera = true) }) {
                                Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text("Camera")
                            }
                            if (imagePath != null) TextButton(onClick = { imagePath = null }) { Text("Remove picture") }
                        }
                    }

                    OutlinedTextField(
                        label,
                        { new ->
                            // Keep the symbol search in step with the label until the person searches on their own.
                            if (symbolQuery == label) symbolQuery = new
                            label = new
                        },
                        label = { Text("Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        spoken, { spoken = it },
                        label = { Text(if (action == CellAction.FOLDER) "Spoken text (not used for folders)" else "Say instead (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SectionTitle("Symbol")
                    OutlinedTextField(
                        symbolQuery, { symbolQuery = it },
                        label = { Text("Search symbols") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val topic = remember(target) {
                        WordWizard.contextWords((target as? EditTarget.Grid)?.let { vm.vocabulary.value.board(it.boardId) })
                    }
                    val results = remember(symbolQuery) { vm.symbols.search(symbolQuery, limit = 40, context = topic) }
                    if (results.isEmpty() && symbolQuery.isNotBlank()) {
                        Text("No symbol for “$symbolQuery”. Try another word, an emoji, or a photo.", fontSize = 13.sp)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        results.forEach { sym ->
                            val selected = libraryId == sym.libraryId
                            Column(
                                Modifier
                                    .size(width = 76.dp, height = 92.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        if (selected) 3.dp else 1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(8.dp),
                                    )
                                    .background(Color.White)
                                    .clickable {
                                        libraryId = sym.libraryId
                                        imagePath = null
                                    }
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                SymbolView(Symbol(libraryId = sym.libraryId), vm.media, Modifier.weight(1f).fillMaxWidth())
                                Text(sym.name, fontSize = 10.sp, maxLines = 1, color = Color.DarkGray)
                            }
                        }
                    }
                    if (libraryId != null) TextButton(onClick = { libraryId = null }) { Text("Don't use a library symbol") }
                    // Typing emoji needs an emoji font; without one (the web) the picks below are drawn as OpenMoji.
                    if (vm.platform.hasEmojiFont) OutlinedTextField(
                        emoji, { emoji = it.take(16) },
                        label = { Text("Emoji (used if there's no symbol)") },
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
                                ) {
                                    if (vm.platform.hasEmojiFont) Text(e, fontSize = 26.sp)
                                    else SymbolView(Symbol(emoji = e), vm.media, Modifier.size(36.dp))
                                }
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
                            FilterChip(false, {
                                pickScenePhoto()
                            }, { Text("+ New photo scene") })
                        }
                    } else {
                        val opens = linkTo?.let { vm.vocabulary.value.board(it) }
                        if (action == CellAction.WORD && opens != null) {
                            SectionTitle("Then opens")
                            ChipRow {
                                FilterChip(true, {}, { Text(opens.name) })
                                FilterChip(false, { linkTo = null }, { Text("Don't open a page") })
                            }
                        }
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
                                OutlinedButton(onClick = { startRecording() }) { Icon(Icons.Default.Mic, null); Text(if (audioPath == null) "Record" else "Re-record") }
                                if (audioPath != null) {
                                    OutlinedButton(onClick = { vm.speaker.playRecording(audioPath!!) }) {
                                        Icon(Icons.Default.PlayArrow, null); Text("Play")
                                    }
                                    TextButton(onClick = { audioPath = null }) { Text("Use voice instead") }
                                }
                            }
                        }
                    }

                    if (hotspot != null || target is EditTarget.NewHotspot) {
                        SectionTitle("Spot size on the photo")
                        Text("Width")
                        Slider(hotspotWidth, { hotspotWidth = it }, valueRange = 0.08f..0.9f)
                        Text("Height")
                        Slider(hotspotHeight, { hotspotHeight = it }, valueRange = 0.08f..0.9f)
                    }

                    if (!isStrip) {
                        SectionTitle("Shows at vocabulary level")
                        ChipRow {
                            (1..Cell.MAX_LEVEL).forEach { l ->
                                FilterChip(level == l, { level = l }, { Text(if (l == Cell.MAX_LEVEL) "$l (all)" else "$l") })
                            }
                        }
                        Text(
                            "Lower levels hide this button in place; it reappears in the same spot.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.outline,
                        )
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
                            if (hotspot == null) OutlinedButton(onClick = { vm.startMove(target) }) { Text("Move…") }
                            OutlinedButton(onClick = { build()?.let(vm::copyCell) }) {
                                Icon(Icons.Default.ContentCopy, null); Text(" Copy")
                            }
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
