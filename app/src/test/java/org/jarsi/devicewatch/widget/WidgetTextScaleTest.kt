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
    fun `the widgets draw their sizes in plain dp whatever density the screen claims as default`() {
        // Fairphone 6 reads a 160 dpi default against its real 480 dpi: the widget used
        // to draw at a third of its size. Nothing here takes a density any more.
        assertThat(widgetSizeDp(baseDp = 22f, spaceScale = 1f)).isEqualTo(22f)
        assertThat(widgetTextSizeDp(baseDp = 13f, fontScale = 1f, spaceScale = 1f)).isEqualTo(13f)
    }

    @Test
    fun `text follows the font setting within the cap`() {
        val sizes = listOf(0.85f, 1.0f, 1.15f, 1.3f, 2.0f).map { widgetTextSizeDp(13f, it, spaceScale = 1f) }
        assertThat(sizes).containsExactly(
            13f * 0.85f, 13f, 13f * MAX_WIDGET_FONT_SCALE, 13f * MAX_WIDGET_FONT_SCALE, 13f * MAX_WIDGET_FONT_SCALE,
        ).inOrder()
    }

    @Test
    fun `a widget that has the height its content needs is drawn at full size`() {
        // Hosts measured on the owner's launcher: Fairphone 6 372 x 613 dp, Pixel 8a 411 x 698 dp.
        listOf(1f, MAX_WIDGET_FONT_SCALE).forEach { font ->
            assertThat(widgetSpaceScale(613f, dashboardNeededHeightDp(font))).isEqualTo(1f)
            assertThat(widgetSpaceScale(698f, dashboardNeededHeightDp(font))).isEqualTo(1f)
        }
        // A 2 x 2 compact widget is about 180 dp tall.
        assertThat(widgetSpaceScale(180f, compactNeededHeightDp(MAX_WIDGET_FONT_SCALE))).isEqualTo(1f)
    }

    @Test
    fun `a widget shorter than its content scales the content down to fit`() {
        // A launcher's shorter 4 x 4 cell, or a larger display size shrinking the host.
        val needed = dashboardNeededHeightDp(1f)
        assertThat(widgetSpaceScale(450f, needed)).isWithin(1e-5f).of(450f / needed)
        assertThat(450f / needed).isIn(com.google.common.collect.Range.open(MIN_WIDGET_SPACE_SCALE, 1f))
    }

    @Test
    fun `the fit never shrinks content past the readable floor`() {
        // The smallest the dashboard resizes to is 320 dp.
        assertThat(widgetSpaceScale(320f, dashboardNeededHeightDp(1f))).isEqualTo(MIN_WIDGET_SPACE_SCALE)
    }

    @Test
    fun `a larger font needs more height`() {
        assertThat(dashboardNeededHeightDp(MAX_WIDGET_FONT_SCALE)).isGreaterThan(dashboardNeededHeightDp(1f))
        assertThat(compactNeededHeightDp(MAX_WIDGET_FONT_SCALE)).isGreaterThan(compactNeededHeightDp(1f))
    }

    @Test
    fun `nonsense sizes leave the widget unscaled`() {
        assertThat(widgetSpaceScale(0f, 500f)).isEqualTo(1f)
        assertThat(widgetSpaceScale(Float.NaN, 500f)).isEqualTo(1f)
        assertThat(widgetSpaceScale(400f, 0f)).isEqualTo(1f)
        assertThat(widgetSpaceScale(400f, Float.POSITIVE_INFINITY)).isEqualTo(1f)
    }
}
