package org.jarsi.devicewatch.data

import java.time.LocalDate
import java.time.ZoneId

/**
 * The figures the Quick Settings tiles, the single-figure widget and the live
 * notification can show (GitHub #7). Declaration order is display order: the
 * notification lists the chosen figures in it, and the widget's picker too. A
 * figure added here appears in every picker; a tile needs its own service as well.
 */
enum class QuickMetric { BATTERY, BATTERY_TEMP, CPU_TEMP, DATA_TODAY, UPTIME, CPU_LOAD, MEMORY, STORAGE }

/** A stored comma-separated selection; names no longer known are skipped. */
fun quickMetricsOf(stored: String?): Set<QuickMetric> =
    stored.orEmpty().split(',').mapNotNull { name -> QuickMetric.entries.firstOrNull { it.name == name } }.toSet()

/** The selection as stored, in list order. */
fun quickMetricsToStored(metrics: Set<QuickMetric>): String =
    QuickMetric.entries.filter { it in metrics }.joinToString(",") { it.name }

/** A single-figure widget's figure; one never chosen (or no longer known) shows the battery. */
fun singleMetricOf(stored: String?): QuickMetric =
    QuickMetric.entries.firstOrNull { it.name == stored } ?: QuickMetric.BATTERY

/** Mobile and Wi-Fi data since local midnight, whatever period the data counter uses. */
data class DataToday(val mobileGb: Double, val wifiGb: Double) {
    val totalGb: Double get() = mobileGb + wifiGb
}

/**
 * Today's data from a [DataUsageSince] read from midnight; null when either figure
 * is missing, which without usage access both are. A mobile-only figure is never
 * passed off as the day's data.
 */
fun dataTodayOf(usage: DataUsageSince): DataToday? =
    if (usage.mobileGb < 0.0 || usage.wifiGb < 0.0) null else DataToday(mobileGb = usage.mobileGb, wifiGb = usage.wifiGb)

fun startOfTodayMillis(today: LocalDate, zone: ZoneId): Long =
    today.atStartOfDay(zone).toInstant().toEpochMilli()
