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
     * How many more bytes the mobile counter may grow before the next alert that has
     * not been sent, or null when there is nothing left to watch: quota off, usage
     * unavailable, or both alerts sent. A level already passed is the caller's job
     * right now ([pendingThresholds]); this looks strictly ahead of the current usage.
     */
    fun bytesToNextThreshold(
        quotaGb: Double,
        usedGb: Double,
        notified80: Boolean,
        notified100: Boolean,
    ): Long? {
        if (quotaGb <= 0.0 || usedGb < 0.0) return null
        val usedBytes = usedGb * GB_BYTES
        return listOf(WARNING_PERCENT to notified80, REACHED_PERCENT to notified100)
            .filterNot { (_, notified) -> notified }
            .map { (percent, _) -> quotaGb * percent / 100.0 * GB_BYTES }
            .firstOrNull { it > usedBytes }
            ?.let { (it - usedBytes).toLong() }
    }

    /**
     * A reading carries the quota it was taken under. If the user changed the quota
     * while the read was in flight, the reading is stale: acting on it would latch
     * an alert for the new quota on the old quota's figures.
     */
    fun readingIsCurrent(readingQuotaGb: Double, currentQuotaGb: Double): Boolean =
        readingQuotaGb == currentQuotaGb

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
