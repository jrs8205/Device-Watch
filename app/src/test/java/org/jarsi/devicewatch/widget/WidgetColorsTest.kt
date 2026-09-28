package org.jarsi.devicewatch.widget

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * GitHub #2: the widget background was a fixed dark blue-grey. The black-background
 * switch turns it pure black at the chosen opacity, in the dark and the light theme alike,
 * and the rest of the palette follows the dark look so the text stays readable on it.
 */
class WidgetColorsTest {

    @Test
    fun `default backgrounds keep the theme colour at the chosen opacity`() {
        assertThat(widgetCardBackground(isDark = true, opacity = 0.5f, blackBackground = false))
            .isEqualTo(Color(0xFF12151A).copy(alpha = 0.5f))
        assertThat(widgetCardBackground(isDark = false, opacity = 0.9f, blackBackground = false))
            .isEqualTo(Color(0xFFFFFFFF).copy(alpha = 0.9f))
    }

    @Test
    fun `the black switch gives pure black in both themes, keeping the opacity`() {
        assertThat(widgetCardBackground(isDark = true, opacity = 0.7f, blackBackground = true))
            .isEqualTo(Color.Black.copy(alpha = 0.7f))
        assertThat(widgetCardBackground(isDark = false, opacity = 1f, blackBackground = true))
            .isEqualTo(Color.Black)
    }

    @Test
    fun `a black background always uses the dark palette for its text`() {
        assertThat(widgetPaletteIsDark(isDark = false, blackBackground = true)).isTrue()
        assertThat(widgetPaletteIsDark(isDark = false, blackBackground = false)).isFalse()
        assertThat(widgetPaletteIsDark(isDark = true, blackBackground = false)).isTrue()
    }
}
