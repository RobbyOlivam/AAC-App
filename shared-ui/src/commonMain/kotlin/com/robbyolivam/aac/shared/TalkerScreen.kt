package com.robbyolivam.aac.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robbyolivam.aac.core.Board
import com.robbyolivam.aac.core.Cell
import com.robbyolivam.aac.core.CellAction
import com.robbyolivam.aac.core.SettingsLock
import com.robbyolivam.aac.core.Symbol
import com.robbyolivam.aac.core.ThemeMode
import com.robbyolivam.aac.core.WordType
import com.robbyolivam.aac.core.fitzgeraldRgb
import com.robbyolivam.aac.shared.resources.Res
import com.robbyolivam.aac.shared.resources.inter_bold
import com.robbyolivam.aac.shared.resources.inter_regular
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.Font

/** The word-kind colour of a button (modified Fitzgerald key), as on Android. */
fun wordColor(type: WordType): Color = Color(0xFF000000 or fitzgeraldRgb(type).toLong())

private val OnWord = Color(0xFF1B1B1B)

// Icons drawn from the bundled OpenMoji set, so they look the same everywhere (the web has no emoji font).
private const val ICON_SPEAK = "o:1F50A"
private const val ICON_DELETE = "o:2B05"
private const val ICON_CLEAR = "o:274C"
private const val ICON_SETTINGS = "o:2699"

@Composable
fun AacTheme(dark: Boolean, content: @Composable () -> Unit) {
    val inter = FontFamily(Font(Res.font.inter_regular), Font(Res.font.inter_bold, FontWeight.Bold))
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = inter), headlineMedium = base.headlineMedium.copy(fontFamily = inter),
        titleLarge = base.titleLarge.copy(fontFamily = inter), titleMedium = base.titleMedium.copy(fontFamily = inter),
        bodyLarge = base.bodyLarge.copy(fontFamily = inter), bodyMedium = base.bodyMedium.copy(fontFamily = inter),
        bodySmall = base.bodySmall.copy(fontFamily = inter), labelLarge = base.labelLarge.copy(fontFamily = inter),
        labelMedium = base.labelMedium.copy(fontFamily = inter), labelSmall = base.labelSmall.copy(fontFamily = inter),
    )
    val colors = if (dark) darkColorScheme(primary = Color(0xFF9ECAFF)) else lightColorScheme(primary = Color(0xFF1D5FA8))
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

/** The whole app: the talker, with Settings over it when open. */
@Composable
fun AacApp(talker: Talker, platform: Platform, systemDark: Boolean) {
    val pictures = remember { Pictures(platform) }
    pictures.index = talker.symbols
    val dark = when (talker.settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    var settingsOpen by remember { mutableStateOf(false) }
    AacTheme(dark) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!talker.loaded) return@Surface
            Box(Modifier.fillMaxSize()) {
                TalkerScreen(talker, pictures, onSettings = { settingsOpen = true })
                if (settingsOpen) SettingsScreen(talker, platform, onClose = { settingsOpen = false })
                talker.busy?.let { Overlay(it) }
                talker.notice?.let { NoticeBar(it, onDismiss = { talker.notice = null }) }
            }
        }
    }
}

@Composable
fun TalkerScreen(talker: Talker, pictures: Pictures, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(6.dp)) {
        MessageBar(talker, pictures, Modifier.fillMaxWidth().height(92.dp))
        Spacer(Modifier.height(6.dp))
        // Always the same height, home or not, so the board never moves.
        PageBar(talker, Modifier.fillMaxWidth().height(48.dp))
        Spacer(Modifier.height(4.dp))
        BoardGrid(talker.board, talker, pictures, Modifier.weight(1f).fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        BottomBar(talker, pictures, onSettings, Modifier.fillMaxWidth().height(72.dp))
    }
}

@Composable
private fun MessageBar(talker: Talker, pictures: Pictures, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.weight(1f).fillMaxHeight().clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(2.dp, MaterialTheme.colorScheme.outline, shape)
                .clickable { talker.speakMessage() }
                .semantics { contentDescription = "Message: tap to speak" }
                .horizontalScroll(rememberScrollState(), reverseScrolling = true)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (talker.message.isEmpty()) {
                Text("Tap words to build a message", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 20.sp)
            }
            talker.message.forEach { word ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    word.symbol?.let { SymbolImage(it, pictures, talker.mediaVersion, Modifier.size(44.dp)) }
                    Text(word.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        BarButton("Speak", ICON_SPEAK, pictures, Modifier.width(92.dp)) { talker.speakMessage() }
        BarButton("Delete", ICON_DELETE, pictures, Modifier.width(80.dp)) { talker.deleteWord() }
        BarButton("Clear", ICON_CLEAR, pictures, Modifier.width(80.dp)) { talker.clear() }
    }
}

@Composable
private fun BarButton(label: String, icon: String, pictures: Pictures, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.fillMaxHeight().clip(shape).background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick).semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SymbolImage(Symbol(libraryId = icon), pictures, 0, Modifier.size(36.dp))
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun PageBar(talker: Talker, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!talker.atHome) {
            Pill("← Back") { talker.back() }
            Pill("Home") { talker.home() }
        }
        Text(
            talker.board.name, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer) }
}

@Composable
fun BoardGrid(board: Board, talker: Talker, pictures: Pictures, modifier: Modifier) {
    val level = talker.settings.vocabularyLevel
    val typical = remember(board) { typicalWordLength(board) }
    Column(modifier) {
        for (row in 0 until board.rows) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0 until board.cols) {
                    val cell = board.cells[row * board.cols + col]
                    val m = Modifier.weight(1f).fillMaxHeight().padding(2.dp)
                    if (cell == null || !cell.visibleAt(level)) {
                        Box(m)
                    } else {
                        CellButton(cell, talker, pictures, typical, m) { talker.select(cell) }
                    }
                }
            }
        }
    }
}

/** The 75th-percentile label length on a page, so labels share one size (as on Android). */
fun typicalWordLength(board: Board): Int {
    val lengths = board.cells.filterNotNull().map { c -> c.label.split(' ').maxOfOrNull { it.length } ?: 0 }.sorted()
    return if (lengths.isEmpty()) 4 else lengths[(lengths.size * 3 / 4).coerceAtMost(lengths.size - 1)].coerceAtLeast(3)
}

@Composable
fun CellButton(cell: Cell, talker: Talker, pictures: Pictures, typicalWord: Int, modifier: Modifier, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val folder = cell.action == CellAction.FOLDER
    val contrast = talker.settings.highContrast
    val background = if (contrast) Color.Black else wordColor(cell.type)
    val textColor = if (contrast) Color.White else OnWord
    BoxWithConstraints(
        modifier.clip(shape).background(background)
            .border(if (contrast) 3.dp else 1.dp, if (contrast) wordColor(cell.type) else Color.Black.copy(alpha = 0.25f), shape)
            .clickable(onClick = onSelect)
            .semantics { contentDescription = cell.label + if (folder) " folder" else "" },
    ) {
        val labelSize = (maxWidth.value / (typicalWord.coerceAtLeast(4) * 0.62f)).coerceIn(11f, maxHeight.value / 4.2f).sp
        val longest = cell.label.split(' ').maxOfOrNull { it.length } ?: 0
        val fitted = if (longest > typicalWord) labelSize * (typicalWord.toFloat() / longest).coerceAtLeast(0.6f) else labelSize
        Column(Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                SymbolImage(cell.symbol, pictures, talker.mediaVersion, Modifier.fillMaxSize())
            }
            if (talker.settings.showLabels) {
                Text(
                    cell.label, fontSize = fitted, lineHeight = fitted * 1.05f, fontWeight = FontWeight.Bold,
                    color = textColor, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (folder) {
            // A folder tab, like the Android app's.
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(width = 22.dp, height = 8.dp).clip(RoundedCornerShape(3.dp)).background(Color.Black.copy(alpha = 0.3f)))
        }
    }
}

@Composable
private fun BottomBar(talker: Talker, pictures: Pictures, onSettings: () -> Unit, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val strip = talker.vocabulary.quickStrip
        strip.forEach { cell ->
            CellButton(cell, talker, pictures, 4, Modifier.weight(1f).fillMaxHeight()) { talker.selectStrip(cell) }
        }
        SettingsButton(talker, pictures, onSettings, Modifier.width(104.dp).fillMaxHeight())
    }
}

/** Opens Settings; with "Protect settings" (the default) it needs a 1.5-second hold. */
@Composable
private fun SettingsButton(talker: Talker, pictures: Pictures, onOpen: () -> Unit, modifier: Modifier) {
    val hold = talker.settings.settingsLock != SettingsLock.NONE
    var hint by remember { mutableStateOf(false) }
    LaunchedEffect(hint) { if (hint) { delay(2500); hint = false } }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = "Settings" }
            .pointerInput(hold) {
                detectTapGestures(
                    onTap = { if (hold) hint = true else onOpen() },
                    onLongPress = { onOpen() },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SymbolImage(Symbol(libraryId = ICON_SETTINGS), pictures, 0, Modifier.size(34.dp))
        Text(if (hint) "Hold to open" else "Settings", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Overlay(text: String) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 6.dp) {
            Text(text, Modifier.padding(28.dp), fontSize = 20.sp)
        }
    }
}

@Composable
private fun NoticeBar(text: String, onDismiss: () -> Unit) {
    // Drawn over the screen, never pushing the board down.
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomCenter) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.inverseSurface, tonalElevation = 8.dp) {
            Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text, color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 17.sp, modifier = Modifier.weight(1f, fill = false))
                TextButton(onClick = onDismiss) { Text("OK", color = MaterialTheme.colorScheme.inversePrimary, fontSize = 17.sp) }
            }
        }
    }
}
