package org.jarsi.devicewatch.system.dream

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Reading the calendar for the Gauges style. The permission can be missing (a setting
 * restored on a new phone) or taken away at any time, and the calendar provider is another
 * app: whatever goes wrong, the screensaver gets no events and keeps running.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DreamCalendarReaderTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val helsinki = ZoneId.of("Europe/Helsinki")

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, helsinki).toInstant().toEpochMilli()

    private fun utcMidnight(day: Int): Long =
        ZonedDateTime.of(2026, 11, day, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    private val now = at(13, 20, 10)

    /** Stands in for the calendar app's provider and remembers what it was asked. */
    class FakeCalendar : ContentProvider() {
        var queries = 0
        var lastUri: Uri? = null
        var lastSelection: String? = null
        var lastSortOrder: String? = null
        var failure: RuntimeException? = null
        var rows: List<Map<String, Any?>> = emptyList()

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            queries++
            lastUri = uri
            lastSelection = selection
            lastSortOrder = sortOrder
            failure?.let { throw it }
            val columns = projection ?: emptyArray()
            // As the calendar's own provider does it: by the stored instants, whatever the
            // time zone, also for an all-day event's UTC midnights.
            val (rangeBegin, rangeEnd) = uri.pathSegments.takeLast(2).map { it.toLong() }
            return MatrixCursor(columns).apply {
                for (row in rows) {
                    val begin = row[CalendarContract.Instances.BEGIN] as Long
                    val end = row[CalendarContract.Instances.END] as Long
                    if (begin <= rangeEnd && end >= rangeBegin) addRow(columns.map { row[it] })
                }
            }
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    private fun installCalendar(): FakeCalendar =
        Robolectric.buildContentProvider(FakeCalendar::class.java).create(CalendarContract.AUTHORITY).get()

    private fun grantPermission() = shadowOf(context).grantPermissions(Manifest.permission.READ_CALENDAR)

    private fun row(title: String?, begin: Long, end: Long, allDay: Boolean) = mapOf(
        CalendarContract.Instances.TITLE to title,
        CalendarContract.Instances.BEGIN to begin,
        CalendarContract.Instances.END to end,
        CalendarContract.Instances.ALL_DAY to (if (allDay) 1 else 0),
    )

    @Test
    fun `without the permission nothing is read and nothing is asked`() {
        val calendar = installCalendar()
        calendar.rows = listOf(row("Hammaslääkäri", at(14, 10), at(14, 11), allDay = false))

        assertThat(readDreamCalendarEvents(context, now, helsinki)).isEmpty()
        assertThat(calendar.queries).isEqualTo(0)
    }

    @Test
    fun `a provider that throws gives no events`() {
        grantPermission()
        val calendar = installCalendar()

        calendar.failure = SecurityException("permission revoked")
        assertThat(readDreamCalendarEvents(context, now, helsinki)).isEmpty()

        calendar.failure = IllegalArgumentException("unknown URL")
        assertThat(readDreamCalendarEvents(context, now, helsinki)).isEmpty()

        assertThat(calendar.queries).isEqualTo(2)
    }

    @Test
    fun `a phone without a calendar provider gives no events`() {
        grantPermission()

        assertThat(readDreamCalendarEvents(context, now, helsinki)).isEmpty()
    }

    @Test
    fun `the instances come back as events, the all-day one in the phone's own time`() {
        grantPermission()
        val calendar = installCalendar()
        calendar.rows = listOf(
            row("Elokuva", at(13, 19), at(13, 21), allDay = false),
            row("Hammaslääkäri", at(14, 10), at(14, 11), allDay = false),
            row("Isänpäivälounas", utcMidnight(15), utcMidnight(16), allDay = true),
        )

        val events = readDreamCalendarEvents(context, now, helsinki)

        assertThat(events).containsExactly(
            DreamCalendarEvent("Elokuva", at(13, 19), at(13, 21), allDay = false),
            DreamCalendarEvent("Hammaslääkäri", at(14, 10), at(14, 11), allDay = false),
            // Stored from UTC midnight to UTC midnight; handed on from local midnight to local midnight.
            DreamCalendarEvent("Isänpäivälounas", at(15, 0), at(16, 0), allDay = true),
        ).inOrder()
    }

    @Test
    fun `an event without a title comes back with an empty one`() {
        grantPermission()
        val calendar = installCalendar()
        calendar.rows = listOf(row(null, at(14, 10), at(14, 11), allDay = false))

        assertThat(readDreamCalendarEvents(context, now, helsinki).single().title).isEmpty()
    }

    @Test
    fun `an all-day event still running west of Greenwich is read`() {
        // Los Angeles, the evening of the 14th. The day's all-day event is stored as ending
        // at UTC midnight, four hours ago there, but the day itself still runs.
        grantPermission()
        val calendar = installCalendar()
        val losAngeles = ZoneId.of("America/Los_Angeles")
        fun local(day: Int, hour: Int): Long =
            ZonedDateTime.of(2026, 11, day, hour, 0, 0, 0, losAngeles).toInstant().toEpochMilli()
        calendar.rows = listOf(row("Birthday", utcMidnight(14), utcMidnight(15), allDay = true))

        val events = readDreamCalendarEvents(context, local(14, 20), losAngeles)

        assertThat(events).containsExactly(
            DreamCalendarEvent("Birthday", local(14, 0), local(15, 0), allDay = true)
        )
    }

    @Test
    fun `an all-day event that begins within the week east of Greenwich is read`() {
        // Helsinki, an hour past midnight. The all-day event a week on begins, by the
        // phone's own time, an hour before the week is out; its stored beginning, UTC
        // midnight, is an hour after it.
        grantPermission()
        val calendar = installCalendar()
        calendar.rows = listOf(row("Joulumarkkinat", utcMidnight(20), utcMidnight(21), allDay = true))

        val events = readDreamCalendarEvents(context, at(13, 1), helsinki)

        assertThat(events).containsExactly(
            DreamCalendarEvent("Joulumarkkinat", at(20, 0), at(21, 0), allDay = true)
        )
    }

    @Test
    fun `what has ended or lies beyond the week is left out`() {
        // The calendar is asked a day wider at both ends than the week, for the all-day
        // events' sake; what that brings along and does not belong is dropped here.
        grantPermission()
        val calendar = installCalendar()
        calendar.rows = listOf(
            // Yesterday's all-day event: stored as ending at 02:00 Helsinki time today.
            row("Eilinen", utcMidnight(12), utcMidnight(13), allDay = true),
            row("Päättynyt", at(12, 22), at(12, 23), allDay = false),
            row("Käynnissä", at(12, 23), at(13, 2), allDay = false),
            row("Liian kaukana", at(20, 10), at(20, 11), allDay = false),
        )

        val events = readDreamCalendarEvents(context, at(13, 1), helsinki)

        assertThat(events).containsExactly(
            DreamCalendarEvent("Käynnissä", at(12, 23), at(13, 2), allDay = false)
        )
    }

    @Test
    fun `the query asks a day wider than the week, for visible, not-declined instances`() {
        grantPermission()
        val calendar = installCalendar()

        readDreamCalendarEvents(context, now, helsinki)

        val day = 24 * 3_600_000L
        val uri = checkNotNull(calendar.lastUri)
        assertThat(uri.authority).isEqualTo(CalendarContract.AUTHORITY)
        assertThat(uri.pathSegments.takeLast(2))
            .containsExactly((now - day).toString(), (now + 8 * day).toString()).inOrder()
        val selection = checkNotNull(calendar.lastSelection)
        assertThat(selection).contains(CalendarContract.Instances.VISIBLE)
        assertThat(selection).contains(CalendarContract.Instances.SELF_ATTENDEE_STATUS)
        assertThat(selection).contains(CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED.toString())
        assertThat(calendar.lastSortOrder).contains(CalendarContract.Instances.BEGIN)
    }

    @Test
    fun `a very full calendar is not read to its end`() {
        grantPermission()
        val calendar = installCalendar()
        calendar.rows = (0 until 500).map { index ->
            row("Tapahtuma $index", at(14, 10) + index * 60_000L, at(14, 11) + index * 60_000L, allDay = false)
        }

        assertThat(readDreamCalendarEvents(context, now, helsinki).size).isAtMost(50)
    }
}
