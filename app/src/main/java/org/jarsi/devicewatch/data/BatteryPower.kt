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
fun batteryPowerWatts(currentRaw: Int, voltageVolts: Double, microamps: Boolean = false): Double? {
    if (currentRaw == 0 || currentRaw == Int.MIN_VALUE || voltageVolts <= 0.0) return null
    val wattsAsMicroAmps = abs(currentRaw.toDouble()) / 1_000_000.0 * voltageVolts
    // A phone known to report µA is never rescaled: a 10 mA idle draw is 0.04 W, not 40 W.
    if (microamps) return wattsAsMicroAmps.takeIf { it <= 150.0 }
    val plausibleWatts = 0.05..150.0
    if (wattsAsMicroAmps in plausibleWatts) return wattsAsMicroAmps
    val wattsAsMilliAmps = wattsAsMicroAmps * 1000.0
    return wattsAsMilliAmps.takeIf { it in plausibleWatts }
}

/**
 * True once a reading settles the unit: 100 000 mA would be 100 A, so a reading
 * that large can only be µA. Remembered per phone, never backed up, so a tiny
 * current later is read as the fraction of a watt it is.
 */
fun batteryCurrentUnitIsMicroamps(currentRaw: Int): Boolean =
    currentRaw != Int.MIN_VALUE && abs(currentRaw) >= 100_000

/**
 * The battery's power while the phone is plugged in but not charging: full, or held
 * at a charge limit. The current is then near zero, so the reading is taken as µA
 * only (a few thousand µA must not be rescaled into a dozen watts) and zero is a
 * real value. Null for a missing reading, no voltage, or an implausible size.
 */
fun batteryTricklePowerWatts(currentRaw: Int, voltageVolts: Double): Double? {
    if (currentRaw == Int.MIN_VALUE || voltageVolts <= 0.0) return null
    val watts = abs(currentRaw.toDouble()) / 1_000_000.0 * voltageVolts
    return watts.takeIf { it <= 150.0 }
}
