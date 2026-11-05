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
    fun `a phone that has reported a large current reports microamps`() {
        // 100 000 mA would be 100 A: only a µA phone ever reports that much.
        assertThat(batteryCurrentUnitIsMicroamps(150_000)).isTrue()
        assertThat(batteryCurrentUnitIsMicroamps(-100_000)).isTrue()
        assertThat(batteryCurrentUnitIsMicroamps(2000)).isFalse()
        assertThat(batteryCurrentUnitIsMicroamps(Int.MIN_VALUE)).isFalse()
    }

    @Test
    fun `a known microamp phone is never rescaled`() {
        // Review round 1: −10 000 µA at 4 V on battery became 40 W through the milliamp rescale.
        assertThat(batteryPowerWatts(-10_000, 4.0, microamps = true)!!).isWithin(0.0001).of(0.04)
        assertThat(batteryPowerWatts(2_000_000, 4.4, microamps = true)!!).isWithin(0.001).of(8.8)
        assertThat(batteryPowerWatts(0, 4.4, microamps = true)).isNull()
        assertThat(batteryPowerWatts(60_000_000, 5.0, microamps = true)).isNull() // 300 W
        // Unit still unknown: the old reading of the band stays, documented here.
        assertThat(batteryPowerWatts(-10_000, 4.0, microamps = false)!!).isWithin(0.001).of(40.0)
    }

    @Test
    fun `a trickle while plugged in is read as microamps only`() {
        // Review: held at a charge limit or full on the charger the current is near zero, and the
        // milliamp rescale turned 3000 µA into 13 W.
        assertThat(batteryTricklePowerWatts(3000, 4.35)!!).isWithin(0.0001).of(0.01305)
        assertThat(batteryTricklePowerWatts(0, 4.4)).isEqualTo(0.0)
        assertThat(batteryTricklePowerWatts(-250_000, 4.0)!!).isWithin(0.001).of(1.0)
        assertThat(batteryTricklePowerWatts(Int.MIN_VALUE, 4.4)).isNull()
        assertThat(batteryTricklePowerWatts(3000, 0.0)).isNull()
        assertThat(batteryTricklePowerWatts(60_000_000, 5.0)).isNull() // 300 W
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
