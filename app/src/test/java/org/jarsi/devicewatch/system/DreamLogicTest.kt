package org.jarsi.devicewatch.system

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.data.batteryPowerWatts
import org.junit.Test

/**
 * Pure-JVM tests for the screensaver's night-dim window (a pure function in
 * MonitorDreamService.kt) and its use of the shared battery-power math.
 */
class DreamLogicTest {

    @Test
    fun `overnight window covers late evening and early morning`() {
        val start = 22 * 60
        val end = 7 * 60
        assertThat(isInNightWindow(23 * 60, start, end)).isTrue()
        assertThat(isInNightWindow(3 * 60, start, end)).isTrue()
        assertThat(isInNightWindow(12 * 60, start, end)).isFalse()
        assertThat(isInNightWindow(21 * 60 + 59, start, end)).isFalse()
    }

    @Test
    fun `window start is inclusive and end is exclusive`() {
        assertThat(isInNightWindow(22 * 60, 22 * 60, 7 * 60)).isTrue()
        assertThat(isInNightWindow(7 * 60, 22 * 60, 7 * 60)).isFalse()
    }

    @Test
    fun `same-day window works and an empty window never matches`() {
        assertThat(isInNightWindow(13 * 60, 12 * 60, 14 * 60)).isTrue()
        assertThat(isInNightWindow(15 * 60, 12 * 60, 14 * 60)).isFalse()
        assertThat(isInNightWindow(12 * 60, 12 * 60, 12 * 60)).isFalse()
    }

    @Test
    fun `microamp devices give plausible watts regardless of sign`() {
        assertThat(batteryPowerWatts(2_000_000, 4.4)!!).isWithin(0.001).of(8.8)
        assertThat(batteryPowerWatts(-2_000_000, 4.4)!!).isWithin(0.001).of(8.8)
    }

    @Test
    fun `milliamp-reporting devices are rescaled`() {
        // Some OEMs report BATTERY_PROPERTY_CURRENT_NOW in mA instead of the documented µA.
        assertThat(batteryPowerWatts(2000, 4.4)!!).isWithin(0.001).of(8.8)
    }

    @Test
    fun `unavailable or implausible readings give null`() {
        assertThat(batteryPowerWatts(0, 4.4)).isNull()
        assertThat(batteryPowerWatts(Int.MIN_VALUE, 4.4)).isNull()
        assertThat(batteryPowerWatts(50, 4.0)!!).isWithin(0.001).of(0.2) // mA; an idle phone's draw
        assertThat(batteryPowerWatts(60_000_000, 5.0)).isNull() // 300 W — implausible
        assertThat(batteryPowerWatts(2_000_000, 0.0)).isNull() // no voltage reading
    }
}
