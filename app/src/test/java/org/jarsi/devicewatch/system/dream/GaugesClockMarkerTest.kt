package org.jarsi.devicewatch.system.dream

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
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
 * AM or PM above the seconds of the Gauges clock is drawn at its own size, a set share of the
 * clock's, wherever it fits there: the fitting that lets a long word for the half of the day
 * give way must leave a short one alone.
 *
 * Seen on the emulator, on a screen turned on its side while the screensaver was on (914 x
 * 411 dp at 420 dpi): "AM" at 10 sp beside a 96 sp clock, in a column with room for it many
 * times over. The marker had kept the size worked out for the narrow width the turn passes
 * through (see `FitAnew`). [DreamTurnedScreenTest] asks the same of every text of every style.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GaugesClockMarkerTest {

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

    /** The marker is as wide as its letters are at the size the style gives it for the clock that was drawn. */
    private fun assertMarkerAtItsOwnSize(landscape: Boolean, case: String, widthsDp: List<Int> = emptyList()) {
        LatinDigits.applyToDefault()
        val context = LatinDigits.context(ApplicationProvider.getApplicationContext<Application>())
        val state = worstCaseDreamUiState(context, DreamChargeState.CHARGING)
        assertThat(state.amPmText).isEqualTo("PM")
        assertThat(state.amPmLeads).isFalse()
        lateinit var density: Density
        // The widths the screen has one after another, in dp; none is the whole screen throughout.
        var widthDp by mutableStateOf(widthsDp.firstOrNull())
        compose.setContent {
            density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides LatinDigits.context(LocalContext.current)) {
                val width = widthDp
                Box(modifier = if (width == null) Modifier.fillMaxSize() else Modifier.fillMaxHeight().width(width.dp)) {
                    DreamScreen(
                        state = state,
                        options = DreamOptions(style = DreamStyle.GAUGES),
                        landscape = landscape,
                        onTap = null,
                        moving = false,
                    )
                }
            }
        }
        compose.waitForIdle()
        for (next in widthsDp.drop(1)) {
            widthDp = next
            compose.waitForIdle()
        }
        fun drawn(text: String): TextLayoutResult {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onAllNodesWithText(text, useUnmergedTree = true).onFirst()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.single()
        }
        val clockSp = drawn(state.timeText).layoutInput.style.fontSize.value
        val ownSp = maxOf(clockSp * 0.22f, 10f)
        val marker = drawn(state.amPmText)
        val atOwnSize = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr).measure(
            text = state.amPmText,
            style = TextStyle(fontSize = ownSp.sp, fontFamily = FontFamily.Monospace),
            softWrap = false,
            maxLines = 1,
        )
        assertWithMessage("$case: the marker's lines").that(marker.lineCount).isEqualTo(1)
        assertWithMessage(
            "$case: beside a $clockSp sp clock \"${state.amPmText}\" is $ownSp sp, " +
                "${atOwnSize.multiParagraph.maxIntrinsicWidth} px wide; it was offered " +
                "${marker.layoutInput.constraints.maxWidth} x ${marker.layoutInput.constraints.maxHeight} px and drawn"
        )
            .that(marker.multiParagraph.maxIntrinsicWidth)
            .isWithin(0.5f).of(atOwnSize.multiParagraph.maxIntrinsicWidth)
    }

    @Test
    @Config(qualifiers = "w914dp-h411dp-land-420dpi")
    fun `on a large phone on its side`() = assertMarkerAtItsOwnSize(landscape = true, case = "914 x 411 dp at 420 dpi")

    // A screen that turns while the screensaver is on is for a moment as narrow as it was
    // upright: the clock is sized for that width first and for the real one after it.
    @Test
    @Config(qualifiers = "w914dp-h411dp-land-420dpi")
    fun `on a large phone turned on its side while the screensaver is on`() =
        assertMarkerAtItsOwnSize(landscape = true, case = "411, then 914 x 411 dp at 420 dpi", widthsDp = listOf(411, 914))

    @Test
    @Config(qualifiers = "w400dp-h320dp-land-xhdpi")
    fun `on the narrowest screen on its side`() = assertMarkerAtItsOwnSize(landscape = true, case = "400 x 320 dp at 320 dpi")
}
