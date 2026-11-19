package org.jarsi.devicewatch.system.dream

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Date
import java.util.Locale

/**
 * Where AM or PM stands beside the big clock. The clock is put together from separate texts,
 * and they stood in one fixed order: the time, its seconds, the marker. In Chinese the marker
 * is read before the time, and the alarm and the estimate on the same screen already wrote it
 * so. The marker's side now comes from the language's own clock pattern; the hours, minutes
 * and seconds keep their order from left to right in every language.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h780dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DreamAmPmPlaceTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val chinese: Locale = Locale.forLanguageTag("zh-CN")
    private val arabic: Locale = Locale.forLanguageTag("ar-EG-u-nu-latn")

    @Test
    fun `the marker's side follows which is read first and from which side reading starts`() {
        // English: the time first, from the left.
        assertThat(amPmLeads("h:mm a", rightToLeft = false)).isFalse()
        // Chinese: the marker first, from the left.
        assertThat(amPmLeads("ah:mm", rightToLeft = false)).isTrue()
        // Arabic: the time first, from the right, so the marker is on its left as in "12:59 PM" written out.
        assertThat(amPmLeads("h:mm a", rightToLeft = true)).isTrue()
        assertThat(amPmLeads("a h:mm", rightToLeft = true)).isFalse()
    }

    @Test
    fun `letters inside quoted text in a pattern are not the marker or the hour`() {
        assertThat(amPmLeads("'a' h:mm a", rightToLeft = false)).isFalse()
        assertThat(amPmLeads("'h' a h:mm", rightToLeft = false)).isTrue()
    }

    @Test
    fun `a pattern without a marker has none to lead`() {
        assertThat(amPmLeads("HH:mm", rightToLeft = false)).isFalse()
        assertThat(amPmLeads("HH:mm", rightToLeft = true)).isFalse()
    }

    @Test
    fun `the clock's texts say on which side the marker goes`() {
        val now = Date(SAMPLE_NOW_MS)

        assertThat(dreamClockFormats(context, Locale.US, is24Hour = false).texts(now).amPmLeads).isFalse()
        assertThat(dreamClockFormats(context, chinese, is24Hour = false).texts(now).amPmLeads).isTrue()
        assertThat(dreamClockFormats(context, arabic, is24Hour = false).texts(now).amPmLeads).isTrue()
        assertThat(dreamClockFormats(context, chinese, is24Hour = true).texts(now).amPmLeads).isFalse()
    }

    private var shownOptions by mutableStateOf(DreamOptions())

    private companion object {
        /** A Friday evening, like the other screensaver tests' sample. */
        const val SAMPLE_NOW_MS = 1_794_593_426_000L
    }

    private fun show(state: DreamUiState) {
        compose.setContent {
            DreamScreen(state = state, options = shownOptions, landscape = false, onTap = null, moving = false)
        }
    }

    private fun boundsOf(text: String): Rect = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot

    /** The three styles that draw the marker on the time's own line. */
    private val besideTheTime = listOf(DreamStyle.GLOW, DreamStyle.EDGE, DreamStyle.GAUGES)

    @Test
    fun `in Chinese the half of the day stands before the time, and the seconds after it`() {
        val state = sampleDreamUiState(context, nowMs = SAMPLE_NOW_MS, locale = chinese, is24Hour = false)
        assertThat(state.amPmText).isNotEmpty()
        show(state)
        for (style in besideTheTime) {
            shownOptions = DreamOptions(style = style)
            compose.waitForIdle()

            assertWithMessage("$style: marker before time")
                .that(boundsOf(state.amPmText).right).isAtMost(boundsOf(state.timeText).left)
            assertWithMessage("$style: seconds after time")
                .that(boundsOf(":${state.secondsText}").left).isAtLeast(boundsOf(state.timeText).right - 1f)
        }
    }

    @Test
    fun `in English PM stays after the time`() {
        val state = sampleDreamUiState(context, nowMs = SAMPLE_NOW_MS, locale = Locale.US, is24Hour = false)
        show(state)
        for (style in besideTheTime) {
            shownOptions = DreamOptions(style = style)
            compose.waitForIdle()

            assertWithMessage("$style").that(boundsOf(state.amPmText).left).isAtLeast(boundsOf(state.timeText).right - 1f)
        }
    }

    // Written out, an Arabic time has its marker on the left of the digits: the line is read
    // from the right, the time first.
    @Test
    fun `in Arabic the marker stands on the left of the time, as in a time written out`() {
        val state = sampleDreamUiState(context, nowMs = SAMPLE_NOW_MS, locale = arabic, is24Hour = false)
        assertThat(state.amPmText).isNotEmpty()
        show(state)
        for (style in besideTheTime) {
            shownOptions = DreamOptions(style = style)
            compose.waitForIdle()

            assertWithMessage("$style").that(boundsOf(state.amPmText).right).isAtMost(boundsOf(state.timeText).left)
        }
    }

    // Ring draws the marker under the time, on the seconds' line.
    @Test
    fun `Ring puts the marker on the side its language reads it`() {
        val state = sampleDreamUiState(context, nowMs = SAMPLE_NOW_MS, locale = chinese, is24Hour = false)
        shownOptions = DreamOptions(style = DreamStyle.RING)
        show(state)

        assertThat(boundsOf(state.amPmText).right).isAtMost(boundsOf(state.secondsText).left)
    }

    @Test
    fun `with the seconds off the marker still stands on its side`() {
        val state = sampleDreamUiState(context, nowMs = SAMPLE_NOW_MS, locale = chinese, is24Hour = false)
        show(state)
        for (style in besideTheTime) {
            shownOptions = DreamOptions(style = style, showSeconds = false)
            compose.waitForIdle()

            assertWithMessage("$style")
                .that(boundsOf(state.amPmText).right).isAtMost(boundsOf(state.timeText).left)
        }
    }
}
