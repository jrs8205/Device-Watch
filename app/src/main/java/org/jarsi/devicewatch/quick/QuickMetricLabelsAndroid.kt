package org.jarsi.devicewatch.quick

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.QuickMetric

/** Each figure's string resources: title, tile label, short name. */
internal fun quickMetricNameRes(metric: QuickMetric): Triple<Int, Int, Int> = when (metric) {
    QuickMetric.BATTERY -> Triple(R.string.quick_battery_title, R.string.quick_battery_tile, R.string.quick_battery_short)
    QuickMetric.BATTERY_TEMP ->
        Triple(R.string.quick_battery_temp_title, R.string.quick_battery_temp_tile, R.string.quick_battery_temp_short)
    QuickMetric.CPU_TEMP -> Triple(R.string.quick_cpu_temp_title, R.string.quick_cpu_temp_tile, R.string.quick_cpu_temp_short)
    QuickMetric.DATA_TODAY ->
        Triple(R.string.quick_data_today_title, R.string.quick_data_today_tile, R.string.quick_data_today_short)
    QuickMetric.UPTIME -> Triple(R.string.quick_uptime_title, R.string.quick_uptime_tile, R.string.quick_uptime_short)
    QuickMetric.CPU_LOAD -> Triple(R.string.quick_cpu_load_title, R.string.quick_cpu_load_tile, R.string.quick_cpu_load_short)
    QuickMetric.MEMORY -> Triple(R.string.quick_memory_title, R.string.quick_memory_tile, R.string.quick_memory_short)
    QuickMetric.STORAGE -> Triple(R.string.quick_storage_title, R.string.quick_storage_tile, R.string.quick_storage_short)
}

/** A figure's tile icon; both temperatures share the thermometer and are told apart by their label. */
@DrawableRes
internal fun quickMetricIconRes(metric: QuickMetric): Int = when (metric) {
    QuickMetric.BATTERY -> R.drawable.ic_widget_battery
    QuickMetric.BATTERY_TEMP, QuickMetric.CPU_TEMP -> R.drawable.ic_quick_thermometer
    QuickMetric.DATA_TODAY -> R.drawable.ic_widget_swap
    QuickMetric.UPTIME -> R.drawable.ic_widget_schedule
    QuickMetric.CPU_LOAD -> R.drawable.ic_widget_cpu
    QuickMetric.MEMORY -> R.drawable.ic_widget_memory
    QuickMetric.STORAGE -> R.drawable.ic_widget_storage
}

/** A figure's title, for the pickers. */
@StringRes
internal fun quickMetricTitleRes(metric: QuickMetric): Int = quickMetricNameRes(metric).first

/** The figures' words in the context's current language. */
fun quickMetricLabels(context: Context): QuickMetricLabels = QuickMetricLabels(
    names = QuickMetric.entries.associateWith { metric ->
        val (title, tile, short) = quickMetricNameRes(metric)
        QuickMetricNames(context.getString(title), context.getString(tile), context.getString(short))
    },
    dayUnit = context.getString(R.string.unit_day_short),
    hourUnit = context.getString(R.string.unit_hour_short),
    minuteUnit = context.getString(R.string.unit_minute_short),
    mobileShort = context.getString(R.string.quick_mobile_short),
    wifiShort = context.getString(R.string.quick_wifi_short),
    needsExtendedAccess = context.getString(R.string.quick_needs_extended_access),
    needsUsageAccess = context.getString(R.string.quick_needs_usage_access),
    notAvailable = context.getString(R.string.quick_not_available),
)
