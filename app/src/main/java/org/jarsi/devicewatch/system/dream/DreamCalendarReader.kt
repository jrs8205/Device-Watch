package org.jarsi.devicewatch.system.dream

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.ZoneId

/** How often the running screensaver reads the calendar again. */
internal const val CALENDAR_REFRESH_MS = 600_000L

/** A calendar can hold any number of events in a week; a handful is all a row list needs. */
private const val CALENDAR_MAX_ROWS_READ = 50

private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * The calendar's events that run at [nowMs] or begin within seven days of it, in the
 * calendar's order, for the Gauges style: only from calendars the user keeps visible, and
 * without invitations they have declined. Read-only, and nothing is kept: the caller holds the list while the
 * screensaver is up and drops it with it.
 *
 * Returns nothing when the permission is missing, and then does not touch the calendar at
 * all: the switch may have come from a backup onto a phone where the permission was never
 * granted, and it can be taken away at any time. The provider is another app, so whatever
 * it throws also means no events rather than a stopped screensaver.
 */
internal fun readDreamCalendarEvents(context: Context, nowMs: Long, zone: ZoneId): List<DreamCalendarEvent> {
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED
    if (!granted) return emptyList()

    // The calendar picks instances by their stored instants, and an all-day event is stored
    // from UTC midnight to UTC midnight: west of Greenwich the day still runs hours after
    // its stored end, and east of it the day begins hours before its stored beginning. So
    // the calendar is asked a day wider at both ends, more than any time zone differs from
    // UTC, and what does not belong is dropped below, once the times are the phone's own.
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { builder ->
        ContentUris.appendId(builder, nowMs - DAY_MS)
        ContentUris.appendId(builder, nowMs + (CALENDAR_HORIZON_DAYS + 1) * DAY_MS)
    }.build()
    val projection = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
    )
    val selection = "${CalendarContract.Instances.VISIBLE} = 1 AND " +
        "(${CalendarContract.Instances.SELF_ATTENDEE_STATUS} IS NULL OR " +
        "${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED})"
    val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"

    return try {
        context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
            buildList {
                while (size < CALENDAR_MAX_ROWS_READ && cursor.moveToNext()) {
                    val title = cursor.getString(0).orEmpty()
                    val begin = cursor.getLong(1)
                    val end = cursor.getLong(2)
                    val allDay = cursor.getInt(3) != 0
                    val event = if (allDay) {
                        // Stored from UTC midnight to UTC midnight: see allDayLocal.
                        val (localBegin, localEnd) = allDayLocal(begin, end, zone)
                        DreamCalendarEvent(title, localBegin, localEnd, allDay = true)
                    } else {
                        DreamCalendarEvent(title, begin, end, allDay = false)
                    }
                    if (event.isUpcoming(nowMs)) add(event)
                }
            }
        } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}
