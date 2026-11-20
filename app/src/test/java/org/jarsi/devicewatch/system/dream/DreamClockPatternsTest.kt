package org.jarsi.devicewatch.system.dream

import android.content.Context
import android.icu.util.ULocale
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.system.chargeCompletionText
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale

/**
 * On a 12-hour clock the screensaver wrote every time of day the English way, "8:10 PM".
 * Where AM or PM goes is the language's own matter: Chinese puts it first.
 */
@RunWith(RobolectricTestRunner::class)
class DreamClockPatternsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Friday 13 November 2026 at 20:10:26, in the JVM's own time zone like the formats. */
    private val fridayEvening: Date = GregorianCalendar(2026, Calendar.NOVEMBER, 13, 20, 10, 26).time

    /** Newer Android puts a narrow no-break space before AM and PM; the tests read it as a space. */
    private fun String.plainSpaces(): String = replace(Char(0x202F), ' ').replace(Char(0xA0), ' ')

    @Test
    fun `English keeps its 12-hour times`() {
        val formats = dreamClockFormats(context, Locale.US, is24Hour = false)

        assertThat(formats.clock(fridayEvening).plainSpaces()).isEqualTo("8:10 PM")
        assertThat(formats.alarm(fridayEvening).plainSpaces()).isEqualTo("Fri 8:10 PM")
    }

    @Test
    fun `Chinese puts the half of the day before the time`() {
        val formats = dreamClockFormats(context, Locale.forLanguageTag("zh-CN"), is24Hour = false)

        assertThat(formats.clock(fridayEvening)).isEqualTo("下午8:10")
        assertThat(formats.alarm(fridayEvening)).startsWith("周五")
        assertThat(formats.alarm(fridayEvening)).endsWith("下午8:10")
    }

    // A Gauges calendar row, through the patterns the screensaver itself asks for.
    @Test
    fun `a calendar row writes a 12-hour time the way the clock and the alarm do`() {
        val zone = ZoneId.systemDefault()
        val now = fridayEvening.time
        val tonight = DreamCalendarEvent("Sauna", beginMs = now + 30 * 60_000L, endMs = now + 90 * 60_000L, allDay = false)
        val tomorrow = tonight.copy(beginMs = now + 24 * 3_600_000L, endMs = now + 25 * 3_600_000L)
        fun row(event: DreamCalendarEvent, locale: Locale) =
            calendarWhenText(event, now, zone, locale, calendarTimePatterns(locale, is24Hour = false), nowWord = "NOW")

        val chinese = Locale.forLanguageTag("zh-CN")
        assertThat(row(tonight, chinese)).isEqualTo("下午8:40")
        assertThat(row(tomorrow, chinese)).startsWith("周六")
        assertThat(row(tomorrow, chinese)).endsWith("下午8:10")
        assertThat(row(tonight, Locale.US).plainSpaces()).isEqualTo("8:40 PM")
        assertThat(row(tomorrow, Locale.US).plainSpaces()).isEqualTo("SAT 8:10 PM")
        assertThat(calendarTimePatterns(chinese, is24Hour = true))
            .isEqualTo(CalendarTimePatterns(time = "HH:mm", weekdayTime = "EEE HH:mm"))
    }

    // External review round 6: Android's own 12-hour pattern for French in Cameroon has no
    // field for the half of the day, so an alarm at 8:10 in the morning and one at 8:10 in
    // the evening were shown exactly alike, and so was the hour the charge is full by.
    @Test
    fun `French in Cameroon tells the morning's 8-10 from the evening's`() {
        val cameroon = Locale.forLanguageTag("fr-CM")
        val fridayMorning: Date = GregorianCalendar(2026, Calendar.NOVEMBER, 13, 8, 10, 26).time
        val formats = dreamClockFormats(context, cameroon, is24Hour = false)

        assertThat(formats.alarm(fridayMorning)).isNotEqualTo(formats.alarm(fridayEvening))
        assertThat(formats.clock(fridayMorning)).isNotEqualTo(formats.clock(fridayEvening))
        // The language's own pattern stays as it is; the half of the day follows the time.
        assertThat(twelveHourClockPattern(cameroon).plainSpaces()).isEqualTo("h:mm a")
        assertThat(twelveHourAlarmPattern(cameroon).plainSpaces()).isEqualTo("EEE h:mm a")
        val patterns = calendarTimePatterns(cameroon, is24Hour = false)
        assertThat(patterns.time.plainSpaces()).isEqualTo("h:mm a")
        assertThat(patterns.weekdayTime.plainSpaces()).isEqualTo("EEE h:mm a")
    }

    @Test
    fun `in every language Android knows a 12-hour time says which half of the day it is in`() {
        fun fields(pattern: String) = pattern.split("'").filterIndexed { index, _ -> index % 2 == 0 }.joinToString(" ")
        val languages = ULocale.getAvailableLocales().map { it.toLocale() }
        val without = languages.filter { 'a' !in fields(twelveHourClockPattern(it)) || 'a' !in fields(twelveHourAlarmPattern(it)) }

        assertThat(languages.size).isAtLeast(500)
        assertThat(without.map { it.toLanguageTag() }).isEmpty()
    }

    // External review round 7: Android's 12-hour pattern for toki pona is "#h:mm a", its numbers
    // behind the sign the language writes them with. The calendar row gave that pattern to a
    // formatter that keeps "#" for itself, and the screensaver went down with the exception.
    @Test
    fun `toki pona's calendar row is written, its number behind its sign`() {
        val tokiPona = Locale.forLanguageTag("tok")
        val now = fridayEvening.time
        val tonight = DreamCalendarEvent("Sauna", beginMs = now + 30 * 60_000L, endMs = now + 90 * 60_000L, allDay = false)

        assertThat(twelveHourClockPattern(tokiPona)).startsWith("#h:mm")
        val row = calendarWhenText(tonight, now, ZoneId.systemDefault(), tokiPona, calendarTimePatterns(tokiPona, is24Hour = false), nowWord = "NOW")
        assertThat(row).startsWith("#8:40")
    }

    // Not the patterns looked at for a letter, as round 6's test did, but everything the
    // screensaver writes with them, written: a row for an event today, one for tomorrow and an
    // all-day one, the alarm, a time alone, the clock's lines and the charge estimate.
    @Test
    fun `in every language Android knows the screensaver writes its times without failing`() {
        val zone = ZoneId.systemDefault()
        val now = fridayEvening.time
        val tonight = DreamCalendarEvent("Sauna", beginMs = now + 30 * 60_000L, endMs = now + 90 * 60_000L, allDay = false)
        val tomorrow = tonight.copy(beginMs = now + 24 * 3_600_000L, endMs = now + 25 * 3_600_000L)
        val allDay = tonight.copy(beginMs = now + 48 * 3_600_000L, endMs = now + 72 * 3_600_000L, allDay = true)
        val languages = ULocale.getAvailableLocales().map { it.toLocale() }
        val failed = mutableListOf<String>()

        for (locale in languages) {
            for (is24Hour in listOf(false, true)) {
                try {
                    val patterns = calendarTimePatterns(locale, is24Hour)
                    val written = listOf(tonight, tomorrow, allDay).map { calendarWhenText(it, now, zone, locale, patterns, nowWord = "NOW") } +
                        dreamClockFormats(context, locale, is24Hour).let { listOf(it.alarm(fridayEvening), it.clock(fridayEvening), it.texts(fridayEvening).date) } +
                        chargeCompletionText(context, now + 42 * 60_000L, now, locale, is24Hour)
                    if (written.any { it.isBlank() }) failed += "${locale.toLanguageTag()} (${if (is24Hour) 24 else 12}): an empty text in $written"
                } catch (e: RuntimeException) {
                    failed += "${locale.toLanguageTag()} (${if (is24Hour) 24 else 12}): $e"
                }
            }
        }

        assertThat(languages.size).isAtLeast(500)
        assertThat(failed).isEmpty()
    }

    @Test
    fun `a 24-hour clock is the same in every language`() {
        for (locale in listOf(Locale.US, Locale.forLanguageTag("zh-CN"), Locale.forLanguageTag("ar-EG-u-nu-latn"))) {
            assertThat(dreamClockFormats(context, locale, is24Hour = true).clock(fridayEvening)).isEqualTo("20:10")
        }
    }
}
