package org.jarsi.devicewatch.presentation

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.pow

/**
 * The report is opened outdoors on a phone, so both themes are held to the WCAG
 * AAA text ratio (7:1) rather than the AA 4.5:1 that looked fine on a desk. The
 * ratios are computed here from the palette the stylesheet is generated from, so
 * a colour cannot be nudged without this failing.
 */
class ReportPaletteTest {

    /** WCAG 2.x relative luminance of an `#rrggbb` colour. */
    private fun luminance(hex: String): Double {
        val value = hex.removePrefix("#")
        require(value.length == 6) { "expected #rrggbb, got $hex" }
        val channels = listOf(0, 2, 4).map { value.substring(it, it + 2).toInt(16) / 255.0 }
        val linear = channels.map { c ->
            if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]
    }

    private fun contrast(a: String, b: String): Double {
        val first = luminance(a)
        val second = luminance(b)
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** Text is read on the card and, in nested boxes and banded rows, on the page. */
    private fun ReportPalette.surfaces() = listOf(card, bg)

    @Test
    fun `body text clears the AAA ratio on both surfaces of both themes`() {
        listOf(LIGHT_PALETTE, DARK_PALETTE).forEach { palette ->
            palette.surfaces().forEach { surface ->
                assertThat(contrast(palette.ink, surface)).isAtLeast(7.0)
            }
        }
    }

    @Test
    fun `the muted grey clears AAA too - it carries labels, not decoration`() {
        // Section headings, column headers, captions and dates all use it, and all
        // of them are small text, so 4.5:1 is not enough.
        listOf(LIGHT_PALETTE, DARK_PALETTE).forEach { palette ->
            palette.surfaces().forEach { surface ->
                assertThat(contrast(palette.muted, surface)).isAtLeast(7.0)
            }
        }
    }

    @Test
    fun `grid lines and control borders clear the non-text ratio`() {
        listOf(LIGHT_PALETTE, DARK_PALETTE).forEach { palette ->
            palette.surfaces().forEach { surface ->
                assertThat(contrast(palette.grid, surface)).isAtLeast(3.0)
            }
        }
    }

    @Test
    fun `the chart colours stay legible well past the non-text minimum`() {
        // The battery line and the charging marker are the report's only graphics
        // carrying data; 3:1 survives a screen indoors, not one in sunlight.
        listOf(LIGHT_PALETTE, DARK_PALETTE).forEach { palette ->
            palette.surfaces().forEach { surface ->
                assertThat(contrast(palette.accent, surface)).isAtLeast(4.5)
                assertThat(contrast(palette.mobile, surface)).isAtLeast(4.5)
            }
        }
    }

    @Test
    fun `the two themes really are a light and a dark one`() {
        assertThat(luminance(LIGHT_PALETTE.bg)).isGreaterThan(0.5)
        assertThat(luminance(DARK_PALETTE.bg)).isLessThan(0.05)
    }

    @Test
    fun `the stylesheet ships the palette it was checked against`() {
        val html = HtmlReportBuilder.build(
            HtmlReportData(
                deviceName = "Pixel 8a",
                generatedAt = java.time.LocalDateTime.of(2026, 8, 15, 12, 0),
                days = emptyList(),
                monthly = emptyList(),
                batterySamples = emptyList(),
                logEntries = emptyList(),
                zone = java.time.ZoneOffset.UTC,
            ),
            TEST_REPORT_LABELS,
        )

        assertThat(html).contains("--muted: ${LIGHT_PALETTE.muted};")
        assertThat(html).contains("--muted: ${DARK_PALETTE.muted};")
        assertThat(html).contains("--accent: ${LIGHT_PALETTE.accent};")
        assertThat(html).contains("--accent: ${DARK_PALETTE.accent};")
        assertThat(html).contains("prefers-color-scheme: dark")
    }
}
