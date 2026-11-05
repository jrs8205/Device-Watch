package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The battery's power in watts from the raw current and the voltage, shared by the
 * screensaver's charging wattage and the "Battery power" figure.
 */
class BatteryPowerTest {

    @Test
    fun `microamp readings give watts regardless of sign`() {
        assertThat(batteryPowerWatts(2_000_000, 4.4)!!).isWithin(0.001).of(8.8)
        assertThat(batteryPowerWatts(-2_000_000, 4.4)!!).isWithin(0.001).of(8.8)
    }

    @Test
    fun `milliamp readings are rescaled`() {
        // Some phones report BATTERY_PROPERTY_CURRENT_NOW in mA instead of the documented µA.
        assertThat(batteryPowerWatts(2000, 4.4)!!).isWithin(0.001).of(8.8)
    }

    @Test
    fun `idle discharge is plausible`() {
        assertThat(batteryPowerWatts(50_000, 4.0)!!).isWithin(0.001).of(0.2)
        assertThat(batteryPowerWatts(50, 4.0)!!).isWithin(0.001).of(0.2)
    }

    @Test
    fun `unavailable or implausible give null`() {
        assertThat(batteryPowerWatts(0, 4.4)).isNull()
        assertThat(batteryPowerWatts(Int.MIN_VALUE, 4.4)).isNull()
        assertThat(batteryPowerWatts(60_000_000, 5.0)).isNull() // 300 W
        assertThat(batteryPowerWatts(2_000_000, 0.0)).isNull() // no voltage reading
        assertThat(batteryPowerWatts(5, 4.0)).isNull() // 0.02 W even as mA
    }
}
