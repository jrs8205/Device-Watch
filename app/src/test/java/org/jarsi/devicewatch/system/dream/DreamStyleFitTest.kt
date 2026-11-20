package org.jarsi.devicewatch.system.dream

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertWithMessage
import org.jarsi.devicewatch.LatinDigits
import org.jarsi.devicewatch.R
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
 * Every style at its narrowest: a 480 x 320 dp phone on its side, a 320 dp wide phone upright
 * and a low one of 320 x 480 dp, with the largest font scale the app lets through, in every
 * language the app has and in every charge state, drawing the widest texts the screensaver
 * can meet. Nothing may be cut off. A style added to [DreamStyle] is covered without a
 * change here.
 *
 * The digits are the ones the app prints (0 to 9 in Arabic too): the screensaver's own
 * context and the process default are set up as the running app sets them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DreamStyleFitTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w480dp-h320dp-land-mdpi")
    fun `narrow landscape in English`() = assertEveryStyleFits(landscape = true)

    @Test
    @Config(qualifiers = "fi-w480dp-h320dp-land-mdpi")
    fun `narrow landscape in Finnish`() = assertEveryStyleFits(landscape = true)

    @Test
    @Config(qualifiers = "w320dp-h640dp-port-mdpi")
    fun `narrow portrait in English`() = assertEveryStyleFits(landscape = false)

    @Test
    @Config(qualifiers = "fi-w320dp-h640dp-port-mdpi")
    fun `narrow portrait in Finnish`() = assertEveryStyleFits(landscape = false)

    @Test
    @Config(qualifiers = "ar-rEG-ldrtl-w480dp-h320dp-land-mdpi")
    fun `narrow landscape in Arabic`() = assertEveryStyleFits(landscape = true)

    @Test
    @Config(qualifiers = "zh-rCN-w480dp-h320dp-land-mdpi")
    fun `narrow landscape in Chinese`() = assertEveryStyleFits(landscape = true)

    @Test
    @Config(qualifiers = "ar-rEG-ldrtl-w320dp-h640dp-port-mdpi")
    fun `narrow portrait in Arabic`() = assertEveryStyleFits(landscape = false)

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h640dp-port-mdpi")
    fun `narrow portrait in Chinese`() = assertEveryStyleFits(landscape = false)

    // The test's Arabic font is not the phone's. The longest line, the estimate with a date,
    // must still fit when the letters are a twentieth wider than they are measured here.
    @Test
    @Config(qualifiers = "ar-rEG-ldrtl-w320dp-h640dp-port-mdpi")
    fun `narrow portrait in Arabic with letters a twentieth wider`() =
        assertEveryStyleFits(landscape = false, fontScale = MAX_FONT_SCALE * 1.05f)

    // A low upright screen: with the clock's extras and the notification icons showing,
    // the estimate under the battery was left a few pixels of height.
    @Test
    @Config(qualifiers = "w320dp-h480dp-port-mdpi")
    fun `low portrait in English`() = assertEveryStyleFits(landscape = false)

    @Test
    @Config(qualifiers = "fi-w320dp-h480dp-port-mdpi")
    fun `low portrait in Finnish`() = assertEveryStyleFits(landscape = false)

    @Test
    @Config(qualifiers = "ar-rEG-ldrtl-w320dp-h480dp-port-mdpi")
    fun `low portrait in Arabic`() = assertEveryStyleFits(landscape = false)

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h480dp-port-mdpi")
    fun `low portrait in Chinese`() = assertEveryStyleFits(landscape = false)

    // A phone can be set to a language the app is not written in, and its word for the half
    // of the day can be a long one: toki pona's is "pi pini suno", where English has "PM".
    // It stands beside the clock in every style, and the clock was sized as if nothing
    // wider than the seconds stood there: on Gauges the word ran off the screen's edge.
    @Test
    @Config(qualifiers = "w320dp-h640dp-port-mdpi")
    fun `narrow portrait with a long word for the half of the day`() =
        assertEveryStyleFits(landscape = false, halfOfDay = LONG_HALF_OF_DAY)

    @Test
    @Config(qualifiers = "w480dp-h320dp-land-mdpi")
    fun `narrow landscape with a long word for the half of the day`() =
        assertEveryStyleFits(landscape = true, halfOfDay = LONG_HALF_OF_DAY)

    @Test
    @Config(qualifiers = "w320dp-h480dp-port-mdpi")
    fun `low portrait with a long word for the half of the day`() =
        assertEveryStyleFits(landscape = false, halfOfDay = LONG_HALF_OF_DAY)

    @Test
    @Config(qualifiers = "w320dp-h640dp-port-mdpi")
    fun `narrow portrait with a long word for the half of the day and no seconds`() =
        assertEveryStyleFits(landscape = false, halfOfDay = LONG_HALF_OF_DAY, showSeconds = false)

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

    /** What the notification row calls itself: "N unread notifications". */
    private fun unreadDescription(state: DreamUiState): String {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val total = state.notificationGroups.sumOf { it.count }
        return context.resources.getQuantityString(R.plurals.dream_unread_notifications, total, total)
    }

    /**
     * [halfOfDay], when given, stands in for the language's own AM or PM, after the time as
     * in English.
     */
    private fun assertEveryStyleFits(
        landscape: Boolean,
        fontScale: Float = MAX_FONT_SCALE,
        halfOfDay: String? = null,
        showSeconds: Boolean = true,
    ) {
        fun worstCase(context: android.content.Context, chargeState: DreamChargeState): DreamUiState =
            worstCaseDreamUiState(context, chargeState).let { state ->
                if (halfOfDay == null) state else state.copy(amPmText = halfOfDay, amPmLeads = false)
            }
        // The digits of the running app: the test's plain application does not set them up.
        LatinDigits.applyToDefault()
        val context = LatinDigits.context(ApplicationProvider.getApplicationContext<Application>())
        var options by mutableStateOf(DreamOptions())
        var state by mutableStateOf(worstCase(context, DreamChargeState.CHARGING))
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, fontScale),
                LocalContext provides LatinDigits.context(LocalContext.current),
            ) {
                DreamScreen(state = state, options = options, landscape = landscape, onTap = null, moving = false)
            }
        }
        // Every style and state is drawn before the test fails, so one run names them all.
        val problems = mutableListOf<String>()
        for (style in DreamStyle.entries) {
            for (chargeState in DreamChargeState.entries) {
                options = DreamOptions(style = style, showCalendar = true, showSeconds = showSeconds)
                state = worstCase(context, chargeState)
                compose.waitForIdle()
                // The icons are decoded off the main thread; the row has its height, and
                // its counts, only once they are in. A style may leave the row out when it
                // runs out of room (Gauges on its side), so the wait is for a row that is
                // on the screen: it names itself to screen readers as soon as it is.
                val rowIsShown = compose.onAllNodes(hasContentDescription(unreadDescription(state)))
                    .fetchSemanticsNodes().isNotEmpty()
                if (rowIsShown) {
                    compose.waitUntil(timeoutMillis = 5_000) {
                        compose.onAllNodesWithText(WORST_CASE_FIRST_COUNT.toString())
                            .fetchSemanticsNodes().isNotEmpty()
                    }
                }
                try {
                    compose.assertNoTextCutOff(case = "$style, $chargeState")
                    compose.assertOnlyDigitsZeroToNine(case = "$style, $chargeState")
                } catch (problem: AssertionError) {
                    problems += problem.message.orEmpty().lineSequence().first()
                }
            }
        }
        assertWithMessage(problems.joinToString("\n", prefix = "\n")).that(problems).isEmpty()
    }

    private companion object {
        /** Toki pona's word for the afternoon, the longest Android has for a half of the day in Latin letters. */
        const val LONG_HALF_OF_DAY = "pi pini suno"
    }
}
