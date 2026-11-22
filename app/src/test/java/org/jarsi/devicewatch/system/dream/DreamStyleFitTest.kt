package org.jarsi.devicewatch.system.dream

import android.app.Application
import android.content.Context
import android.graphics.Paint
import android.icu.util.ULocale
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
import org.jarsi.devicewatch.quantityText
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Every style at its narrowest: a 480 x 320 dp phone on its side, a 320 dp wide phone upright
 * and a low one of 320 x 480 dp, with the largest font scale the app lets through, in every
 * language the app has and in every charge state, drawing the widest texts the screensaver
 * can meet. Nothing may be cut off. A style added to [DreamStyle] is covered without a
 * change here.
 *
 * The digits are the ones the app prints (0 to 9 in Arabic too): the screensaver's own
 * context and the process default are set up as the running app sets them.
 *
 * The dates and the times of day are written in the phone's language, which need not be one
 * the app has: the last cases draw every style on a phone in the languages whose lines are the
 * widest, and on a 400 dp wide screen on its side as well. With the environment variable
 * [ALL_LANGUAGES_SWITCH] set they draw every language Android knows.
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

    // A phone can be set to any language Android knows. The app's texts are then the English
    // ones, but the dates and the times of day are written the phone's way, and some languages
    // need three times the room English does: Latvian's Thursday afternoon on a calendar row is
    // "CETURTD., 12:59 PĒCPUSDIENĀ". Every style is drawn in the languages whose lines are the
    // widest of all, found among every language Android has ([widestPhoneLanguages]).
    @Test
    @Config(qualifiers = "w480dp-h320dp-land-mdpi")
    fun `narrow landscape on a phone in the languages with the widest times`() =
        assertEveryStyleFits(landscape = true, phoneLanguages = widestPhoneLanguages())

    @Test
    @Config(qualifiers = "w400dp-h320dp-land-mdpi")
    fun `narrowest landscape on a phone in the languages with the widest times`() =
        assertEveryStyleFits(landscape = true, phoneLanguages = widestPhoneLanguages())

    @Test
    @Config(qualifiers = "w320dp-h640dp-port-mdpi")
    fun `narrow portrait on a phone in the languages with the widest times`() =
        assertEveryStyleFits(landscape = false, phoneLanguages = widestPhoneLanguages())

    @Test
    @Config(qualifiers = "w320dp-h480dp-port-mdpi")
    fun `low portrait on a phone in the languages with the widest times`() =
        assertEveryStyleFits(landscape = false, phoneLanguages = widestPhoneLanguages())

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

    /**
     * What the notification row calls itself, "N unread notifications", as the screen under
     * test writes it: by the screen's own function, from [context], the one the screen is
     * drawn with. Asked of the test's plain application it came out with the number in
     * Arabic-Indic digits in the Arabic cases, where the screen writes 676: the row was not
     * found by that name, and the wait for its counts was skipped (external review round 10).
     */
    private fun unreadDescription(context: Context, state: DreamUiState): String {
        val total = state.notificationGroups.sumOf { it.count }
        return quantityText(context, R.plurals.dream_unread_notifications, total, total)
    }

    /**
     * The languages whose times and dates take the most room, a few for each line the
     * screensaver writes in the phone's language, measured in the test's own font among every
     * language Android knows; and the ones the external review drew.
     */
    private fun widestPhoneLanguages(): List<Locale> {
        val context = LatinDigits.context(ApplicationProvider.getApplicationContext<Application>())
        val paint = Paint().apply { textSize = 100f }
        val languages = ULocale.getAvailableLocales().map { LatinDigits.locale(it.toLocale()) }
        val states = languages.associateWith { worstCaseDreamUiState(context, DreamChargeState.CHARGING, it) }
        val lines: List<(DreamUiState) -> String> = listOf(
            { it.amPmText }, { it.dateText }, { it.shortDateText }, { it.compactDateText }, { it.alarmText },
            { it.fullTimeText }, { it.fullClockText }, { it.calendarRows.first().whenText },
        )
        assertWithMessage("languages").that(languages.size).isAtLeast(500)
        // The whole sweep, for a change to how the lines fit: every language whose lines differ
        // from another's, about four minutes for the four screens. The switch is an input of
        // Gradle's test task (app/build.gradle.kts), so a run with it is never answered with the
        // result of one without.
        val all = System.getenv(ALL_LANGUAGES_SWITCH) != null
        val drawn = if (all) {
            languages.distinctBy { language -> lines.map { it(states.getValue(language)) } }
        } else {
            val widest = lines.flatMap { line ->
                languages.sortedByDescending { paint.measureText(line(states.getValue(it))) }.take(WIDEST_PER_LINE)
            }
            (widest + REVIEWED_PHONE_LANGUAGES.map { LatinDigits.locale(Locale.forLanguageTag(it)) }).distinct()
        }
        // In the test report's output, so that a reader of the result sees which run it was.
        println("DreamStyleFitTest: ${drawn.size} phone languages drawn of ${languages.size} ($ALL_LANGUAGES_SWITCH ${if (all) "set" else "not set"})")
        return drawn
    }

    /**
     * [halfOfDay], when given, stands in for the language's own AM or PM, after the time as
     * in English. [phoneLanguages] are the languages the dates and times are written in, one
     * after another; null is the language of the texts.
     */
    private fun assertEveryStyleFits(
        landscape: Boolean,
        fontScale: Float = MAX_FONT_SCALE,
        halfOfDay: String? = null,
        showSeconds: Boolean = true,
        phoneLanguages: List<Locale?> = listOf(null),
    ) {
        fun worstCase(context: Context, chargeState: DreamChargeState, language: Locale?): DreamUiState {
            val state = if (language == null) {
                worstCaseDreamUiState(context, chargeState)
            } else {
                worstCaseDreamUiState(context, chargeState, language)
            }
            return if (halfOfDay == null) state else state.copy(amPmText = halfOfDay, amPmLeads = false)
        }
        // The digits of the running app: the test's plain application does not set them up.
        LatinDigits.applyToDefault()
        val context = LatinDigits.context(ApplicationProvider.getApplicationContext<Application>())
        var options by mutableStateOf(DreamOptions())
        var state by mutableStateOf(worstCase(context, DreamChargeState.CHARGING, phoneLanguages.first()))
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, fontScale),
                LocalContext provides LatinDigits.context(LocalContext.current),
                // On the test's own thread: see LocalNotificationIconDecoding.
                LocalNotificationIconDecoding provides EmptyCoroutineContext,
            ) {
                DreamScreen(state = state, options = options, landscape = landscape, onTap = null, moving = false)
            }
        }
        // Every style and state is drawn before the test fails, so one run names them all.
        val problems = mutableListOf<String>()
        var rowsFound = 0
        for (language in phoneLanguages) for (style in DreamStyle.entries) {
            for (chargeState in DreamChargeState.entries) {
                val case = listOfNotNull(language?.toLanguageTag(), style, chargeState).joinToString()
                options = DreamOptions(style = style, showCalendar = true, showSeconds = showSeconds)
                state = worstCase(context, chargeState, language)
                compose.waitForIdle()
                // The row has its height, and its counts, only once its icons are in; here
                // they are decoded in place, and the wait is for the frame that draws them. A
                // style may leave the row out when it runs out of room (Gauges on its side),
                // so the wait is for a row that is on the screen: it names itself to screen
                // readers as soon as it is.
                val rowIsShown = compose.onAllNodes(hasContentDescription(unreadDescription(context, state)))
                    .fetchSemanticsNodes().isNotEmpty()
                if (rowIsShown) {
                    rowsFound++
                    compose.waitUntil(timeoutMillis = 5_000) {
                        compose.onAllNodesWithText(WORST_CASE_FIRST_COUNT.toString())
                            .fetchSemanticsNodes().isNotEmpty()
                    }
                } else if (compose.onAllNodesWithText(WORST_CASE_FIRST_COUNT.toString()).fetchSemanticsNodes().isNotEmpty()) {
                    // A count is on the screen, so the row is; the test looked for it by another
                    // name than the screen gives it, and went on without the wait above.
                    problems += "$case: the notification row is drawn, and no node calls itself \"${unreadDescription(context, state)}\""
                }
                try {
                    // Only a phone's own language can make a line too long for its floor.
                    compose.assertNoTextCutOff(case, linesMayWrap = language != null)
                    compose.assertOnlyDigitsZeroToNine(case)
                } catch (problem: AssertionError) {
                    problems += problem.message.orEmpty().lineSequence().first()
                }
            }
        }
        assertWithMessage(problems.joinToString("\n", prefix = "\n")).that(problems).isEmpty()
        // Every screen here has room for the row in some style: were it found in none, the
        // wait for its counts would have been skipped everywhere.
        assertWithMessage("cases in which the notification row was found by its name").that(rowsFound).isGreaterThan(0)
        println("DreamStyleFitTest: the notification row found in $rowsFound cases")
    }

    private companion object {
        /** Toki pona's word for the afternoon, the longest Android has for a half of the day in Latin letters. */
        const val LONG_HALF_OF_DAY = "pi pini suno"

        /** An environment variable that, when set, has every language drawn instead of the widest ones. */
        const val ALL_LANGUAGES_SWITCH = "DREAM_FIT_ALL_LANGUAGES"

        /** How many of the widest languages are drawn for each line. */
        const val WIDEST_PER_LINE = 2

        /** Bulgarian, whose calendar row the external review found cut off; Latvian, a phone language with a long afternoon. */
        val REVIEWED_PHONE_LANGUAGES = listOf("bg", "lv")
    }
}
