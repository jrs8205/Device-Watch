package org.jarsi.devicewatch

import android.content.Context
import android.content.res.Configuration
import android.icu.util.ULocale
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.jarsi.devicewatch.data.ModuleUpdate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale

/**
 * External review round 9: on a phone in Tibetan or Dzongkha the History page's "Collected
 * since" line and the day headings of its notification log named the month in the language's
 * own digits, beside a day and a year written 0 to 9. Those two languages name their months
 * by number, and a name is the language's own text, which no numbering system rewrites. The
 * rows of the same list had been seen to (round 8); the dates written by another formatter had
 * not, on that page or on three others.
 *
 * Every date a page writes in the phone's language is made by the functions asked here, and
 * [DateRouteGuardTest] holds the pages to them.
 */
@RunWith(RobolectricTestRunner::class)
class PageDatesTest {

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private val day: LocalDate = LocalDate.of(2026, 11, 20)

    /** Friday 20 November 2026 at 20:20:26, in the JVM's own time zone like the formats. */
    private val evening: Date = GregorianCalendar(2026, Calendar.NOVEMBER, 20, 20, 20, 26).time

    private val everyLanguage: List<Locale> get() = ULocale.getAvailableLocales().map { it.toLocale() }

    /** A context as the app's own are: the phone's language, with the app's digits. */
    private fun on(phone: Locale, clock: String = "24"): Context {
        Settings.System.putString(app.contentResolver, Settings.System.TIME_12_24, clock)
        return app.createConfigurationContext(Configuration().apply { setLocale(LatinDigits.locale(phone)) })
    }

    private fun String.hasDigitsZeroToNine(): Boolean = any { it in '0'..'9' }

    @Test
    fun `a Tibetan or Dzongkha date names its month with the digits 0 to 9`() {
        for (tag in listOf("bo", "bo-CN", "bo-IN", "dz", "dz-BT")) {
            val phone = Locale.forLanguageTag(tag)
            val written = mapOf(
                "the History page's, an app's and the battery's dates" to LocalizedDateFormat(FormatStyle.MEDIUM, phone).format(day),
                "the Privacy page's day titles" to mediumDateText(on(phone), evening.time),
            )
            for ((where, text) in written) {
                assertWithMessage("$tag, $where: $text").that(text.hasOtherDigits()).isFalse()
                // The eleventh month, the twentieth day, the year: each is there, in the app's digits.
                assertWithMessage("$tag, $where: $text").that(Regex("[0-9]+").findAll(text).map { it.value }.toList())
                    .containsExactly("2026", "11", "20")
            }
        }
    }

    @Test
    fun `in every language Android knows a date or a time on a page holds only the digits 0 to 9`() {
        val november = YearMonth.from(day)
        val wrong = mutableListOf<String>()
        for (phone in everyLanguage) {
            val written = buildList {
                // As the pages hand the language over, and as it is before the app's rule is laid on it.
                for (locale in listOf(LatinDigits.locale(phone), phone).distinct()) {
                    add(LocalizedDateFormat(FormatStyle.MEDIUM, locale).format(day))
                    add(LocalizedDateFormat(FormatStyle.LONG, locale).format(day))
                    add(moduleUpdateText(ModuleUpdate(november, 20), locale))
                    add(moduleUpdateText(ModuleUpdate(november, null), locale))
                }
                for (clock in listOf("12", "24")) {
                    val context = on(phone, clock)
                    add(mediumDateText(context, evening.time))
                    add(shortDateText(context, evening.time))
                    add(timeOfDayFormat(context).format(evening))
                }
            }
            if (written.any { it.hasOtherDigits() || it.isBlank() }) wrong += "${phone.toLanguageTag()}: $written"
        }

        assertThat(everyLanguage.size).isAtLeast(500)
        assertWithMessage(wrong.joinToString("\n", prefix = "\n")).that(wrong).isEmpty()
    }

    // The owner's ruling from the first rounds: a numbering system chosen in Android's settings
    // is not overridden. What each formatter then writes is its own; nothing is rewritten here.
    @Test
    fun `where the user chose a numbering system a date is left as its formatter wrote it`() {
        val tibetanDigits = Locale.forLanguageTag("bo-u-nu-tibt")
        val asWritten = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(tibetanDigits))

        assertThat(asWritten.hasOtherDigits()).isTrue()
        assertThat(LocalizedDateFormat(FormatStyle.MEDIUM, tibetanDigits).format(day)).isEqualTo(asWritten)
        // Android's own formatter writes the chosen digits, and they stay.
        val arabicDigits = Locale.forLanguageTag("ar-EG-u-nu-arab")
        for (text in listOf(mediumDateText(on(arabicDigits), evening.time), shortDateText(on(arabicDigits), evening.time))) {
            assertWithMessage(text).that(text.hasOtherDigits()).isTrue()
            assertWithMessage(text).that(text.hasDigitsZeroToNine()).isFalse()
        }
        // The Latin digits, when they are the ones chosen, are written in a month's name too.
        val latinDigits = Locale.forLanguageTag("bo-u-nu-latn")
        assertThat(LocalizedDateFormat(FormatStyle.MEDIUM, latinDigits).format(day).hasOtherDigits()).isFalse()
    }

    @Test
    fun `a date stays as its language writes it`() {
        fun medium(tag: String) = LocalizedDateFormat(FormatStyle.MEDIUM, Locale.forLanguageTag(tag)).format(day)
        fun long(tag: String) = LocalizedDateFormat(FormatStyle.LONG, Locale.forLanguageTag(tag)).format(day)

        assertThat(medium("en-US")).isEqualTo("Nov 20, 2026")
        assertThat(medium("fi-FI")).isEqualTo("20.11.2026")
        assertThat(medium("zh-CN")).isEqualTo("2026年11月20日")
        assertThat(long("en-US")).isEqualTo("November 20, 2026")
        assertThat(long("fi-FI")).isEqualTo("20. marraskuuta 2026")
        // Egypt writes other digits by itself, and Thailand counts its years from the Buddha:
        // the app's dates are in the Gregorian calendar and the app's digits.
        assertThat(medium("ar-EG")).contains("2026")
        assertThat(medium("th-TH")).contains("2026")
        assertThat(medium("fa-IR")).contains("2026")
    }

    // A Play system update without a day was written "month year" in every language: in
    // Chinese the eleventh month before 2026. It is written as the language writes a month
    // and its year, by the format the History page's months already have.
    @Test
    fun `the month of a Play system update is written as its language writes a month and a year`() {
        val november = ModuleUpdate(YearMonth.of(2026, 11), null)

        assertThat(moduleUpdateText(november, Locale.US)).isEqualTo("November 2026")
        assertThat(moduleUpdateText(november, Locale.forLanguageTag("fi-FI"))).isEqualTo("marraskuu 2026")
        assertThat(moduleUpdateText(november, Locale.forLanguageTag("zh-CN"))).isEqualTo("2026年11月")
        assertThat(moduleUpdateText(november, Locale.forLanguageTag("ar"))).endsWith(" 2026")
        assertThat(moduleUpdateText(ModuleUpdate(YearMonth.of(2026, 11), 20), Locale.US)).isEqualTo("November 20, 2026")
    }
}
