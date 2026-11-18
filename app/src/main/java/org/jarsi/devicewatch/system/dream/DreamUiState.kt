package org.jarsi.devicewatch.system.dream

import android.content.Context
import android.graphics.drawable.Icon
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.system.NotificationIconGroup
import org.jarsi.devicewatch.system.chargeCompletionText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Everything a screensaver style draws, as ready texts and figures. One collector produces
 * it for the running screensaver; the settings preview uses [sampleDreamUiState].
 */
internal data class DreamUiState(
    /** "20:10", or "8:10" on a 12-hour clock. */
    val timeText: String,
    /** "26", without a colon. */
    val secondsText: String,
    /** "PM"; empty on a 24-hour clock. */
    val amPmText: String,
    /** The long date with a capital first letter: "Friday, November 13". */
    val dateText: String,
    /** "Fri, November 13", as the locale formats it. */
    val shortDateText: String,
    /** "Fri Nov 13", as the locale formats it. */
    val compactDateText: String,
    /** The next alarm as weekday and time, without an icon; empty when there is none. */
    val alarmText: String,
    /** The phone's name; null when its switch is off. */
    val deviceName: String?,
    val batteryLevel: Int,
    val chargeState: DreamChargeState,
    /** "3.82 V" */
    val voltsText: String,
    /** "26.3 °C" */
    val tempText: String,
    /** "11.0 W"; null unless the battery is charging and the reading is plausible. */
    val wattsText: String?,
    /** "Full, estimated around 20:52"; empty unless the battery is charging. */
    val fullTimeText: String,
    /** The estimate's time alone, "20:52"; empty when there is no estimate. */
    val fullClockText: String,
    val notificationGroups: List<NotificationIconGroup>,
    /** Grows by one whenever a notification arrives while the screensaver is up. */
    val notificationPulseToken: Int,
    val clockMillis: Long,
    /** The next calendar events, worded for the screen; empty unless the Gauges style asks. */
    val calendarRows: List<DreamCalendarRow> = emptyList(),
)

/** The clock's lines for one moment. */
internal data class DreamClockTexts(
    val time: String,
    val seconds: String,
    val amPm: String,
    val date: String,
    val shortDate: String,
    val compactDate: String,
)

/**
 * The screensaver's date and time formats for one locale and clock setting, built once
 * rather than on every tick. Not thread-safe: used from the main thread only.
 */
internal class DreamClockFormats(
    private val locale: Locale,
    private val is24Hour: Boolean,
    longDatePattern: String,
    shortDatePattern: String,
    compactDatePattern: String,
    /** The language's own 12-hour patterns; a 24-hour clock is written the same everywhere. */
    clockPattern12: String,
    alarmPattern12: String,
) {
    private val time = SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm", locale)
    private val seconds = SimpleDateFormat("ss", locale)
    private val amPm = SimpleDateFormat("a", locale)
    private val longDate = SimpleDateFormat(longDatePattern, locale)
    private val shortDate = SimpleDateFormat(shortDatePattern, locale)
    private val compactDate = SimpleDateFormat(compactDatePattern, locale)
    private val alarm = SimpleDateFormat(if (is24Hour) "EEE HH:mm" else alarmPattern12, locale)
    private val clock = SimpleDateFormat(if (is24Hour) "HH:mm" else clockPattern12, locale)

    fun texts(now: Date): DreamClockTexts = DreamClockTexts(
        time = time.format(now),
        seconds = seconds.format(now),
        amPm = if (is24Hour) "" else amPm.format(now),
        date = longDate.format(now).replaceFirstChar { it.uppercase(locale) },
        shortDate = shortDate.format(now),
        compactDate = compactDate.format(now),
    )

    /** The next alarm: weekday and time. */
    fun alarm(at: Date): String = alarm.format(at)

    /** A time of day on its own, with AM or PM on a 12-hour clock. */
    fun clock(at: Date): String = clock.format(at)
}

internal fun dreamClockFormats(context: Context, locale: Locale, is24Hour: Boolean) = DreamClockFormats(
    locale = locale,
    is24Hour = is24Hour,
    longDatePattern = context.getString(R.string.dream_date_pattern),
    shortDatePattern = context.getString(R.string.dream_date_pattern_short),
    compactDatePattern = context.getString(R.string.dream_date_pattern_compact),
    clockPattern12 = twelveHourClockPattern(locale),
    alarmPattern12 = twelveHourAlarmPattern(locale),
)

/**
 * A time of day on a 12-hour clock as the language writes it: AM or PM after the time in
 * English ("8:10 PM"), before it in Chinese.
 */
internal fun twelveHourClockPattern(locale: Locale): String =
    android.text.format.DateFormat.getBestDateTimePattern(locale, "hmma")

/** The same with the weekday, for the next alarm. */
internal fun twelveHourAlarmPattern(locale: Locale): String =
    android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEhmma")

internal fun batteryVoltsText(volts: Double, locale: Locale): String =
    String.format(locale, "%.2f V", volts)

internal fun batteryTempText(celsius: Double, locale: Locale): String =
    String.format(locale, "%.1f °C", celsius)

/** Charging power; shown only while the battery is filling and the reading is plausible. */
internal fun batteryWattsText(watts: Double?, state: DreamChargeState, locale: Locale): String? =
    if (state == DreamChargeState.CHARGING && watts != null) String.format(locale, "%.1f W", watts) else null

/** The Glow style's battery details line: volts and temperature, plus watts while charging. */
internal fun glowDetailsText(state: DreamUiState): String =
    listOfNotNull(state.voltsText, state.tempText, state.wattsText).joinToString("  ·  ")

/**
 * A fixed sample for the settings preview: 64 % and charging, with the real date and
 * minute so the thumbnail reads like the screensaver would right now.
 */
internal fun sampleDreamUiState(context: Context, nowMs: Long, locale: Locale, is24Hour: Boolean): DreamUiState {
    val formats = dreamClockFormats(context, locale, is24Hour)
    val clock = formats.texts(Date(nowMs))
    val fullAtMs = nowMs + 42 * 60_000L
    val alarmAt = Calendar.getInstance().apply {
        timeInMillis = nowMs
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 8)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time
    val chargeState = DreamChargeState.CHARGING
    return DreamUiState(
        timeText = clock.time,
        secondsText = "26",
        amPmText = clock.amPm,
        dateText = clock.date,
        shortDateText = clock.shortDate,
        compactDateText = clock.compactDate,
        alarmText = formats.alarm(alarmAt),
        deviceName = "Pixel 8a",
        batteryLevel = 64,
        chargeState = chargeState,
        voltsText = batteryVoltsText(3.82, locale),
        tempText = batteryTempText(26.3, locale),
        wattsText = batteryWattsText(11.0, chargeState, locale),
        fullTimeText = chargeCompletionText(context, fullAtMs, nowMs, locale, is24Hour),
        fullClockText = formats.clock(Date(fullAtMs)),
        notificationGroups = listOf(
            sampleNotificationGroup(context, "sample.first", 1, R.drawable.ic_quick_power),
            sampleNotificationGroup(context, "sample.second", 2, R.drawable.ic_widget_battery),
            sampleNotificationGroup(context, "sample.third", 1, R.drawable.ic_quick_battery_temp),
        ),
        notificationPulseToken = 0,
        clockMillis = nowMs,
    )
}

private fun sampleNotificationGroup(context: Context, name: String, count: Int, iconRes: Int) =
    NotificationIconGroup(
        packageName = name,
        count = count,
        newestPostTime = 0L,
        smallIcon = Icon.createWithResource(context, iconRes),
    )
