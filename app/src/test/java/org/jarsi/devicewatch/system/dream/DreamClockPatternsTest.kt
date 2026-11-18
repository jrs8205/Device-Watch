package org.jarsi.devicewatch.system.dream

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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

    @Test
    fun `a 24-hour clock is the same in every language`() {
        for (locale in listOf(Locale.US, Locale.forLanguageTag("zh-CN"), Locale.forLanguageTag("ar-EG-u-nu-latn"))) {
            assertThat(dreamClockFormats(context, locale, is24Hour = true).clock(fridayEvening)).isEqualTo("20:10")
        }
    }
}
