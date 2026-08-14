package org.jarsi.devicewatch.widget

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WidgetTextScaleTest {

    @Test
    fun `scales at or below the cap pass through unchanged`() {
        assertThat(clampedWidgetFontScale(1.0f)).isEqualTo(1.0f)
        assertThat(clampedWidgetFontScale(1.15f)).isEqualTo(1.15f)
        assertThat(clampedWidgetFontScale(MAX_WIDGET_FONT_SCALE)).isEqualTo(MAX_WIDGET_FONT_SCALE)
    }

    @Test
    fun `a widget never grows past the cap however large the system setting is`() {
        // The largest display-size + font-size combinations reach well past 2x,
        // which is what clips the widget: its grid cell does not grow with it.
        assertThat(clampedWidgetFontScale(1.5f)).isEqualTo(MAX_WIDGET_FONT_SCALE)
        assertThat(clampedWidgetFontScale(2.0f)).isEqualTo(MAX_WIDGET_FONT_SCALE)
        assertThat(clampedWidgetFontScale(3.2f)).isEqualTo(MAX_WIDGET_FONT_SCALE)
    }

    @Test
    fun `shrinking the system font is honoured down to a floor`() {
        // Smaller text cannot break the layout, so the user's choice is kept —
        // but not so far that the widget becomes unreadable.
        assertThat(clampedWidgetFontScale(0.9f)).isEqualTo(0.9f)
        assertThat(clampedWidgetFontScale(0.5f)).isEqualTo(MIN_WIDGET_FONT_SCALE)
    }

    @Test
    fun `nonsense values fall back to the unscaled size`() {
        assertThat(clampedWidgetFontScale(0f)).isEqualTo(1f)
        assertThat(clampedWidgetFontScale(-2f)).isEqualTo(1f)
        assertThat(clampedWidgetFontScale(Float.NaN)).isEqualTo(1f)
        assertThat(clampedWidgetFontScale(Float.POSITIVE_INFINITY)).isEqualTo(1f)
    }
}
