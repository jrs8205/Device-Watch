package org.jarsi.devicewatch

import android.content.Context
import android.content.res.Configuration
import android.icu.util.ULocale
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale

/**
 * A time of day on the app's pages, in the widget and in its lists is written with Android's
 * own format for the phone's language and clock setting. For French in Cameroon on a 12-hour
 * clock that format is "h:mm": a charge that ended at 8:10 in the morning and one that ended
 * at 8:10 in the evening read alike. The screensaver's alarm had the same fault (external
 * review round 6); these are the other places a time is shown.
 */
@RunWith(RobolectricTestRunner::class)
class TimeOfDayTest {

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private val morning: Date = GregorianCalendar(2026, Calendar.NOVEMBER, 13, 8, 10, 26).time
    private val evening: Date = GregorianCalendar(2026, Calendar.NOVEMBER, 13, 20, 10, 26).time

    private fun on(phone: String, clock: String): Context {
        Settings.System.putString(app.contentResolver, Settings.System.TIME_12_24, clock)
        return app.createConfigurationContext(Configuration().apply { setLocale(Locale.forLanguageTag(phone)) })
    }

    private fun String.plainSpaces(): String = replace(Char(0x202F), ' ').replace(Char(0xA0), ' ')

    @Test
    fun `Android's own 12-hour format for French in Cameroon cannot tell morning from evening`() {
        val androids = android.text.format.DateFormat.getTimeFormat(on("fr-CM", "12"))

        assertThat(androids.format(morning)).isEqualTo(androids.format(evening))
    }

    @Test
    fun `the app's time of day tells them apart there`() {
        val format = timeOfDayFormat(on("fr-CM", "12"))

        assertThat(format.format(morning)).isNotEqualTo(format.format(evening))
        assertThat((format as SimpleDateFormat).toPattern().plainSpaces()).isEqualTo("h:mm a")
    }

    @Test
    fun `where Android's format is whole it is used as it is`() {
        for (phone in listOf("en-US", "fi-FI", "zh-CN", "ar-EG", "fr-FR")) {
            for (clock in listOf("12", "24")) {
                val context = on(phone, clock)
                val androids = android.text.format.DateFormat.getTimeFormat(context) as SimpleDateFormat
                val ours = timeOfDayFormat(context) as SimpleDateFormat
                assertWithMessage("$phone $clock").that(ours.toPattern()).isEqualTo(androids.toPattern())
            }
        }
        // A 24-hour clock needs no half of the day, in Cameroon either.
        assertThat((timeOfDayFormat(on("fr-CM", "24")) as SimpleDateFormat).toPattern()).doesNotContain("a")
    }

    // External review round 7: a pattern Android made went to java.time's formatter, which
    // keeps "#" for itself; Android's patterns for toki pona write a number behind that sign.
    @Test
    fun `a pattern Android made is written by Android's own formatter, sign and all`() {
        val tokiPona = Locale.forLanguageTag("tok")

        assertThat(android.text.format.DateFormat.getBestDateTimePattern(tokiPona, "Hm")).isEqualTo("#HH:mm")
        assertThat(androidFormat("Hm", tokiPona).format(evening.time)).isEqualTo("#20:10")
        assertThat(AndroidPatternFormat("#h:mm a", Locale.US).format(evening.time).plainSpaces()).isEqualTo("#8:10 PM")
    }

    // Everything the pages write with a pattern of Android's, written, in every language
    // Android knows: the chart's time axis, a day of the History list, a month of the data table.
    @Test
    fun `in every language Android knows the pages write their dates and times without failing`() {
        val day = LocalDate.of(2026, 11, 13)
        val languages = ULocale.getAvailableLocales().map { it.toLocale() }
        val failed = mutableListOf<String>()

        for (locale in languages) {
            try {
                val written = listOf(
                    androidFormat("Hm", locale).format(evening.time),
                    androidFormat("EEEdMMM", locale).format(day),
                    androidFormat("LLLLyyyy", locale).format(YearMonth.from(day)),
                    AndroidPatternFormat("EEE", locale).format(evening.time),
                )
                if (written.any { it.isBlank() }) failed += "${locale.toLanguageTag()}: an empty text in $written"
            } catch (e: RuntimeException) {
                failed += "${locale.toLanguageTag()}: $e"
            }
        }

        assertThat(languages.size).isAtLeast(500)
        assertThat(failed).isEmpty()
    }

    // The other formatter counted every language's days in the Gregorian calendar and wrote
    // them with the digits 0 to 9. Android's own would follow the language's calendar and
    // digits; the app's dates stay as they were.
    @Test
    fun `the days stay Gregorian and the digits the app's, whatever the language's own are`() {
        val day = LocalDate.of(2026, 11, 13)
        fun month(tag: String) = androidFormat("LLLLyyyy", Locale.forLanguageTag(tag)).format(YearMonth.from(day))
        fun date(tag: String) = androidFormat("EEEdMMM", Locale.forLanguageTag(tag)).format(day)

        assertThat(month("en-US")).isEqualTo("November 2026")
        assertThat(month("fi-FI")).isEqualTo("marraskuu 2026")
        assertThat(date("fi-FI")).isEqualTo("pe 13. marrask.")
        // Thailand counts its years from the Buddha, Iran by the Persian calendar, and Iran
        // and Egypt write other digits.
        assertThat(month("th-TH")).endsWith("2026")
        assertThat(month("fa-IR")).endsWith("2026")
        assertThat(date("fa-IR")).contains("13")
        assertThat(date("ar-EG")).contains("13")
        assertThat(month("ar-SA")).endsWith("2026")
        // A numbering system the user chose in Android's settings stands, as everywhere in the app.
        assertThat(date("ar-EG-u-nu-arab")).doesNotContain("13")
    }

    @Test
    fun `a day is written as that day in the zone it is counted in`() {
        val day = LocalDate.of(2026, 11, 13)
        for (zone in listOf("Pacific/Kiritimati", "Pacific/Pago_Pago", "Europe/Helsinki", "UTC")) {
            val format = AndroidPatternFormat("yyyy-MM-dd", Locale.US, ZoneId.of(zone))
            assertWithMessage(zone).that(format.format(day)).isEqualTo("2026-11-13")
            assertWithMessage(zone).that(format.format(YearMonth.from(day))).isEqualTo("2026-11-01")
        }
    }

    // A pattern of Android's given to java.time's formatter is how the screensaver went down.
    // These four files write patterns of their own (ISO stamps and the like); a new use
    // anywhere else has to be looked at.
    @Test
    fun `no pattern of Android's is given to another formatter`() {
        val users = SourceXml.source("java/org/jarsi/devicewatch").walkTopDown()
            .filter { it.extension == "kt" && "DateTimeFormatter.ofPattern(" in it.readText() }
            .map { it.name }.toList()
        assertThat(users).containsExactly("AppOpsHistoryParser.kt", "SystemStatsRepositoryImpl.kt", "HistoryViewModel.kt", "HtmlReportBuilder.kt")

        val askers = SourceXml.source("java/org/jarsi/devicewatch").walkTopDown()
            .filter { it.extension == "kt" && "getBestDateTimePattern(" in it.readText() }
            .map { it.name }.toList()
        assertThat(askers).containsExactly("TimeOfDay.kt", "DreamUiState.kt")
    }

    // Every time of day goes through the one function: a page that asked Android directly
    // would show the fault again.
    @Test
    fun `no page asks Android for its time format directly`() {
        val askers = SourceXml.source("java/org/jarsi/devicewatch").walkTopDown()
            .filter { it.extension == "kt" && it.name != "TimeOfDay.kt" }
            .filter { "DateFormat.getTimeFormat(" in it.readText() }
            .map { it.name }.toList()

        assertThat(askers).isEmpty()
    }
}
