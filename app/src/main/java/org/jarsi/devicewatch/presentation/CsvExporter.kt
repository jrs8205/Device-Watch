package org.jarsi.devicewatch.presentation

import org.jarsi.devicewatch.data.NotificationLogEntry
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** RFC 4180 CSV rendering of the usage history and the notification log. */
object CsvExporter {

    private const val USAGE_HEADER = "day,screen_time_minutes,unlocks,notifications,boots,charges"
    private const val LOG_HEADER = "time,package,app,title,text"

    /** Quotes a field only when it needs it; embedded quotes are doubled. */
    fun escapeField(value: String): String {
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuoting) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }

    /** ISO dates in the given order (the caller passes days ascending). */
    fun usageHistoryCsv(days: List<HistoryDay>): String = buildString {
        appendLine(USAGE_HEADER)
        days.forEach { day ->
            val minutes = (day.screenTimeMillis + 30_000L) / 60_000L
            appendLine(
                "${day.day},$minutes,${day.unlocks},${day.notifications},${day.boots},${day.charges}"
            )
        }
    }

    /** ISO-8601 local timestamps in the given order (the log hands entries newest first). */
    fun notificationLogCsv(entries: List<NotificationLogEntry>, zone: ZoneId): String =
        buildString {
            appendLine(LOG_HEADER)
            entries.forEach { entry ->
                val time = LocalDateTime
                    .ofInstant(Instant.ofEpochMilli(entry.timeMillis), zone)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                val fields = listOf(
                    time,
                    entry.packageName,
                    entry.appLabel,
                    entry.title,
                    entry.text,
                ).joinToString(",") { escapeField(it) }
                appendLine(fields)
            }
        }
}
