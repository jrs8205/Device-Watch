package org.jarsi.devicewatch.system.dream

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.jarsi.devicewatch.R
import org.junit.Test
import kotlin.math.pow

/**
 * The Night style's reds. They are dim on purpose, so their contrast on black is checked
 * against what a large clock (3:1) and small text (4.5:1) need to stay readable.
 */
class NightStyleTest {

    private fun luminance(color: Color): Double {
        fun channel(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun contrastOnBlack(color: Color): Double = (luminance(color) + 0.05) / 0.05

    @Test
    fun `the clock red has a contrast of at least three on black`() {
        assertThat(contrastOnBlack(NIGHT_CLOCK)).isAtLeast(3.0)
    }

    @Test
    fun `the small-text red has a contrast of at least four and a half on black`() {
        assertThat(contrastOnBlack(NIGHT_TEXT)).isAtLeast(4.5)
    }

    @Test
    fun `the reds stay dim`() {
        // Night is for a dark bedroom: neither red may come near white's 21:1.
        assertThat(contrastOnBlack(NIGHT_CLOCK)).isLessThan(4.5)
        assertThat(contrastOnBlack(NIGHT_TEXT)).isLessThan(6.0)
    }

    @Test
    fun `each charge state has an icon`() {
        assertThat(nightChargeIcon(DreamChargeState.CHARGING)).isEqualTo(R.drawable.ic_quick_power)
        assertThat(nightChargeIcon(DreamChargeState.FULL)).isEqualTo(R.drawable.ic_quick_power)
        assertThat(nightChargeIcon(DreamChargeState.HELD)).isEqualTo(R.drawable.ic_dream_plug)
        assertThat(nightChargeIcon(DreamChargeState.ON_BATTERY)).isEqualTo(R.drawable.ic_widget_battery)
        for (state in DreamChargeState.entries) {
            assertWithMessage("$state").that(nightChargeIcon(state)).isNotEqualTo(0)
        }
    }
}
