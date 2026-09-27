package org.jarsi.devicewatch.data

/**
 * Device settings and states that change while the app runs — power modes,
 * connectivity checks, attached storage, security — read on each screen refresh
 * rather than once like [DeviceInfo]. Every field is display-ready and uses
 * [UNAVAILABLE_TEXT] when the platform does not expose the value.
 */
data class DeviceState(
    // Power
    val powerSaveMode: String = UNAVAILABLE_TEXT,
    val deviceIdle: String = UNAVAILABLE_TEXT,
    val batteryOptimizationExempt: String = UNAVAILABLE_TEXT,
)
