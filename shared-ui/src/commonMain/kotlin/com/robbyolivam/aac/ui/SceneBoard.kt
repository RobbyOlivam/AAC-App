package com.robbyolivam.aac.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robbyolivam.aac.core.Board
import com.robbyolivam.aac.core.Cell
import com.robbyolivam.aac.core.Hotspot
import com.robbyolivam.aac.data.MediaFiles
import com.robbyolivam.aac.core.Settings
import com.robbyolivam.aac.ui.theme.OnWordColor
import com.robbyolivam.aac.ui.theme.wordColor

const val SCENE_GROUP = "scene"

fun scanHotspotId(index: Int) = "hotspot-$index"

/**
 * Visual scene display: a photo with tappable hotspots. In edit mode, tapping the photo adds a
 * hotspot and hotspots can be dragged to a new spot.
 */
@Composable
fun SceneBoard(
    board: Board,
    settings: Settings,
    editMode: Boolean,
    highlightIndex: Int?,
    media: MediaFiles,
    isScanned: (groupId: String, itemId: String) -> Boolean,
    onSelect: (Int, Cell) -> Unit,
    onBackgroundTap: (Float, Float) -> Unit,
    onMoveHotspot: (Int, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    exploring: Boolean = false,
) {
    val scene = board.scene ?: return
    val image by produceState(media.cached(scene.imagePath), scene.imagePath) { value = media.load(scene.imagePath) }
    BoxWithConstraints(modifier.padding(4.dp), contentAlignment = Alignment.Center) {
        val aspect = image?.let { it.width.toFloat() / it.height } ?: (4f / 3f)
        // Fit the photo inside the available space, keeping its aspect ratio.
        val boxAspect = maxWidth / maxHeight
        val w = if (aspect > boxAspect) maxWidth else maxHeight * aspect
        val h = if (aspect > boxAspect) maxWidth / aspect else maxHeight
        val currentTap by rememberUpdatedState(onBackgroundTap)
        Box(
            Modifier
                .size(w, h)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .pointerInput(editMode) {
                    if (editMode) detectTapGestures { p -> currentTap(p.x / size.width, p.y / size.height) }
                },
        ) {
            val photo = image
            if (photo != null) {
                Image(photo, contentDescription = board.name, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            } else {
                Text("Photo missing", color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
            scene.hotspots.forEachIndexed { index, hotspot ->
                if (!hotspot.cell.visibleAt(settings.vocabularyLevel) && !editMode && !exploring) return@forEachIndexed
                HotspotView(
                    hotspot = hotspot,
                    areaWidth = w,
                    areaHeight = h,
                    editMode = editMode,
                    highlighted = highlightIndex == index,
                    scanned = isScanned(SCENE_GROUP, scanHotspotId(index)),
                    onSelect = { onSelect(index, hotspot.cell) },
                    onMoved = { x, y -> onMoveHotspot(index, x, y) },
                )
            }
        }
    }
}

@Composable
private fun HotspotView(
    hotspot: Hotspot,
    areaWidth: androidx.compose.ui.unit.Dp,
    areaHeight: androidx.compose.ui.unit.Dp,
    editMode: Boolean,
    highlighted: Boolean,
    scanned: Boolean,
    onSelect: () -> Unit,
    onMoved: (Float, Float) -> Unit,
) {
    var drag by remember(hotspot) { mutableStateOf(Offset.Zero) }
    val select by rememberUpdatedState(onSelect)
    val moved by rememberUpdatedState(onMoved)
    val shape = RoundedCornerShape(12.dp)
    val color = wordColor(hotspot.cell.type)
    val border = when {
        scanned -> ScanHighlight
        highlighted -> MaterialTheme.colorScheme.primary
        else -> color
    }
    Box(
        Modifier
            .offset(areaWidth * hotspot.x, areaHeight * hotspot.y)
            .offset { androidx.compose.ui.unit.IntOffset(drag.x.toInt(), drag.y.toInt()) }
            .size(areaWidth * hotspot.width, areaHeight * hotspot.height)
            .clip(shape)
            .background(color.copy(alpha = if (editMode) 0.35f else 0.18f))
            .border(if (scanned || highlighted) 6.dp else 3.dp, border, shape)
            .pointerInput(editMode) {
                if (editMode) {
                    detectDragGestures(
                        onDrag = { change, amount -> change.consume(); drag += amount },
                        onDragEnd = {
                            val parentW = size.width / hotspot.width
                            val parentH = size.height / hotspot.height
                            val x = (hotspot.x + drag.x / parentW).coerceIn(0f, 1f - hotspot.width)
                            val y = (hotspot.y + drag.y / parentH).coerceIn(0f, 1f - hotspot.height)
                            drag = Offset.Zero
                            moved(x, y)
                        },
                    )
                }
            }
            .pointerInput(Unit) { detectTapGestures { select() } }
            .semantics {
                role = Role.Button
                contentDescription = hotspot.cell.label
                onClick { select(); true }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            "${hotspot.cell.symbol.emoji.orEmpty()} ${hotspot.cell.label}".trim(),
            color = OnWordColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier
                .padding(4.dp)
                .background(color, RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
