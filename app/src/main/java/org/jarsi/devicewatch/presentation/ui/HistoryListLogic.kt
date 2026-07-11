package org.jarsi.devicewatch.presentation.ui

import androidx.annotation.StringRes
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.presentation.HistoryDay

/** Metric shown by the history day list; label reuses the Overview usage-counter strings. */
internal enum class HistoryMetric(@StringRes val labelRes: Int) {
    ScreenTime(R.string.screen_time_total_label),
    Unlocks(R.string.unlock_count_label),
    Notifications(R.string.notification_count_label),
    Boots(R.string.boot_count_label),
    Charges(R.string.charge_count_label);

    fun valueOf(day: HistoryDay): Long = when (this) {
        ScreenTime -> day.screenTimeMillis
        Unlocks -> day.unlocks.toLong()
        Notifications -> day.notifications.toLong()
        Boots -> day.boots.toLong()
        Charges -> day.charges.toLong()
    }
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
