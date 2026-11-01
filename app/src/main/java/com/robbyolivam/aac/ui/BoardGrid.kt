package com.robbyolivam.aac.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robbyolivam.aac.core.Board
import com.robbyolivam.aac.core.Cell
import com.robbyolivam.aac.core.CellAction
import com.robbyolivam.aac.data.MediaFiles
import com.robbyolivam.aac.data.Settings
import com.robbyolivam.aac.ui.theme.OnWordColor
import com.robbyolivam.aac.ui.theme.wordColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Touch handling tuned for motor access: optional hold time (ignore brushes), select on
 * press or on release, and long-press for word forms when no hold time is set.
 */
fun Modifier.aacSelectable(
    holdTimeMs: Int,
    acceptOnRelease: Boolean,
    onPressedChange: (Boolean) -> Unit,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
): Modifier = pointerInput(holdTimeMs, acceptOnRelease) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        onPressedChange(true)
        try {
            if (holdTimeMs <= 0 && !acceptOnRelease) {
                onSelect()
                waitForUpOrCancellation()
                return@awaitEachGesture
            }
            val timeout = if (holdTimeMs > 0) holdTimeMs.toLong() else viewConfiguration.longPressTimeoutMillis
            var released = false
            val endedEarly = withTimeoutOrNull(timeout) {
                val up = waitForUpOrCancellation()
                if (up != null) {
                    up.consume()
                    released = true
                }
            } != null
            if (endedEarly) {
                // With a hold time, a quick tap is treated as an accidental touch.
                if (released && holdTimeMs <= 0) onSelect()
                return@awaitEachGesture
            }
            if (holdTimeMs > 0) {
                if (acceptOnRelease) {
                    if (waitForUpOrCancellation() != null) onSelect()
                } else {
                    onSelect()
                    waitForUpOrCancellation()
                }
            } else {
                onLongPress()
                waitForUpOrCancellation()
            }
        } finally {
            onPressedChange(false)
        }
    }
}

@Composable
fun BoardGrid(
    board: Board,
    settings: Settings,
    editMode: Boolean,
    highlightIndex: Int?,
    movingIndex: Int?,
    media: MediaFiles,
    isScanned: (groupId: String, itemId: String) -> Boolean,
    onSelect: (Int, Cell?) -> Unit,
    onLongPress: (Cell?) -> Unit,
    modifier: Modifier = Modifier,
    exploring: Boolean = false,
) {
    val dwellOn = settings.dwellMs > 0 && !editMode
    var dwellIndex by remember { mutableStateOf<Int?>(null) }
    val dwellProgress = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    val currentBoard by rememberUpdatedState(board)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val hapticsOn by rememberUpdatedState(settings.haptics)
    val level by rememberUpdatedState(settings.vocabularyLevel)
    val currentExploring by rememberUpdatedState(exploring)
    val pad = 2.dp

    // Slide-to-select: the finger can rest and slide anywhere; whichever button it rests on for
    // the dwell time is chosen. Lifting the finger cancels. A chosen button needs the finger to
    // leave it before it can be chosen again.
    val dwellModifier = if (!dwellOn) Modifier else Modifier.pointerInput(settings.dwellMs) {
        coroutineScope {
            val scope = this
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var current: Int? = null
                var chosen: Int? = null
                var job: Job? = null
                fun indexAt(p: Offset): Int? {
                    val b = currentBoard
                    val padPx = pad.toPx()
                    val col = ((p.x - padPx) / ((size.width - 2 * padPx) / b.cols)).toInt()
                    val row = ((p.y - padPx) / ((size.height - 2 * padPx) / b.rows)).toInt()
                    if (col !in 0 until b.cols || row !in 0 until b.rows) return null
                    val index = row * b.cols + col
                    val cell = b.cells[index] ?: return null
                    return index.takeIf { currentExploring || cell.visibleAt(level) }
                }
                fun track(p: Offset) {
                    val target = indexAt(p)
                    if (target == current) return
                    current = target
                    if (target != chosen) chosen = null
                    job?.cancel()
                    dwellIndex = target
                    if (target == null || target == chosen) return
                    job = scope.launch {
                        dwellProgress.snapTo(0f)
                        dwellProgress.animateTo(1f, tween(settings.dwellMs, easing = LinearEasing))
                        chosen = target
                        dwellIndex = null
                        if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnSelect(target, currentBoard.cells[target])
                    }
                }
                try {
                    track(down.position)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        track(change.position)
                    }
                } finally {
                    job?.cancel()
                    dwellIndex = null
                }
            }
        }
    }

    Column(modifier.then(dwellModifier).padding(pad)) {
        for (row in 0 until board.rows) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0 until board.cols) {
                    val index = row * board.cols + col
                    val cell = board.cells[index]
                    CellButton(
                        cell = cell,
                        settings = settings,
                        editMode = editMode,
                        highlighted = highlightIndex == index,
                        moving = movingIndex == index,
                        scanned = isScanned(scanRowId(row), scanCellId(index)),
                        media = media,
                        onSelect = { onSelect(index, cell) },
                        onLongPress = { onLongPress(cell) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        gap = 2.dp,
                        exploring = exploring,
                        touchEnabled = !dwellOn,
                        dwellProgress = if (dwellIndex == index) ({ dwellProgress.value }) else null,
                    )
                }
            }
        }
    }
}

fun scanRowId(row: Int) = "row-$row"
fun scanCellId(index: Int) = "cell-$index"

/** Bright, unmistakable switch-scanning highlight (distinct from the "where is it" pulse). */
val ScanHighlight = Color(0xFFFF6D00)

@Composable
fun CellButton(
    cell: Cell?,
    settings: Settings,
    editMode: Boolean,
    highlighted: Boolean,
    moving: Boolean,
    media: MediaFiles,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    scanned: Boolean = false,
    /** Space around the button; with [Settings.touchGaps] on, touches there still count. */
    gap: Dp = 0.dp,
    /** Show buttons hidden by level or by hand (explore mode). */
    exploring: Boolean = false,
    /** False while slide-to-select handles touches for the whole board. */
    touchEnabled: Boolean = true,
    /** Slide-to-select progress (0–1) while a finger rests on this button. */
    dwellProgress: (() -> Float)? = null,
) {
    val level = settings.vocabularyLevel
    val visible = cell != null && cell.visibleAt(level)
    val shown = visible || (cell != null && exploring)
    if (!shown && !editMode) {
        Box(modifier)
        return
    }
    val haptics = LocalHapticFeedback.current
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val hapticsOn by rememberUpdatedState(settings.haptics)
    var pressed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val contrast = settings.highContrast
    val typeColor = cell?.let { wordColor(it.type) }
    val background = when {
        cell == null -> MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
        contrast -> Color.Black
        else -> typeColor!!
    }
    val textColor = if (contrast) Color.White else OnWordColor
    val pulse = if (highlighted) {
        val transition = rememberInfiniteTransition(label = "guide")
        transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse").value
    } else {
        0f
    }
    val borderColor = when {
        scanned -> ScanHighlight
        highlighted -> MaterialTheme.colorScheme.primary.copy(alpha = pulse)
        moving -> MaterialTheme.colorScheme.secondary
        cell == null -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        contrast -> typeColor!!
        else -> Color.Black.copy(alpha = 0.25f)
    }
    val borderWidth = when {
        scanned -> 6.dp
        highlighted || moving -> 4.dp
        contrast -> 4.dp
        else -> 1.dp
    }
    val label = cell?.label ?: "Empty"

    val select = if (!touchEnabled) Modifier else Modifier.aacSelectable(
        holdTimeMs = if (editMode) 0 else settings.holdTimeMs,
        acceptOnRelease = editMode || settings.acceptOnRelease,
        onPressedChange = { pressed = it },
        onSelect = {
            if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentOnSelect()
        },
        onLongPress = {
            if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            currentOnLongPress()
        },
    )
    val gapsSelect = settings.touchGaps && gap > 0.dp
    val progressColor = MaterialTheme.colorScheme.primary
    Box(modifier.then(if (gapsSelect) select else Modifier).padding(gap)) { Box(
        Modifier
            .fillMaxSize()
            .scale(if (pressed) 0.94f else 1f)
            .clip(shape)
            .background(background)
            .border(borderWidth, borderColor, shape)
            .alpha(
                when {
                    cell == null || visible -> 1f
                    editMode -> 0.4f
                    else -> 0.6f // explore mode: shown, but clearly "extra"
                },
            )
            .then(if (gapsSelect) Modifier else select)
            .then(
                if (dwellProgress == null) Modifier else Modifier.drawWithContent {
                    drawContent()
                    val h = 8.dp.toPx()
                    drawRect(progressColor, topLeft = Offset(0f, size.height - h), size = Size(size.width * dwellProgress(), h))
                },
            )
            .semantics {
                role = Role.Button
                contentDescription = if (cell?.action == CellAction.FOLDER) "$label folder" else label
                onClick { currentOnSelect(); true }
                onLongClick { currentOnLongPress(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (cell == null) {
            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            return@Box
        }
        if (cell.action == CellAction.FOLDER) {
            // Folder tab, so folders read differently from words at a glance.
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 6.dp)
                    .size(width = 22.dp, height = 5.dp)
                    .background(textColor.copy(alpha = 0.45f), RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)),
            )
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 3.dp)) {
            // Short, wide buttons (landscape phones) put the picture beside the label.
            val wide = settings.showLabels && maxWidth > maxHeight * 1.7f
            // Labels grow with the button (tablets) but never shrink below a readable size.
            val density = LocalDensity.current
            val labelSize = with(density) {
                val byHeight = if (wide) maxHeight * 0.36f else maxHeight * 0.16f
                // Never so big that the longest word has to break mid-word.
                val textWidth = if (wide) maxWidth - maxHeight - 8.dp else maxWidth - 4.dp
                val longestWord = cell.label.split(' ').maxOf { it.length }.coerceAtLeast(3)
                val byWidth = textWidth / (0.62f * longestWord)
                minOf(byHeight, byWidth).toSp().value.coerceIn(if (compact) 8f else 9.5f, 26f).sp * settings.labelScale
            }
            if (wide) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.fillMaxHeight().aspectRatio(1f), contentAlignment = Alignment.Center) {
                        CellSymbol(cell, media, settings.showLabels, textColor)
                    }
                    Text(
                        text = cell.label,
                        color = textColor,
                        fontSize = labelSize,
                        lineHeight = labelSize * 1.1f,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp).weight(1f),
                    )
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CellSymbol(cell, media, settings.showLabels, textColor)
                    }
                    if (settings.showLabels) {
                        Text(
                            text = cell.label,
                            color = textColor,
                            fontSize = labelSize,
                            lineHeight = labelSize * 1.08f,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (editMode) {
            Row(Modifier.align(Alignment.TopEnd).padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (cell.level > level) Text("L${cell.level}", fontSize = 10.sp, color = textColor, fontWeight = FontWeight.Bold)
                if (cell.audioPath != null) Icon(Icons.Default.VolumeUp, null, Modifier.size(14.dp), tint = textColor)
                if (cell.hidden) Icon(Icons.Default.VisibilityOff, null, Modifier.size(14.dp), tint = textColor)
            }
        }
    } }
}

/** The picture for a cell (photo, library symbol or emoji), sized to whatever space it's given. */
@Composable
fun CellSymbol(cell: Cell, media: MediaFiles, showLabels: Boolean, textColor: Color) {
    SymbolView(
        symbol = cell.symbol,
        media = media,
        modifier = Modifier.fillMaxSize(),
        fallbackText = if (showLabels) null else cell.label,
        textColor = textColor,
    )
}
