package org.jarsi.devicewatch.presentation

import androidx.annotation.StringRes
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.AccessEvent
import org.jarsi.devicewatch.data.PrivacyLog
import org.jarsi.devicewatch.data.PrivacyLogRead
import org.jarsi.devicewatch.data.PrivacySensor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The latest read of Android's record, and whether the last attempt got nothing. The band and the page share one. */
data class PrivacyState(
    val read: PrivacyLogRead? = null,
    val readFailed: Boolean = false,
)

/** One sensor's line on the Apps tab's band: how many apps used it, and when the last one did. */
data class PrivacyBandLine(val sensor: PrivacySensor, val apps: Int, val lastMillis: Long?)

/** Which day a moment falls on, as the page names it. */
enum class RelativeDay { TODAY, YESTERDAY, EARLIER }

/** A length of time broken into the units the page prints; [underSecond] when there is nothing whole to print. */
data class DurationParts(val hours: Long, val minutes: Long, val seconds: Long) {
    val underSecond: Boolean get() = hours == 0L && minutes == 0L && seconds == 0L
}

/** The pure half of the privacy log's screens: what is said, not how it is drawn. */
object PrivacyPresentation {

    /** The sensors the band has a line for; the rest wait on the page. */
    val BAND_SENSORS: List<PrivacySensor> = listOf(PrivacySensor.CAMERA, PrivacySensor.MICROPHONE, PrivacySensor.LOCATION)

    /** How many of an app's moments the page lists before saying "and N earlier". */
    const val EVENTS_SHOWN = 40

    fun bandLines(log: PrivacyLog): List<PrivacyBandLine> = BAND_SENSORS.map { sensor ->
        val use = log.sensor(sensor)
        PrivacyBandLine(sensor, apps = use.apps.size, lastMillis = use.apps.maxOfOrNull { it.lastMillis ?: Long.MIN_VALUE }?.takeIf { it != Long.MIN_VALUE })
    }

    fun dayOf(timeMillis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(timeMillis).atZone(zone).toLocalDate()

    fun relativeDay(timeMillis: Long, nowMillis: Long, zone: ZoneId): RelativeDay {
        val day = dayOf(timeMillis, zone)
        val today = dayOf(nowMillis, zone)
        return when (day) {
            today -> RelativeDay.TODAY
            today.minusDays(1) -> RelativeDay.YESTERDAY
            else -> RelativeDay.EARLIER
        }
    }

    fun durationParts(millis: Long): DurationParts {
        val seconds = millis / 1_000L
        return DurationParts(hours = seconds / 3_600L, minutes = seconds % 3_600L / 60L, seconds = seconds % 60L)
    }

    /**
     * A length from a minute-kept record ([PrivacyLog.minuteQuantized]) in the whole
     * minutes Android rounded it up to; never under one, since a use that was kept
     * at all lasted something.
     */
    fun aboutMinutes(millis: Long): Long = ((millis + 59_999L) / 60_000L).coerceAtLeast(1L)

    /** The Android user an app's uid belongs to: 0 for the phone's owner, 10 and up for a work profile or another user. */
    fun profileOf(uid: Int): Int = uid / 100_000

    /** The latest [limit] of [events] (which come latest first), and how many earlier ones were left out. */
    fun shownEvents(events: List<AccessEvent>, limit: Int = EVENTS_SHOWN): Pair<List<AccessEvent>, Int> =
        events.take(limit) to (events.size - limit).coerceAtLeast(0)

    /** [events] by the day they fell on, latest day first, each day's events as given (latest first). */
    fun groupByDay(events: List<AccessEvent>, zone: ZoneId): List<Pair<LocalDate, List<AccessEvent>>> =
        events.groupBy { dayOf(it.timeMillis, zone) }.toList().sortedByDescending { it.first }

    @StringRes
    fun sensorNameRes(sensor: PrivacySensor): Int = when (sensor) {
        PrivacySensor.CAMERA -> R.string.privacy_sensor_camera
        PrivacySensor.MICROPHONE -> R.string.privacy_sensor_microphone
        PrivacySensor.LOCATION -> R.string.privacy_sensor_location
        PrivacySensor.OTHER -> R.string.privacy_sensor_other
    }

    /** A plain name for an operation the page lists under Other; null for one it has no words for. */
    @StringRes
    fun opNameRes(op: String): Int? = when (op) {
        "BIND_ACCESSIBILITY_SERVICE" -> R.string.privacy_op_accessibility
        "READ_DEVICE_IDENTIFIERS" -> R.string.privacy_op_device_identifiers
        "SYSTEM_ALERT_WINDOW" -> R.string.privacy_op_overlay
        "BODY_SENSORS" -> R.string.privacy_op_body_sensors
        "READ_CONTACTS", "WRITE_CONTACTS" -> R.string.privacy_op_contacts
        "READ_CALL_LOG", "WRITE_CALL_LOG" -> R.string.privacy_op_call_log
        "READ_SMS", "RECEIVE_SMS", "SEND_SMS" -> R.string.privacy_op_sms
        "READ_CALENDAR", "WRITE_CALENDAR" -> R.string.privacy_op_calendar
        else -> null
    }
}
