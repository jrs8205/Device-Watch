package org.jarsi.devicewatch.system.dream

import android.app.Application
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertWithMessage
import org.jarsi.devicewatch.presentation.ui.MAX_FONT_SCALE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The screensaver's battery block at its narrowest: a 480 x 320 dp phone on its side with
 * the largest font scale the app lets through. The charge state's word is one line that
 * shrinks to fit, and it has a smallest size, so a long word can still be cut off. Real
 * text measuring is needed for that, hence the native graphics mode.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w480dp-h320dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DreamBatteryBlockTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `every charge state's word fits the narrow landscape block in English`() = assertEveryWordFits()

    @Test
    @Config(qualifiers = "+fi")
    fun `every charge state's word fits the narrow landscape block in Finnish`() = assertEveryWordFits()

    private fun assertEveryWordFits() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        var state by mutableStateOf(DreamChargeState.CHARGING)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, MAX_FONT_SCALE)) {
                BatteryBlockLandscape(
                    modifier = Modifier.width(NARROW_BLOCK_WIDTH_DP.dp),
                    chargeState = state,
                    batteryLevel = 80,
                    batteryGradientColors = getBatteryGradientColors(80),
                    detailsStr = "3,82 V  ·  26,3 °C",
                    fullTimeStr = "",
                )
            }
        }
        for (candidate in DreamChargeState.entries) {
            state = candidate
            compose.waitForIdle()
            val word = context.getString(candidate.label)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(word).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertWithMessage("$candidate \"$word\" is cut off at ${layout.size.width} px")
                .that(layout.didOverflowWidth || layout.hasVisualOverflow)
                .isFalse()
        }
    }

    private companion object {
        /**
         * The block's width on a 480 dp wide screen, from the Glow style's landscape row:
         * 28 dp padding on both sides, the 1.5 dp divider and the 18 dp gap come off, and the
         * block takes 0.42 of what is left.
         */
        const val NARROW_BLOCK_WIDTH_DP = (480f - 2 * 28f - 1.5f - 18f) * 0.42f
    }
}
