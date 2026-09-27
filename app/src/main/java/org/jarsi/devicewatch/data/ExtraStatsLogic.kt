package org.jarsi.devicewatch.data

/** Where the charge comes from, as `BatteryManager.EXTRA_PLUGGED` reports it. */
enum class ChargeSource { NONE, AC, USB, WIRELESS, DOCK }

/** `PowerManager.getCurrentThermalStatus()` levels; [UNKNOWN] when unsupported. */
enum class ThermalLevel { UNKNOWN, NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN }

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

    /**
     * Time since boot the CPU spent in deep sleep: `SystemClock.elapsedRealtime()`
     * counts it, `uptimeMillis()` does not. Null when the clocks disagree.
     */
    fun deepSleepMillis(elapsedMillis: Long, awakeMillis: Long): Long? {
        if (elapsedMillis <= 0L || awakeMillis < 0L || awakeMillis > elapsedMillis) return null
        return elapsedMillis - awakeMillis
    }

    /** [deepSleepMillis] as a whole percentage of the time since boot. */
    fun deepSleepPercent(elapsedMillis: Long, awakeMillis: Long): Int? {
        val asleep = deepSleepMillis(elapsedMillis, awakeMillis) ?: return null
        return (asleep * 100 / elapsedMillis).toInt()
    }

    /** `PowerManager.THERMAL_STATUS_NONE` (0) .. `THERMAL_STATUS_SHUTDOWN` (6). */
    fun thermalLevel(status: Int): ThermalLevel = when (status) {
        0 -> ThermalLevel.NONE
        1 -> ThermalLevel.LIGHT
        2 -> ThermalLevel.MODERATE
        3 -> ThermalLevel.SEVERE
        4 -> ThermalLevel.CRITICAL
        5 -> ThermalLevel.EMERGENCY
        6 -> ThermalLevel.SHUTDOWN
        else -> ThermalLevel.UNKNOWN
    }

    /**
     * `PowerManager.getThermalHeadroom()` as a whole percentage of the point where
     * severe throttling starts (1.0); null when unsupported or rate-limited (NaN).
     */
    fun headroomPercent(headroom: Float): Int? {
        if (!headroom.isFinite() || headroom < 0f) return null
        return Math.round(headroom * 100f)
    }

    /**
     * Whether the headroom may be asked again: the platform answers NaN when asked
     * more often than every [HEADROOM_INTERVAL_MILLIS].
     */
    fun headroomDue(lastReadMillis: Long?, nowMillis: Long): Boolean {
        if (lastReadMillis == null || nowMillis < lastReadMillis) return true
        return nowMillis - lastReadMillis >= HEADROOM_INTERVAL_MILLIS
    }

    const val HEADROOM_INTERVAL_MILLIS = 10_000L

    private const val MINUTE_MILLIS = 60_000L
}
