package org.jarsi.devicewatch.system.dream

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

    // A marker on the time's left (AM or PM in Chinese): 2.0 px/sp, 0.15 of the clock's size
    // and never below 13.2 sp, 8 px away from the time.
    private fun fitBesideLead(availablePx: Float) = fittedClockSpBesideLead(
        availablePx = availablePx,
        leadPerSp = 2.0f,
        leadRatio = 0.15f,
        leadMinSp = 13.2f,
        leadGapPx = 8f,
        fit = ::fit,
    )

    /** The width of the whole line at a clock size: marker, gap, time, gap, seconds. */
    private fun lineWidth(sp: Int): Float =
        2.0f * maxOf(0.15f * sp, 13.2f) + 8f + 3.0f * sp + 24f + 1.5f * maxOf(0.25f * sp, 22f)

    @Test
    fun `a marker on the time's left takes its room off the clock`() {
        // Alone the clock would be 111 sp; the marker at that size is 2.0*16.65 + 8 = 41.3 px,
        // and (400 - 41.3 - 24) / 3.375 = 99.2.
        assertThat(fitBesideLead(400f)).isEqualTo(99)
        assertThat(fitBesideLead(400f)).isLessThan(fit(400f))
    }

    @Test
    fun `without a marker the clock is sized as before`() {
        val sized = fittedClockSpBesideLead(400f, leadPerSp = 0f, leadRatio = 0.15f, leadMinSp = 13.2f, leadGapPx = 8f, fit = ::fit)

        assertThat(sized).isEqualTo(fit(400f))
    }

    @Test
    fun `the line with a marker fits wherever the clock is above its smallest size`() {
        var width = 260f
        while (width <= 2000f) {
            val size = fitBesideLead(width)
            if (size > 56) assertThat(lineWidth(size)).isAtMost(width)
            width += 10f
        }
    }
}
