package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DefaultDensityTest {

    @Test
    fun `a pixel's stable density is its default at any display size it offers`() {
        assertThat(knownDefaultDensityDpi(stableDpi = 420, currentDpi = 420)).isEqualTo(420)
        assertThat(knownDefaultDensityDpi(stableDpi = 420, currentDpi = 546)).isEqualTo(420)
        assertThat(knownDefaultDensityDpi(stableDpi = 420, currentDpi = 357)).isEqualTo(420)
    }

    @Test
    fun `android's own fallback of 160 dpi is not taken for a default`() {
        // Fairphone 6: ro.sf.lcd_density is 480, but the app reads the 160 dpi fallback.
        assertThat(knownDefaultDensityDpi(stableDpi = 160, currentDpi = 480)).isNull()
    }

    @Test
    fun `a screen that really is 160 dpi keeps it`() {
        assertThat(knownDefaultDensityDpi(stableDpi = 160, currentDpi = 160)).isEqualTo(160)
    }

    @Test
    fun `a ratio no display-size setting produces means the stable density is not this screen's`() {
        // A Samsung running its panel below native resolution: 640 stable, 420 in use.
        assertThat(knownDefaultDensityDpi(stableDpi = 640, currentDpi = 420)).isNull()
        assertThat(knownDefaultDensityDpi(stableDpi = 420, currentDpi = 1000)).isNull()
    }

    @Test
    fun `nonsense densities give no default`() {
        assertThat(knownDefaultDensityDpi(stableDpi = 0, currentDpi = 420)).isNull()
        assertThat(knownDefaultDensityDpi(stableDpi = -1, currentDpi = 420)).isNull()
        assertThat(knownDefaultDensityDpi(stableDpi = 420, currentDpi = 0)).isNull()
    }
}
