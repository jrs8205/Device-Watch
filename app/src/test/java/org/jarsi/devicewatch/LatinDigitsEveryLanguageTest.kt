package org.jarsi.devicewatch

import android.content.Context
import android.icu.util.ULocale
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.jarsi.devicewatch.system.chargeCompletionText
import org.jarsi.devicewatch.system.dream.DreamCalendarEvent
import org.jarsi.devicewatch.system.dream.calendarTimePatterns
import org.jarsi.devicewatch.system.dream.calendarWhenText
import org.jarsi.devicewatch.system.dream.dreamClockFormats
import org.jarsi.devicewatch.system.dream.dreamLocale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale

/**
 * External review round 8: on a phone in Chakma the History page wrote 20:20 in Chakma digits.
 * The rule that gives a language the digits 0 to 9 asked for the zero of the language's number
 * symbols, which is one UTF-16 unit; Chakma's digits take two, so the answer was a plain "0"
 * standing in for a zero that does not fit, and the language was taken for one that writes
 * 0 to 9 already. Adlam is the same, and so was N'Ko in these tests.
 *
 * The rule is asked here about every language Android knows, and so is everything that writes
 * a date or a time with it.
 */
@RunWith(RobolectricTestRunner::class)
class LatinDigitsEveryLanguageTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Friday 20 November 2026 at 20:20:26, in the JVM's own time zone like the formats. */
    private val evening: Date = GregorianCalendar(2026, Calendar.NOVEMBER, 20, 20, 20, 26).time

    private val everyLanguage: List<Locale> get() = ULocale.getAvailableLocales().map { it.toLocale() }

    private fun String.otherDigits(): Boolean = hasOtherDigits()

    private fun point(code: Int): String = codePoint(code)

    @Test
    fun `Chakma, Adlam and N'Ko are given the digits 0 to 9`() {
        for (tag in listOf("ccp", "ccp-BD", "ff-Adlm", "ff-Adlm-GN", "nqo")) {
            val locale = Locale.forLanguageTag(tag)

            assertWithMessage(tag).that(LatinDigits.locale(locale).getUnicodeLocaleType("nu")).isEqualTo("latn")
            assertWithMessage(tag).that(AndroidPatternFormat("HH:mm", locale).format(evening.time)).isEqualTo("20:20")
        }
    }

    @Test
    fun `a language that writes 0 to 9 by itself is left as it is`() {
        for (tag in listOf("en-US", "fi-FI", "zh-CN", "ar-MA", "tok")) {
            val locale = Locale.forLanguageTag(tag)

            assertWithMessage(tag).that(LatinDigits.locale(locale)).isSameInstanceAs(locale)
        }
    }

    @Test
    fun `a numbering system the user chose stands`() {
        val chakmaDigits = Locale.forLanguageTag("ccp-u-nu-cakm")
        val chakmaTwenty = point(0x11138) + point(0x11136)

        assertThat(LatinDigits.locale(chakmaDigits)).isSameInstanceAs(chakmaDigits)
        assertThat(AndroidPatternFormat("HH:mm", chakmaDigits).format(evening.time)).isEqualTo("$chakmaTwenty:$chakmaTwenty")
        assertThat(LatinDigits.text(chakmaTwenty, chakmaDigits)).isEqualTo(chakmaTwenty)
    }

    // Tibetan and Dzongkha name their months by number, in their own digits: the name is a text
    // of the language, which no numbering system rewrites.
    @Test
    fun `a digit inside a language's own name is written as 0 to 9 too`() {
        val tibetan = LatinDigits.locale(Locale.forLanguageTag("bo"))
        val tenthMonth = "ཟླ་" + point(0x0F21) + point(0x0F20)

        assertThat(LatinDigits.text("$tenthMonth 10", tibetan)).isEqualTo("ཟླ་10 10")
        assertThat(LatinDigits.text(point(0x11138) + point(0x11136), Locale.forLanguageTag("ccp"))).isEqualTo("20")
        val plain = "Fri 20 Nov"
        assertThat(LatinDigits.text(plain, Locale.US)).isSameInstanceAs(plain)
    }

    @Test
    fun `in every language Android knows the pages' dates and times hold only the digits 0 to 9`() {
        val wrong = mutableListOf<String>()
        for (locale in everyLanguage) {
            val written = listOf(
                AndroidPatternFormat("HH:mm", locale).format(evening.time),
                androidFormat("EEEdMMM", locale).format(evening.time),
                androidFormat("LLLLyyyy", locale).format(evening.time),
                androidFormat("Hm", locale).format(evening.time),
            )
            if (written.any { it.otherDigits() }) wrong += "${locale.toLanguageTag()}: $written"
        }

        assertThat(everyLanguage.size).isAtLeast(500)
        assertThat(wrong).isEmpty()
    }

    @Test
    fun `in every language Android knows the screensaver's dates and times hold only the digits 0 to 9`() {
        val zone = ZoneId.systemDefault()
        val now = evening.time
        val tomorrow = DreamCalendarEvent("Sauna", beginMs = now + 24 * 3_600_000L, endMs = now + 25 * 3_600_000L, allDay = false)
        val wrong = mutableListOf<String>()
        for (phone in everyLanguage) {
            // The language as the screensaver takes it from the phone's configuration.
            val locale = dreamLocale(android.content.res.Configuration().apply { setLocale(phone) })
            for (is24Hour in listOf(false, true)) {
                val formats = dreamClockFormats(context, locale, is24Hour)
                val texts = formats.texts(evening)
                val written = listOf(
                    texts.time, texts.seconds, texts.amPm, texts.date, texts.shortDate, texts.compactDate,
                    formats.alarm(evening), formats.clock(evening),
                    calendarWhenText(tomorrow, now, zone, locale, calendarTimePatterns(locale, is24Hour), nowWord = "NOW"),
                    chargeCompletionText(context, now + 72 * 3_600_000L, now, locale, is24Hour),
                )
                if (written.any { it.otherDigits() }) wrong += "${phone.toLanguageTag()} (${if (is24Hour) 24 else 12}): $written"
            }
        }

        assertThat(wrong).isEmpty()
    }
}
