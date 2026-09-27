package org.jarsi.devicewatch.data

/** The optional alerts; each is off until the user switches it on. */
enum class HealthAlert { HOT_BATTERY, LOW_STORAGE, FAST_DRAIN }

/** What a reading means for an alert: post it, allow it again, or nothing. */
enum class AlertStep { FIRE, REARM, NONE }

/**
 * When the optional alerts fire. Each alert posts once and is then latched; it
 * re-arms only after the reading has clearly returned to normal, so a value
 * wobbling around the limit does not alert over and over.
 */
object AlertLogic {

    /** 45 °C: above what charging or a demanding app normally reaches. */
    const val HOT_BATTERY_DECI_C = 450
    private const val COOLED_DECI_C = 400

    const val LOW_STORAGE_FREE_PERCENT = 10
    private const val STORAGE_REARM_FREE_PERCENT = 12

    const val FAST_DRAIN_PERCENT_PER_HOUR = 20
    private const val CALM_DRAIN_PERCENT_PER_HOUR = 10

    private const val DRAIN_WINDOW_MILLIS = 60L * 60 * 1000
    private const val MIN_DRAIN_SPAN_MILLIS = 30L * 60 * 1000
    private const val HOUR_MILLIS = 60L * 60 * 1000

    fun hotBattery(tempDeciC: Int?, latched: Boolean): AlertStep = step(
        known = tempDeciC != null,
        fire = tempDeciC != null && tempDeciC >= HOT_BATTERY_DECI_C,
        rearm = tempDeciC != null && tempDeciC <= COOLED_DECI_C,
        latched = latched,
    )

    /** Whole percent of the storage free, rounded down so 9.9 % counts as under ten. */
    fun freePercent(totalGb: Double, usedGb: Double): Int? {
        if (totalGb <= 0.0 || usedGb < 0.0 || usedGb > totalGb) return null
        return ((totalGb - usedGb) / totalGb * 100).toInt()
    }

    fun lowStorage(freePercent: Int?, latched: Boolean): AlertStep = step(
        known = freePercent != null,
        fire = freePercent != null && freePercent < LOW_STORAGE_FREE_PERCENT,
        rearm = freePercent != null && freePercent >= STORAGE_REARM_FREE_PERCENT,
        latched = latched,
    )

    /**
     * Levels lost per hour across the samples of the last hour, or null when the
     * hour holds a charge or spans under half an hour: a short stretch turns one
     * level step into a wild rate.
     */
    fun drainPercentPerHour(samples: List<BatterySample>, nowMillis: Long): Int? {
        val window = samples
            .filter { it.timeMillis in (nowMillis - DRAIN_WINDOW_MILLIS)..nowMillis }
            .sortedBy { it.timeMillis }
        if (window.size < 2 || window.any { it.charging }) return null
        val span = window.last().timeMillis - window.first().timeMillis
        if (span < MIN_DRAIN_SPAN_MILLIS) return null
        return Math.round((window.first().level - window.last().level) * HOUR_MILLIS.toDouble() / span).toInt()
    }

    fun fastDrain(percentPerHour: Int?, charging: Boolean, latched: Boolean): AlertStep = when {
        latched && (charging || (percentPerHour != null && percentPerHour < CALM_DRAIN_PERCENT_PER_HOUR)) ->
            AlertStep.REARM
        !latched && !charging && percentPerHour != null && percentPerHour >= FAST_DRAIN_PERCENT_PER_HOUR ->
            AlertStep.FIRE
        else -> AlertStep.NONE
    }

    private fun step(known: Boolean, fire: Boolean, rearm: Boolean, latched: Boolean): AlertStep = when {
        !known -> AlertStep.NONE
        !latched && fire -> AlertStep.FIRE
        latched && rearm -> AlertStep.REARM
        else -> AlertStep.NONE
    }
}
