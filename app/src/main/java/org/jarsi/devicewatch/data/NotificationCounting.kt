package com.example.modernwidget.data

import java.time.LocalDate

/** Pure decision + retention rules for the notification day counter. */
object NotificationCounting {

    /**
     * Whether a posted notification counts as a "real" notification: ongoing
     * (media players, foreground services), group summaries and updates to an
     * already-active key are excluded so the number stays believable.
     */
    fun shouldCountNotification(
        isOngoing: Boolean,
        isGroupSummary: Boolean,
        keyAlreadyActive: Boolean,
    ): Boolean {
        return !isOngoing && !isGroupSummary && !keyAlreadyActive
    }

    /**
     * The days whose counts are kept: the last [RETENTION_DAYS] days, so that a
     * full 31-day billing cycle can always be summed from stored daily counts.
     */
    fun retainedDays(today: LocalDate): Set<LocalDate> {
        return (0L until RETENTION_DAYS).map { today.minusDays(it) }.toSet()
    }

    private const val RETENTION_DAYS = 62L

    /** Keys look like `total:<epochDay>` or `pkg:<epochDay>:<packageName>`. Malformed keys are purged. */
    fun isRetainedStatsKey(prefsKey: String, retained: Set<LocalDate>): Boolean {
        val parts = prefsKey.split(':', limit = 3)
        val epochDay = when {
            parts.size == 2 && parts[0] == "total" -> parts[1].toLongOrNull()
            parts.size == 3 && parts[0] == "pkg" -> parts[1].toLongOrNull()
            else -> null
        } ?: return false
        return retained.any { it.toEpochDay() == epochDay }
    }
}
