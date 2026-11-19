package org.jarsi.devicewatch.system.dream

import android.content.Context
import android.graphics.drawable.Icon
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.system.NotificationIconGroup
import org.jarsi.devicewatch.system.chargeCompletionText
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar

/** The count on the first notification icon of [worstCaseDreamUiState], to wait for it by. */
internal const val WORST_CASE_FIRST_COUNT = 88

/**
 * The widest state a style can be asked to draw in the context's language: a 12-hour clock
 * with its AM/PM marker, the longest weekday and month the language has, a long device name,
 * three-digit figures, an estimate that needs a date, and eight apps with two-digit counts.
 */
internal fun worstCaseDreamUiState(context: Context, chargeState: DreamChargeState): DreamUiState {
    val locale = context.resources.configuration.locales[0]
    val formats = dreamClockFormats(context, locale, is24Hour = false)
    // Every day of a leap year at 12:59 PM, to find the longest of each line.
    val days: List<Date> = (0 until 366).map { day ->
        GregorianCalendar(2028, Calendar.JANUARY, 1, 12, 59, 59).apply { add(Calendar.DAY_OF_YEAR, day) }.time
    }
    val texts = days.map(formats::texts)
    val now = GregorianCalendar(2028, Calendar.NOVEMBER, 24, 12, 59, 59).time
    val fullAt = GregorianCalendar(2028, Calendar.NOVEMBER, 27, 12, 59, 0).time
    val charging = chargeState == DreamChargeState.CHARGING
    return DreamUiState(
        timeText = "12:59",
        secondsText = "59",
        amPmText = texts.first().amPm,
        amPmLeads = texts.first().amPmLeads,
        dateText = texts.maxBy { it.date.length }.date,
        shortDateText = texts.maxBy { it.shortDate.length }.shortDate,
        compactDateText = texts.maxBy { it.compactDate.length }.compactDate,
        alarmText = days.take(7).map(formats::alarm).maxBy { it.length },
        deviceName = "Samsung Galaxy S24 Ultra",
        batteryLevel = 100,
        chargeState = chargeState,
        voltsText = batteryVoltsText(4.45, locale),
        tempText = batteryTempText(-10.5, locale),
        wattsText = batteryWattsText(120.0, chargeState, locale),
        fullTimeText = if (charging) {
            chargeCompletionText(context, fullAt.time, now.time, locale, is24Hour = false)
        } else {
            ""
        },
        fullClockText = if (charging) formats.clock(fullAt) else "",
        notificationGroups = (0 until 8).map { index ->
            NotificationIconGroup(
                packageName = "worst.case.$index",
                count = WORST_CASE_FIRST_COUNT - index,
                newestPostTime = (8 - index).toLong(),
                smallIcon = Icon.createWithResource(context, R.drawable.ic_quick_power),
            )
        },
        notificationPulseToken = 0,
        clockMillis = now.time,
        // Three events, each with the longest time a row can carry and a title far too
        // long for any row: the title is the one text that may be cut, with an ellipsis.
        calendarRows = List(3) { index ->
            DreamCalendarRow(
                whenText = days.take(7).map(formats::alarm).maxBy { it.length }.uppercase(locale),
                title = "Event ${index + 1} with a title that goes on and on, well past the row's end",
            )
        },
    )
}

/** A small state for tests of pure logic; every argument has an everyday default. */
internal fun plainDreamUiState(
    chargeState: DreamChargeState = DreamChargeState.CHARGING,
    batteryLevel: Int = 64,
    wattsText: String? = "11,0 W",
    alarmText: String = "la 08:00",
    fullClockText: String = "20:52",
    notificationGroups: List<NotificationIconGroup> = emptyList(),
    calendarRows: List<DreamCalendarRow> = emptyList(),
): DreamUiState = DreamUiState(
    timeText = "20:10",
    secondsText = "26",
    amPmText = "",
    amPmLeads = false,
    dateText = "Perjantaina 13. marraskuuta",
    shortDateText = "pe 13. marraskuuta",
    compactDateText = "pe 13.11.",
    alarmText = alarmText,
    deviceName = "Pixel 8a",
    batteryLevel = batteryLevel,
    chargeState = chargeState,
    voltsText = "3,82 V",
    tempText = "26,3 °C",
    wattsText = wattsText,
    fullTimeText = if (chargeState == DreamChargeState.CHARGING) "Täynnä, arviolta noin klo $fullClockText" else "",
    fullClockText = fullClockText,
    notificationGroups = notificationGroups,
    notificationPulseToken = 0,
    clockMillis = 0L,
    calendarRows = calendarRows,
)
