package org.jarsi.devicewatch.presentation

import org.jarsi.devicewatch.data.NotificationLogEntry
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** RFC 4180 CSV rendering of the usage history and the notification log. */
object CsvExporter {

    // Screen-on time and used storage came in 1.6.0 and go last, so the earlier columns keep their places.
    private const val USAGE_HEADER =
        "day,screen_time_minutes,unlocks,notifications,boots,charges,screen_on_minutes,storage_used_gb"
    private const val LOG_HEADER = "time,package,app,title,text"

    /**
     * Characters a spreadsheet takes as the start of a formula. Notification text
     * is written by other apps, so a title like `=HYPERLINK(...)` would otherwise
     * run the moment the export is opened in Excel or LibreOffice.
     */
    private val FORMULA_TRIGGERS = setOf('=', '+', '-', '@', '\t', '\r')

    /**
     * Quotes a field only when it needs it; embedded quotes are doubled. A field
     * that starts like a formula gets a leading apostrophe (the OWASP CSV-injection
     * mitigation) and is always quoted.
     */
    fun escapeField(value: String): String {
        val startsLikeFormula = value.firstOrNull() in FORMULA_TRIGGERS
        val guarded = if (startsLikeFormula) "'$value" else value
        val needsQuoting = startsLikeFormula ||
            guarded.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuoting) return guarded
        return "\"${guarded.replace("\"", "\"\"")}\""
    }

    /**
     * ISO dates in the given order (the caller passes days ascending). A cell is
     * empty — not 0 — for days before that metric was first collected
     * ([HistoryCoverage]); a spreadsheet then averages over the measured days only.
     */
    fun usageHistoryCsv(days: List<HistoryDay>): String = buildString {
        appendLine(USAGE_HEADER)
        val coverage = HistoryCoverage.of(days)
        days.forEach { day ->
            val minutes = (day.screenTimeMillis + 30_000L) / 60_000L
            val cells = listOf(
                day.day.toString(),
                minutes.takeIf { coverage.screenTimeKnown(day.day) }?.toString().orEmpty(),
                day.unlocks.takeIf { coverage.unlocksKnown(day.day) }?.toString().orEmpty(),
                day.notifications.takeIf { coverage.notificationsKnown(day.day) }?.toString().orEmpty(),
                day.boots.takeIf { coverage.bootsKnown(day.day) }?.toString().orEmpty(),
                day.charges.takeIf { coverage.chargesKnown(day.day) }?.toString().orEmpty(),
                ((day.screenOnMillis + 30_000L) / 60_000L)
                    .takeIf { coverage.screenOnKnown(day.day) }?.toString().orEmpty(),
                // A plain dot decimal, whatever the phone's locale, so a spreadsheet reads a number.
                day.storageUsedBytes.takeIf { it > 0L }
                    ?.let { String.format(Locale.ROOT, "%.2f", it / BYTES_PER_GIB) }.orEmpty(),
            )
            appendLine(cells.joinToString(","))
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

    private const val BYTES_PER_GIB = 1024.0 * 1024.0 * 1024.0
}
