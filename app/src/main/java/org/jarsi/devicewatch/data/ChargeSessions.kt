package org.jarsi.devicewatch.data

/**
 * One charge, from the sample where the charger went in to the one where it came
 * out (or the last one, when the charge is [ongoing] or a collection gap ends it).
 */
data class ChargeSession(
    val startMillis: Long,
    val endMillis: Long,
    val startLevel: Int,
    val endLevel: Int,
    val ongoing: Boolean,
    /** Highest battery temperature seen during the charge, tenths of a degree; null before 1.6.0 samples. */
    val peakTemperatureDeciC: Int?,
) {
    val durationMillis: Long get() = endMillis - startMillis

    /** Levels gained per hour; null for a charge too short for the figure to mean anything. */
    val percentPerHour: Int?
        get() {
            if (durationMillis < MIN_RATE_DURATION_MILLIS) return null
            return Math.round((endLevel - startLevel) * HOUR_MILLIS.toDouble() / durationMillis).toInt()
        }

    private companion object {
        const val HOUR_MILLIS = 3_600_000L
        const val MIN_RATE_DURATION_MILLIS = 5 * 60_000L
    }
}

/**
 * Charges read back from the battery history. The history keeps the level and
 * the charging state of every sample, so the charges of its whole retained
 * window are known from the start, not only from when this view was added.
 */
object ChargeSessions {

    /** Samples further apart than this bound a collection gap, as on the battery chart. */
    const val GAP_MILLIS = 90L * 60 * 1000

    /** Shorter charges that gained nothing are charger contact bounce, not charges. */
    private const val MIN_BOUNCE_MILLIS = 2L * 60 * 1000

    /** Charges in [samples] (any order), newest first. */
    fun from(samples: List<BatterySample>, nowMillis: Long): List<ChargeSession> {
        val sorted = samples.sortedBy { it.timeMillis }
        val sessions = mutableListOf<ChargeSession>()
        var index = 0
        while (index < sorted.size) {
            if (!sorted[index].charging) {
                index++
                continue
            }
            val first = sorted[index]
            var last = first
            var peak = first.temperatureDeciC
            var next = index + 1
            while (next < sorted.size && sorted[next].charging &&
                sorted[next].timeMillis - last.timeMillis <= GAP_MILLIS
            ) {
                last = sorted[next]
                peak = maxOfNullable(peak, last.temperatureDeciC)
                next++
            }
            val after = sorted.getOrNull(next)
            val unplugged = after != null && !after.charging && after.timeMillis - last.timeMillis <= GAP_MILLIS
            val session = ChargeSession(
                startMillis = first.timeMillis,
                endMillis = if (unplugged) after!!.timeMillis else last.timeMillis,
                startLevel = first.level,
                endLevel = if (unplugged) after!!.level else last.level,
                ongoing = after == null && nowMillis - last.timeMillis <= GAP_MILLIS,
                peakTemperatureDeciC = peak,
            )
            val bounce = !session.ongoing && session.durationMillis < MIN_BOUNCE_MILLIS &&
                session.endLevel <= session.startLevel
            if (!bounce) sessions += session
            index = next
        }
        return sessions.asReversed()
    }

    private fun maxOfNullable(a: Int?, b: Int?): Int? = when {
        a == null -> b
        b == null -> a
        else -> maxOf(a, b)
    }
}
