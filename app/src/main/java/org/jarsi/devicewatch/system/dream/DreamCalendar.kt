package org.jarsi.devicewatch.system.dream

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.jarsi.devicewatch.AndroidPatternFormat
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

/** Whether the event runs at [nowMs] or begins within [horizonDays] after it. */
internal fun DreamCalendarEvent.isUpcoming(nowMs: Long, horizonDays: Int = CALENDAR_HORIZON_DAYS): Boolean =
    endMs > nowMs && beginMs <= nowMs + horizonDays * DAY_MS

/**
 * The next events worth a row: those running now and those that begin within the horizon,
 * earliest first, [max] at most. An event that has ended is left out.
 */
internal fun upcomingEvents(
    events: List<DreamCalendarEvent>,
    nowMs: Long,
    max: Int = CALENDAR_MAX_EVENTS,
    horizonDays: Int = CALENDAR_HORIZON_DAYS,
): List<DreamCalendarEvent> = events
    .filter { it.isUpcoming(nowMs, horizonDays) }
    .sortedBy { it.beginMs }
    .take(max)

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
    patterns: CalendarTimePatterns,
    nowWord: String,
): String {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val begin = Instant.ofEpochMilli(event.beginMs).atZone(zone)
    val beginDay = begin.toLocalDate()
    if (beginDay.isBefore(today)) return nowWord
    val pattern = when {
        event.allDay -> "EEE"
        beginDay == today -> patterns.time
        else -> patterns.weekdayTime
    }
    // The patterns are Android's own on a 12-hour clock, so Android's formatter writes them.
    return AndroidPatternFormat(pattern, locale, zone).format(event.beginMs).uppercase(locale)
}

/** How a calendar row writes a time of day, and a weekday with a time. */
internal data class CalendarTimePatterns(val time: String, val weekdayTime: String)

/**
 * The patterns for a language and clock setting. On a 12-hour clock they are the language's
 * own, the ones the screensaver's alarm and estimate use: AM or PM after the time in English,
 * before it in Chinese. A 24-hour time is written the same everywhere.
 */
internal fun calendarTimePatterns(locale: Locale, is24Hour: Boolean): CalendarTimePatterns =
    if (is24Hour) {
        CalendarTimePatterns(time = "HH:mm", weekdayTime = "EEE HH:mm")
    } else {
        CalendarTimePatterns(time = twelveHourClockPattern(locale), weekdayTime = twelveHourAlarmPattern(locale))
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
    patterns: CalendarTimePatterns,
    showTitles: Boolean,
    nowWord: String,
    placeholder: String,
): List<DreamCalendarRow> = upcomingEvents(events, nowMs).map { event ->
    val title = event.title.trim().replace(WHITESPACE, " ")
    DreamCalendarRow(
        whenText = calendarWhenText(event, nowMs, zone, locale, patterns, nowWord),
        title = if (showTitles && title.isNotEmpty()) title else placeholder,
    )
}

private val WHITESPACE = Regex("\\s+")
