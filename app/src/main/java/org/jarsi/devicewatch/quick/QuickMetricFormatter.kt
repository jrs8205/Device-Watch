package org.jarsi.devicewatch.quick

import org.jarsi.devicewatch.data.DataToday
import org.jarsi.devicewatch.data.QuickMetric
import org.jarsi.devicewatch.data.SystemStats
import org.jarsi.devicewatch.data.UNAVAILABLE_TEXT
import org.jarsi.devicewatch.widget.compactDataAmountText
import org.jarsi.devicewatch.widget.formatDouble
import org.jarsi.devicewatch.widget.percentText
import java.util.Locale

/**
 * A figure's three names: [title] in Settings and the widget's picker, [tileLabel]
 * on a tile and the widget, [shortName] in the notification, where two
 * temperatures or two "x / y GB" figures side by side need a name each.
 */
data class QuickMetricNames(val title: String, val tileLabel: String, val shortName: String)

/** The localized words [QuickMetricFormatter] needs, so it stays plain Kotlin under test. */
data class QuickMetricLabels(
    val names: Map<QuickMetric, QuickMetricNames>,
    val dayUnit: String,
    val hourUnit: String,
    val minuteUnit: String,
    val mobileShort: String,
    val wifiShort: String,
    val needsExtendedAccess: String,
    val needsUsageAccess: String,
    val notAvailable: String,
)

/**
 * One figure ready to show. A missing figure has [UNAVAILABLE_TEXT] for its value
 * and says why in [unavailableReason].
 */
data class QuickMetricReading(
    val metric: QuickMetric,
    val value: String,
    val detail: String?,
    val unavailableReason: String?,
) {
    val available: Boolean get() = unavailableReason == null
}

/** Turns a reading of the device into the figures of GitHub #7, the same on every surface. */
object QuickMetricFormatter {

    fun read(
        metric: QuickMetric,
        stats: SystemStats,
        dataToday: DataToday?,
        extendedAccess: Boolean,
        labels: QuickMetricLabels,
    ): QuickMetricReading {
        fun shown(value: String, detail: String? = null) = QuickMetricReading(metric, value, detail, null)
        fun missing(reason: String = labels.notAvailable) = QuickMetricReading(metric, UNAVAILABLE_TEXT, null, reason)

        return when (metric) {
            QuickMetric.BATTERY ->
                if (stats.batteryLevel < 0) missing()
                else shown(percentText(stats.batteryLevel), stats.batteryStatus.takeIf { it != UNAVAILABLE_TEXT })
            // Below zero is the shared "unavailable" sentinel for temperatures, as on the widgets.
            QuickMetric.BATTERY_TEMP ->
                if (stats.batteryTemp < 0.0) missing() else shown(formatDouble(stats.batteryTemp, 1, " °C"))
            QuickMetric.CPU_TEMP -> when {
                stats.cpuTemp > 0.0 -> shown(formatDouble(stats.cpuTemp, 1, " °C"))
                // Most phones hand the processor's sensors only to a privileged shell.
                !extendedAccess -> missing(labels.needsExtendedAccess)
                else -> missing()
            }
            QuickMetric.DATA_TODAY ->
                if (dataToday == null) missing(labels.needsUsageAccess)
                else shown(
                    compactDataAmountText(dataToday.totalGb),
                    "${labels.mobileShort} ${compactDataAmountText(dataToday.mobileGb)} · " +
                        "${labels.wifiShort} ${compactDataAmountText(dataToday.wifiGb)}",
                )
            QuickMetric.UPTIME ->
                if (stats.uptimeMillis <= 0L) missing()
                else shown(uptimeText(stats.uptimeMillis, labels.dayUnit, labels.hourUnit, labels.minuteUnit))
            QuickMetric.CPU_LOAD ->
                if (stats.cpuLoadPercent < 0) missing() else shown(percentText(stats.cpuLoadPercent))
            QuickMetric.MEMORY ->
                if (stats.usedRamGb < 0.0 || stats.totalRamGb <= 0.0) missing()
                else shown(String.format(Locale.getDefault(), "%.1f / %.1f GB", stats.usedRamGb, stats.totalRamGb))
            QuickMetric.STORAGE ->
                if (stats.usedStorageGb < 0.0 || stats.totalStorageGb <= 0.0) missing()
                else shown(String.format(Locale.getDefault(), "%.0f / %.0f GB", stats.usedStorageGb, stats.totalStorageGb))
        }
    }

    /** Uptime in its two largest units: "3 pv 4 t", "4 t 12 min", "12 min". */
    fun uptimeText(millis: Long, dayUnit: String, hourUnit: String, minuteUnit: String): String {
        val minutes = millis / 60_000L
        val hours = minutes / 60
        val days = hours / 24
        return when {
            days >= 1 -> "$days $dayUnit ${hours % 24} $hourUnit"
            hours >= 1 -> "$hours $hourUnit ${minutes % 60} $minuteUnit"
            else -> "$minutes $minuteUnit"
        }
    }
}
