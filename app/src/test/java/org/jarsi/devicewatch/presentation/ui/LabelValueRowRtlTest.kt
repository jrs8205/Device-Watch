package org.jarsi.devicewatch.presentation.ui

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.data.BatterySample
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rows in a language written from the right. A name-and-value row put its two parts at
 * fixed sides, so in Arabic the name stayed on the left, away from where reading starts.
 * The battery chart is the other way round: time runs from left to right on it in every
 * language, and its scale stays beside the newest reading.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h780dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LabelValueRowRtlTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showRow(direction: LayoutDirection, label: String = "Name", value: String = "Value") {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                LabelValueRow(label = { Text(label) }, value = { Text(value) })
            }
        }
    }

    private fun bounds(text: String): Rect = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot

    private val rootWidth: Float get() = compose.onRoot().fetchSemanticsNode().boundsInRoot.width

    @Test
    fun `left to right the name is at the left edge and the value at the right`() {
        showRow(LayoutDirection.Ltr)

        assertThat(bounds("Name").left).isEqualTo(0f)
        assertThat(bounds("Value").right).isEqualTo(rootWidth)
    }

    @Test
    fun `right to left the name is at the right edge and the value at the left`() {
        showRow(LayoutDirection.Rtl)

        assertThat(bounds("Name").right).isEqualTo(rootWidth)
        assertThat(bounds("Value").left).isEqualTo(0f)
    }

    @Test
    fun `stacked right to left, the name starts from the right and the value ends at the left`() {
        val name = "A name long enough to need most of the row"
        val value = "A value that cannot share the line"
        showRow(LayoutDirection.Rtl, name, value)

        assertThat(bounds(value).top).isAtLeast(bounds(name).bottom)
        assertThat(bounds(name).right).isEqualTo(rootWidth)
        assertThat(bounds(value).left).isEqualTo(0f)
    }

    @Test
    fun `the battery chart keeps its scale on the right in a right-to-left language`() {
        val now = System.currentTimeMillis()
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                BatteryChart(
                    samples = listOf(
                        BatterySample(now - 2 * 3_600_000L, 80, false),
                        BatterySample(now - 3_600_000L, 74, false),
                    ),
                    range = BatteryChartRange.Day,
                )
            }
        }

        assertThat(bounds("100 %").right).isEqualTo(rootWidth)
    }
}
