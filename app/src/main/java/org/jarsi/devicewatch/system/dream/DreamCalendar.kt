package org.jarsi.devicewatch.system.dream

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// Which calendar events the Gauges style lists and how it words when they are. Plain
// Kotlin: reading the calendar is the reader's job, and nothing here is stored anywhere.

/** One occurrence of a calendar event, as much of it as a row needs. */
internal data class DreamCalendarEvent(
    val title: String,
    val beginMs: Long,
    val endMs: Long,
    val allDay: Boolean,
)

/** A calendar row of the Gauges style: when on the left, what on the right. */
internal data class DreamCalendarRow(val whenText: String, val title: String)

/** How many events are listed, and how far ahead they are looked for. */
internal const val CALENDAR_MAX_EVENTS = 3
internal const val CALENDAR_HORIZON_DAYS = 7

private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * An all-day event's span in the phone's own time. The calendar stores an all-day event
 * from UTC midnight to UTC midnight; taken as instants, that is the day before for anyone
 * west of Greenwich and the wrong hours for everyone else. The dates are kept and the
 * midnights moved to [zone].
 */
internal fun allDayLocal(beginUtcMs: Long, endUtcMs: Long, zone: ZoneId): Pair<Long, Long> {
    fun localMidnight(utcMs: Long): Long = Instant.ofEpochMilli(utcMs)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()
    return localMidnight(beginUtcMs) to localMidnight(endUtcMs)
}

/**
 * The next events worth a row: those running now and those that begin within the horizon,
 * earliest first, [max] at most. An event that has ended is left out.
 */
internal fun upcomingEvents(
    events: List<DreamCalendarEvent>,
    nowMs: Long,
    max: Int = CALENDAR_MAX_EVENTS,
    horizonDays: Int = CALENDAR_HORIZON_DAYS,
): List<DreamCalendarEvent> {
    val horizonMs = nowMs + horizonDays * DAY_MS
    return events
        .filter { it.endMs > nowMs && it.beginMs <= horizonMs }
        .sortedBy { it.beginMs }
        .take(max)
}

/**
 * When an event is, as short as it can be said, in capitals: the time alone for one that
 * begins today, the weekday alone for an all-day event, weekday and time otherwise. An
 * event that began before today and still runs reads [nowWord]: its own beginning would
 * read like a day still to come.
 */
internal fun calendarWhenText(
    event: DreamCalendarEvent,
    nowMs: Long,
    zone: ZoneId,
    locale: Locale,
    is24Hour: Boolean,
    nowWord: String,
): String {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val begin = Instant.ofEpochMilli(event.beginMs).atZone(zone)
    val beginDay = begin.toLocalDate()
    if (beginDay.isBefore(today)) return nowWord
    val weekday = DateTimeFormatter.ofPattern("EEE", locale).format(begin)
    val time = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale).format(begin)
    val text = when {
        event.allDay -> weekday
        beginDay == today -> time
        else -> "$weekday $time"
    }
    return text.uppercase(locale)
}

/**
 * The calendar rows for the screen. With [showTitles] off, and for an event without a
 * title, the row says [placeholder] instead: the titles can be read by anyone who sees the
 * phone on its charger.
 */
internal fun calendarRows(
    events: List<DreamCalendarEvent>,
    nowMs: Long,
    zone: ZoneId,
    locale: Locale,
    is24Hour: Boolean,
    showTitles: Boolean,
    nowWord: String,
    placeholder: String,
): List<DreamCalendarRow> = upcomingEvents(events, nowMs).map { event ->
    val title = event.title.trim().replace(WHITESPACE, " ")
    DreamCalendarRow(
        whenText = calendarWhenText(event, nowMs, zone, locale, is24Hour, nowWord),
        title = if (showTitles && title.isNotEmpty()) title else placeholder,
    )
}

private val WHITESPACE = Regex("\\s+")
