package org.jarsi.devicewatch.data

/**
 * Pure rules for the mobile-data quota — no clock, no Android. Each threshold alerts
 * at most once per counting period; the caller owns the latches (they are stored
 * per period, so a new day or billing cycle re-arms both).
 */
object DataQuotaLogic {

    /** Warning level in percent; the second alert fires when the quota is full. */
    const val WARNING_PERCENT = 80
    const val REACHED_PERCENT = 100

    /** Bytes per gigabyte, the same factor the stats repository divides NetworkStats bytes by. */
    private const val GB_BYTES = 1024L * 1024 * 1024

    /**
     * How many more bytes the mobile counter may grow before the service must look
     * again, or null when there is nothing to watch: quota off or usage unavailable.
     * That is the distance to the next alert not yet sent — a level already passed
     * is the caller's job right now ([pendingThresholds]) — but never more than the
     * warning level's share of the quota. A new day or billing cycle starts from
     * zero and cannot reach its own warning before that many bytes have passed,
     * and the watch counts from registration, so it fires no later than the new
     * period's first crossing even when every alert of this period has been sent
     * and the period turns with the screen off.
     */
    fun bytesToNextCheck(
        quotaGb: Double,
        usedGb: Double,
        notified80: Boolean,
        notified100: Boolean,
    ): Long? {
        if (quotaGb <= 0.0 || usedGb < 0.0) return null
        val usedBytes = usedGb * GB_BYTES
        val newPeriodBytes = quotaGb * WARNING_PERCENT / 100.0 * GB_BYTES
        val nextAlertBytes = listOf(WARNING_PERCENT to notified80, REACHED_PERCENT to notified100)
            .filterNot { (_, notified) -> notified }
            .map { (percent, _) -> quotaGb * percent / 100.0 * GB_BYTES }
            .firstOrNull { it > usedBytes }
            ?.let { it - usedBytes }
        return minOf(nextAlertBytes ?: newPeriodBytes, newPeriodBytes).toLong()
    }

    /**
     * What a reading may do, given the settings stamp it was taken under and the
     * one current now. The settings are written from the UI outside the service's
     * lock, so a reading can arrive after a change to the quota, the counter mode
     * or the cycle start day; acting on it would latch or re-arm on another
     * quota's or another period's figures, or take down a watch a newer reading
     * has armed. A stale reading does nothing — the change itself triggers a fresh
     * one. A current reading without a quota (quota off, or usage access gone)
     * leaves nothing the watch could measure, so it releases the watch.
     */
    fun classifyReading(reading: QuotaSettingsStamp, current: QuotaSettingsStamp): QuotaReadingAction = when {
        reading.generation != current.generation -> QuotaReadingAction.IGNORE
        reading.quotaGb <= 0.0 -> QuotaReadingAction.RELEASE_WATCH
        reading.quotaGb == current.quotaGb -> QuotaReadingAction.CHECK
        else -> QuotaReadingAction.IGNORE
    }

    /** Thresholds crossed and not yet notified, ascending; empty when quota <= 0 or usedGb < 0. */
    fun pendingThresholds(
        quotaGb: Double,
        usedGb: Double,
        notified80: Boolean,
        notified100: Boolean,
    ): List<Int> {
        val percent = percentUsed(quotaGb, usedGb) ?: return emptyList()
        return buildList {
            if (percent >= WARNING_PERCENT && !notified80) add(WARNING_PERCENT)
            if (percent >= REACHED_PERCENT && !notified100) add(REACHED_PERCENT)
        }
    }

    /**
     * 0..100+ percent used, or null when quota disabled/usage unavailable. Truncated
     * rather than rounded, so neither the progress bar nor an alert can claim a level
     * the usage has not actually reached.
     */
    fun percentUsed(quotaGb: Double, usedGb: Double): Int? {
        if (quotaGb <= 0.0 || usedGb < 0.0) return null
        return ((usedGb / quotaGb) * 100).toInt()
    }
}

enum class QuotaReadingAction {
    /** Evaluate the thresholds and re-arm the usage watch. */
    CHECK,

    /** No quota, or no usage figure to compare it with: nothing is left to watch. */
    RELEASE_WATCH,

    /** The reading predates a settings change and must not touch alerts or the watch. */
    IGNORE,
}

/** The quota a reading was taken under (<= 0: none) and the settings generation then current. */
data class QuotaSettingsStamp(val quotaGb: Double, val generation: Long)
