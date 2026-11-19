package org.jarsi.devicewatch.system.dream

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The screensaver's texts that the app did not write: the phone's name, which is the user's
 * own, and a calendar event's title. Laid out by an Arabic screen's direction, a phone called
 * "123 456" was shown as "456 123". They read by their own first letter, and from the left
 * when they have none; where they stand on the screen is not changed by that.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "ar-rEG-w360dp-h780dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DreamOutsideTextTest {

    @get:Rule
    val compose = createComposeRule()

    private var style by mutableStateOf(DreamStyle.GLOW)

    private val state = plainDreamUiState(
        calendarRows = listOf(DreamCalendarRow(whenText = "10:00", title = EVENT)),
    ).copy(deviceName = NAME)

    private fun show() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                DreamScreen(
                    state = state,
                    options = DreamOptions(style = style, showCalendar = true),
                    landscape = false,
                    onTap = null,
                    moving = false,
                )
            }
        }
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    private fun assertReadsFromTheLeft(text: String, where: String) {
        val layout = layoutOf(text)
        assertWithMessage("$where: $text").that(layout.layoutInput.style.textDirection).isEqualTo(TextDirection.ContentOrLtr)
        assertWithMessage("$where: $text").that(layout.getParagraphDirection(0)).isEqualTo(ResolvedTextDirection.Ltr)
    }

    @Test
    fun `the phone's name keeps its written order in every style that shows it`() {
        show()
        for (shown in listOf(DreamStyle.GLOW, DreamStyle.EDGE, DreamStyle.RING, DreamStyle.GAUGES)) {
            style = shown
            compose.waitForIdle()

            assertReadsFromTheLeft(NAME, shown.name)
        }
    }

    @Test
    fun `a calendar event's title keeps its written order`() {
        style = DreamStyle.GAUGES
        show()

        assertReadsFromTheLeft(EVENT, "Gauges")
    }

    private companion object {
        /** A name with no letter in it: the same in capitals, which Gauges writes it in. */
        const val NAME = "123 456"
        const val EVENT = "7 Minute Workout"
    }
}
