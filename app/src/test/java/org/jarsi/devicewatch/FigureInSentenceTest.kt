package org.jarsi.devicewatch

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.jarsi.devicewatch.data.CameraFacing
import org.jarsi.devicewatch.data.CameraLens
import org.jarsi.devicewatch.data.CameraLogic
import org.jarsi.devicewatch.data.UNAVAILABLE_TEXT
import org.jarsi.devicewatch.data.signalBarsText
import org.jarsi.devicewatch.data.swapText
import org.jarsi.devicewatch.data.volumeFreeText
import org.jarsi.devicewatch.presentation.ClusterRole
import org.jarsi.devicewatch.presentation.cpuRangeText
import org.jarsi.devicewatch.presentation.ui.chargePeakText
import org.jarsi.devicewatch.presentation.ui.cpuLoadText
import org.jarsi.devicewatch.presentation.ui.freeSpaceText
import org.jarsi.devicewatch.presentation.ui.launchCountText
import org.jarsi.devicewatch.system.hotBatteryTitle
import org.jarsi.devicewatch.system.lowStorageText
import org.jarsi.devicewatch.widget.signalDbmText
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * A number and its Latin unit inside an Arabic sentence.
 *
 * A value alone on its line reads by its own first letter, so "1000 mAh" was right. Put
 * after an Arabic word the line reads from the right and the two came apart: "0.5 GB of
 * 1.8 GB" drew the second GB away from its 1.8, and the capacity read "mAh 10000". Every
 * figure that goes into a translated text is one left-to-right piece, and the sentence
 * around it still reads from the right.
 */
@RunWith(RobolectricTestRunner::class)
class FigureInSentenceTest {

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val arabicLocale: Locale = Locale.forLanguageTag("ar-EG-u-nu-latn")

    /** Arabic with the digits the app prints, as [LatinDigits] makes the app's own context. */
    private val arabic: Context
        get() = app.createConfigurationContext(Configuration().apply { setLocale(arabicLocale) })

    private val defaultBefore: Locale = Locale.getDefault()

    @After
    fun restoreDefault() {
        Locale.setDefault(defaultBefore)
    }

    /**
     * Each of [figures] stands whole on the line as a reader sees it, and the line reads from
     * the right: a figure said later stands further left.
     */
    private fun assertWhole(text: String, vararg figures: String) {
        for (seen in listOf(VisualOrder.rightToLeft(text), VisualOrder.byContent(text))) {
            for (figure in figures) assertWithMessage(seen).that(seen).contains(figure)
            assertWithMessage(seen).that(figures.map { seen.indexOf(it) }).isInStrictOrder(reverseOrder<Int>())
        }
    }

    @Test
    fun `left alone the two come apart, which is what the check must see`() {
        val word = arabic.getString(R.string.camera_lens_main)

        assertThat(VisualOrder.rightToLeft("$word 10000 mAh")).startsWith("mAh 10000")
        assertThat(VisualOrder.byContent("0.5 GB $word 1.8 GB")).doesNotContain("1.8 GB")
        assertThat(VisualOrder.byContent("1000 mAh")).isEqualTo("1000 mAh")
    }

    @Test
    fun `swap in use out of all there is`() {
        Locale.setDefault(arabicLocale)
        assertWhole(swapText(arabic, usedGb = 0.5, totalGb = 1.8), "0.5 GB", "1.8 GB")

        Locale.setDefault(Locale.US)
        assertThat(swapText(app, usedGb = 0.5, totalGb = 1.8)).isEqualTo("0.5 GB of 1.8 GB")
    }

    @Test
    fun `free space on a memory card`() {
        Locale.setDefault(arabicLocale)
        assertWhole(volumeFreeText(arabic, freeGb = 12.4, totalGb = 59.5), "12.4 GB", "59.5 GB")

        Locale.setDefault(Locale.US)
        assertThat(volumeFreeText(app, freeGb = 12.4, totalGb = 59.5)).isEqualTo("12.4 GB free of 59.5 GB")
    }

    @Test
    fun `free storage under the storage bar`() {
        Locale.setDefault(arabicLocale)
        assertWhole(freeSpaceText(arabic, freeGb = 4.2), "4 GB")

        Locale.setDefault(Locale.US)
        assertThat(freeSpaceText(app, freeGb = 4.2)).isEqualTo("4 GB free")
    }

    @Test
    fun `a charge's highest temperature`() {
        Locale.setDefault(arabicLocale)
        assertWhole(chargePeakText(arabic, peakDeciC = 415), "41.5 °C")

        Locale.setDefault(Locale.US)
        assertThat(chargePeakText(app, peakDeciC = 415)).isEqualTo("peak 41.5 °C")
    }

    @Test
    fun `the hot battery alert`() {
        Locale.setDefault(arabicLocale)
        assertWhole(hotBatteryTitle(arabic, temperatureDeciC = 453), "45.3 °C")

        Locale.setDefault(Locale.US)
        assertThat(hotBatteryTitle(app, temperatureDeciC = 453)).isEqualTo("The battery is hot: 45.3 °C")
    }

    @Test
    fun `the low storage alert`() {
        Locale.setDefault(arabicLocale)
        assertWhole(lowStorageText(arabic, freeGb = 3.25), "3.3 GB")

        Locale.setDefault(Locale.US)
        assertThat(lowStorageText(app, freeGb = 3.25)).startsWith("3.3 GB free.")
    }

    @Test
    fun `the widget's signal line`() {
        Locale.setDefault(arabicLocale)
        val strength = signalDbmText(arabic, -95)
        assertWhole(arabic.getString(R.string.widget_signal_line, strength, arabic.getString(R.string.signal_strong)), "-95 dBm")

        Locale.setDefault(Locale.US)
        assertThat(signalDbmText(app, -95)).isEqualTo("-95 dBm")
    }

    @Test
    fun `a named lens and what it is`() {
        val lens = CameraLens(CameraFacing.BACK, megapixels = 50, equivalentFocalMm = 25, aperture = 1.9f, ois = true)

        val line = arabic.getString(
            R.string.camera_lens_line,
            arabic.getString(R.string.camera_lens_main),
            CameraLogic.lensText(lens, arabicLocale),
        )
        assertWhole(line, "50 MP · f/1.9 · 25 mm · OIS")

        assertThat(CameraLogic.lensText(lens, Locale.US)).isEqualTo("50 MP · f/1.9 · 25 mm · OIS")
    }

    // A load without a speed has no letter at all: by itself it was laid out from the right.
    @Test
    fun `processor load, with and without a speed`() {
        Locale.setDefault(arabicLocale)
        assertThat(VisualOrder.byContent(cpuLoadText(loadPercent = 5, freqGhz = -1.0))).isEqualTo("5 %")
        assertThat(VisualOrder.byContent(cpuLoadText(loadPercent = 5, freqGhz = 1.8))).isEqualTo("5 % · 1.8 GHz")

        Locale.setDefault(Locale.US)
        assertThat(cpuLoadText(loadPercent = 5, freqGhz = 1.8)).isEqualTo("5 % · 1.8 GHz")
        assertThat(cpuLoadText(loadPercent = 5, freqGhz = -1.0)).isEqualTo("5 %")
        assertThat(cpuLoadText(loadPercent = -1, freqGhz = -1.0)).isEqualTo(UNAVAILABLE_TEXT)
    }

    // A figure with no letter in it ("3 / 4", "1–10×", "12×") has no direction of its own: a
    // line read from the right turned it round. It is one left-to-right piece where it is made.
    @Test
    fun `signal bars, a zoom range and a launch count keep their written order`() {
        assertThat(VisualOrder.byContent("3 / 4")).isEqualTo("4 / 3")
        assertThat(VisualOrder.byContent("1–10×")).isEqualTo("×10–1")

        Locale.setDefault(arabicLocale)
        assertThat(VisualOrder.rightToLeft(signalBarsText(3))).isEqualTo("3 / 4")
        assertThat(VisualOrder.rightToLeft(CameraLogic.zoomText(1f, 10f, arabicLocale)!!)).isEqualTo("1–10×")
        assertThat(VisualOrder.rightToLeft(launchCountText(12))).isEqualTo("12×")

        Locale.setDefault(Locale.US)
        assertThat(signalBarsText(3)).isEqualTo("3 / 4")
        assertThat(CameraLogic.zoomText(1f, 10f, Locale.US)).isEqualTo("1–10×")
        assertThat(launchCountText(12)).isEqualTo("12×")
    }

    // "4–7" names cores four to seven; turned round it named none.
    @Test
    fun `the cores of a group in the Kernel page's title`() {
        val group = arabic.getString(ClusterRole.ONLY.labelRes)

        assertWhole(arabic.getString(R.string.kernel_cluster_title, group, cpuRangeText(listOf(4, 5, 6, 7))), "4–7")
        assertWhole(arabic.getString(R.string.kernel_cluster_title, group, cpuRangeText(listOf(0, 2, 4))), "0, 2, 4")
    }

    @Test
    fun `the data quota in the settings`() {
        assertWhole(arabic.getString(R.string.data_quota_value, "20"), "20 GB")
    }

    // The report's search looks for what is typed, letter for letter, in rows that carry
    // 2026-08-14: the hint has to show a date in that order.
    @Test
    fun `the report's search hints show a date as it has to be typed`() {
        val days = VisualOrder.rightToLeft(arabic.getString(R.string.report_search_hint_days))
        assertThat(days).contains("2026-08-14")
        assertThat(days.split("2026-08").size - 1).isEqualTo(2)
        assertThat(days).doesNotContain("08-2026")

        val log = VisualOrder.rightToLeft(arabic.getString(R.string.report_search_hint_log))
        assertThat(log).contains("2026-08-14")
    }

    // Texts whose unit stands in the translation itself.
    @Test
    fun `the Arabic texts that hold a unit themselves`() {
        assertWhole(arabic.getString(R.string.battery_full_capacity_value, 10000), "10000 mAh")
        assertWhole(arabic.getString(R.string.device_info_ram_estimate, 3), "3 GB")
        assertWhole(arabic.getString(R.string.alert_hot_setting_description), "45 °C")
        assertWhole(arabic.getString(R.string.wifi_capabilities_basic), "6 GHz")
        val stored = Bidi.ltr("1.20 GB", arabicLocale)
        val packed = Bidi.ltr("480 MB", arabicLocale)
        assertWhole(arabic.getString(R.string.kernel_zram_stored_value, stored, packed, "2.5"), "1.20 GB", "480 MB", "2.5×")
    }
}
