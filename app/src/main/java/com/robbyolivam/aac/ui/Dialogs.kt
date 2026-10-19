package com.robbyolivam.aac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.robbyolivam.aac.core.Cell
import com.robbyolivam.aac.core.EnglishMorphology
import com.robbyolivam.aac.data.MediaFiles
import com.robbyolivam.aac.ui.theme.OnWordColor
import com.robbyolivam.aac.ui.theme.wordColor

/** Word forms popup: go → goes / going / went / gone. */
@Composable
fun WordFormsDialog(cell: Cell, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val forms = remember(cell) { EnglishMorphology.forms(cell.text, cell.type) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("${cell.symbol.emoji.orEmpty()} ${cell.label}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            forms.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { form ->
                        Box(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(wordColor(cell.type))
                                .clickable { onPick(form) }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(form, fontSize = 22.sp, color = OnWordColor, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
        }
    }
}

/** "Where is it?" — finds a word and shows the path to it. */
@Composable
fun SearchDialog(vm: AacViewModel, media: MediaFiles, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val results = remember(query) { vm.search(query) }
    val vocabulary = vm.vocabulary.value
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Where is it?") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Type a word") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(results) { loc ->
                        val path = loc.path.dropLast(1).mapNotNull { step ->
                            vocabulary.board(step.boardId)?.cells?.getOrNull(step.cellIndex)?.label
                        }
                        val root = vocabulary.board(loc.path.first().boardId)?.name.orEmpty()
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.startGuide(loc) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(wordColor(loc.cell.type)), contentAlignment = Alignment.Center) {
                                Text(loc.cell.symbol.emoji.orEmpty(), fontSize = 22.sp)
                            }
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(loc.cell.label, fontWeight = FontWeight.SemiBold)
                                Text((listOf(root) + path).joinToString(" › "), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        HorizontalDivider()
                    }
                }
                if (query.isNotBlank() && results.isEmpty()) {
                    Text("No button for “$query” yet. You can type it with ABC, or add it in edit mode.", Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Big, high-contrast message for the person across the table (or a noisy room). */
@Composable
fun PartnerDisplay(text: String, onSpeak: () -> Unit, onClose: () -> Unit) {
    var flipped by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val size = when {
            text.length < 20 -> 64.sp
            text.length < 60 -> 48.sp
            else -> 34.sp
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Text(
                text = text.ifBlank { "…" },
                color = Color.White,
                fontSize = size,
                lineHeight = size * 1.15f,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp).rotate(if (flipped) 180f else 0f),
            )
            Row(Modifier.align(Alignment.BottomCenter).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                IconButton(onClick = { flipped = !flipped }) { Icon(Icons.Default.ScreenRotation, "Flip for partner", tint = Color.White) }
                IconButton(onClick = onSpeak) { Icon(Icons.Default.VolumeUp, "Speak", tint = Color.White) }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            }
        }
    }
}

@Composable
fun PinDialog(expected: String, onSuccess: () -> Unit, onDismiss: () -> Unit) {
    var entry by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter PIN") },
        text = {
            OutlinedTextField(
                value = entry,
                onValueChange = { entry = it.filter(Char::isDigit).take(8); wrong = false },
                singleLine = true,
                isError = wrong,
                supportingText = { if (wrong) Text("That PIN isn't right") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (entry == expected) onSuccess() else wrong = true }) { Text("Open") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TextInputDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit, label: String? = null) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, label = label?.let { { Text(it) } })
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.trim()) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Rename a page and (for grids) change its size. Cells keep their row and column. */
@Composable
fun BoardOptionsDialog(vm: AacViewModel, board: com.robbyolivam.aac.core.Board, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(board.name) }
    var rows by remember { mutableStateOf(board.rows) }
    var cols by remember { mutableStateOf(board.cols) }
    val lost = if (board.isScene) 0 else board.cellsLostByResize(rows, cols)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Page options") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                if (!board.isScene) {
                    Stepper("Rows", rows, 1..12) { rows = it }
                    Stepper("Columns", cols, 1..12) { cols = it }
                    Text(
                        "Buttons keep their row and column, so growing a page never moves anything.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    if (lost > 0) {
                        Text("$lost button(s) are outside the new size and will be removed (Undo brings them back).", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank() && name != board.name) vm.renameBoard(board.id, name.trim())
                if (!board.isScene && (rows != board.rows || cols != board.cols)) vm.resizeBoard(board.id, rows, cols)
                onDismiss()
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        TextButton(onClick = { if (value > range.first) onChange(value - 1) }) { Text("−", fontSize = 22.sp) }
        Text("$value", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        TextButton(onClick = { if (value < range.last) onChange(value + 1) }) { Text("+", fontSize = 22.sp) }
    }
}
