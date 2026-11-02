package com.robbyolivam.aac

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.robbyolivam.aac.data.SettingsLock
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Fire HD 10 (13th gen, 2023): 1920×1200 at hdpi = 1280×800 dp, Fire OS 8 = Android 11 (API 30). */
private const val FIRE_HD10_LAND = "w1280dp-h800dp-land-hdpi"
private const val FIRE_HD10_PORT = "w800dp-h1280dp-port-hdpi"

/**
 * The app on the Fire HD 10: the same Android version (API 30) and screen, in both orientations.
 * Screenshots: app/build/screenshots/fire_*.png
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], qualifiers = FIRE_HD10_LAND)
class FireTabletTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<AacApplication>()

    init {
        com.robbyolivam.aac.ui.renderSymbolsSynchronously = true
    }

    private fun launch(firstRun: Boolean = false, setup: () -> Unit = {}) {
        app.settings.update { it.copy(tutorialOffered = !firstRun, settingsLock = SettingsLock.NONE) }
        setup()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/screenshots/fire_$name.png")

    private fun hasText(text: String, substring: Boolean = false) =
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun coreBoardLandscape() {
        launch()
        listOf("I", "want", "more").forEach { compose.onNodeWithContentDescription(it).performClick() }
        shot("land_core")
        assertTrue(hasText("more"))
    }

    @Test
    @Config(qualifiers = FIRE_HD10_PORT)
    fun coreBoardPortrait() {
        launch()
        shot("port_core")
    }

    @Test
    fun folderAndBack() {
        launch()
        compose.onNodeWithContentDescription("Things folder").performClick()
        shot("land_things")
        assertTrue("tablet word is there", hasText("tablet"))
        compose.onNodeWithText("Back to Core", substring = true).performClick()
    }

    @Test
    fun keyboard() {
        launch()
        compose.onNodeWithContentDescription("ABC").performClick()
        shot("land_keyboard")
    }

    @Test
    fun settingsAndEditing() {
        launch()
        compose.onNodeWithContentDescription("Settings").performClick()
        shot("land_settings")
        compose.onNodeWithText("Edit boards").performClick()
        compose.waitForIdle()
        shot("land_edit")
        compose.onNodeWithContentDescription("Add a word").performClick()
        compose.waitForIdle()
        shot("land_add_word")
    }

    @Test
    fun voiceHelpPointsToFireSettings() {
        org.robolectric.shadows.ShadowBuild.setManufacturer("Amazon")
        launch()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitForIdle()
        assertTrue(hasText("Keyboard & Language", substring = true))
    }

    @Test
    fun tourOnFirstRun() {
        launch(firstRun = true)
        compose.onNodeWithText("Take the tour").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.waitForIdle()
        shot("land_tour_message")
    }

    @Test
    @Config(qualifiers = FIRE_HD10_PORT)
    fun tourPortrait() {
        launch(firstRun = true)
        compose.onNodeWithText("Take the tour").performClick()
        repeat(3) { compose.onNodeWithText("Next").performClick() }
        compose.waitForIdle()
        shot("port_tour_board")
    }
}
