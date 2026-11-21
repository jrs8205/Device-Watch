package org.jarsi.devicewatch.system.dream

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertWithMessage
import org.jarsi.devicewatch.LatinDigits
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * A screen that turns while the screensaver is on is drawn as one that stood that way from
 * the start.
 *
 * While it turns, a style is drawn for a moment in its new layout at the old width, and then
 * at the real one. Sizes worked out from the width follow it: the clock, and with the clock
 * the seconds, the date and AM or PM. A text that fits itself to its room kept the largest
 * size it was first given, because the library compares a text's parameters without that one
 * and so never handed the text its new one (see `FitAnew`). Seen on the emulator: Gauges
 * turned on its side drew "AM" at 10 sp beside a 96 sp clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DreamTurnedScreenTest {

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

    /** Every text on the screen with the box it takes, which follows the size it is drawn at, in the order drawn. */
    private fun drawnTexts(): List<String> {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        return (0 until nodes.fetchSemanticsNodes().size).map { index ->
            val layouts = mutableListOf<TextLayoutResult>()
            nodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            "\"${layout.layoutInput.text.text}\" in ${layout.size.width} x ${layout.size.height} px"
        }
    }

    /**
     * Each style is drawn [landscape] on a screen [finalWidthDp] x [finalHeightDp], once
     * straight away and once after a moment at [firstWidthDp] x [firstHeightDp], and the two
     * must come out alike, text for text.
     */
    private fun assertTurnedIsDrawnAsFromTheStart(
        landscape: Boolean,
        firstWidthDp: Int,
        firstHeightDp: Int,
        finalWidthDp: Int,
        finalHeightDp: Int,
    ) {
        LatinDigits.applyToDefault()
        val context = LatinDigits.context(ApplicationProvider.getApplicationContext<Application>())
        var options by mutableStateOf(DreamOptions())
        var state by mutableStateOf(worstCaseDreamUiState(context, DreamChargeState.CHARGING))
        var widthDp by mutableStateOf(finalWidthDp)
        var heightDp by mutableStateOf(finalHeightDp)
        // A new number has the screensaver made anew, with nothing kept from the one before.
        var drawing by mutableStateOf(0)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides LatinDigits.context(LocalContext.current)) {
                key(drawing) {
                    Box(modifier = Modifier.width(widthDp.dp).height(heightDp.dp)) {
                        DreamScreen(state = state, options = options, landscape = landscape, onTap = null, moving = false)
                    }
                }
            }
        }
        val problems = mutableListOf<String>()
        for (style in DreamStyle.entries) for (chargeState in listOf(DreamChargeState.CHARGING, DreamChargeState.ON_BATTERY)) {
            // No notification row: its icons arrive later and are not what is asked about here.
            options = DreamOptions(style = style, showCalendar = true, showNotifications = false)
            state = worstCaseDreamUiState(context, chargeState)

            widthDp = finalWidthDp
            heightDp = finalHeightDp
            drawing++
            compose.waitForIdle()
            val fromTheStart = drawnTexts()

            widthDp = firstWidthDp
            heightDp = firstHeightDp
            drawing++
            compose.waitForIdle()
            widthDp = finalWidthDp
            heightDp = finalHeightDp
            compose.waitForIdle()
            val turned = drawnTexts()

            assertWithMessage("$style, $chargeState: texts were drawn").that(fromTheStart).isNotEmpty()
            val differing = fromTheStart.zip(turned).filter { (a, b) -> a != b }
            if (fromTheStart.size != turned.size) {
                problems += "$style, $chargeState: ${fromTheStart.size} texts from the start, ${turned.size} after the turn"
            }
            for ((a, b) in differing) problems += "$style, $chargeState: from the start $a; after the turn $b"
        }
        assertWithMessage(problems.joinToString("\n", prefix = "\n")).that(problems).isEmpty()
    }

    // The emulator's own screen, 2400 x 1080 px at 420 dpi.
    @Test
    @Config(qualifiers = "w914dp-h914dp-420dpi")
    fun `turned on its side`() = assertTurnedIsDrawnAsFromTheStart(
        landscape = true,
        firstWidthDp = 411,
        firstHeightDp = 914,
        finalWidthDp = 914,
        finalHeightDp = 411,
    )

    @Test
    @Config(qualifiers = "w914dp-h914dp-420dpi")
    fun `turned upright`() = assertTurnedIsDrawnAsFromTheStart(
        landscape = false,
        firstWidthDp = 914,
        firstHeightDp = 411,
        finalWidthDp = 411,
        finalHeightDp = 914,
    )

    // A small phone, where the sizes worked out from the width differ most between the two.
    @Test
    @Config(qualifiers = "w640dp-h640dp-xhdpi")
    fun `a small phone turned on its side`() = assertTurnedIsDrawnAsFromTheStart(
        landscape = true,
        firstWidthDp = 320,
        firstHeightDp = 640,
        finalWidthDp = 640,
        finalHeightDp = 320,
    )

    @Test
    @Config(qualifiers = "w640dp-h640dp-xhdpi")
    fun `a small phone turned upright`() = assertTurnedIsDrawnAsFromTheStart(
        landscape = false,
        firstWidthDp = 640,
        firstHeightDp = 320,
        finalWidthDp = 320,
        finalHeightDp = 640,
    )
}
