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
            return MatrixCursor(columns).apply {
                for (row in rows) addRow(columns.map { row[it] })
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
    fun `the query asks for the next seven days of visible, not-declined instances`() {
        grantPermission()
        val calendar = installCalendar()

        readDreamCalendarEvents(context, now, helsinki)

        val uri = checkNotNull(calendar.lastUri)
        assertThat(uri.authority).isEqualTo(CalendarContract.AUTHORITY)
        assertThat(uri.pathSegments.takeLast(2))
            .containsExactly(now.toString(), (now + 7 * 24 * 3_600_000L).toString()).inOrder()
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
