package org.jarsi.devicewatch.data

import kotlin.math.abs

/**
 * The battery's power in watts from `BATTERY_PROPERTY_CURRENT_NOW` and the battery
 * voltage, shared by the screensaver's charging wattage and the "Battery power" figure.
 *
 * CURRENT_NOW is documented as µA, but some phones report mA, and the sign that means
 * charging varies by maker: the magnitude is used, and a reading only plausible as mA is
 * rescaled. Whether the phone is charging or discharging comes from the battery status,
 * never from the sign. Returns null for a missing or implausible reading, so no surface
 * ever shows a fabricated value. The floor admits an idle phone's discharge (tens of mA),
 * which the screensaver never needed because it reads only while charging.
 */
fun batteryPowerWatts(currentRaw: Int, voltageVolts: Double): Double? {
    if (currentRaw == 0 || currentRaw == Int.MIN_VALUE || voltageVolts <= 0.0) return null
    val plausibleWatts = 0.05..150.0
    val wattsAsMicroAmps = abs(currentRaw.toDouble()) / 1_000_000.0 * voltageVolts
    if (wattsAsMicroAmps in plausibleWatts) return wattsAsMicroAmps
    val wattsAsMilliAmps = wattsAsMicroAmps * 1000.0
    return wattsAsMilliAmps.takeIf { it in plausibleWatts }
}
