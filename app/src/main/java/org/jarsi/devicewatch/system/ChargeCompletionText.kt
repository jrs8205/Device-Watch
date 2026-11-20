package org.jarsi.devicewatch.system

import android.content.Context
import org.jarsi.devicewatch.LatinDigits
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.system.dream.twelveHourClockPattern
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/** Keep Android's estimate, but make a completion after midnight unambiguous. */
internal fun chargeCompletionText(
    context: Context,
    fullAtMillis: Long,
    nowMillis: Long,
    locale: Locale,
    is24Hour: Boolean,
): String {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val fullDay = Instant.ofEpochMilli(fullAtMillis).atZone(zone).toLocalDate()
    val date = Date(fullAtMillis)
    val time = LatinDigits.text(
        SimpleDateFormat(if (is24Hour) "HH:mm" else twelveHourClockPattern(locale), locale).format(date),
        locale,
    )
    return when (fullDay) {
        today -> context.getString(R.string.dream_full_time_estimate, time)
        today.plusDays(1) -> context.getString(R.string.dream_full_time_tomorrow, time)
        else -> context.getString(
            R.string.dream_full_time_date,
            LatinDigits.text(DateFormat.getDateInstance(DateFormat.SHORT, locale).format(date), locale),
            time,
        )
    }
}
