package org.jarsi.devicewatch.system.dream

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Which calendar events the Gauges style lists and how it words when they are. "Now" is
 * Friday 13 November 2026 at 20:10 in Helsinki unless a test says otherwise.
 */
class DreamCalendarTest {

    private val helsinki = ZoneId.of("Europe/Helsinki")
    private val losAngeles = ZoneId.of("America/Los_Angeles")
    private val finnish = Locale.forLanguageTag("fi-FI")

    private fun at(day: Int, hour: Int, minute: Int = 0, zone: ZoneId = helsinki): Long =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private val now = at(13, 20, 10)

    private fun event(title: String, begin: Long, end: Long = begin + HOUR, allDay: Boolean = false) =
        DreamCalendarEvent(title = title, beginMs = begin, endMs = end, allDay = allDay)

    /** An all-day event as the calendar stores it: from UTC midnight to UTC midnight. */
    private fun storedAllDay(title: String, day: Int, days: Int = 1): DreamCalendarEvent {
        val begin = ZonedDateTime.of(2026, 11, day, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        return DreamCalendarEvent(title, begin, begin + days * DAY, allDay = true)
    }

    /** The same event as the reader hands it on: re-anchored to the phone's own midnights. */
    private fun localAllDay(title: String, day: Int, days: Int = 1, zone: ZoneId = helsinki): DreamCalendarEvent {
        val stored = storedAllDay(title, day, days)
        val (begin, end) = allDayLocal(stored.beginMs, stored.endMs, zone)
        return stored.copy(beginMs = begin, endMs = end)
    }

    private fun whenText(
        event: DreamCalendarEvent,
        nowMs: Long = now,
        zone: ZoneId = helsinki,
        locale: Locale = finnish,
        is24Hour: Boolean = true,
    ) = calendarWhenText(event, nowMs, zone, locale, is24Hour, nowWord = "NYT")

    private fun rows(events: List<DreamCalendarEvent>, showTitles: Boolean = true) = calendarRows(
        events = events,
        nowMs = now,
        zone = helsinki,
        locale = finnish,
        is24Hour = true,
        showTitles = showTitles,
        nowWord = "NYT",
        placeholder = "Tapahtuma",
    )

    @Test
    fun `the next three in time order`() {
        val events = listOf(
            event("Neljäs", at(17, 9)),
            event("Toinen", at(14, 14, 30)),
            event("Ensimmäinen", at(14, 10)),
            event("Kolmas", at(15, 12)),
        )

        assertThat(upcomingEvents(events, now).map { it.title })
            .containsExactly("Ensimmäinen", "Toinen", "Kolmas").inOrder()
    }

    @Test
    fun `an event that has ended is left out`() {
        val events = listOf(
            event("Ohi", at(13, 18), end = at(13, 19)),
            event("Loppui juuri", at(13, 19), end = now),
            event("Tulossa", at(14, 10)),
        )

        assertThat(upcomingEvents(events, now).map { it.title }).containsExactly("Tulossa")
    }

    @Test
    fun `an event running now comes first`() {
        val events = listOf(
            event("Huomenna", at(14, 10)),
            event("Käynnissä", at(13, 19, 30), end = at(13, 21)),
        )

        assertThat(upcomingEvents(events, now).map { it.title })
            .containsExactly("Käynnissä", "Huomenna").inOrder()
    }

    @Test
    fun `an event eight days ahead is left out, one six days ahead is kept`() {
        val events = listOf(
            event("Kahdeksan päivän päästä", at(21, 20, 10)),
            event("Kuuden päivän päästä", at(19, 20, 10)),
        )

        assertThat(upcomingEvents(events, now).map { it.title }).containsExactly("Kuuden päivän päästä")
    }

    @Test
    fun `the horizon is seven days to the minute`() {
        val events = listOf(
            event("Tasan viikon päästä", at(20, 20, 10)),
            event("Minuutin yli", at(20, 20, 11)),
        )

        assertThat(upcomingEvents(events, now).map { it.title }).containsExactly("Tasan viikon päästä")
    }

    @Test
    fun `tomorrow's event reads weekday and time`() {
        assertThat(whenText(event("Hammaslääkäri", at(14, 10)))).isEqualTo("LA 10:00")
    }

    @Test
    fun `today's event reads the time only`() {
        assertThat(whenText(event("Sauna", at(13, 21, 30)))).isEqualTo("21:30")
    }

    @Test
    fun `an event that began earlier today and still runs reads its time`() {
        assertThat(whenText(event("Elokuva", at(13, 19), end = at(13, 21)))).isEqualTo("19:00")
    }

    @Test
    fun `an all-day event reads the weekday only`() {
        assertThat(whenText(localAllDay("Isänpäivälounas", day = 15))).isEqualTo("SU")
        // Today's all-day event too: it reads as today, not as a time.
        assertThat(whenText(localAllDay("Messut", day = 13))).isEqualTo("PE")
    }

    @Test
    fun `an all-day event keeps its local weekday in Helsinki and in Los Angeles`() {
        // The calendar stores Sunday 15 November as UTC midnight to UTC midnight. In Los
        // Angeles that instant is still Saturday afternoon.
        val stored = storedAllDay("Isänpäivä", day = 15)

        for (zone in listOf(helsinki, losAngeles)) {
            val (begin, end) = allDayLocal(stored.beginMs, stored.endMs, zone)
            val local = stored.copy(beginMs = begin, endMs = end)
            val nowThere = at(13, 20, 10, zone)

            assertThat(calendarWhenText(local, nowThere, zone, Locale.US, true, "NOW")).isEqualTo("SUN")
            // It lasts the whole local day, midnight to midnight.
            assertThat(end - begin).isEqualTo(DAY)
            assertThat(begin).isEqualTo(at(15, 0, 0, zone))
        }
    }

    @Test
    fun `a running event that began yesterday reads the now word`() {
        val conference = event("Konferenssi", at(12, 9), end = at(14, 17))
        val trip = localAllDay("Matka", day = 11, days = 5)

        assertThat(whenText(conference)).isEqualTo("NYT")
        assertThat(whenText(trip)).isEqualTo("NYT")
    }

    @Test
    fun `a 12-hour clock`() {
        val dentist = event("Dentist", at(14, 10))
        val tonight = event("Sauna", at(13, 21, 30))

        assertThat(whenText(dentist, locale = Locale.US, is24Hour = false)).isEqualTo("SAT 10:00 AM")
        assertThat(whenText(tonight, locale = Locale.US, is24Hour = false)).isEqualTo("9:30 PM")
        assertThat(whenText(dentist, locale = Locale.US, is24Hour = true)).isEqualTo("SAT 10:00")
    }

    @Test
    fun `the rows carry the time and the title`() {
        val events = listOf(
            event("Auton huolto, Vantaa", at(14, 14, 30)),
            event("Hammaslääkäri", at(14, 10)),
            localAllDay("Isänpäivälounas", day = 15),
        )

        assertThat(rows(events)).containsExactly(
            DreamCalendarRow("LA 10:00", "Hammaslääkäri"),
            DreamCalendarRow("LA 14:30", "Auton huolto, Vantaa"),
            DreamCalendarRow("SU", "Isänpäivälounas"),
        ).inOrder()
    }

    @Test
    fun `hidden titles show the placeholder`() {
        val events = listOf(event("Hammaslääkäri", at(14, 10)))

        assertThat(rows(events, showTitles = false)).containsExactly(DreamCalendarRow("LA 10:00", "Tapahtuma"))
    }

    @Test
    fun `a blank title shows the placeholder`() {
        val events = listOf(event("", at(14, 10)), event("   ", at(14, 12)))

        assertThat(rows(events).map { it.title }).containsExactly("Tapahtuma", "Tapahtuma")
    }

    @Test
    fun `a title's stray spaces and line breaks are tidied`() {
        val events = listOf(event("  Kokous\nneuvotteluhuone 2  ", at(14, 10)))

        assertThat(rows(events).single().title).isEqualTo("Kokous neuvotteluhuone 2")
    }

    @Test
    fun `no events, no rows`() {
        assertThat(rows(emptyList())).isEmpty()
        assertThat(upcomingEvents(emptyList(), now)).isEmpty()
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}
