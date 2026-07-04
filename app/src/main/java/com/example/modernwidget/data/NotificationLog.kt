package com.example.modernwidget.data

import java.time.LocalDate

/** One captured notification. Everything stays on the device. */
data class NotificationLogEntry(
    val timeMillis: Long,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
)

/** Rolling on-device log of real notifications (same filter as the day counter). */
interface NotificationLog {
    fun append(entry: NotificationLogEntry)

    /** All retained entries, newest first. */
    fun entriesNewestFirst(): List<NotificationLogEntry>
}

/**
 * Line format for the daily log files: tab-separated
 * `v1<TAB>millis<TAB>package<TAB>label<TAB>title<TAB>text` with `\` `\t` `\n` `\r`
 * escaped, so one notification is always exactly one line. TSV instead of JSON
 * because org.json is not available to plain-JVM unit tests.
 */
object NotificationLogCodec {

    const val RETENTION_DAYS = 7L
    const val MAX_ENTRIES_PER_DAY = 1000

    fun encode(entry: NotificationLogEntry): String = listOf(
        "v1",
        entry.timeMillis.toString(),
        escape(entry.packageName),
        escape(entry.appLabel),
        escape(entry.title),
        escape(entry.text),
    ).joinToString("\t")

    fun decodeOrNull(line: String): NotificationLogEntry? {
        val parts = line.split('\t')
        if (parts.size != 6 || parts[0] != "v1") return null
        val millis = parts[1].toLongOrNull() ?: return null
        return NotificationLogEntry(
            timeMillis = millis,
            packageName = unescape(parts[2]),
            appLabel = unescape(parts[3]),
            title = unescape(parts[4]),
            text = unescape(parts[5]),
        )
    }

    fun fileNameFor(day: LocalDate): String = "${day.toEpochDay()}.log"

    fun isRetainedFileName(name: String, today: LocalDate): Boolean {
        val epochDay = name.removeSuffix(".log").toLongOrNull() ?: return false
        if (!name.endsWith(".log")) return false
        val age = today.toEpochDay() - epochDay
        return age in 0 until RETENTION_DAYS
    }

    private fun escape(value: String): String = buildString(value.length) {
        for (c in value) when (c) {
            '\\' -> append("\\\\")
            '\t' -> append("\\t")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(c)
        }
    }

    private fun unescape(value: String): String = buildString(value.length) {
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    '\\' -> append('\\')
                    't' -> append('\t')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    else -> append(value[i + 1])
                }
                i += 2
            } else {
                append(c); i += 1
            }
        }
    }
}
