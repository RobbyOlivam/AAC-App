package com.robbyolivam.aac.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** One stop on the guided tour: the part of the screen to light up, and what to say about it. */
data class TutorialStep(val anchor: String?, val title: String, val text: String)

val tutorialSteps = listOf(
    TutorialStep(null, "Welcome!", "This quick tour shows the main parts of the talker. It takes about a minute. Tap Next to go on."),
    TutorialStep("message", "Message bar", "Words you tap appear here. Tap the message to say it all. The buttons beside it speak, delete the last word, or clear."),
    TutorialStep("predictions", "Suggestions", "Likely next words, and full-sentence ideas (the sparkle). Nothing is said until you tap one."),
    TutorialStep("board", "Core words", "The words people use most. They never move, so hands learn where they are. Colours show the kind of word."),
    TutorialStep("folder", "Folders", "Buttons with a little tab open a page of words, like Food. “Back to Core” at the top brings you back."),
    TutorialStep("strip", "Quick strip", "Yes, No, Stop, Help and more, on every page. One tap says it straight away."),
    TutorialStep("nav", "Menu", "Home goes back to Core. ABC is the keyboard. Phrases has ready-made sentences. Forms changes a word (want → wanted). Find shows where a word is."),
    TutorialStep("nav-settings", "Settings", "Voice, touch, levels and more. Edit boards (at the top of Settings) adds or changes buttons. How to use has this tour and the full guide."),
    TutorialStep(null, "You're ready!", "Try tapping “I”, “want”, “more”, then tap the message to hear it. The full guide is in Settings → How to use."),
)

/** Where tour targets are on screen (in root coordinates), registered by [tutorialAnchor]. */
val LocalTutorialAnchors = compositionLocalOf<SnapshotStateMap<String, Rect>?> { null }

/** Marks this element as the tour's target [key]. */
@Composable
fun Modifier.tutorialAnchor(key: String): Modifier {
    val anchors = LocalTutorialAnchors.current ?: return this
    return onGloballyPositioned { anchors[key] = it.boundsInRoot() }
}

/** Lets tests mark a tour target without the rest of the app. */
object TutorialTestHooks {
    @Composable
    fun Modifier.anchor(key: String): Modifier = tutorialAnchor(key)
}

/** Shown once, the first time the app opens: offer the tour (or not now). */
@Composable
fun WelcomeOffer(onStart: () -> Unit, onLater: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer()
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { contentDescription = "Welcome" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) { drawRect(Color.Black.copy(alpha = 0.6f)) }
        TutorialCard(
            title = "Welcome to your talker",
            text = "Would you like a quick tour of how it works? You can take it any time from Settings → How to use.",
        ) {
            OutlinedButton(onClick = onLater) { Text("Not now") }
            Button(onClick = onStart) { Text("Take the tour") }
        }
    }
}

/** The tour: dims the screen, cuts a spotlight around the current target, and explains it. */
@Composable
fun TutorialOverlay(step: Int, anchors: Map<String, Rect>, onBack: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    val current = tutorialSteps[step]
    // Targets are measured from the top of the window, but this overlay may start lower (below
    // the status bar, a notch or other system bars). Measure where the overlay itself is and
    // move each target into the overlay's own coordinates, so the spotlight lands on it exactly.
    var origin by remember { mutableStateOf<Offset?>(null) }
    val target = current.anchor?.let { anchors[it] }?.let { rect -> origin?.let { rect.translate(-it) } }
    val last = step == tutorialSteps.lastIndex
    val highlight = MaterialTheme.colorScheme.primary
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
            // Swallow taps so the board can't be used by accident during the tour.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        val density = LocalDensity.current
        val pad = with(density) { 6.dp.toPx() }
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(Color.Black.copy(alpha = 0.65f))
            if (target != null) {
                val hole = Rect(target.left - pad, target.top - pad, target.right + pad, target.bottom + pad)
                drawRoundRect(Color.Transparent, hole.topLeft, hole.size, CornerRadius(16f, 16f), blendMode = BlendMode.Clear)
                drawRoundRect(highlight, hole.topLeft, hole.size, CornerRadius(16f, 16f), style = Stroke(width = 4.dp.toPx()))
            }
        }
        if (target != null) {
            // An invisible box exactly over the spotlight, so tests (and accessibility tools) can
            // check it lines up with its target.
            with(density) {
                Box(
                    Modifier
                        .offset { IntOffset(target.left.roundToInt(), target.top.roundToInt()) }
                        .size(target.width.toDp(), target.height.toDp())
                        .testTag("tour-spotlight"),
                )
            }
        }
        // Put the card below the target if there's room, otherwise above it; centred without one.
        val screenHeight = with(density) { maxHeight.toPx() }
        val cardOffset: IntOffset? = target?.let { t ->
            val gap = with(density) { 16.dp.toPx() }
            val below = t.bottom + gap
            val y = if (below < screenHeight * 0.62f) below else (t.top - gap - with(density) { 230.dp.toPx() }).coerceAtLeast(gap)
            IntOffset(0, y.roundToInt())
        }
        Box(
            Modifier.fillMaxWidth().then(if (cardOffset != null) Modifier.offset { cardOffset } else Modifier.fillMaxSize()),
            contentAlignment = if (cardOffset != null) Alignment.TopCenter else Alignment.Center,
        ) {
            TutorialCard(title = current.title, text = current.text, progress = "${step + 1} of ${tutorialSteps.size}") {
                if (!last) TextButton(onClick = onSkip) { Text("Skip") }
                if (step > 0) OutlinedButton(onClick = onBack) { Text("Back") }
                Button(onClick = onNext) { Text(if (last) "Done" else "Next") }
            }
        }
    }
}

@Composable
private fun TutorialCard(title: String, text: String, progress: String? = null, buttons: @Composable () -> Unit) {
    Surface(
        Modifier.padding(16.dp).widthIn(max = 480.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (progress != null) Text(progress, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(text, fontSize = 16.sp, lineHeight = 22.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                buttons()
            }
        }
    }
}
