package org.jarsi.devicewatch.presentation.ui

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * Short lengths of time used to be put together in code as "34 min" and "1 min 27 s",
 * whatever the language. They are the language's own texts now.
 */
@RunWith(RobolectricTestRunner::class)
class DurationTextTest {

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * Arabic and nothing more. A text's own number prints with the language's digits here,
     * which tells a text taken from the resources from one put together in code.
     */
    private val arabic: Context
        get() = app.createConfigurationContext(Configuration().apply { setLocale(Locale.forLanguageTag("ar-EG")) })

    @Test
    fun `under an hour a length is the language's minutes text`() {
        assertThat(durationText(arabic, 34 * 60_000L)).isEqualTo(arabic.getString(R.string.duration_minutes, 34))
        assertThat(durationText(app, 34 * 60_000L)).isEqualTo("34 min")
    }

    @Test
    fun `from an hour up a length is hours and minutes as before`() {
        assertThat(durationText(app, 134 * 60_000L)).isEqualTo("2h 14m")
    }

    @Test
    fun `a hold of minutes and seconds is the language's text`() {
        assertThat(holdDurationText(arabic, 87_000L))
            .isEqualTo(arabic.getString(R.string.duration_minutes_seconds, 1, 27))
        assertThat(holdDurationText(app, 87_000L)).isEqualTo("1 min 27 s")
    }

    private val arabicDigits: Locale = Locale.forLanguageTag("ar-EG-u-nu-latn")
    private val chineseLocale: Locale = Locale.forLanguageTag("zh-CN")
    private val finnishLocale: Locale = Locale.forLanguageTag("fi-FI")

    private fun contextIn(locale: Locale): Context =
        app.createConfigurationContext(Configuration().apply { setLocale(locale) })

    /** The text without the invisible marks that keep its direction. */
    private fun String.visible(): String = filterNot { it.code in 0x2066..0x2069 }

    // How long a full battery would last was "7.5 h" in every language, in a sentence whose
    // other length of time was written with the language's own unit.
    @Test
    fun `hours with a decimal carry the language's own unit`() {
        val inArabic = hoursText(arabic, 7.5, arabicDigits)
        assertThat(inArabic.visible()).isEqualTo(arabic.getString(R.string.duration_hours_decimal, "7.5"))
        assertThat(inArabic.visible()).isNotEqualTo("7.5 h")

        val inChinese = hoursText(contextIn(chineseLocale), 7.5, chineseLocale)
        assertThat(inChinese).isEqualTo(contextIn(chineseLocale).getString(R.string.duration_hours_decimal, "7.5"))
        assertThat(inChinese).isNotEqualTo("7.5 h")
    }

    @Test
    fun `hours in English and Finnish read as before`() {
        assertThat(hoursText(app, 7.5, Locale.US)).isEqualTo("7.5 h")
        assertThat(hoursText(app, 13.2, Locale.US)).isEqualTo("13 h")
        assertThat(hoursText(contextIn(finnishLocale), 7.5, finnishLocale)).isEqualTo("7,5 h")
    }

    // The since-charge page's average drain: "2.5 %/h" in every language.
    @Test
    fun `a drain per hour carries the language's own hour`() {
        val inArabic = percentPerHourText(arabic, 2.54, arabicDigits)
        assertThat(inArabic.visible()).isEqualTo(arabic.getString(R.string.since_charge_avg_drain_value, "2.5"))
        assertThat(inArabic).doesNotContain("h")

        val inChinese = percentPerHourText(contextIn(chineseLocale), 2.54, chineseLocale)
        assertThat(inChinese).isEqualTo(contextIn(chineseLocale).getString(R.string.since_charge_avg_drain_value, "2.5"))
        assertThat(inChinese).doesNotContain("h")
    }

    @Test
    fun `a drain per hour in English and Finnish reads as before`() {
        assertThat(percentPerHourText(app, 2.54, Locale.US)).isEqualTo("2.5 %/h")
        assertThat(percentPerHourText(contextIn(finnishLocale), 2.54, finnishLocale)).isEqualTo("2,5 %/h")
    }

    // In an Arabic sentence the length keeps to itself, read from the right like the sentence.
    @Test
    fun `in an Arabic sentence the hours stand as one piece in their own direction`() {
        val inArabic = hoursText(arabic, 7.5, arabicDigits)

        assertThat(inArabic.first().code).isEqualTo(0x2068)
        assertThat(inArabic.last().code).isEqualTo(0x2069)
        assertThat(hoursText(app, 7.5, Locale.US).first()).isEqualTo('7')
    }

    @Test
    fun `a hold of seconds is the language's text`() {
        assertThat(holdDurationText(arabic, 6_000L)).isEqualTo(arabic.getString(R.string.duration_seconds, 6))
        assertThat(holdDurationText(app, 6_000L)).isEqualTo("6 s")
    }
}
