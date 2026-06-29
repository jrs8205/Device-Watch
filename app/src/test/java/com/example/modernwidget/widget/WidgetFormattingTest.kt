package com.example.modernwidget.widget

import com.example.modernwidget.data.UNAVAILABLE_DOUBLE
import com.example.modernwidget.data.UNAVAILABLE_INT
import com.example.modernwidget.data.UNAVAILABLE_TEXT
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Pure-JVM tests for the widget's display formatters. Locale is pinned so that
 * decimal formatting is deterministic regardless of the machine's locale.
 */
class WidgetFormattingTest {

    private lateinit var originalLocale: Locale

    @Before
    fun pinLocale() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `percentText shows percent or dash`() {
        assertThat(percentText(50)).isEqualTo("50%")
        assertThat(percentText(UNAVAILABLE_INT)).isEqualTo(UNAVAILABLE_TEXT)
    }

    @Test
    fun `progressPercentOrNull clamps and rejects negatives`() {
        assertThat(progressPercentOrNull(UNAVAILABLE_INT)).isNull()
        assertThat(progressPercentOrNull(50)).isEqualTo(50)
        assertThat(progressPercentOrNull(150)).isEqualTo(100)
    }

    @Test
    fun `speedText shows Mb per second or dash`() {
        assertThat(speedText(100)).isEqualTo("100 Mb/s")
        assertThat(speedText(UNAVAILABLE_INT)).isEqualTo(UNAVAILABLE_TEXT)
    }

    @Test
    fun `gbText formats with decimals or dash`() {
        assertThat(gbText(12.5)).isEqualTo("12.5 GB")
        assertThat(gbText(12.0, decimals = 0)).isEqualTo("12 GB")
        assertThat(gbText(UNAVAILABLE_DOUBLE)).isEqualTo(UNAVAILABLE_TEXT)
    }

    @Test
    fun `formatDouble applies suffix or dash`() {
        assertThat(formatDouble(25.5, 1, "°C")).isEqualTo("25.5°C")
        assertThat(formatDouble(UNAVAILABLE_DOUBLE, 1, "°C")).isEqualTo(UNAVAILABLE_TEXT)
    }

    @Test
    fun `mobileDataText handles used-only, used-of-total, and unavailable`() {
        assertThat(mobileDataText(UNAVAILABLE_DOUBLE, UNAVAILABLE_DOUBLE)).isEqualTo(UNAVAILABLE_TEXT)
        assertThat(mobileDataText(1.5, UNAVAILABLE_DOUBLE)).isEqualTo("1.5 GB")
        assertThat(mobileDataText(1.5, 10.0)).isEqualTo("1.5 GB / 10 GB")
    }
}
