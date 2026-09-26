package org.jarsi.devicewatch.presentation.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppScaleTest {

    @Test
    fun `font scales at or below the cap pass through unchanged`() {
        assertThat(clampedFontScale(1.0f)).isEqualTo(1.0f)
        assertThat(clampedFontScale(1.1f)).isEqualTo(1.1f)
        assertThat(clampedFontScale(MAX_FONT_SCALE)).isEqualTo(MAX_FONT_SCALE)
    }

    @Test
    fun `the font never grows past the cap`() {
        assertThat(clampedFontScale(1.3f)).isEqualTo(MAX_FONT_SCALE)
        assertThat(clampedFontScale(2.0f)).isEqualTo(MAX_FONT_SCALE)
    }

    @Test
    fun `a shrunk system font is honoured in full`() {
        assertThat(clampedFontScale(0.85f)).isEqualTo(0.85f)
    }

    @Test
    fun `nonsense font scales fall back to the unscaled size`() {
        assertThat(clampedFontScale(0f)).isEqualTo(1f)
        assertThat(clampedFontScale(-2f)).isEqualTo(1f)
        assertThat(clampedFontScale(Float.NaN)).isEqualTo(1f)
        assertThat(clampedFontScale(Float.POSITIVE_INFINITY)).isEqualTo(1f)
    }

    @Test
    fun `the default display size is untouched`() {
        // Pixel 8a: 1080 px at 2.625 = 411 dp, comfortably above the floor.
        assertThat(clampedDensity(systemDensity = 2.625f, defaultDensity = 2.625f, widthPx = 1080))
            .isEqualTo(2.625f)
    }

    @Test
    fun `a larger display size is capped where the layout would drop under the width floor`() {
        // "Largest" on the same phone would leave 318 dp; the cap holds 400 dp.
        assertThat(clampedDensity(systemDensity = 3.4f, defaultDensity = 2.625f, widthPx = 1080))
            .isEqualTo(1080f / MIN_LAYOUT_WIDTH_DP)
    }

    @Test
    fun `the display is never shrunk below the device's default density`() {
        // A 720 px phone at its default 2.0 is already 360 dp wide: the floor
        // cannot be met without shrinking below the default, so the default stays.
        assertThat(clampedDensity(systemDensity = 2.6f, defaultDensity = 2.0f, widthPx = 720))
            .isEqualTo(2.0f)
    }

    @Test
    fun `a smaller display size chosen by the user is honoured`() {
        assertThat(clampedDensity(systemDensity = 2.2f, defaultDensity = 2.625f, widthPx = 1080))
            .isEqualTo(2.2f)
    }

    @Test
    fun `nonsense display values leave the system density alone`() {
        assertThat(clampedDensity(systemDensity = 2.625f, defaultDensity = 2.625f, widthPx = 0))
            .isEqualTo(2.625f)
        assertThat(clampedDensity(systemDensity = 2.625f, defaultDensity = 0f, widthPx = 1080))
            .isEqualTo(2.625f)
        assertThat(clampedDensity(systemDensity = Float.NaN, defaultDensity = 2.625f, widthPx = 1080))
            .isEqualTo(2.625f)
    }
}
