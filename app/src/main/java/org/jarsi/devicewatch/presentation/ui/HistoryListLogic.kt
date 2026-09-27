package org.jarsi.devicewatch.presentation.ui

import androidx.annotation.StringRes
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.presentation.HistoryDay

/** Metric shown by the history day list; label reuses the Overview usage-counter strings. */
internal enum class HistoryMetric(@StringRes val labelRes: Int) {
    ScreenTime(R.string.screen_time_total_label),
    ScreenOn(R.string.screen_on_label),
    Unlocks(R.string.unlock_count_label),
    Notifications(R.string.notification_count_label),
    Boots(R.string.boot_count_label),
    Charges(R.string.charge_count_label),
    Storage(R.string.storage_row);

    fun valueOf(day: HistoryDay): Long = when (this) {
        ScreenTime -> day.screenTimeMillis
        ScreenOn -> day.screenOnMillis
        Unlocks -> day.unlocks.toLong()
        Notifications -> day.notifications.toLong()
        Boots -> day.boots.toLong()
        Charges -> day.charges.toLong()
        Storage -> day.storageUsedBytes
    }

    /** The value a history row shows for [day]: a duration for the time metrics, else a count. */
    fun rowValue(day: HistoryDay): HistoryRowValue = when (this) {
        ScreenTime, ScreenOn -> HistoryRowValue.Duration(valueOf(day))
        Storage -> HistoryRowValue.Bytes(valueOf(day).takeIf { it > 0L })
        else -> HistoryRowValue.Count(valueOf(day))
    }
}

internal sealed interface HistoryRowValue {
    data class Duration(val millis: Long) : HistoryRowValue
    data class Count(val value: Long) : HistoryRowValue

    /** Null for a day without a reading: the phone was off, or the app not running. */
    data class Bytes(val bytes: Long?) : HistoryRowValue
}

/**
 * How much the used storage of `newestFirst[index]` grew since the nearest earlier
 * day with a reading; null when either reading is missing.
 */
internal fun storageChangeBytes(newestFirst: List<HistoryDay>, index: Int): Long? {
    val current = newestFirst.getOrNull(index)?.storageUsedBytes?.takeIf { it > 0L } ?: return null
    val earlier = newestFirst.drop(index + 1).firstOrNull { it.storageUsedBytes > 0L } ?: return null
    return current - earlier.storageUsedBytes
}

/**
 * The rows the history list shows for [metric]: newest day first, trimmed so the
 * list starts at the first day this metric ever recorded a value — days before
 * that are "not collected yet" (listener not granted, app not installed), not
 * real zeros. Zero days after the first data day are kept.
 */
internal fun daysNewestFirstSinceFirstData(
    days: List<HistoryDay>,
    metric: HistoryMetric,
): List<HistoryDay> {
    val firstIndex = days.indexOfFirst { metric.valueOf(it) > 0L }
    if (firstIndex == -1) return emptyList()
    return days.drop(firstIndex).asReversed()
}
