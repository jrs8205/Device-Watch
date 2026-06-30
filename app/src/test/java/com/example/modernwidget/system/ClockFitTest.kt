package com.example.modernwidget.system

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure-JVM tests for the screensaver clock's width-fit math. Widths-per-sp below are picked so
 * the arithmetic is easy to follow (time = 3.0 px/sp, seconds = 1.5 px/sp, 24 px gap).
 */
class ClockFitTest {

    private fun fit(availablePx: Float) = fittedClockSp(
        availablePx = availablePx,
        gapPx = 24f,
        timePerSp = 3.0f,
        secPerSp = 1.5f,
        secondsRatio = 0.25f,
        secondsMinSp = 22,
        minClockSp = 56,
        maxClockSp = 120,
    )

    @Test
    fun `wide width is clamped to the max size`() {
        assertThat(fit(5000f)).isEqualTo(120)
    }

    @Test
    fun `medium width shrinks below the max but stays large`() {
        // (400 - 24) / (3.0 + 1.5*0.25) = 376 / 3.375 = 111.4
        assertThat(fit(400f)).isEqualTo(111)
    }

    @Test
    fun `narrow width pins the seconds at its floor and still fits`() {
        // Proportional seconds would be < 22sp, so the floor branch applies:
        // (300 - 24 - 1.5*22) / 3.0 = 243 / 3.0 = 81
        assertThat(fit(300f)).isEqualTo(81)
    }

    @Test
    fun `tiny width is clamped to the min size`() {
        assertThat(fit(100f)).isEqualTo(56)
    }

    @Test
    fun `result never decreases as available width grows`() {
        var previous = 0
        var width = 80f
        while (width <= 2000f) {
            val size = fit(width)
            assertThat(size).isAtLeast(previous)
            assertThat(size).isAtLeast(56)
            assertThat(size).isAtMost(120)
            previous = size
            width += 20f
        }
    }
}
