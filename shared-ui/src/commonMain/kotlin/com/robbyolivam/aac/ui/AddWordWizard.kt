package com.robbyolivam.aac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.collectAsState
import com.robbyolivam.aac.core.Board
import com.robbyolivam.aac.core.CellAction
import com.robbyolivam.aac.core.EnglishMorphology
import com.robbyolivam.aac.core.Symbol
import com.robbyolivam.aac.core.WordType
import com.robbyolivam.aac.core.WordWizard
import com.robbyolivam.aac.core.findWord
import com.robbyolivam.aac.core.LibrarySymbol
import com.robbyolivam.aac.ui.theme.wordColor

/** Kinds of word offered in the wizard, in plain language. The kind sets the button color. */
private val wordKinds = listOf(
    WordType.NOUN to "Thing",
    WordType.VERB to "Action",
    WordType.ADJECTIVE to "Describing",
    WordType.ADVERB to "How / when",
    WordType.PERSON to "Person",
    WordType.PLACE to "Place",
    WordType.SOCIAL to "Social",
    WordType.QUESTION to "Question",
    WordType.PREPOSITION to "Little word",
    WordType.PRONOUN to "Pronoun",
)

/**
 * Add a word in one screen: type it, and the wizard picks a symbol, the kind of word (color),
 * a folder and the first empty spot there, and shows the forms it will have. Everything can be
 * changed before adding.
 */
@Composable
fun AddWordWizard(vm: AacViewModel, onDone: () -> Unit) {
    val vocabulary by vm.vocabulary.collectAsState()
    var word by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf<LibrarySymbol?>(null) }
    var type by remember { mutableStateOf(WordType.NOUN) }
    var boardId by remember { mutableStateOf<String?>(null) }
    var index by remember { mutableStateOf<Int?>(null) }
    // Once the person picks something themselves, new guesses don't overwrite it.
    var typeChosen by remember { mutableStateOf(false) }
    var placeChosen by remember { mutableStateOf(false) }
    var symbolChosen by remember { mutableStateOf(false) }

    val trimmed = word.trim()
    // Pictures that fit the chosen folder come first (and the guess is redone if the folder changes).
    val matches = remember(trimmed, boardId) {
        if (trimmed.length < 2) emptyList()
        else vm.symbols.search(trimmed, limit = 16, context = WordWizard.contextWords(boardId?.let(vocabulary::board)))
    }
    val existing = remember(trimmed, vocabulary) { if (trimmed.length < 2) null else vocabulary.findWord(trimmed) }
    val folders = remember(vocabulary) {
        vocabulary.home.cells.filterNotNull()
            .filter { it.action == CellAction.FOLDER }
            .mapNotNull { c -> c.linkTo?.let(vocabulary::board)?.takeIf { !it.isScene } }
            .distinctBy { it.id } + vocabulary.home
    }

    LaunchedEffect(trimmed, matches) {
        if (trimmed.length < 2) return@LaunchedEffect
        val best = matches.firstOrNull { it.name.equals(trimmed, true) } ?: matches.firstOrNull()
        if (!symbolChosen) symbol = best
        val plan = WordWizard.plan(trimmed, best?.grammar, best?.tags, vocabulary)
        if (!typeChosen) type = existing?.cell?.type?.takeIf { it != WordType.FOLDER } ?: plan.type
        if (!placeChosen) {
            boardId = plan.boardId
            index = plan.index
        }
    }

    val board = boardId?.let(vocabulary::board)
    val canAdd = trimmed.isNotEmpty() && board != null && index != null && board.cells.getOrNull(index!!) == null

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDone) { Icon(Icons.Default.Close, null); Text("Cancel") }
                    Text("Add a word", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = {
                            vm.addWord(trimmed, type, symbol?.libraryId, board!!.id, index!!)
                            onDone()
                        },
                        enabled = canAdd,
                    ) { Text("Add") }
                }
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(word, { word = it }, label = { Text("New word or phrase") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    existing?.let { found ->
                        Text(
                            "“${found.cell.label}” is already on ${found.path.joinToString(" → ") { vocabulary.board(it.boardId)?.name ?: it.boardId }}.",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.error,
                        )
                    }

                    if (trimmed.length >= 2) {
                        Title("Picture")
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            matches.forEach { s ->
                                val selected = symbol?.libraryId == s.libraryId
                                Box(
                                    Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                        .clickable { symbol = s; symbolChosen = true }
                                        .padding(6.dp)
                                        .semantics { contentDescription = "Picture: ${s.name}" },
                                ) {
                                    SymbolView(Symbol(libraryId = s.libraryId), vm.media, Modifier.fillMaxSize())
                                }
                            }
                            if (matches.isEmpty()) Text("No pictures found; you can add a photo later in the button editor.", fontSize = 13.sp)
                        }

                        Title("Kind of word (sets the color)")
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            wordKinds.forEach { (kind, label) ->
                                FilterChip(
                                    selected = type == kind,
                                    onClick = { type = kind; typeChosen = true },
                                    label = { Text(label) },
                                    leadingIcon = { Box(Modifier.size(14.dp).clip(CircleShape).background(wordColor(kind))) },
                                )
                            }
                        }

                        Title("Folder")
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            folders.forEach { f ->
                                FilterChip(
                                    selected = boardId == f.id,
                                    onClick = {
                                        boardId = f.id
                                        index = WordWizard.firstEmptySlot(f)
                                        placeChosen = true
                                    },
                                    label = { Text(f.name) },
                                )
                            }
                        }

                        board?.let { b ->
                            Title("Spot (tap an empty one to change)")
                            if (WordWizard.firstEmptySlot(b) == null) {
                                Text(
                                    "“${b.name}” is full. Pick another folder, or make room with Page options (bigger page) in edit mode.",
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.error,
                                )
                            }
                            SpotPicker(b, index) { index = it; placeChosen = true }
                        }

                        val forms = EnglishMorphology.forms(trimmed, type).drop(1)
                        if (forms.isNotEmpty()) {
                            Title("Word forms")
                            Text("Long-press the button (or use Forms) for: ${forms.joinToString(", ")}", fontSize = 14.sp)
                        }
                    }
                    Box(Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}

/** A small map of the page: filled spots show their word, empty ones can be picked. */
@Composable
private fun SpotPicker(board: Board, selected: Int?, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().aspectRatio(board.cols / board.rows.toFloat() * 1.4f)) {
        for (row in 0 until board.rows) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0 until board.cols) {
                    val i = row * board.cols + col
                    val cell = board.cells[i]
                    val isSelected = i == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(1.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    cell == null -> MaterialTheme.colorScheme.surface
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                            )
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                            .then(if (cell == null) Modifier.clickable { onPick(i) } else Modifier)
                            .semantics { contentDescription = if (cell == null) "Empty spot ${row + 1}, ${col + 1}" else cell.label },
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            isSelected -> Text("NEW", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                            cell != null -> Text(cell.label, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Clip, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else -> Text("+", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}
