package org.jarsi.devicewatch.system.dream

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The Minimal style's one line of figures. */
class MinimalStyleTest {

    @Test
    fun `the status line names the level, the state in lower case and the power`() {
        val parts = minimalStatusParts(
            state = plainDreamUiState(chargeState = DreamChargeState.CHARGING, wattsText = "11,0 W"),
            percent = "64 %",
            word = "Latautuu",
            showWatts = true,
        )

        assertThat(parts).containsExactly("64 %", "latautuu", "11,0 W").inOrder()
    }

    @Test
    fun `without a power reading the line has two parts`() {
        val parts = minimalStatusParts(
            state = plainDreamUiState(chargeState = DreamChargeState.HELD, wattsText = null),
            percent = "80 %",
            word = "Ei lataudu",
            showWatts = true,
        )

        assertThat(parts).containsExactly("80 %", "ei lataudu").inOrder()
    }

    @Test
    fun `the power is left out when the battery details are switched off`() {
        val parts = minimalStatusParts(
            state = plainDreamUiState(chargeState = DreamChargeState.CHARGING, wattsText = "11,0 W"),
            percent = "64 %",
            word = "Latautuu",
            showWatts = false,
        )

        assertThat(parts).containsExactly("64 %", "latautuu").inOrder()
    }
}
