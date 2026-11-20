package org.jarsi.devicewatch

import android.content.Context
import java.text.DateFormat
import java.text.SimpleDateFormat

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
