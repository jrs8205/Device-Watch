package org.jarsi.devicewatch.system.dream

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * The screensaver as a whole, drawn from a fixed sample: what each style puts on the screen
 * and what the frame around the styles does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h780dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DreamScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Application get() = ApplicationProvider.getApplicationContext()

    private fun sample(): DreamUiState =
        sampleDreamUiState(context, nowMs = SAMPLE_NOW_MS, locale = Locale.US, is24Hour = true)

    private fun show(options: DreamOptions = DreamOptions(), landscape: Boolean, onTap: (() -> Unit)? = null) {
        val state = sample()
        compose.setContent {
            DreamScreen(state = state, options = options, landscape = landscape, onTap = onTap, moving = false)
        }
    }

    @Test
    fun `Glow draws the sample in portrait`() {
        show(landscape = false)
        assertGlowSampleIsOnScreen()
    }

    @Test
    @Config(qualifiers = "w780dp-h360dp-land-mdpi")
    fun `Glow draws the sample in landscape`() {
        show(landscape = true)
        assertGlowSampleIsOnScreen()
    }

    private fun assertGlowSampleIsOnScreen() {
        val state = sample()
        compose.onNodeWithText(state.timeText).assertExists()
        compose.onNodeWithText(":${state.secondsText}").assertExists()
        compose.onNodeWithText(state.dateText).assertExists()
        compose.onNodeWithText("Pixel 8a").assertExists()
        compose.onNodeWithText("⏰ ${state.alarmText}").assertExists()
        compose.onNodeWithText("64%").assertExists()
        compose.onNodeWithText(context.getString(R.string.dream_charging)).assertExists()
        compose.onNodeWithText("3.82 V  ·  26.3 °C  ·  11.0 W").assertExists()
        compose.onNodeWithText(state.fullTimeText).assertExists()
    }

    @Test
    fun `Minimal draws the sample in portrait`() {
        show(options = DreamOptions(style = DreamStyle.MINIMAL), landscape = false)
        assertMinimalSampleIsOnScreen()
    }

    @Test
    @Config(qualifiers = "w780dp-h360dp-land-mdpi")
    fun `Minimal draws the sample in landscape`() {
        show(options = DreamOptions(style = DreamStyle.MINIMAL), landscape = true)
        assertMinimalSampleIsOnScreen()
    }

    private fun assertMinimalSampleIsOnScreen() {
        val state = sample()
        compose.onNodeWithText(state.timeText).assertExists()
        compose.onNodeWithText(state.shortDateText.uppercase(Locale.US)).assertExists()
        compose.onNodeWithText("64%  ·  charging  ·  11.0 W").assertExists()
        // What Minimal never shows: seconds, the device name, the alarm, the estimate.
        compose.onAllNodesWithText(":${state.secondsText}").assertCountEquals(0)
        compose.onAllNodesWithText("Pixel 8a").assertCountEquals(0)
        compose.onAllNodesWithText(state.alarmText, substring = true).assertCountEquals(0)
        compose.onAllNodesWithText(state.fullTimeText).assertCountEquals(0)
    }

    @Test
    fun `Night draws the sample in portrait`() {
        show(options = DreamOptions(style = DreamStyle.NIGHT), landscape = false)
        assertNightSampleIsOnScreen()
    }

    @Test
    @Config(qualifiers = "w780dp-h360dp-land-mdpi")
    fun `Night draws the sample in landscape`() {
        show(options = DreamOptions(style = DreamStyle.NIGHT), landscape = true)
        assertNightSampleIsOnScreen()
    }

    private fun assertNightSampleIsOnScreen() {
        val state = sample()
        compose.onNodeWithText(state.timeText).assertExists()
        compose.onNodeWithText(state.alarmText.uppercase(Locale.US)).assertExists()
        compose.onNodeWithText("64%").assertExists()
        // What Night never shows: the date, the device name, the figures, and any emoji
        // (they would be drawn in their own colours, not in red).
        compose.onAllNodesWithText(state.dateText).assertCountEquals(0)
        compose.onAllNodesWithText(state.shortDateText.uppercase(Locale.US)).assertCountEquals(0)
        compose.onAllNodesWithText("Pixel 8a").assertCountEquals(0)
        compose.onAllNodesWithText("11.0 W", substring = true).assertCountEquals(0)
        for (emoji in listOf("\u26A1", "\u23F0", "\uD83D\uDD0C", "\uD83D\uDD0B")) {
            compose.onAllNodesWithText(emoji, substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun `Ring draws the sample in portrait`() {
        show(options = DreamOptions(style = DreamStyle.RING), landscape = false)
        assertRingSampleIsOnScreen()
    }

    @Test
    @Config(qualifiers = "w780dp-h360dp-land-mdpi")
    fun `Ring draws the sample in landscape`() {
        show(options = DreamOptions(style = DreamStyle.RING), landscape = true)
        assertRingSampleIsOnScreen()
    }

    private fun assertRingSampleIsOnScreen() {
        val state = sample()
        compose.onNodeWithText(state.timeText).assertExists()
        compose.onNodeWithText(state.secondsText).assertExists()
        compose.onNodeWithText(state.shortDateText.uppercase(Locale.US)).assertExists()
        compose.onNodeWithText("64%").assertExists()
        compose.onNodeWithText("\u26A1 charging  \u00B7  11.0 W  \u00B7  26.3 \u00B0C").assertExists()
        // What Ring never shows: the device name, the alarm, the estimate.
        compose.onAllNodesWithText("Pixel 8a").assertCountEquals(0)
        compose.onAllNodesWithText(state.alarmText, substring = true).assertCountEquals(0)
        compose.onAllNodesWithText(state.fullTimeText).assertCountEquals(0)
    }

    @Test
    fun `the flip button is gone`() {
        show(landscape = false)

        compose.onAllNodesWithText("⇅").assertCountEquals(0)
        compose.onAllNodesWithText("⇄").assertCountEquals(0)
    }

    @Test
    fun `a tap closes`() {
        var taps = 0
        show(landscape = false, onTap = { taps++ })

        compose.onRoot().performClick()

        assertThat(taps).isEqualTo(1)
    }

    private companion object {
        /** 13 November 2026 at 18:10:26 UTC; the exact local time does not matter here. */
        const val SAMPLE_NOW_MS = 1_794_593_426_000L
    }
}
