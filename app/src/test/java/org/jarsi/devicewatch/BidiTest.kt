package org.jarsi.devicewatch

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.jarsi.devicewatch.data.QuickMetric
import org.jarsi.devicewatch.presentation.capOverMaxText
import org.jarsi.devicewatch.quick.QuickMetricFormatter
import org.jarsi.devicewatch.quick.QuickMetricLabels
import org.jarsi.devicewatch.quick.QuickMetricNames
import org.jarsi.devicewatch.quick.quickStats
import org.jarsi.devicewatch.widget.mobileDataText
import org.junit.After
import org.junit.Test
import java.util.Locale

/**
 * A figure made of numbers and signs ("12 → 15", "5.1 / 7.6 GB", "+12 %") has no direction of
 * its own. Inside a right-to-left text Android would lay its parts out from the right: the
 * later number first, the arrow pointing at the wrong one. Such figures are kept as one
 * left-to-right piece in the languages written from the right, and untouched elsewhere.
 */
class BidiTest {

    private val arabic: Locale = Locale.forLanguageTag("ar-EG-u-nu-latn")
    private val defaultBefore: Locale = Locale.getDefault()

    @After
    fun restoreDefault() {
        Locale.setDefault(defaultBefore)
    }

    private fun isolated(text: String): String = Char(0x2066) + text + Char(0x2069)

    @Test
    fun `the languages written from the right are told from the others`() {
        for (tag in listOf("ar", "ar-EG", "ar-EG-u-nu-latn", "he", "fa-IR", "ur")) {
            assertWithMessage(tag).that(Bidi.isRtl(Locale.forLanguageTag(tag))).isTrue()
        }
        for (tag in listOf("en-US", "fi-FI", "zh-CN", "und")) {
            assertWithMessage(tag).that(Bidi.isRtl(Locale.forLanguageTag(tag))).isFalse()
        }
    }

    @Test
    fun `a figure is wrapped as one left-to-right piece in Arabic`() {
        val wrapped = Bidi.ltr("12 → 15", arabic)

        assertThat(wrapped.first().code).isEqualTo(0x2066)
        assertThat(wrapped.last().code).isEqualTo(0x2069)
        assertThat(wrapped.substring(1, wrapped.length - 1)).isEqualTo("12 → 15")
    }

    @Test
    fun `a figure is left as it is in a language written from the left`() {
        for (locale in listOf(Locale.US, Locale.forLanguageTag("fi-FI"), Locale.forLanguageTag("zh-CN"))) {
            assertThat(Bidi.ltr("12 → 15", locale)).isEqualTo("12 → 15")
        }
    }

    @Test
    fun `without a locale it goes by the app's language`() {
        Locale.setDefault(arabic)
        assertThat(Bidi.ltr("+12 %")).isEqualTo(isolated("+12 %"))

        Locale.setDefault(Locale.US)
        assertThat(Bidi.ltr("+12 %")).isEqualTo("+12 %")
    }

    @Test
    fun `memory and storage on a tile stay left to right in Arabic`() {
        Locale.setDefault(arabic)
        val stats = quickStats(usedRamGb = 5.1, totalRamGb = 7.6, usedStorageGb = 84.2, totalStorageGb = 128.0)

        assertThat(QuickMetricFormatter.read(QuickMetric.MEMORY, stats, null, true, labels).value)
            .isEqualTo(isolated("5.1 / 7.6 GB"))
        assertThat(QuickMetricFormatter.read(QuickMetric.STORAGE, stats, null, true, labels).value)
            .isEqualTo(isolated("84 / 128 GB"))
    }

    @Test
    fun `mobile data used of the plan stays left to right in Arabic`() {
        Locale.setDefault(arabic)

        assertThat(mobileDataText(1.5, 10.0)).isEqualTo(isolated("1.50 GB / 10 GB"))
    }

    @Test
    fun `a limit over the maximum stays left to right in Arabic`() {
        assertThat(capOverMaxText(2_294_000L, 2_914_000L, arabic)).isEqualTo(isolated("2.29 / 2.91 GHz"))
        assertThat(capOverMaxText(820_000L, 2_914_000L, arabic)).isEqualTo(isolated("820 MHz / 2.91 GHz"))
    }

    private val labels = QuickMetricLabels(
        names = QuickMetric.entries.associateWith { QuickMetricNames(it.name, it.name, it.name) },
        dayUnit = "d",
        hourUnit = "h",
        minuteUnit = "min",
        mobileShort = "mob",
        wifiShort = "wifi",
        needsExtendedAccess = "needs extended access",
        needsUsageAccess = "needs usage access",
        notAvailable = "not available",
        charging = "charging",
        discharging = "discharging",
    )
}
