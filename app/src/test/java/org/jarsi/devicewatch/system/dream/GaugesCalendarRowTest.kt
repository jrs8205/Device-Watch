package org.jarsi.devicewatch.system.dream

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.jarsi.devicewatch.LatinDigits
import org.jarsi.devicewatch.presentation.ui.MAX_FONT_SCALE
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * External review round 8: on a phone in Bulgarian with a 12-hour clock, a calendar row of
 * the Gauges style wrote tomorrow's event as "СБ, 12:59 Ч. СЛ.ОБ.", 185.5 px wide, on a row
 * that had 163 px (a 400 x 320 dp screen on its side). The row measured the time first: its
 * end was cut off and the event's title was left no room at all.
 *
 * The whole row is drawn here, the time worded by the screensaver's own function in the
 * phone's language, and both halves are asked about: the time must be whole, and the title
 * must have room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GaugesCalendarRowTest {

    @get:Rule
    val compose = createComposeRule()

    private val defaultsBefore = Triple(
        Locale.getDefault(),
        Locale.getDefault(Locale.Category.DISPLAY),
        Locale.getDefault(Locale.Category.FORMAT),
    )

    @After
    fun restoreDefaults() {
        Locale.setDefault(defaultsBefore.first)
        Locale.setDefault(Locale.Category.DISPLAY, defaultsBefore.second)
        Locale.setDefault(Locale.Category.FORMAT, defaultsBefore.third)
    }

    private class Drawn(val layout: TextLayoutResult, val bounds: Rect)

    /** Newer Android puts a narrow no-break space before AM and PM; the tests read it as a space. */
    private fun String.plainSpaces(): String = replace(Char(0x202F), ' ').replace(Char(0xA0), ' ')

    private class DrawnRow(val time: Drawn, val title: Drawn, val timeText: String)

    /** The first calendar row of Gauges on a phone in [phone], with nothing but the calendar switched on beside the battery. */
    private fun drawRow(phone: String, landscape: Boolean): DrawnRow {
        LatinDigits.applyToDefault()
        val context = LatinDigits.context(ApplicationProvider.getApplicationContext<Application>())
        val locale = LatinDigits.locale(Locale.forLanguageTag(phone))
        val state = worstCaseDreamUiState(context, DreamChargeState.ON_BATTERY, locale)
        val options = DreamOptions(
            style = DreamStyle.GAUGES,
            showCalendar = true,
            showAlarm = false,
            showNotifications = false,
            showBatteryDetails = false,
        )
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, MAX_FONT_SCALE),
                LocalContext provides LatinDigits.context(LocalContext.current),
            ) {
                DreamScreen(state = state, options = options, landscape = landscape, onTap = null, moving = false)
            }
        }
        compose.waitForIdle()
        val event = state.calendarRows.first()
        fun drawn(text: String): Drawn {
            val node = compose.onAllNodesWithText(text, useUnmergedTree = true).onFirst()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return Drawn(layouts.single(), node.fetchSemanticsNode().boundsInRoot)
        }
        return DrawnRow(time = drawn(event.whenText), title = drawn(event.title), timeText = event.whenText)
    }

    private fun assertTimeWholeAndTitleHasRoom(row: DrawnRow, case: String) {
        val time = row.time.layout
        assertWithMessage("$case: \"${row.timeText}\" overflows its box").that(time.didOverflowWidth || time.didOverflowHeight).isFalse()
        assertWithMessage("$case: a line of \"${row.timeText}\" ends in an ellipsis")
            .that((0 until time.lineCount).any { time.isLineEllipsized(it) }).isFalse()
        assertWithMessage("$case: a word of \"${row.timeText}\" is broken in two")
            .that(time.multiParagraph.minIntrinsicWidth).isAtMost(time.size.width + 0.5f)
        if (time.lineCount == 1) {
            assertWithMessage("$case: \"${row.timeText}\" is cut off at its box's edge")
                .that(time.multiParagraph.maxIntrinsicWidth).isAtMost(time.size.width + 0.5f)
        }
        assertWithMessage("$case: the time takes ${time.lineCount} lines").that(time.lineCount).isAtMost(DREAM_LINE_MAX_LINES)
        assertWithMessage("$case: the letters of the title that are shown")
            .that(row.title.layout.getLineEnd(0, visibleEnd = true)).isAtLeast(TRUNCATED_SHOWS_AT_LEAST)
        assertWithMessage("$case: the title's share of the row, of ${row.title.bounds.right - row.time.bounds.left} px")
            .that(row.title.bounds.width).isAtLeast((row.title.bounds.right - row.time.bounds.left) * 0.39f)
        // Side by side, the time where reading starts: nothing of one is drawn over the other.
        assertWithMessage("$case: the time ends at ${row.time.bounds.right} and the title starts at ${row.title.bounds.left}")
            .that(row.time.bounds.right).isAtMost(row.title.bounds.left)
    }

    @Test
    @Config(qualifiers = "w400dp-h320dp-land-mdpi")
    fun `the Bulgarian row the review drew shows its time whole and leaves the title room`() {
        val row = drawRow("bg", landscape = true)

        assertThat(row.timeText.plainSpaces()).isEqualTo("СБ, 12:59 Ч. СЛ.ОБ.")
        assertTimeWholeAndTitleHasRoom(row, "bg, 400 x 320")
    }

    @Test
    @Config(qualifiers = "w400dp-h320dp-land-mdpi")
    fun `Latvian's long afternoon is whole on the narrowest row`() {
        val row = drawRow("lv", landscape = true)

        assertThat(row.timeText.plainSpaces()).isEqualTo("CETURTD., 12:59 PĒCPUSDIENĀ")
        assertTimeWholeAndTitleHasRoom(row, "lv, 400 x 320")
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-port-mdpi")
    fun `Latvian's long afternoon is whole on an upright phone`() {
        assertTimeWholeAndTitleHasRoom(drawRow("lv", landscape = false), "lv, 320 x 640")
    }

    // Where the row has the room, nothing changes: the time on one line at its own size.
    @Test
    @Config(qualifiers = "w800dp-h360dp-land-mdpi")
    fun `on a row with room the time stands on one line at its own size`() {
        val row = drawRow("en-US", landscape = true)

        assertTimeWholeAndTitleHasRoom(row, "en-US, 800 x 360")
        assertThat(row.time.layout.lineCount).isEqualTo(1)
        assertThat(row.timeText.plainSpaces()).isEqualTo("SAT 12:59 PM")
        // As wide as the same letters measured at the row label's own size, 13.2 sp.
        val context = ApplicationProvider.getApplicationContext<Application>()
        val atOwnSize = TextMeasurer(createFontFamilyResolver(context), Density(1f, MAX_FONT_SCALE), LayoutDirection.Ltr).measure(
            text = row.timeText,
            style = TextStyle(fontSize = 13.2f.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.18.em),
            softWrap = false,
            maxLines = 1,
        )
        assertThat(row.time.layout.multiParagraph.maxIntrinsicWidth).isWithin(0.5f).of(atOwnSize.multiParagraph.maxIntrinsicWidth)
        assertThat(row.title.bounds.right - row.time.bounds.left).isGreaterThan(300f)
    }
}
