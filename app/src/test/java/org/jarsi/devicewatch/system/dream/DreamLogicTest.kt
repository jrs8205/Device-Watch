package org.jarsi.devicewatch.system.dream

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.data.batteryPowerWatts
import org.junit.Test

/**
 * Pure-JVM tests for the screensaver's night-dim window (pure functions in
 * DreamDim.kt), its charge state and its use of the shared battery-power math.
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

    @Test
    fun `the bolt pulses only while the battery is still filling`() {
        assertThat(dreamChargeState(STATUS_CHARGING, plugged = true, level = 43))
            .isEqualTo(DreamChargeState.CHARGING)
        assertThat(DreamChargeState.CHARGING.pulses).isTrue()
        assertThat(DreamChargeState.entries.filter { it.pulses }).containsExactly(DreamChargeState.CHARGING)
    }

    @Test
    fun `a full battery on the charger is full whatever the status says`() {
        // Phones go on reporting CHARGING at 100 % for a while before they say FULL.
        assertThat(dreamChargeState(STATUS_CHARGING, plugged = true, level = 100)).isEqualTo(DreamChargeState.FULL)
        assertThat(dreamChargeState(STATUS_FULL, plugged = true, level = 100)).isEqualTo(DreamChargeState.FULL)
        assertThat(dreamChargeState(STATUS_NOT_CHARGING, plugged = true, level = 100)).isEqualTo(DreamChargeState.FULL)
    }

    @Test
    fun `plugged in without charging is held, not on battery and not charging`() {
        // A charge limit, a pause for heat, or a charger too weak to fill the battery.
        assertThat(dreamChargeState(STATUS_NOT_CHARGING, plugged = true, level = 80)).isEqualTo(DreamChargeState.HELD)
        assertThat(dreamChargeState(STATUS_FULL, plugged = true, level = 80)).isEqualTo(DreamChargeState.HELD)
        assertThat(dreamChargeState(STATUS_DISCHARGING, plugged = true, level = 43)).isEqualTo(DreamChargeState.HELD)
    }

    @Test
    fun `off the charger is on battery, at a hundred percent too`() {
        assertThat(dreamChargeState(STATUS_DISCHARGING, plugged = false, level = 43))
            .isEqualTo(DreamChargeState.ON_BATTERY)
        assertThat(dreamChargeState(STATUS_DISCHARGING, plugged = false, level = 100))
            .isEqualTo(DreamChargeState.ON_BATTERY)
        assertThat(dreamChargeState(-1, plugged = false, level = 50)).isEqualTo(DreamChargeState.ON_BATTERY)
    }

    private companion object {
        const val STATUS_CHARGING = android.os.BatteryManager.BATTERY_STATUS_CHARGING
        const val STATUS_DISCHARGING = android.os.BatteryManager.BATTERY_STATUS_DISCHARGING
        const val STATUS_NOT_CHARGING = android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING
        const val STATUS_FULL = android.os.BatteryManager.BATTERY_STATUS_FULL
    }

    @Test
    fun `the Night style takes over inside the night window only`() {
        fun styleAt(minute: Int) = effectiveDreamStyle(
            selected = DreamStyle.GLOW,
            nightStyleAuto = true,
            nightWindowActive = isInNightWindow(minute, 22 * 60, 7 * 60),
        )

        assertThat(styleAt(21 * 60 + 59)).isEqualTo(DreamStyle.GLOW)
        assertThat(styleAt(22 * 60)).isEqualTo(DreamStyle.NIGHT)
        assertThat(styleAt(6 * 60 + 59)).isEqualTo(DreamStyle.NIGHT)
        assertThat(styleAt(7 * 60)).isEqualTo(DreamStyle.GLOW)
    }

    @Test
    fun `without the switch nothing changes`() {
        assertThat(effectiveDreamStyle(DreamStyle.MINIMAL, nightStyleAuto = false, nightWindowActive = true))
            .isEqualTo(DreamStyle.MINIMAL)
    }

    @Test
    fun `Night chosen by hand stays Night outside the window`() {
        assertThat(effectiveDreamStyle(DreamStyle.NIGHT, nightStyleAuto = true, nightWindowActive = false))
            .isEqualTo(DreamStyle.NIGHT)
    }
}
