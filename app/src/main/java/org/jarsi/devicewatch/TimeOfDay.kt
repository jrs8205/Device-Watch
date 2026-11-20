package org.jarsi.devicewatch

import android.content.Context
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date
import java.util.IllformedLocaleException
import java.util.Locale

/**
 * Android's own format for a time of day in the phone's language, 12- or 24-hour as the
 * phone is set, made sure to say which half of the day a 12-hour time is in
 * ([withHalfOfDay]). Every time the pages, the lists and the widget show is written with it;
 * nothing else asks Android for its time format.
 */
fun timeOfDayFormat(context: Context): DateFormat {
    val format = android.text.format.DateFormat.getTimeFormat(context)
    if (format is SimpleDateFormat) {
        val pattern = format.toPattern()
        val whole = withHalfOfDay(pattern)
        if (whole != pattern) format.applyPattern(whole)
    }
    return format
}

/**
 * A date or a time written with a pattern Android made
 * ([android.text.format.DateFormat.getBestDateTimePattern]), by Android's own formatter.
 *
 * Such a pattern is that formatter's and nobody else's. Toki pona writes its numbers behind
 * a sign, and Android's patterns for it are "#h:mm a" and "#HH:mm"; `java.time`'s formatter
 * keeps "#" for itself and threw, which took the screensaver down with a calendar row to
 * write, and the History page with a chart to draw. Android's formatter takes every sign and
 * letter its own patterns can hold.
 *
 * Two things stay as they were with the other formatter: the days are the ones the app
 * counts by, in the Gregorian calendar whatever the language's own is, and the digits are
 * the app's ([LatinDigits]).
 */
internal class AndroidPatternFormat(pattern: String, locale: Locale, private val zone: ZoneId = ZoneId.systemDefault()) {

    private val digits = LatinDigits.locale(locale)

    private val format = android.icu.text.SimpleDateFormat(pattern, gregorian(digits)).apply {
        timeZone = android.icu.util.TimeZone.getTimeZone(zone.id)
    }

    /** With the digits of a month's own name as 0 to 9 too ([LatinDigits.text]). */
    fun format(millis: Long): String = LatinDigits.text(format.format(Date(millis)), digits)

    fun format(day: LocalDate): String = format(day.atStartOfDay(zone).toInstant().toEpochMilli())

    fun format(month: YearMonth): String = format(month.atDay(1))

    private companion object {
        /** [locale] with its calendar named, so that the names of the months come from that calendar too. */
        fun gregorian(locale: Locale): Locale = try {
            Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("ca", "gregory").build()
        } catch (_: IllformedLocaleException) {
            locale // a locale the builder cannot take is used as it is
        }
    }
}

/** The formatter for what [skeleton] asks for ("EEEdMMM"), in the pattern Android makes of it for [locale]. */
internal fun androidFormat(skeleton: String, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): AndroidPatternFormat =
    AndroidPatternFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale, zone)

/**
 * [pattern12] with a field for the half of the day, if it has none. Android answers a request
 * for one with the language's own pattern, and that need not hold it: for French in Cameroon
 * it is "h:mm", and 8:10 in the morning and in the evening were written alike. The pattern is
 * kept as the language has it and the field put after the time's last figure. A pattern
 * without a 12-hour field is left alone: its hours already tell the two apart.
 */
internal fun withHalfOfDay(pattern12: String): String {
    var quoted = false
    var lastTimeField = -1
    var twelveHour = false
    for ((index, char) in pattern12.withIndex()) {
        when {
            char == PATTERN_QUOTE -> quoted = !quoted
            quoted -> Unit
            char == 'a' -> return pattern12
            char == 'h' || char == 'K' -> { twelveHour = true; lastTimeField = index }
            char == 'm' || char == 's' -> lastTimeField = index
        }
    }
    if (!twelveHour) return pattern12
    return pattern12.substring(0, lastTimeField + 1) + NO_BREAK_SPACE + "a" + pattern12.substring(lastTimeField + 1)
}

/** What a date pattern puts around text that is not a field. */
internal const val PATTERN_QUOTE = '\''

/** Keeps the half of the day on its time's line. Written as a number: it looks like a space. */
private val NO_BREAK_SPACE = Char(0xA0)
