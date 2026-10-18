package com.robbyolivam.aac.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robbyolivam.aac.core.Cell
import com.robbyolivam.aac.core.Symbol
import com.robbyolivam.aac.data.MediaFiles
import com.robbyolivam.aac.data.SettingsLock
import com.robbyolivam.aac.ui.theme.OnWordColor
import com.robbyolivam.aac.ui.theme.wordColor

@Composable
fun CommunicationScreen(vm: AacViewModel) {
    val vocabulary by vm.vocabulary.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val board = vocabulary.board(vm.currentBoardId) ?: vocabulary.home
    val guide = vm.guide
    val guideOnBoard = vm.guideIndex(board.id)
    // When the next guide step is on another root board, point at the button that gets there.
    val guideRoot = guide?.path?.first()?.boardId?.takeIf { guideOnBoard == null }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (vm.editMode) EditBanner(vm, board.name)
        MessageBar(vm, vm.media)
        PredictionRow(vm, vm.media, showPredictions = settings.showPredictions)
        if (guide != null) GuideBanner(guide.cell.label, onCancel = vm::cancelGuide)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (vm.panel) {
                Panel.BOARD -> BoardGrid(
                    board = board,
                    settings = settings,
                    editMode = vm.editMode,
                    highlightIndex = guideOnBoard,
                    movingIndex = vm.moveSource?.takeIf { it.boardId == board.id }?.index,
                    media = vm.media,
                    onSelect = { index, cell -> vm.onCellSelected(board.id, index, cell) },
                    onLongPress = vm::onCellLongPress,
                    modifier = Modifier.fillMaxSize(),
                )
                Panel.KEYBOARD -> KeyboardPanel(
                    layout = settings.keyboardLayout,
                    typing = vm.typing,
                    onKey = vm::typeKey,
                    onSpace = vm::typeSpace,
                    onBackspace = vm::backspace,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        QuickStrip(vm, vocabulary.quickStrip, settings)
        NavBar(
            vm = vm,
            highlightHome = guideRoot != null && guideRoot == vocabulary.homeBoardId,
            highlightPhrases = guideRoot != null && guideRoot == vocabulary.phrasesBoardId,
            settingsLock = settings.settingsLock,
            pin = settings.pin,
        )
    }

    vm.formsFor?.let { cell -> WordFormsDialog(cell, onPick = { vm.selectForm(cell, it) }, onDismiss = { vm.formsFor = null }) }
    if (vm.searchOpen) SearchDialog(vm, vm.media, onDismiss = { vm.searchOpen = false })
    if (vm.showPartner) PartnerDisplay(vm.messageText(), onSpeak = vm::speakMessage, onClose = { vm.showPartner = false })
    vm.editing?.let { target -> EditCellDialog(vm, target) }
}

@Composable
private fun MessageBar(vm: AacViewModel, media: MediaFiles) {
    val scroll = rememberScrollState()
    LaunchedEffect(vm.message.size, vm.typing) { scroll.animateScrollTo(scroll.maxValue) }
    Row(
        Modifier.fillMaxWidth().height(76.dp).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .semantics {
                    role = Role.Button
                    contentDescription = "Message: ${vm.messageText()}. Tap to speak."
                    onClick { vm.speakMessage(); true }
                }
                .clickable { vm.speakMessage() }
                .horizontalScroll(scroll)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (vm.message.isEmpty() && vm.typing.isEmpty()) {
                Text("Tap words to build a message", color = MaterialTheme.colorScheme.outline, fontSize = 16.sp)
            }
            vm.message.forEach { token -> MessageChip(token.text, token.symbol, media) }
            if (vm.typing.isNotEmpty()) {
                Text(vm.typing + "▏", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        BarIcon(Icons.Default.VolumeUp, "Speak message", vm::speakMessage, tint = MaterialTheme.colorScheme.primary)
        BarIcon(Icons.Default.Backspace, "Delete last word", vm::backspace)
        BarIcon(Icons.Default.Clear, "Clear message", vm::clearMessage)
    }
}

@Composable
private fun MessageChip(text: String, symbol: Symbol?, media: MediaFiles) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp)) {
        val image = remember(symbol?.imagePath) { symbol?.imagePath?.let { media.load(it) } }
        when {
            image != null -> Image(image, null, Modifier.size(30.dp).clip(RoundedCornerShape(4.dp)))
            !symbol?.emoji.isNullOrBlank() -> Text(symbol?.emoji.orEmpty(), fontSize = 22.sp)
        }
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
private fun BarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit, tint: Color? = null) {
    IconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
        Icon(icon, contentDescription = description, tint = tint ?: MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun PredictionRow(vm: AacViewModel, media: MediaFiles, showPredictions: Boolean) {
    val context = LocalContext.current
    val suggestion = vm.polishSuggestion()
    Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (suggestion != null) {
                item("polish") {
                    Pill(
                        text = suggestion,
                        background = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        leading = { Icon(Icons.Default.AutoAwesome, "Suggestion", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                        onClick = { vm.acceptSuggestion(suggestion) },
                    )
                }
            }
            if (showPredictions && !vm.editMode) {
                if (vm.panel == Panel.KEYBOARD) {
                    items(vm.keyboardPredictions(), key = { "k-$it" }) { word ->
                        Pill(text = word, onClick = { vm.choosePrediction(word) })
                    }
                } else {
                    items(vm.boardPredictions(), key = { "b-${it.id}" }) { cell ->
                        Pill(
                            text = cell.label,
                            background = wordColor(cell.type),
                            textColor = OnWordColor,
                            leading = { SymbolMini(cell, media) },
                            onClick = { vm.choosePrediction(cell.text) },
                        )
                    }
                }
            }
        }
        IconButton(onClick = { vm.showPartner = true }) { Icon(Icons.Default.Tv, "Show message to partner") }
        IconButton(onClick = {
            val text = vm.messageText()
            if (text.isNotBlank()) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, "Share message"))
            }
        }) { Icon(Icons.Default.Share, "Share message") }
    }
}

@Composable
private fun SymbolMini(cell: Cell, media: MediaFiles) {
    val image = remember(cell.symbol.imagePath) { cell.symbol.imagePath?.let { media.load(it) } }
    when {
        image != null -> Image(image, null, Modifier.size(22.dp).clip(RoundedCornerShape(3.dp)))
        !cell.symbol.emoji.isNullOrBlank() -> Text(cell.symbol.emoji.orEmpty(), fontSize = 18.sp)
    }
}

@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    background: Color = MaterialTheme.colorScheme.surfaceVariant,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        Text(text, color = textColor, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GuideBanner(label: String, onCancel: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Showing the way to “$label” — follow the flashing buttons", Modifier.weight(1f), fontSize = 14.sp)
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Stop showing the way") }
        }
    }
}

@Composable
private fun QuickStrip(vm: AacViewModel, cells: List<Cell>, settings: com.robbyolivam.aac.data.Settings) {
    Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 2.dp)) {
        cells.forEachIndexed { index, cell ->
            CellButton(
                cell = cell,
                settings = settings.copy(labelScale = settings.labelScale * 0.95f),
                editMode = vm.editMode,
                highlighted = false,
                moving = false,
                media = vm.media,
                onSelect = { vm.onStripSelected(index, cell) },
                onLongPress = {},
                modifier = Modifier.weight(1f).fillMaxHeight().padding(2.dp),
                compact = true,
            )
        }
    }
}

@Composable
private fun NavBar(vm: AacViewModel, highlightHome: Boolean, highlightPhrases: Boolean, settingsLock: SettingsLock, pin: String) {
    var askPin by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().height(60.dp).background(MaterialTheme.colorScheme.surface).padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavButton(Icons.Default.Home, "Home", highlightHome) { vm.goHome() }
        NavButton(Icons.Default.ArrowBack, "Back") { vm.goBack() }
        NavButton(Icons.Default.Keyboard, "ABC", vm.panel == Panel.KEYBOARD) {
            vm.panel = if (vm.panel == Panel.KEYBOARD) Panel.BOARD else Panel.KEYBOARD
        }
        NavButton(Icons.Default.Forum, "Phrases", highlightPhrases) { vm.openPhrases() }
        NavButton(Icons.Default.Spellcheck, "Forms", vm.formsMode) { vm.formsMode = !vm.formsMode }
        NavButton(Icons.Default.Search, "Find") { vm.searchOpen = true }
        val openSettings = { vm.screen = Screen.SETTINGS }
        when (settingsLock) {
            SettingsLock.NONE -> NavButton(Icons.Default.Settings, "Settings", onClick = openSettings)
            SettingsLock.PIN -> NavButton(Icons.Default.Settings, "Settings") {
                if (pin.isBlank()) openSettings() else askPin = true
            }
            SettingsLock.HOLD -> HoldNavButton(
                onHold = openSettings,
                onTap = { Toast.makeText(context, "Hold to open settings", Toast.LENGTH_SHORT).show() },
            )
        }
    }
    if (askPin) {
        PinDialog(expected = pin, onSuccess = { askPin = false; vm.screen = Screen.SETTINGS }, onDismiss = { askPin = false })
    }
}

@Composable
private fun RowScope.NavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
        Text(label, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun RowScope.HoldNavButton(onHold: () -> Unit, onTap: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val hold by rememberUpdatedState(onHold)
    val tap by rememberUpdatedState(onTap)
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    try {
                        val releasedEarly = withTimeoutOrNull(1500L) { waitForUpOrCancellation(); true } != null
                        if (releasedEarly) tap() else {
                            hold()
                            waitForUpOrCancellation()
                        }
                    } finally {
                        pressed = false
                    }
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = "Settings (hold)"
                onClick { hold(); true }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(26.dp))
        Text(if (pressed) "Hold…" else "Settings", fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun EditBanner(vm: AacViewModel, boardName: String) {
    val canUndo by vm.store.canUndo.collectAsStateWithLifecycle()
    val canRedo by vm.store.canRedo.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Editing “$boardName”", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { renaming = true })
                val move = vm.moveSource
                Text(
                    if (move != null) "Tap where “${move.cell.label}” should go" else "Tap a button to change it",
                    fontSize = 12.sp,
                )
            }
            if (vm.moveSource != null) TextButton(onClick = vm::cancelMove) { Text("Cancel move") }
            IconButton(onClick = vm::undo, enabled = canUndo) { Icon(Icons.Default.Undo, "Undo") }
            IconButton(onClick = vm::redo, enabled = canRedo) { Icon(Icons.Default.Redo, "Redo") }
            TextButton(onClick = vm::exitEditMode) { Text("Done") }
            Box(Modifier.width(4.dp))
        }
    }
    if (renaming) {
        TextInputDialog(
            title = "Board name",
            initial = boardName,
            onConfirm = { vm.renameBoard(vm.currentBoardId, it); renaming = false },
            onDismiss = { renaming = false },
        )
    }
}
