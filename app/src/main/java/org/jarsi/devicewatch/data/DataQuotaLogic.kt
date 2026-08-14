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
