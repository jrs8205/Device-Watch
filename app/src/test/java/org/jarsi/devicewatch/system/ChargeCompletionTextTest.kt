package org.jarsi.devicewatch.system

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZonedDateTime
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ChargeCompletionTextTest {
    private val originalZone = TimeZone.getDefault()

    @Before fun setUp() { TimeZone.setDefault(TimeZone.getTimeZone("Europe/Helsinki")) }
    @After fun tearDown() { TimeZone.setDefault(originalZone) }

    private fun text(now: String, full: String, language: String = "fi", is24Hour: Boolean = true): String {
        val locale = Locale.forLanguageTag(language)
        val app: Context = ApplicationProvider.getApplicationContext()
        val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            setLocale(locale)
        })
        return chargeCompletionText(
            context,
            ZonedDateTime.parse(full).toInstant().toEpochMilli(),
            ZonedDateTime.parse(now).toInstant().toEpochMilli(),
            locale,
            is24Hour,
        )
    }

    @Test
    fun `adaptive charging target next morning says tomorrow in Finnish and English`() {
        val now = "2026-09-28T21:25+03:00[Europe/Helsinki]"
        val full = "2026-09-29T09:28+03:00[Europe/Helsinki]"
        assertThat(text(now, full)).isEqualTo("Täynnä, arviolta huomenna klo 09:28")
        assertThat(text(now, full, "en")).isEqualTo("Full, estimated tomorrow around 09:28")
        assertThat(text(now, full, "en-US", false)).isEqualTo("Full, estimated tomorrow around 9:28 AM")
    }

    @Test
    fun `the same estimate stops saying tomorrow once midnight passes`() {
        val full = "2026-09-29T09:28+03:00[Europe/Helsinki]"
        assertThat(text("2026-09-29T00:01+03:00[Europe/Helsinki]", full))
            .isEqualTo("Täynnä, arviolta noin klo 09:28")
    }

    @Test
    fun `tomorrow follows local dates even across the daylight saving change`() {
        // The target is over 24 hours away because this night has an extra hour.
        assertThat(text(
            "2026-10-24T09:00+03:00[Europe/Helsinki]",
            "2026-10-25T09:28+02:00[Europe/Helsinki]",
        )).isEqualTo("Täynnä, arviolta huomenna klo 09:28")
    }

    @Test
    fun `targets beyond tomorrow include a date`() {
        val now = "2026-09-28T21:25+03:00[Europe/Helsinki]"
        val full = "2026-09-30T09:28+03:00[Europe/Helsinki]"
        assertThat(text(now, full, "fi-FI")).contains("30.9.2026")
        assertThat(text(now, full, "en-US")).contains("9/30/26")
    }
}
