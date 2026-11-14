package org.jarsi.devicewatch.system.dream

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.presentation.ui.MAX_FONT_SCALE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every style at its narrowest: a 480 x 320 dp phone on its side and a 320 dp wide phone
 * upright, with the largest font scale the app lets through, in both languages and in every
 * charge state, drawing the widest texts the screensaver can meet. Nothing may be cut off.
 * A style added to [DreamStyle] is covered without a change here.
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

    /** What the notification row calls itself: "N unread notifications". */
    private fun unreadDescription(state: DreamUiState): String {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val total = state.notificationGroups.sumOf { it.count }
        return context.resources.getQuantityString(R.plurals.dream_unread_notifications, total, total)
    }

    private fun assertEveryStyleFits(landscape: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        var options by mutableStateOf(DreamOptions())
        var state by mutableStateOf(worstCaseDreamUiState(context, DreamChargeState.CHARGING))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, MAX_FONT_SCALE)) {
                DreamScreen(state = state, options = options, landscape = landscape, onTap = null, moving = false)
            }
        }
        for (style in DreamStyle.entries) {
            for (chargeState in DreamChargeState.entries) {
                options = DreamOptions(style = style)
                state = worstCaseDreamUiState(context, chargeState)
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
                compose.assertNoTextCutOff(case = "$style, $chargeState")
            }
        }
    }
}
