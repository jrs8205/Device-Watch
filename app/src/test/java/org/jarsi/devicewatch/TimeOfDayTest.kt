package org.jarsi.devicewatch

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.text.SimpleDateFormat
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
