package org.jarsi.devicewatch.presentation.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppFontScaleTest {

    @Test
    fun `scales at or below the cap pass through unchanged`() {
        assertThat(clampedNavBarFontScale(1.0f)).isEqualTo(1.0f)
        assertThat(clampedNavBarFontScale(1.15f)).isEqualTo(1.15f)
        assertThat(clampedNavBarFontScale(MAX_NAV_BAR_FONT_SCALE))
            .isEqualTo(MAX_NAV_BAR_FONT_SCALE)
    }

    @Test
    fun `the navigation bar never grows past the cap`() {
        // Font size and display size multiply, so the largest combination the
        // system offers reaches well past 2x.
        assertThat(clampedNavBarFontScale(1.5f)).isEqualTo(MAX_NAV_BAR_FONT_SCALE)
        assertThat(clampedNavBarFontScale(2.0f)).isEqualTo(MAX_NAV_BAR_FONT_SCALE)
        assertThat(clampedNavBarFontScale(3.2f)).isEqualTo(MAX_NAV_BAR_FONT_SCALE)
    }

    @Test
    fun `a shrunk system font is honoured in full`() {
        // Unlike the widget there is no floor: smaller labels cannot break the
        // bar, and shrinking the font is a deliberate choice worth respecting.
        assertThat(clampedNavBarFontScale(0.85f)).isEqualTo(0.85f)
        assertThat(clampedNavBarFontScale(0.5f)).isEqualTo(0.5f)
    }

    @Test
    fun `nonsense values fall back to the unscaled size`() {
        assertThat(clampedNavBarFontScale(0f)).isEqualTo(1f)
        assertThat(clampedNavBarFontScale(-2f)).isEqualTo(1f)
        assertThat(clampedNavBarFontScale(Float.NaN)).isEqualTo(1f)
        assertThat(clampedNavBarFontScale(Float.POSITIVE_INFINITY)).isEqualTo(1f)
    }
}
