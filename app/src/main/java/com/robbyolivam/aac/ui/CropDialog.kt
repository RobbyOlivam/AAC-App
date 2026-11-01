package com.robbyolivam.aac.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Square crop math. The photo covers a square viewport at [zoom] (1 = the photo's short side
 * fills the square), moved by [panX]/[panY] (viewport pixels, from centered).
 */
object CropMath {
    const val MAX_ZOOM = 5f

    /** The scale from photo pixels to viewport pixels. */
    fun scale(width: Int, height: Int, viewport: Float, zoom: Float) = viewport / min(width, height) * zoom

    /** How far the photo may move from centered before it would leave a gap: (x, y). */
    fun maxPan(width: Int, height: Int, viewport: Float, zoom: Float): Pair<Float, Float> {
        val s = scale(width, height, viewport, zoom)
        return max(0f, (width * s - viewport) / 2) to max(0f, (height * s - viewport) / 2)
    }

    /** The square of the photo (left, top, side, in photo pixels) inside the viewport. */
    fun cropSquare(width: Int, height: Int, viewport: Float, zoom: Float, panX: Float, panY: Float): Triple<Int, Int, Int> {
        val s = scale(width, height, viewport, zoom)
        val side = (viewport / s).roundToInt().coerceIn(1, min(width, height))
        val left = ((width * s - viewport) / 2 - panX) / s
        val top = ((height * s - viewport) / 2 - panY) / s
        return Triple(
            left.roundToInt().coerceIn(0, width - side),
            top.roundToInt().coerceIn(0, height - side),
            side,
        )
    }
}

/**
 * Lets the person drag and pinch a photo so the important part fills the button.
 * [onDone] gets the cropped square; "Use whole photo" keeps it uncropped.
 */
@Composable
fun CropDialog(bitmap: Bitmap, onDone: (Bitmap) -> Unit, onCancel: () -> Unit) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var viewport by remember { mutableFloatStateOf(1f) }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Text("Crop picture", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Button(onClick = {
                        val (left, top, side) = CropMath.cropSquare(bitmap.width, bitmap.height, viewport, zoom, panX, panY)
                        onDone(Bitmap.createBitmap(bitmap, left, top, side, side))
                    }) { Text("Use") }
                }
                Text("Drag to move · pinch to zoom", Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val side = min(maxWidth.value, maxHeight.value).dp
                    Canvas(
                        Modifier
                            .size(side)
                            .border(3.dp, MaterialTheme.colorScheme.primary)
                            .pointerInput(bitmap) {
                                detectTransformGestures { _, pan, gestureZoom, _ ->
                                    zoom = (zoom * gestureZoom).coerceIn(1f, CropMath.MAX_ZOOM)
                                    val (mx, my) = CropMath.maxPan(bitmap.width, bitmap.height, viewport, zoom)
                                    panX = (panX + pan.x).coerceIn(-mx, mx)
                                    panY = (panY + pan.y).coerceIn(-my, my)
                                }
                            },
                    ) {
                        viewport = size.width
                        val s = CropMath.scale(bitmap.width, bitmap.height, viewport, zoom)
                        val w = bitmap.width * s
                        val h = bitmap.height * s
                        drawRect(Color.Black)
                        drawImage(
                            image,
                            dstOffset = IntOffset(((viewport - w) / 2 + panX).roundToInt(), ((viewport - h) / 2 + panY).roundToInt()),
                            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                        )
                    }
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onDone(bitmap) }) { Text("Use whole photo") }
                }
            }
        }
    }
}
