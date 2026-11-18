package org.jarsi.devicewatch.widget

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar

/**
 * The widget's "updated at" time was always written the Finnish way, "14.05". It follows the
 * phone now: its language's separator and its 12- or 24-hour clock.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetClockTextTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val afternoon: Date = GregorianCalendar(2026, Calendar.NOVEMBER, 18, 14, 5).time

    private fun phoneClock(hours: String) {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, hours)
    }

    @Test
    fun `a 24-hour phone in English reads the time with a colon`() {
        phoneClock("24")

        assertThat(widgetClockText(context, afternoon)).isEqualTo("14:05")
    }

    @Test
    fun `a 12-hour phone reads it with AM or PM`() {
        phoneClock("12")

        val text = widgetClockText(context, afternoon)

        assertThat(text).startsWith("2:05")
        assertThat(text).endsWith("PM")
    }
}
