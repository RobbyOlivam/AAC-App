package com.robbyolivam.aac.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PostAdd
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
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.robbyolivam.aac.core.Board
import com.robbyolivam.aac.core.Tone
import com.robbyolivam.aac.core.Cell
import com.robbyolivam.aac.core.Symbol
import com.robbyolivam.aac.core.Vocabulary
import com.robbyolivam.aac.data.ScanMode
import com.robbyolivam.aac.data.Settings
import com.robbyolivam.aac.data.SettingsLock
import com.robbyolivam.aac.ui.theme.OnWordColor
import com.robbyolivam.aac.ui.theme.wordColor

/** One button of the main navigation (bottom bar in portrait, side rail in landscape). */
private class NavAction(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val active: Boolean,
    val onClick: () -> Unit,
)

private class Predictions(val suggestion: String?, val board: List<Cell>, val keyboard: List<String>)

@Composable
fun CommunicationScreen(vm: AacViewModel) {
    val vocabulary by vm.vocabulary.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val board = vocabulary.board(vm.currentBoardId) ?: vocabulary.home
    val guide = vm.guide
    val guideOnBoard = vm.guideIndex(board.id)
    // When the next guide step is on another root board, point at the button that gets there.
    val guideRoot = guide?.path?.first()?.boardId?.takeIf { guideOnBoard == null }
    val showBackBar = vm.panel == Panel.KEYBOARD || board.id != vocabulary.homeBoardId
    val predictions = Predictions(
        suggestion = vm.sentenceSuggestion(),
        board = if (settings.showPredictions && !vm.editMode && vm.panel == Panel.BOARD) vm.boardPredictions() else emptyList(),
        keyboard = if (settings.showPredictions && !vm.editMode && vm.panel == Panel.KEYBOARD) vm.keyboardPredictions() else emptyList(),
    )
    val scanning = settings.scanMode != ScanMode.OFF && !vm.editMode
    val scanner = vm.scanner
    val isScanned: (String, String) -> Boolean = { g, i -> scanning && scanner.isHighlighted(g, i) }

    var askPin by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val openSettings: () -> Unit = {
        when {
            settings.settingsLock == SettingsLock.PIN && settings.pin.isNotBlank() -> askPin = true
            else -> vm.screen = Screen.SETTINGS
        }
    }
    val nav = listOfNotNull(
        NavAction("home", Icons.Default.Home, "Home", guideRoot == vocabulary.homeBoardId) { vm.goHome() },
        NavAction("abc", Icons.Default.Keyboard, "ABC", vm.panel == Panel.KEYBOARD) {
            vm.panel = if (vm.panel == Panel.KEYBOARD) Panel.BOARD else Panel.KEYBOARD
        },
        NavAction("phrases", Icons.Default.Forum, "Phrases", guideRoot == vocabulary.phrasesBoardId) { vm.openPhrases() },
        NavAction("forms", Icons.Default.Spellcheck, "Forms", vm.formsMode) { vm.formsPressed() },
        NavAction("find", Icons.Default.Search, "Find", false) { vm.searchOpen = true },
        if (!settings.showExploreButton) null else NavAction("explore", Icons.Default.Explore, if (vm.exploring) "Exploring" else "Explore", vm.exploring) { vm.exploring = !vm.exploring },
        NavAction("settings", Icons.Default.Settings, "Settings", false, openSettings),
    )

    if (scanning) {
        val groups = buildScanGroups(vm, vocabulary, board, settings, showBackBar, predictions, nav)
        SideEffect { scanner.groups = groups }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val landscape = maxWidth > maxHeight
        val compactHeight = maxHeight < 520.dp
        val boardArea: @Composable (Modifier) -> Unit = { modifier ->
            BoardArea(vm, board, settings, guideOnBoard, isScanned, modifier)
        }
        val holdToast = { Toast.makeText(context, "Hold to open settings", Toast.LENGTH_SHORT).show() }

        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(84.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).tutorialAnchor("nav")) {
                    nav.forEach { action ->
                        NavItem(action, settings, isScanned("nav", action.id), holdToast, Modifier.weight(1f).fillMaxWidth().navAnchor(action.id))
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (vm.editMode) EditBanner(vm, board)
                    if (compactHeight) {
                        Row(Modifier.fillMaxWidth().height(64.dp)) {
                            MessageBar(vm, isScanned, Modifier.tutorialAnchor("message").weight(1.3f).fillMaxHeight())
                            PredictionRow(vm, predictions, isScanned, Modifier.tutorialAnchor("predictions").weight(1f).fillMaxHeight())
                        }
                    } else {
                        MessageBar(vm, isScanned, Modifier.tutorialAnchor("message").fillMaxWidth().height(76.dp))
                        PredictionRow(vm, predictions, isScanned, Modifier.tutorialAnchor("predictions").fillMaxWidth().height(48.dp))
                    }
                    if (settings.showToneButtons && !vm.editMode) ToneRow(vm, isScanned, Modifier.fillMaxWidth().height(44.dp))
                    if (guide != null) GuideBanner(guide.cell.label, onCancel = vm::cancelGuide)
                    if (showBackBar) BackBar(vm, board, vocabulary, isScanned, compact = compactHeight)
                    boardArea(Modifier.weight(1f).fillMaxWidth().tutorialAnchor("board"))
                }
                Column(Modifier.width(if (compactHeight) 92.dp else 110.dp).fillMaxHeight().padding(vertical = 2.dp).tutorialAnchor("strip")) {
                    QuickStripItems(vm, vocabulary.quickStrip, settings, isScanned) { Modifier.weight(1f).fillMaxWidth().padding(2.dp) }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                if (vm.editMode) EditBanner(vm, board)
                MessageBar(vm, isScanned, Modifier.tutorialAnchor("message").fillMaxWidth().height(76.dp))
                PredictionRow(vm, predictions, isScanned, Modifier.tutorialAnchor("predictions").fillMaxWidth().height(48.dp))
                if (settings.showToneButtons && !vm.editMode) ToneRow(vm, isScanned, Modifier.fillMaxWidth().height(44.dp))
                if (guide != null) GuideBanner(guide.cell.label, onCancel = vm::cancelGuide)
                if (showBackBar) BackBar(vm, board, vocabulary, isScanned, compact = false)
                boardArea(Modifier.weight(1f).fillMaxWidth().tutorialAnchor("board"))
                Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 2.dp).tutorialAnchor("strip")) {
                    QuickStripItems(vm, vocabulary.quickStrip, settings, isScanned) { Modifier.weight(1f).fillMaxHeight().padding(2.dp) }
                }
                Row(Modifier.fillMaxWidth().height(60.dp).background(MaterialTheme.colorScheme.surface).padding(horizontal = 2.dp).tutorialAnchor("nav")) {
                    nav.forEach { action ->
                        NavItem(action, settings, isScanned("nav", action.id), holdToast, Modifier.weight(1f).fillMaxHeight().navAnchor(action.id))
                    }
                }
            }
        }

        if (scanning && settings.scanTouchAnywhere) ScanTouchOverlay(settings.scanMode, scanner)

        val tourStep = vm.tutorialStep
        val anchors = LocalTutorialAnchors.current
        when {
            tourStep != null && anchors != null -> TutorialOverlay(tourStep, anchors, vm::tutorialBack, vm::tutorialNext, vm::endTutorial)
            !settings.tutorialOffered && !vm.editMode -> WelcomeOffer(onStart = vm::startTutorial, onLater = vm::declineTutorial)
        }
    }

    if (askPin) {
        PinDialog(expected = settings.pin, onSuccess = { askPin = false; vm.screen = Screen.SETTINGS }, onDismiss = { askPin = false })
    }
    vm.formsFor?.let { cell -> WordFormsDialog(cell, vm.media, onPick = { vm.selectForm(cell, it) }, onDismiss = vm::dismissForms) }
    if (vm.searchOpen) SearchDialog(vm, vm.media, onDismiss = { vm.searchOpen = false })
    if (vm.showPartner) PartnerDisplay(vm.messageText(), onSpeak = vm::speakMessage, onClose = { vm.showPartner = false })
    vm.editing?.let { target -> EditCellDialog(vm, target) }
    if (vm.addingWord) AddWordWizard(vm, onDone = { vm.addingWord = false })
}

@Composable
private fun BoardArea(
    vm: AacViewModel,
    board: Board,
    settings: Settings,
    guideOnBoard: Int?,
    isScanned: (String, String) -> Boolean,
    modifier: Modifier,
) {
    Box(modifier) {
        when {
            vm.panel == Panel.KEYBOARD -> KeyboardPanel(
                layout = settings.keyboardLayout,
                typing = vm.typing,
                onKey = vm::typeKey,
                onSpace = vm::typeSpace,
                onBackspace = vm::backspace,
                isScanned = isScanned,
                modifier = Modifier.fillMaxSize(),
            )
            board.isScene -> SceneBoard(
                board = board,
                settings = settings,
                editMode = vm.editMode,
                highlightIndex = guideOnBoard,
                media = vm.media,
                isScanned = isScanned,
                onSelect = { index, cell -> vm.onCellSelected(board.id, index, cell) },
                onBackgroundTap = { x, y -> vm.onSceneBackgroundTap(board.id, x, y) },
                onMoveHotspot = { index, x, y -> vm.updateHotspot(board.id, index) { it.copy(x = x, y = y) } },
                modifier = Modifier.fillMaxSize(),
                exploring = vm.exploring,
            )
            else -> BoardGrid(
                board = board,
                settings = settings,
                editMode = vm.editMode,
                highlightIndex = guideOnBoard,
                movingIndex = vm.moveSource?.takeIf { it.boardId == board.id }?.index,
                media = vm.media,
                isScanned = isScanned,
                onSelect = { index, cell -> vm.onCellSelected(board.id, index, cell) },
                onLongPress = vm::onCellLongPress,
                modifier = Modifier.fillMaxSize(),
                exploring = vm.exploring && !vm.editMode,
            )
        }
    }
}

/** Switch-scanning order: way back, the board, predictions, quick strip, message, navigation. */
private fun buildScanGroups(
    vm: AacViewModel,
    vocabulary: Vocabulary,
    board: Board,
    settings: Settings,
    showBackBar: Boolean,
    predictions: Predictions,
    nav: List<NavAction>,
): List<ScanGroup> = buildList {
    val level = settings.vocabularyLevel
    if (showBackBar) {
        add(ScanGroup("header", "Back", listOf(ScanItem("back", "Back to ${vm.backTargetName()}") { vm.goBack() })))
    }
    when {
        vm.panel == Panel.KEYBOARD -> {
            keyboardRows(settings.keyboardLayout).forEachIndexed { r, row ->
                add(ScanGroup("keys-$r", row.take(3).toList().joinToString(" "), row.map { ch ->
                    ScanItem("key-$ch", ch.toString()) { vm.typeKey(ch.toString()) }
                }))
            }
            add(ScanGroup("keys-actions", "space, delete", listOf(
                ScanItem("key-space", "space") { vm.typeSpace() },
                ScanItem("key-delete", "delete") { vm.backspace() },
            )))
        }
        board.isScene -> {
            val items = board.scene!!.hotspots.mapIndexedNotNull { i, h ->
                if (!vm.usable(h.cell)) null else ScanItem(scanHotspotId(i), h.cell.label) { vm.onCellSelected(board.id, i, h.cell) }
            }
            if (items.isNotEmpty()) add(ScanGroup(SCENE_GROUP, board.name, items))
        }
        else -> for (row in 0 until board.rows) {
            val items = (0 until board.cols).mapNotNull { col ->
                val index = row * board.cols + col
                val cell = board.cells[index]?.takeIf { vm.usable(it) } ?: return@mapNotNull null
                ScanItem(scanCellId(index), cell.label) { vm.onCellSelected(board.id, index, cell) }
            }
            if (items.isNotEmpty()) add(ScanGroup(scanRowId(row), items.take(3).joinToString(", ") { it.label }, items))
        }
    }
    val predictionItems = buildList {
        predictions.suggestion?.let { s -> add(ScanItem("pred-polish", s) { vm.acceptSuggestion(s) }) }
        predictions.board.forEachIndexed { i, c -> add(ScanItem("pred-$i", c.label) { vm.choosePrediction(c.text) }) }
        predictions.keyboard.forEachIndexed { i, w -> add(ScanItem("pred-$i", w) { vm.choosePrediction(w) }) }
    }
    if (predictionItems.isNotEmpty()) add(ScanGroup("predictions", "predictions", predictionItems))
    add(ScanGroup("strip", "quick words", vocabulary.quickStrip.mapIndexed { i, c ->
        ScanItem("strip-$i", c.label) { vm.onStripSelected(i, c) }
    }))
    add(ScanGroup("message", "speak", listOf(
        ScanItem("speak", "speak") { vm.speakMessage() },
        ScanItem("delete", "delete word") { vm.backspace() },
        ScanItem("clear", "clear") { vm.clearMessage() },
    )))
    if (settings.showToneButtons) {
        add(ScanGroup("tones", "tones", Tone.entries.map { t -> ScanItem("tone-${t.name}", t.label) { vm.speakMessage(t) } }))
    }
    add(ScanGroup("nav", "menu", nav.map { ScanItem(it.id, it.label, it.onClick) }))
}

/** With scanning on, the whole screen can be the switch: tap anywhere (or left/right halves). */
@Composable
private fun ScanTouchOverlay(mode: ScanMode, scanner: Scanner) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(mode) {
                detectTapGestures { p ->
                    if (mode == ScanMode.STEP && p.x < size.width / 2) scanner.move() else scanner.select()
                }
            },
    ) {
        Text(
            if (mode == ScanMode.STEP) "Scanning: tap left side = next, right side = select" else "Scanning: tap anywhere to select",
            fontSize = 12.sp,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp)
                .background(ScanHighlight.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private fun Modifier.scanBorder(scanned: Boolean, shape: RoundedCornerShape = RoundedCornerShape(10.dp)): Modifier =
    if (scanned) this.border(4.dp, ScanHighlight, shape) else this

@Composable
private fun MessageBar(vm: AacViewModel, isScanned: (String, String) -> Boolean, modifier: Modifier) {
    val scroll = rememberScrollState()
    LaunchedEffect(vm.message.size, vm.typing) { scroll.animateScrollTo(scroll.maxValue) }
    Row(modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
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
                Text(
                    if (vm.formsMode) "Forms: tap a word to pick its form (wants, wanted…)" else "Tap words to build a message",
                    color = if (vm.formsMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    fontSize = 16.sp,
                )
            }
            vm.message.forEach { token -> MessageChip(token.text, token.symbol, vm) }
            if (vm.formsMode && vm.message.isNotEmpty()) {
                Text("· Forms: tap a word", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }
            if (vm.typing.isNotEmpty()) {
                Text(vm.typing + "▏", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        BarIcon(Icons.Default.VolumeUp, "Speak message", vm::speakMessage, isScanned("message", "speak"), tint = MaterialTheme.colorScheme.primary)
        BarIcon(Icons.Default.Backspace, "Delete last word", vm::backspace, isScanned("message", "delete"))
        BarIcon(Icons.Default.Clear, "Clear message", vm::clearMessage, isScanned("message", "clear"))
    }
}

@Composable
private fun MessageChip(text: String, symbol: Symbol?, vm: AacViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp)) {
        if (symbol != null) SymbolView(symbol, vm.media, Modifier.size(30.dp))
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
private fun BarIcon(icon: ImageVector, description: String, onClick: () -> Unit, scanned: Boolean, tint: Color? = null) {
    IconButton(onClick = onClick, modifier = Modifier.size(52.dp).scanBorder(scanned)) {
        Icon(icon, contentDescription = description, tint = tint ?: MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun PredictionRow(vm: AacViewModel, predictions: Predictions, isScanned: (String, String) -> Boolean, modifier: Modifier) {
    val context = LocalContext.current
    Row(modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            predictions.suggestion?.let { suggestion ->
                item("polish") {
                    Pill(
                        text = suggestion,
                        background = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        leading = { Icon(Icons.Default.AutoAwesome, "Suggestion", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                        scanned = isScanned("predictions", "pred-polish"),
                        onClick = { vm.acceptSuggestion(suggestion) },
                    )
                }
            }
            itemsIndexed(predictions.keyboard, key = { _, w -> "k-$w" }) { i, word ->
                Pill(text = word, scanned = isScanned("predictions", "pred-$i"), onClick = { vm.choosePrediction(word) })
            }
            itemsIndexed(predictions.board, key = { _, c -> "b-${c.id}" }) { i, cell ->
                Pill(
                    text = cell.label,
                    background = wordColor(cell.type),
                    textColor = OnWordColor,
                    leading = { SymbolMini(cell, vm) },
                    scanned = isScanned("predictions", "pred-$i"),
                    onClick = { vm.choosePrediction(cell.text) },
                )
            }
        }
        val settings by vm.settings.collectAsStateWithLifecycle()
        if (settings.usageLogging) {
            // Always visible while logging is on, and the person can pause it themselves.
            IconButton(onClick = vm::toggleUsagePause) {
                if (settings.usagePaused) {
                    Icon(Icons.Default.PauseCircle, "Usage log paused. Tap to resume.", tint = MaterialTheme.colorScheme.outline)
                } else {
                    Icon(Icons.Default.FiberManualRecord, "Usage log on. Tap to pause.", tint = Color(0xFFD32F2F))
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
private fun Modifier.navAnchor(id: String): Modifier = if (id == "settings") tutorialAnchor("nav-settings") else this

/** Say the whole message as a question, excited, whispered… One tap, no mode to switch off. */
@Composable
private fun ToneRow(vm: AacViewModel, isScanned: (String, String) -> Boolean, modifier: Modifier) {
    Row(modifier.padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Tone.entries.forEach { tone ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .scanBorder(isScanned("tones", "tone-${tone.name}"), RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClickLabel = "Say it ${tone.label.lowercase()}") { vm.speakMessage(tone) }
                    .semantics { contentDescription = "Say message: ${tone.label}" },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tone.emoji, fontSize = 13.sp, lineHeight = 14.sp)
                    Text(tone.label, fontSize = 10.sp, lineHeight = 11.sp, maxLines = 1, softWrap = false, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SymbolMini(cell: Cell, vm: AacViewModel) {
    SymbolView(cell.symbol, vm.media, Modifier.size(24.dp))
}

@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    background: Color = MaterialTheme.colorScheme.surfaceVariant,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    leading: (@Composable () -> Unit)? = null,
    scanned: Boolean = false,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .height(40.dp)
            .clip(shape)
            .background(background)
            .scanBorder(scanned, shape)
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
        Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Showing the way to “$label” — follow the flashing buttons", Modifier.weight(1f), fontSize = 14.sp)
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Stop showing the way") }
        }
    }
}

/**
 * In-app way back, right above the board: "◀ Back to Core" plus the page you're on.
 * Nobody has to know the phone's own back gesture.
 */
@Composable
private fun BackBar(
    vm: AacViewModel,
    board: Board,
    vocabulary: Vocabulary,
    isScanned: (String, String) -> Boolean,
    compact: Boolean,
) {
    val title = if (vm.panel == Panel.KEYBOARD) "Keyboard" else board.name
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().height(if (compact) 44.dp else 52.dp).padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary)
                .scanBorder(isScanned("header", "back"), shape)
                .clickable(role = Role.Button, onClickLabel = "Go back") { vm.goBack() }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            Text(
                "  Back to ${vm.backTargetName()}",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 15.sp else 17.sp,
                maxLines = 1,
            )
        }
        Text(
            title,
            Modifier.weight(1f),
            fontSize = if (compact) 16.sp else 19.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (vm.panel == Panel.BOARD && vm.backTargetName() != vocabulary.home.name) {
            TextButton(onClick = vm::goHome) {
                Icon(Icons.Default.Home, null)
                Text(" ${vocabulary.home.name}")
            }
        }
    }
}

@Composable
private fun QuickStripItems(
    vm: AacViewModel,
    cells: List<Cell>,
    settings: Settings,
    isScanned: (String, String) -> Boolean,
    itemModifier: () -> Modifier,
) {
    val stripSettings = settings.copy(labelScale = settings.labelScale * 0.95f, vocabularyLevel = Cell.MAX_LEVEL)
    cells.forEachIndexed { index, cell ->
        CellButton(
            cell = cell,
            settings = stripSettings,
            editMode = vm.editMode,
            highlighted = false,
            moving = false,
            media = vm.media,
            onSelect = { vm.onStripSelected(index, cell) },
            onLongPress = {},
            modifier = itemModifier(),
            compact = true,
            scanned = isScanned("strip", "strip-$index"),
        )
    }
}

@Composable
private fun NavItem(action: NavAction, settings: Settings, scanned: Boolean, holdToast: () -> Unit, modifier: Modifier) {
    val holdToOpen = action.id == "settings" && settings.settingsLock == SettingsLock.HOLD
    var pressed by remember { mutableStateOf(false) }
    val click by rememberUpdatedState(action.onClick)
    val toast by rememberUpdatedState(holdToast)
    val shape = RoundedCornerShape(10.dp)
    val input = if (holdToOpen) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                try {
                    val releasedEarly = withTimeoutOrNull(1500L) { waitForUpOrCancellation(); true } != null
                    if (releasedEarly) toast() else {
                        click()
                        waitForUpOrCancellation()
                    }
                } finally {
                    pressed = false
                }
            }
        }
    } else {
        Modifier.clickable(role = Role.Button) { click() }
    }
    Column(
        modifier
            .padding(2.dp)
            .clip(shape)
            .background(if (action.active || pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent)
            .scanBorder(scanned, shape)
            .then(input)
            .semantics {
                role = Role.Button
                contentDescription = if (holdToOpen) "${action.label} (hold)" else action.label
                onClick { click(); true }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(action.icon, contentDescription = null, modifier = Modifier.size(26.dp))
        Text(if (holdToOpen && pressed) "Hold…" else action.label, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun EditBanner(vm: AacViewModel, board: Board) {
    val canUndo by vm.store.canUndo.collectAsStateWithLifecycle()
    val canRedo by vm.store.canRedo.collectAsStateWithLifecycle()
    var options by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Editing “${board.name}”", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val move = vm.moveSource
                Text(
                    when {
                        move != null -> "Tap where “${move.cell.label}” should go"
                        vm.clipboard != null && !board.isScene -> "Copied “${vm.clipboard!!.label}” · tap an empty spot to paste"
                        board.isScene -> "Tap the photo to add a spot · drag spots to move"
                        else -> "Tap a button to change it"
                    },
                    fontSize = 12.sp,
                )
            }
            if (vm.moveSource != null) TextButton(onClick = vm::cancelMove) { Text("Cancel move") }
            IconButton(onClick = { vm.addingWord = true }) { Icon(Icons.Default.PostAdd, "Add a word") }
            IconButton(onClick = { options = true }) { Icon(Icons.Default.Tune, "Page options") }
            IconButton(onClick = vm::undo, enabled = canUndo) { Icon(Icons.Default.Undo, "Undo") }
            IconButton(onClick = vm::redo, enabled = canRedo) { Icon(Icons.Default.Redo, "Redo") }
            TextButton(onClick = vm::exitEditMode) { Text("Done") }
            Box(Modifier.width(4.dp))
        }
    }
    if (options) BoardOptionsDialog(vm, board, onDismiss = { options = false })
}
