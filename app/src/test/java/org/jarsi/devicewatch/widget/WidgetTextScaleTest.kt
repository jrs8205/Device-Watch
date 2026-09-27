package org.jarsi.devicewatch.widget

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.presentation.ui.MAX_FONT_SCALE
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

    @Test
    fun `the widget font cap is the app's own cap`() {
        // One bound for every surface: a widget read beside the app must not
        // come out larger than the same figure inside it.
        assertThat(MAX_WIDGET_FONT_SCALE).isEqualTo(MAX_FONT_SCALE)
        assertThat(clampedWidgetFontScale(1.3f)).isEqualTo(MAX_FONT_SCALE)
    }

    @Test
    fun `a larger display size is undone so the content keeps its default pixel size`() {
        // Pixel 8 class: default 420 dpi, largest display size 546 dpi. The
        // launcher cell keeps its pixel size, so the content must too.
        assertThat(widgetDensityRatio(systemDensity = 3.4125f, defaultDensity = 2.625f))
            .isWithin(1e-5f).of(2.625f / 3.4125f)
    }

    @Test
    fun `the default or a smaller display size passes through unchanged`() {
        assertThat(widgetDensityRatio(systemDensity = 2.625f, defaultDensity = 2.625f)).isEqualTo(1f)
        assertThat(widgetDensityRatio(systemDensity = 2.2f, defaultDensity = 2.625f)).isEqualTo(1f)
    }

    @Test
    fun `nonsense densities leave the widget unscaled`() {
        assertThat(widgetDensityRatio(0f, 2.625f)).isEqualTo(1f)
        assertThat(widgetDensityRatio(Float.NaN, 2.625f)).isEqualTo(1f)
        assertThat(widgetDensityRatio(3.4f, 0f)).isEqualTo(1f)
        assertThat(widgetDensityRatio(3.4f, Float.POSITIVE_INFINITY)).isEqualTo(1f)
    }
}
