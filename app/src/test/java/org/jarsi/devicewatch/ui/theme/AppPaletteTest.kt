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
 * The app's palette is fixed (no Material You), which is what makes stating a
 * contrast ratio possible at all — a wallpaper-derived scheme would differ on
 * every device. These tests are what the fixed palette buys: every pair is held
 * to WCAG AAA for text and 3:1 for meaningful graphics.
 *
 * Both surfaces are checked, because the app puts text on both: the page
 * background, and the section card — `surfaceVariant` at 40 % over it.
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
        val surfaceVariant: Color,
        val surfaceContainer: Color,
        val onSurface: Color,
        val onSurfaceVariant: Color,
        val accent: Color,
        val onAccent: Color,
        val outline: Color,
        val statusOk: Color,
        val statusWarn: Color,
        val error: Color,
    ) {
        /** What `Card(containerColor = surfaceVariant.copy(alpha = .4f))` paints. */
        val card: Color = surfaceVariant.over(background, CARD_SURFACE_ALPHA)

        /** Where the palette's text colors are actually used. */
        val surfaces
            get() = listOf(
                "background" to background,
                "surfaceContainer" to surfaceContainer,
            )

        /**
         * The notice card's fill. Only [onSurface] and the status dot land here,
         * so it is checked separately rather than against the whole palette.
         */
        fun tint(status: Color) = status.over(background, STATUS_TINT_ALPHA)
    }

    private val themes = listOf(
        Theme(
            name = "dark",
            background = DarkBackground,
            surfaceVariant = DarkSurfaceVariant,
            surfaceContainer = DarkSurfaceContainer,
            onSurface = DarkOnSurface,
            onSurfaceVariant = DarkOnSurfaceVariant,
            accent = DarkAccent,
            onAccent = DarkBackground,
            outline = DarkOutline,
            statusOk = DarkStatusOk,
            statusWarn = DarkStatusWarn,
            error = DarkError,
        ),
        Theme(
            name = "light",
            background = LightBackground,
            surfaceVariant = LightSurfaceVariant,
            surfaceContainer = LightSurfaceContainer,
            onSurface = LightOnSurface,
            onSurfaceVariant = LightOnSurfaceVariant,
            accent = LightAccent,
            onAccent = LightBackground,
            outline = LightOutline,
            statusOk = LightStatusOk,
            statusWarn = LightStatusWarn,
            error = LightError,
        ),
    )

    private fun Theme.assertOnBothSurfaces(label: String, color: Color, minimum: Double) {
        surfaces.forEach { (surfaceName, surface) ->
            val ratio = contrast(color, surface)
            assertWithMessage(ratio, "$name/$surfaceName $label", minimum)
        }
    }

    private fun assertWithMessage(ratio: Double, what: String, minimum: Double) {
        assertThat(ratio).isAtLeast(minimum)
        // Truth prints the ratio on failure; the name tells you which pair broke.
        check(ratio >= minimum) { "$what is ${"%.2f".format(ratio)}:1, needs $minimum:1" }
    }

    @Test
    fun `every text color clears AAA on both surfaces it appears on`() {
        themes.forEach { theme ->
            theme.assertOnBothSurfaces("onSurface", theme.onSurface, AAA_TEXT)
            theme.assertOnBothSurfaces("onSurfaceVariant", theme.onSurfaceVariant, AAA_TEXT)
            theme.assertOnBothSurfaces("accent", theme.accent, AAA_TEXT)
            theme.assertOnBothSurfaces("error", theme.error, AAA_TEXT)
        }
    }

    @Test
    fun `the status colors clear AAA - they label state, not decorate it`() {
        themes.forEach { theme ->
            theme.assertOnBothSurfaces("statusOk", theme.statusOk, AAA_TEXT)
            theme.assertOnBothSurfaces("statusWarn", theme.statusWarn, AAA_TEXT)
        }
    }

    @Test
    fun `the tinted notice card carries its message and its dot`() {
        // The card holds onSurface text and a status dot — the dot is a graphic,
        // and it is never the only cue: the message says the same thing.
        themes.forEach { theme ->
            listOf("ok" to theme.statusOk, "warn" to theme.statusWarn).forEach { (name, status) ->
                val tint = theme.tint(status)
                assertWithMessage(
                    contrast(theme.onSurface, tint), "${theme.name}/${name}Tint message", AAA_TEXT
                )
                assertWithMessage(
                    contrast(status, tint), "${theme.name}/${name}Tint dot", GRAPHIC
                )
            }
        }
    }

    @Test
    fun `outlines and meters clear the non-text minimum`() {
        themes.forEach { theme ->
            theme.assertOnBothSurfaces("outline", theme.outline, GRAPHIC)
        }
    }

    @Test
    fun `button labels clear AAA against the button itself`() {
        // primary is a container as well as a text color: Button paints it and
        // writes onPrimary on top.
        themes.forEach { theme ->
            assertWithMessage(
                contrast(theme.onAccent, theme.accent), "${theme.name} onAccent/accent", AAA_TEXT
            )
        }
    }

    @Test
    fun `the selected tab, chip and segment are marked strongly enough`() {
        // secondaryContainer marks selection. Material's faint default tint sat
        // at 1.5:1 against the bar it lives on, which cannot carry a state.
        themes.forEach { theme ->
            assertWithMessage(
                contrast(theme.accent, theme.surfaceContainer),
                "${theme.name} selection pill", GRAPHIC
            )
        }
    }

    @Test
    fun `the elevated surface is exactly the card the sections paint`() {
        // The navigation bar uses surfaceContainer while section cards blend
        // surfaceVariant over the page; if the two drifted apart, one of them
        // would carry text this test never checked.
        themes.forEach { theme ->
            assertThat(contrast(theme.surfaceContainer, theme.card)).isLessThan(1.02)
        }
    }

    @Test
    fun `the card stays a distinct surface from the page`() {
        // Not a WCAG rule — the card would simply be pointless if it matched the
        // page exactly, and a future palette edit could make it do so silently.
        themes.forEach { theme ->
            assertThat(contrast(theme.card, theme.background)).isGreaterThan(1.05)
        }
    }

    @Test
    fun `the two themes really are a light and a dark one`() {
        assertThat(luminance(LightBackground)).isGreaterThan(0.5)
        assertThat(luminance(DarkBackground)).isLessThan(0.05)
    }
}
