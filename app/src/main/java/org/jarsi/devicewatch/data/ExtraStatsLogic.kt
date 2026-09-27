package org.jarsi.devicewatch.data

/** Where the charge comes from, as `BatteryManager.EXTRA_PLUGGED` reports it. */
enum class ChargeSource { NONE, AC, USB, WIRELESS, DOCK }

/**
 * Pure rules behind the readings added in 1.6.0 on top of the original stats:
 * charge source, the system's own battery estimate, deep sleep, thermal state,
 * private DNS, the Google Play system update and the data and storage
 * breakdowns. No Android types, so every rule runs on the JVM.
 */
object ExtraStatsLogic {

    /**
     * `BatteryManager.BATTERY_PLUGGED_AC` (1), `_USB` (2), `_WIRELESS` (4) and
     * `_DOCK` (8, API 33). Anything else, including 0 for unplugged, is no source.
     */
    fun chargeSource(plugged: Int): ChargeSource = when (plugged) {
        1 -> ChargeSource.AC
        2 -> ChargeSource.USB
        4 -> ChargeSource.WIRELESS
        8 -> ChargeSource.DOCK
        else -> ChargeSource.NONE
    }

    /**
     * `PowerManager.getBatteryDischargePrediction()` in whole minutes, or null when
     * the system has no prediction or one under a minute (nothing worth showing).
     */
    fun predictionMinutes(millis: Long?): Int? {
        if (millis == null || millis < MINUTE_MILLIS) return null
        return (millis / MINUTE_MILLIS).toInt()
    }

    private const val MINUTE_MILLIS = 60_000L
}
