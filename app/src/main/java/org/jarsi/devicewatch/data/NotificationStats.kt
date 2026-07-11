package org.jarsi.devicewatch.data

import java.time.LocalDate

/** Daily notification counts persisted by the notification listener. */
interface NotificationStats {
    fun totalForDay(day: LocalDate): Int

    fun countForPackage(packageName: String, day: LocalDate): Int

    /** Sum of daily totals over [start]..[end] (inclusive). */
    fun totalBetween(start: LocalDate, end: LocalDate): Int

    fun increment(packageName: String, day: LocalDate)

    /** Drops counts older than the retention window (today + yesterday). */
    fun purge(today: LocalDate)

    /** Whether this app's NotificationListenerService is currently granted access. */
    fun isListenerEnabled(): Boolean
}
