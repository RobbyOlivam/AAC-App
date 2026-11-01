package com.robbyolivam.aac

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.robbyolivam.aac.core.Cell
import com.robbyolivam.aac.core.CellAction
import com.robbyolivam.aac.core.DefaultVocabulary
import com.robbyolivam.aac.core.Hotspot
import com.robbyolivam.aac.core.Symbol
import com.robbyolivam.aac.core.WordType
import com.robbyolivam.aac.core.addBoard
import com.robbyolivam.aac.core.addHotspot
import com.robbyolivam.aac.core.newSceneBoard
import com.robbyolivam.aac.core.setCell
import com.robbyolivam.aac.data.ScanMode
import com.robbyolivam.aac.data.SettingsLock
import com.robbyolivam.aac.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val PIXEL = "w412dp-h915dp-port-xxhdpi"
private const val PIXEL_LAND = "w915dp-h412dp-land-xxhdpi"
private const val TABLET = "w800dp-h1280dp-port-xhdpi"
private const val TABLET_LAND = "w1280dp-h800dp-land-xhdpi"

/**
 * Drives the real app on the JVM (Robolectric) and saves screenshots of each state.
 * `./gradlew :app:recordRoborazziDebug` writes the PNGs to app/build/screenshots/;
 * a plain test run still checks every assertion.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PIXEL)
class ScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /** Applies state changes (settings, vocabulary), then reopens the screen so it starts fresh with them. */
    private fun launch(setup: () -> Unit = {}) {
        setup()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    private val app get() = ApplicationProvider.getApplicationContext<AacApplication>()

    init {
        com.robbyolivam.aac.ui.renderSymbolsSynchronously = true
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/screenshots/$name.png")

    private fun hasText(text: String, substring: Boolean = false) =
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun coreBoardPhone() {
        launch()
        shot("phone_core")
    }

    @Test
    @Config(qualifiers = PIXEL_LAND)
    fun coreBoardPhoneLandscape() {
        launch()
        shot("phone_land_core")
    }

    @Test
    @Config(qualifiers = TABLET)
    fun coreBoardTablet() {
        launch()
        shot("tablet_core")
    }

    @Test
    @Config(qualifiers = TABLET_LAND)
    fun coreBoardTabletLandscape() {
        launch()
        shot("tablet_land_core")
    }

    @Test
    fun newFoldersAndSymbols() {
        launch()
        for (folder in listOf("Food", "Animals", "Little words", "Body")) {
            compose.onNodeWithContentDescription("$folder folder").performClick()
            shot("phone_folder_" + folder.lowercase().replace(' ', '_'))
            compose.onNodeWithText("Back to Core", substring = true).performClick()
        }
    }

    @Test
    fun emojiStyleSetting() {
        launch { app.settings.update { it.copy(symbolStyle = com.robbyolivam.aac.data.SymbolStyle.EMOJI) } }
        shot("phone_core_emoji_style")
    }

    @Test
    fun folderShowsInAppBackAndWordReturnsHome() {
        launch()
        compose.onNodeWithContentDescription("Food folder").performClick()
        assertTrue("Back bar shown in folder", hasText("Back to Core", substring = true))
        shot("phone_food_folder")
        compose.onNodeWithContentDescription("pizza").performClick()
        compose.waitForIdle()
        assertTrue("Returned to core after picking a word", !hasText("Back to Core", substring = true))
        assertTrue("pizza is in the message", hasText("pizza"))
        shot("phone_after_pizza")
    }

    @Test
    fun backBarGoesBack() {
        launch()
        compose.onNodeWithContentDescription("Food folder").performClick()
        compose.onNodeWithText("Back to Core", substring = true).performClick()
        compose.waitForIdle()
        assertTrue(!hasText("Back to Core", substring = true))
        compose.onNodeWithContentDescription("want").assertExists()
    }

    @Test
    @Config(qualifiers = PIXEL_LAND)
    fun folderLandscape() {
        launch()
        compose.onNodeWithContentDescription("Feelings folder").performClick()
        assertTrue(hasText("Back to Core", substring = true))
        shot("phone_land_feelings_folder")
    }

    @Test
    fun keyboardHasBackAndTypes() {
        launch()
        compose.onNodeWithContentDescription("ABC").performClick()
        compose.onNodeWithText("h").performClick()
        compose.onNodeWithText("i").performClick()
        assertTrue(hasText("Back to", substring = true))
        shot("phone_keyboard")
    }

    @Test
    @Config(qualifiers = PIXEL_LAND)
    fun keyboardLandscape() {
        launch()
        compose.onNodeWithContentDescription("ABC").performClick()
        shot("phone_land_keyboard")
    }

    @Test
    fun stepScanningSelectsAWord() {
        launch { app.settings.update { it.copy(scanMode = ScanMode.STEP) } }
        val selectSide: () -> Unit = {
            compose.onRoot().performTouchInput { click(Offset(width * 0.8f, height * 0.5f)) }
            compose.waitForIdle()
        }
        selectSide() // start: first row lights up
        shot("phone_scan_row")
        selectSide() // into the row: first button
        shot("phone_scan_item")
        selectSide() // pick "I"
        assertTrue("I was added to the message", !hasText("Tap words to build a message"))
        assertTrue("scanning banner shown", hasText("Scanning", substring = true))
    }

    @Test
    fun highContrastLevelOne() {
        launch { app.settings.update { it.copy(highContrast = true, vocabularyLevel = 1) } }
        shot("phone_contrast_level1")
        assertTrue(!hasText("who"))
    }

    @Test
    fun settingsAndEditMode() {
        launch { app.settings.update { it.copy(settingsLock = SettingsLock.NONE) } }
        compose.onNodeWithContentDescription("Settings").performClick()
        assertTrue(hasText("Back to talking", substring = true))
        shot("phone_settings")
        compose.onNodeWithText("Edit boards", substring = true).performClick()
        compose.waitForIdle()
        assertTrue(hasText("Editing", substring = true))
        shot("phone_edit_mode")
    }

    @Test
    fun settingsDarkModeIsReadable() {
        launch { app.settings.update { it.copy(settingsLock = SettingsLock.NONE, themeMode = ThemeMode.DARK) } }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Vibrate on tap").performScrollTo()
        shot("phone_settings_dark")
    }

    @Test
    fun voiceAgeSetting() {
        launch { app.settings.update { it.copy(settingsLock = SettingsLock.NONE) } }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Kid").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(com.robbyolivam.aac.core.VoiceAge.KID, app.settings.settings.value.voiceAge)
        shot("phone_settings_voice_age")
    }

    @Test
    fun exploreShowsHiddenWordsForNow() {
        launch { app.settings.update { it.copy(vocabularyLevel = 1, showExploreButton = true) } }
        assertTrue("who is hidden at level 1", !hasText("who"))
        compose.onNodeWithContentDescription("Explore").performClick()
        assertTrue("explore shows who", hasText("who"))
        shot("phone_explore")
        compose.onNodeWithContentDescription("who").performClick()
        compose.waitForIdle()
        assertEquals("who went into the message", 2, compose.onAllNodesWithText("who").fetchSemanticsNodes().size)
        compose.onNodeWithContentDescription("Exploring").performClick()
        compose.waitForIdle()
        assertTrue("hidden again after turning explore off", compose.onAllNodesWithContentDescription("who").fetchSemanticsNodes().isEmpty())
        assertEquals(1, app.settings.settings.value.vocabularyLevel)
    }

    @Test
    fun copyAndPasteAButton() {
        launch {
            app.settings.update { it.copy(settingsLock = SettingsLock.NONE) }
            app.vocabulary.edit { it.setCell(DefaultVocabulary.HOME, 47, null) }
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Edit boards", substring = true).performClick()
        compose.onNodeWithContentDescription("want").performClick()
        compose.onNodeWithText("Copy", substring = true).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(hasText("tap an empty spot to paste", substring = true))
        compose.onNodeWithContentDescription("Empty").performClick()
        compose.onNodeWithText("Paste “want” here", substring = true).performClick()
        compose.waitForIdle()
        val home = app.vocabulary.vocabulary.value.board(DefaultVocabulary.HOME)!!
        val pasted = home.cells[47]!!
        assertEquals("want", pasted.label)
        assertTrue("pasted button has its own id", home.cells.count { it?.id == pasted.id } == 1)
        shot("phone_after_paste")
    }

    @Test
    fun abbreviationExpandsWhenTyped() {
        launch { app.vocabulary.edit { it.copy(abbreviations = mapOf("hru" to "how are you")) } }
        compose.onNodeWithContentDescription("ABC").performClick()
        listOf("h", "r", "u").forEach { compose.onNodeWithText(it).performClick() }
        assertTrue("expansion offered as a prediction", hasText("how are you"))
        compose.onNodeWithText("space").performClick()
        compose.waitForIdle()
        listOf("how", "are", "you").forEach { assertTrue("$it in message", hasText(it)) }
        shot("phone_abbreviation")
    }

    @Test
    fun slideToSelectChoosesAfterResting() {
        launch { app.settings.update { it.copy(dwellMs = 600) } }
        val want = compose.onNodeWithContentDescription("want").fetchSemanticsNode().boundsInRoot
        val go = compose.onNodeWithContentDescription("go").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput { down(want.center) }
        compose.mainClock.advanceTimeBy(300)
        compose.onRoot().performTouchInput { moveTo(go.center) } // slid away before the dwell time
        compose.mainClock.advanceTimeBy(800)
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("go chosen by resting on it", compose.onAllNodesWithText("go").fetchSemanticsNodes().size >= 2)
        assertEquals("want not chosen (finger moved on)", 1, compose.onAllNodesWithText("want").fetchSemanticsNodes().size)
    }

    @Test
    fun settingsTouchAndVolume() {
        launch { app.settings.update { it.copy(settingsLock = SettingsLock.NONE, dwellMs = 800, touchGaps = true, volume = 0.8f) } }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Slide to select (rest on a button)").performScrollTo()
        shot("phone_settings_touch")
    }

    @Test
    fun usageLogFeedsTheReport() {
        launch { app.settings.update { it.copy(usageLogging = true, settingsLock = SettingsLock.NONE) } }
        listOf("I", "want", "more").forEach { compose.onNodeWithContentDescription(it).performClick() }
        compose.onNodeWithContentDescription("Speak message").performClick()
        compose.onNodeWithContentDescription("Food folder").performClick()
        compose.onNodeWithContentDescription("pizza").performClick()
        compose.onNodeWithContentDescription("want").performClick()
        compose.waitForIdle()
        val events = app.usage.readAll()
        assertEquals(listOf("I", "want", "more", "I want more", "pizza", "want"), events.map { it.text })
        assertEquals(com.robbyolivam.aac.core.UsageSource.FOLDER, events[4].source)

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Usage report…").performScrollTo().performClick()
        compose.waitUntil(5_000) { hasText("Words used") }
        assertTrue("5 words", hasText("5"))
        assertTrue("MLU of the one message", hasText("3.0"))
        assertTrue("80% core", hasText("80%"))
        shot("phone_usage_report")
        compose.onNodeWithText("Core page: taps per button").performScrollTo()
        shot("phone_usage_heatmap")
    }

    @Test
    fun privacyPauseStopsLogging() {
        launch { app.settings.update { it.copy(usageLogging = true) } }
        compose.onNodeWithContentDescription("I").performClick()
        compose.onNodeWithContentDescription("Usage log on. Tap to pause.").performClick()
        compose.onNodeWithContentDescription("want").performClick()
        compose.waitForIdle()
        assertTrue(app.settings.settings.value.usagePaused)
        shot("phone_usage_paused")
        compose.onNodeWithContentDescription("Usage log paused. Tap to resume.").performClick()
        compose.onNodeWithContentDescription("go").performClick()
        compose.waitForIdle()
        assertEquals(listOf("I", "go"), app.usage.readAll().map { it.text })
    }

    @Test
    fun noLoggingUnlessTurnedOn() {
        launch()
        compose.onNodeWithContentDescription("I").performClick()
        compose.onNodeWithContentDescription("Speak message").performClick()
        compose.waitForIdle()
        assertTrue(app.usage.readAll().isEmpty())
        assertTrue(compose.onAllNodesWithContentDescription("Usage log on. Tap to pause.").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun toneButtonsSayTheMessageInATone() {
        launch { app.settings.update { it.copy(showToneButtons = true, usageLogging = true) } }
        listOf("you", "want", "more").forEach { compose.onNodeWithContentDescription(it).performClick() }
        shot("phone_tone_row")
        compose.onNodeWithContentDescription("Say message: Question").performClick()
        compose.onNodeWithContentDescription("Say message: Excited").performClick()
        compose.waitForIdle()
        val spoken = app.usage.readAll().filter { it.utterance }.map { it.text }
        assertEquals(listOf("you want more?", "you want more!"), spoken)
    }

    @Test
    @Config(qualifiers = PIXEL_LAND)
    fun toneRowLandscape() {
        launch { app.settings.update { it.copy(showToneButtons = true) } }
        shot("phone_land_tone_row")
    }

    @Test
    fun previewOnFirstTapUsesOnSecond() {
        launch { app.settings.update { it.copy(previewTaps = true) } }
        compose.onNodeWithContentDescription("want").performClick()
        compose.waitForIdle()
        assertTrue("first tap only previews", hasText("Tap words to build a message"))
        compose.onNodeWithContentDescription("go").performClick()
        compose.onNodeWithContentDescription("want").performClick()
        compose.waitForIdle()
        assertTrue("moving to another button previews that one instead", hasText("Tap words to build a message"))
        compose.onNodeWithContentDescription("want").performClick()
        compose.waitForIdle()
        assertTrue("second tap on the same button uses it", !hasText("Tap words to build a message"))
        assertEquals(1, compose.onAllNodesWithText("go").fetchSemanticsNodes().size)
        compose.onNodeWithContentDescription("Help").performClick() // quick strip: never needs two taps
    }

    @Test
    fun addWordWizardPlacesTheWord() {
        launch { app.settings.update { it.copy(settingsLock = SettingsLock.NONE) } }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Edit boards", substring = true).performClick()
        compose.onNodeWithContentDescription("Add a word").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("mango")
        compose.waitForIdle()
        shot("phone_add_word_wizard")
        compose.onNodeWithText("Add").performClick()
        compose.waitForIdle()
        val food = app.vocabulary.vocabulary.value.board("food")!!
        val mango = food.cells.filterNotNull().single { it.label == "mango" }
        assertEquals(com.robbyolivam.aac.core.WordType.NOUN, mango.type)
        assertEquals("the Mulberry mango symbol", "m:1888", mango.symbol.libraryId)
        assertTrue("shows the folder it went into", hasText("Editing “Food”"))
        shot("phone_add_word_result")
    }

    @Test
    fun addWordWizardFlagsExistingWordsAndGuessesVerbs() {
        launch { app.settings.update { it.copy(settingsLock = SettingsLock.NONE) } }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Edit boards", substring = true).performClick()
        compose.onNodeWithContentDescription("Add a word").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("pizza")
        compose.waitForIdle()
        assertTrue(hasText("is already on", substring = true))
        compose.onNode(hasSetTextAction()).performTextReplacement("bake")
        compose.waitForIdle()
        assertTrue("verb forms shown", hasText("bakes", substring = true))
    }

    @Test
    fun expandWordsSettingFailsGracefullyWithoutGemini() {
        launch { app.settings.update { it.copy(settingsLock = SettingsLock.NONE, expandWords = true) } }
        listOf("want", "more").forEach { compose.onNodeWithContentDescription(it).performClick() }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Expand my words (on-device AI)").performScrollTo()
        compose.waitUntil(10_000) { hasText("Not available on this phone", substring = true) || hasText("Checking this phone…") }
        shot("phone_settings_expand_words")
    }

    @Test
    @Config(qualifiers = PIXEL_LAND)
    fun photoScene() {
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(135, 206, 235))
            drawRect(0f, 420f, 800f, 600f, Paint().apply { color = Color.rgb(76, 175, 80) })
            drawRect(120f, 220f, 300f, 430f, Paint().apply { color = Color.rgb(121, 85, 72) })
        }
        val path = app.media.saveBitmap(bitmap, maxSize = 800)
        launch { app.vocabulary.edit { v ->
            v.addBoard(newSceneBoard("scene-test", "Park", path))
                .addHotspot("scene-test", Hotspot(0.12f, 0.35f, 0.3f, 0.4f, Cell("s1", "slide", type = WordType.NOUN)))
                .addHotspot("scene-test", Hotspot(0.6f, 0.62f, 0.3f, 0.3f, Cell("s2", "grass", type = WordType.NOUN)))
                .setCell(
                    DefaultVocabulary.HOME, 47,
                    Cell("park", "Park", symbol = Symbol(emoji = "🏞️"), type = WordType.FOLDER, action = CellAction.FOLDER, linkTo = "scene-test"),
                )
        } }
        compose.onNodeWithContentDescription("Park folder").performClick()
        compose.onNodeWithContentDescription("slide").performClick()
        compose.waitForIdle()
        shot("phone_land_scene")
        assertTrue("slide added", hasText("slide"))
        assertEquals("scene-test", app.vocabulary.vocabulary.value.board("scene-test")?.id)
    }
}
