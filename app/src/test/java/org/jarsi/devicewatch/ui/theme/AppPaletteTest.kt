package org.jarsi.devicewatch.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.pow

/** Composites this color over [background] at [alpha], as Compose would. */
private fun Color.over(background: Color, alpha: Float) = Color(
    red = red * alpha + background.red * (1f - alpha),
    green = green * alpha + background.green * (1f - alpha),
    blue = blue * alpha + background.blue * (1f - alpha),
)

/**
 * The palette is fixed (no Material You), which is what makes stating a contrast
 * ratio possible at all — a wallpaper-derived scheme would differ on every
 * device. These tests are what the fixed palette buys.
 *
 * Every text color is checked against all three surfaces the app writes on: the
 * page, the navigation bar and bottom sheet, and dialogs. The bands carry no
 * fill of their own, so there is no fourth surface — the notice band's status
 * tint is the one exception and gets its own test.
 */
class AppPaletteTest {

    private companion object {
        const val AAA_TEXT = 7.0
        const val GRAPHIC = 3.0
    }

    private fun luminance(color: Color): Double {
        fun channel(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val first = luminance(a)
        val second = luminance(b)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }

    private class Theme(
        val name: String,
        val background: Color,
        val surfaceContainer: Color,
        val surfaceContainerHigh: Color,
        val onSurface: Color,
        val onSurfaceVariant: Color,
        val accent: Color,
        val accentGraphic: Color,
        val onAccent: Color,
        val accentContainer: Color,
        val onAccentContainer: Color,
        val outline: Color,
        val outlineVariant: Color,
        val statusOk: Color,
        val statusWarn: Color,
        val error: Color,
    ) {
        val surfaces
            get() = listOf(
                "background" to background,
                "surfaceContainer" to surfaceContainer,
                "surfaceContainerHigh" to surfaceContainerHigh,
            )

        fun tint(status: Color) = status.over(background, STATUS_TINT_ALPHA)
    }

    private val themes = listOf(
        Theme(
            name = "dark",
            background = DarkBackground,
            surfaceContainer = DarkSurfaceContainer,
            surfaceContainerHigh = DarkSurfaceContainerHigh,
            onSurface = DarkOnSurface,
            onSurfaceVariant = DarkOnSurfaceVariant,
            accent = DarkAccent,
            accentGraphic = DarkAccentGraphic,
            onAccent = DarkOnAccent,
            accentContainer = DarkAccentContainer,
            onAccentContainer = DarkOnAccentContainer,
            outline = DarkOutline,
            outlineVariant = DarkOutlineVariant,
            statusOk = DarkStatusOk,
            statusWarn = DarkStatusWarn,
            error = DarkError,
        ),
        Theme(
            name = "light",
            background = LightBackground,
            surfaceContainer = LightSurfaceContainer,
            surfaceContainerHigh = LightSurfaceContainerHigh,
            onSurface = LightOnSurface,
            onSurfaceVariant = LightOnSurfaceVariant,
            accent = LightAccent,
            accentGraphic = LightAccent,
            onAccent = LightOnAccent,
            accentContainer = LightAccentContainer,
            onAccentContainer = LightOnAccentContainer,
            outline = LightOutline,
            outlineVariant = LightOutlineVariant,
            statusOk = LightStatusOk,
            statusWarn = LightStatusWarn,
            error = LightError,
        ),
    )

    private fun Theme.assertOnEverySurface(label: String, color: Color, minimum: Double) {
        surfaces.forEach { (surfaceName, surface) ->
            assertRatio(contrast(color, surface), "$name/$surfaceName $label", minimum)
        }
    }

    private fun assertRatio(ratio: Double, what: String, minimum: Double) {
        assertThat(ratio).isAtLeast(minimum)
        check(ratio >= minimum) { "$what is ${"%.2f".format(ratio)}:1, needs $minimum:1" }
    }

    @Test
    fun `every text color clears AAA on every surface it lands on`() {
        themes.forEach { theme ->
            theme.assertOnEverySurface("onSurface", theme.onSurface, AAA_TEXT)
            theme.assertOnEverySurface("onSurfaceVariant", theme.onSurfaceVariant, AAA_TEXT)
            theme.assertOnEverySurface("accent", theme.accent, AAA_TEXT)
            theme.assertOnEverySurface("error", theme.error, AAA_TEXT)
            theme.assertOnEverySurface("statusOk", theme.statusOk, AAA_TEXT)
            theme.assertOnEverySurface("statusWarn", theme.statusWarn, AAA_TEXT)
        }
    }

    @Test
    fun `the band rules clear the non-text minimum`() {
        // With the cards gone these hairlines are the only thing separating one
        // section from the next, and the navigation bar's top rule is the only
        // thing separating it from the page.
        themes.forEach { theme ->
            theme.assertOnEverySurface("outline", theme.outline, GRAPHIC)
        }
    }

    @Test
    fun `a meter reads against the page and against its own track`() {
        themes.forEach { theme ->
            assertRatio(
                contrast(theme.accentGraphic, theme.background),
                "${theme.name} meter fill", GRAPHIC
            )
            assertRatio(
                contrast(theme.accentGraphic, theme.outlineVariant),
                "${theme.name} meter fill vs track", GRAPHIC
            )
        }
    }

    @Test
    fun `text on a filled accent clears AAA`() {
        // Buttons, the selection pill, selected chips and the segmented button
        // all put onAccent text on a solid accent. The handoff's bright #2196F3
        // cannot do this at any text color — black on it reaches only 6.7:1 —
        // which is why the pill uses the lighter accent instead.
        themes.forEach { theme ->
            assertRatio(
                contrast(theme.onAccent, theme.accent), "${theme.name} onAccent/accent", AAA_TEXT
            )
            assertRatio(
                contrast(theme.onAccentContainer, theme.accentContainer),
                "${theme.name} onAccentContainer/accentContainer", AAA_TEXT
            )
        }
    }

    @Test
    fun `the selection pill is visible against the bar it sits in`() {
        themes.forEach { theme ->
            assertRatio(
                contrast(theme.accent, theme.surfaceContainer),
                "${theme.name} selection pill", GRAPHIC
            )
        }
    }

    @Test
    fun `the tinted notice band carries its message and its dot`() {
        themes.forEach { theme ->
            listOf("ok" to theme.statusOk, "warn" to theme.statusWarn).forEach { (name, status) ->
                val tint = theme.tint(status)
                assertRatio(
                    contrast(theme.onSurface, tint), "${theme.name}/${name}Tint message", AAA_TEXT
                )
                assertRatio(contrast(status, tint), "${theme.name}/${name}Tint dot", GRAPHIC)
            }
        }
    }

    @Test
    fun `the two themes really are a light and a dark one`() {
        assertThat(luminance(LightBackground)).isGreaterThan(0.5)
        assertThat(luminance(DarkBackground)).isLessThan(0.05)
    }
}
