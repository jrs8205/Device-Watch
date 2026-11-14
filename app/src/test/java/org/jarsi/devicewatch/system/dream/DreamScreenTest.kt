package org.jarsi.devicewatch.system.dream

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.platform.ViewRootForTest
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
    fun `Gauges draws the sample in portrait`() {
        show(options = DreamOptions(style = DreamStyle.GAUGES), landscape = false)
        assertGaugesSampleIsOnScreen()
    }

    @Test
    @Config(qualifiers = "w780dp-h360dp-land-mdpi")
    fun `Gauges draws the sample in landscape`() {
        show(options = DreamOptions(style = DreamStyle.GAUGES), landscape = true)
        assertGaugesSampleIsOnScreen()
    }

    private fun assertGaugesSampleIsOnScreen() {
        val state = sample()
        compose.onNodeWithText("PIXEL 8A").assertExists()
        compose.onNodeWithText(state.compactDateText.uppercase(Locale.US), substring = true).assertExists()
        compose.onNodeWithText(state.timeText).assertExists()
        compose.onNodeWithText(":${state.secondsText}").assertExists()
        val rows = listOf(
            R.string.dream_row_battery to "64% \u26A1",
            R.string.dream_row_power to "11.0 W",
            R.string.dream_row_temperature to "26.3 \u00B0C",
            R.string.dream_row_full to "~${state.fullClockText}",
            R.string.dream_row_alarm to state.alarmText,
        )
        for ((label, value) in rows) {
            compose.onNodeWithText(context.getString(label)).assertExists()
            compose.onNodeWithText(value).assertExists()
        }
        compose.onNodeWithText(context.getString(R.string.dream_row_notifications)).assertExists()
    }

    @Test
    fun `Edge draws the sample's texts and no charge emoji in portrait`() {
        show(options = DreamOptions(style = DreamStyle.EDGE), landscape = false)
        assertEdgeSampleIsOnScreen()
    }

    @Test
    @Config(qualifiers = "w780dp-h360dp-land-mdpi")
    fun `Edge draws the sample's texts and no charge emoji in landscape`() {
        show(options = DreamOptions(style = DreamStyle.EDGE), landscape = true)
        assertEdgeSampleIsOnScreen()
    }

    private fun assertEdgeSampleIsOnScreen() {
        val state = sample()
        compose.onNodeWithText(state.timeText).assertExists()
        compose.onNodeWithText(":${state.secondsText}").assertExists()
        compose.onNodeWithText(state.dateText).assertExists()
        compose.onNodeWithText("Pixel 8a").assertExists()
        compose.onNodeWithText(context.getString(R.string.dream_charging)).assertExists()
        compose.onNodeWithText("64%").assertExists()
        compose.onNodeWithText("3.82 V  \u00B7  26.3 \u00B0C  \u00B7  11.0 W").assertExists()
        compose.onNodeWithText(state.fullTimeText).assertExists()
        // The line round the screen says what the bolt, the plug and the battery said.
        for (emoji in listOf("\u26A1", "\uD83D\uDD0C", "\uD83D\uDD0B")) {
            compose.onAllNodesWithText(emoji, substring = true).assertCountEquals(0)
        }
    }

    /**
     * The screen as a picture. The view is drawn straight into a bitmap: what a style draws
     * into the frame's background lands there, which is where the Edge line lives.
     */
    private fun drawScreen(): Bitmap {
        val view = (compose.onRoot().fetchSemanticsNode().root as ViewRootForTest).view
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        return bitmap
    }

    private fun brightestChannel(picture: Bitmap, x: Int, y: Int): Float {
        val pixel = picture.getPixel(x, y)
        return maxOf(android.graphics.Color.red(pixel), android.graphics.Color.green(pixel), android.graphics.Color.blue(pixel)) / 255f
    }

    @Test
    fun `Edge lights the line as far as the battery is full`() {
        // 360 x 780 px at one pixel a dp. Standing still the line is in the lane 7 dp from
        // the edge; at 64 % it has left the bottom centre, climbed the left side, crossed
        // the top and come a little way down the right side.
        show(options = DreamOptions(style = DreamStyle.EDGE), landscape = false)

        val picture = drawScreen()
        fun brightness(x: Int, y: Int): Float = brightestChannel(picture, x, y)

        assertThat(brightness(7, 400)).isGreaterThan(0.6f)      // left side: lit
        assertThat(brightness(100, 7)).isGreaterThan(0.6f)      // top: lit
        assertThat(brightness(100, 773)).isGreaterThan(0.6f)    // bottom, left of centre: lit
        assertThat(brightness(353, 600)).isLessThan(0.2f)       // right side, low: the dark track
        assertThat(brightness(260, 773)).isLessThan(0.2f)       // bottom, right of centre: the dark track
        assertThat(brightness(353, 600)).isGreaterThan(0.03f)   // ...which is a track, not nothing
        assertThat(brightness(30, 300)).isLessThan(0.03f)       // inside the line: black
    }

    @Test
    fun `Edge starts from the viewer's bottom when the layout is flipped`() {
        // Flipped, the viewer's bottom is the screen's top: the line leaves the top centre
        // and, clockwise for the viewer, runs to the right first.
        show(options = DreamOptions(style = DreamStyle.EDGE, layoutSwapped = true), landscape = false)

        val picture = drawScreen()
        fun brightness(x: Int, y: Int): Float = brightestChannel(picture, x, y)

        assertThat(brightness(260, 7)).isGreaterThan(0.6f)      // top, right of centre: lit
        assertThat(brightness(353, 400)).isGreaterThan(0.6f)    // right side: lit
        assertThat(brightness(100, 7)).isLessThan(0.2f)         // top, left of centre: the dark track
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
