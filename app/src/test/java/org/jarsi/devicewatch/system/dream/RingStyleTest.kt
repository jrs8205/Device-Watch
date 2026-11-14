package org.jarsi.devicewatch.system.dream

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The Ring style's arithmetic: how far the ring is filled, its tint and its line of figures. */
class RingStyleTest {

    @Test
    fun `the ring is empty at 0, half at 50 and closed at 100, and never beyond`() {
        assertThat(ringSweepDegrees(0)).isEqualTo(0f)
        assertThat(ringSweepDegrees(50)).isEqualTo(180f)
        assertThat(ringSweepDegrees(100)).isEqualTo(360f)
        assertThat(ringSweepDegrees(-5)).isEqualTo(0f)
        assertThat(ringSweepDegrees(140)).isEqualTo(360f)
    }

    @Test
    fun `the tint follows the battery's three bands`() {
        val red = Color(0xFF200908)
        val amber = Color(0xFF1F1805)
        val green = Color(0xFF0A1F18)

        assertThat(ringTint(5)).isEqualTo(red)
        assertThat(ringTint(20)).isEqualTo(red)
        assertThat(ringTint(21)).isEqualTo(amber)
        assertThat(ringTint(60)).isEqualTo(amber)
        assertThat(ringTint(61)).isEqualTo(green)
        assertThat(ringTint(100)).isEqualTo(green)
    }

    @Test
    fun `while charging the status line has the state, the power and the temperature`() {
        val parts = ringStatusParts(
            state = plainDreamUiState(chargeState = DreamChargeState.CHARGING, wattsText = "11,0 W"),
            icon = "+",
            word = "Latautuu",
            showDetails = true,
        )

        assertThat(parts).containsExactly("+ latautuu", "11,0 W", "26,3 °C").inOrder()
    }

    @Test
    fun `off the charger the status line has the state and the temperature`() {
        val parts = ringStatusParts(
            state = plainDreamUiState(chargeState = DreamChargeState.ON_BATTERY, wattsText = null),
            icon = "-",
            word = "Akkuvirralla",
            showDetails = true,
        )

        assertThat(parts).containsExactly("- akkuvirralla", "26,3 °C").inOrder()
    }

    @Test
    fun `with the battery details switched off only the state is left`() {
        val parts = ringStatusParts(
            state = plainDreamUiState(chargeState = DreamChargeState.CHARGING, wattsText = "11,0 W"),
            icon = "+",
            word = "Latautuu",
            showDetails = false,
        )

        assertThat(parts).containsExactly("+ latautuu")
    }
}
