package org.jarsi.devicewatch.quick

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.data.DataToday
import org.jarsi.devicewatch.data.QuickMetric
import org.jarsi.devicewatch.data.QuickMetric.BATTERY
import org.jarsi.devicewatch.data.QuickMetric.BATTERY_TEMP
import org.jarsi.devicewatch.data.QuickMetric.CPU_LOAD
import org.jarsi.devicewatch.data.QuickMetric.CPU_TEMP
import org.jarsi.devicewatch.data.QuickMetric.DATA_TODAY
import org.jarsi.devicewatch.data.QuickMetric.MEMORY
import org.jarsi.devicewatch.data.QuickMetric.STORAGE
import org.jarsi.devicewatch.data.QuickMetric.UPTIME
import org.jarsi.devicewatch.data.SystemStats
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale

class QuickMetricFormatterTest {

    private lateinit var savedLocale: Locale

    @Before
    fun setUp() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("fi-FI"))
    }

    @After
    fun tearDown() {
        Locale.setDefault(savedLocale)
    }

    @Test
    fun `battery shows level and status`() {
        val r = read(BATTERY, quickStats(batteryLevel = 78, batteryStatus = "Ladataan"))
        assertThat(r.value).isEqualTo("78%")
        assertThat(r.detail).isEqualTo("Ladataan")
        assertThat(r.available).isTrue()
    }

    @Test
    fun `battery temperature uses the locale's decimal separator`() {
        assertThat(read(BATTERY_TEMP, quickStats(batteryTemp = 31.4)).value).isEqualTo("31,4 °C")
        Locale.setDefault(Locale.US)
        assertThat(read(BATTERY_TEMP, quickStats(batteryTemp = 31.4)).value).isEqualTo("31.4 °C")
    }

    @Test
    fun `cpu temperature says why it is missing`() {
        assertThat(read(CPU_TEMP, quickStats(cpuTemp = -1.0), extendedAccess = false).unavailableReason)
            .isEqualTo("vaatii laajennetun käytön")
        assertThat(read(CPU_TEMP, quickStats(cpuTemp = -1.0), extendedAccess = true).unavailableReason)
            .isEqualTo("ei saatavilla")
        assertThat(read(CPU_TEMP, quickStats(cpuTemp = 47.2), extendedAccess = false).value).isEqualTo("47,2 °C")
    }

    @Test
    fun `data today without usage access asks for it`() {
        val r = read(DATA_TODAY, quickStats(), dataToday = null)
        assertThat(r.unavailableReason).isEqualTo("vaatii käyttötietojen luvan")
        assertThat(r.value).isEqualTo("—")
    }

    @Test
    fun `data today totals mobile and wifi`() {
        val r = read(DATA_TODAY, quickStats(), dataToday = DataToday(mobileGb = 0.3, wifiGb = 1.1))
        assertThat(r.value).isEqualTo("1,4 GB")
        assertThat(r.detail).isEqualTo("mob 307 MB · wifi 1,1 GB")
    }

    @Test
    fun `uptime keeps two units`() {
        assertThat(QuickMetricFormatter.uptimeText(((3 * 24 + 4) * 60 + 5) * 60_000L, "pv", "t", "min"))
            .isEqualTo("3 pv 4 t")
        assertThat(QuickMetricFormatter.uptimeText((4 * 60 + 12) * 60_000L, "pv", "t", "min")).isEqualTo("4 t 12 min")
        assertThat(QuickMetricFormatter.uptimeText(59_000L, "pv", "t", "min")).isEqualTo("0 min")
        assertThat(read(UPTIME, quickStats(uptimeMillis = 0L)).available).isFalse()
    }

    @Test
    fun `cpu load is a percentage`() {
        assertThat(read(CPU_LOAD, quickStats(cpuLoadPercent = 23)).value).isEqualTo("23%")
    }

    @Test
    fun `memory and storage show used of total`() {
        assertThat(read(MEMORY, quickStats(usedRamGb = 5.1, totalRamGb = 7.6)).value).isEqualTo("5,1 / 7,6 GB")
        assertThat(read(STORAGE, quickStats(usedStorageGb = 84.2, totalStorageGb = 128.0)).value).isEqualTo("84 / 128 GB")
        assertThat(read(MEMORY, quickStats(usedRamGb = -1.0, totalRamGb = 7.6)).available).isFalse()
    }

    @Test
    fun `icon text is the figure at its shortest`() {
        assertThat(iconText(BATTERY, quickStats(batteryLevel = 83))).isEqualTo("83%")
        assertThat(iconText(BATTERY, quickStats(batteryLevel = 100))).isEqualTo("100%")
        assertThat(iconText(BATTERY_TEMP, quickStats(batteryTemp = 27.6))).isEqualTo("28°")
        assertThat(iconText(CPU_TEMP, quickStats(cpuTemp = 35.0))).isEqualTo("35°")
        assertThat(iconText(CPU_LOAD, quickStats(cpuLoadPercent = 12))).isEqualTo("12%")
        assertThat(iconText(MEMORY, quickStats(usedRamGb = 5.4, totalRamGb = 7.4))).isEqualTo("73%")
        assertThat(iconText(STORAGE, quickStats(usedStorageGb = 21.0, totalStorageGb = 110.0))).isEqualTo("19%")
    }

    @Test
    fun `icon text for data and uptime keeps one unit`() {
        assertThat(iconText(DATA_TODAY, quickStats(), DataToday(mobileGb = 0.08, wifiGb = 0.103))).isEqualTo("187M")
        assertThat(iconText(DATA_TODAY, quickStats(), DataToday(mobileGb = 0.3, wifiGb = 1.1))).isEqualTo("1,4G")
        assertThat(iconText(DATA_TODAY, quickStats(), DataToday(mobileGb = 2.0, wifiGb = 10.3))).isEqualTo("12G")
        assertThat(iconText(UPTIME, quickStats(uptimeMillis = ((2 * 24 + 19) * 60) * 60_000L))).isEqualTo("2pv")
        assertThat(iconText(UPTIME, quickStats(uptimeMillis = 19 * 60 * 60_000L))).isEqualTo("19t")
        assertThat(iconText(UPTIME, quickStats(uptimeMillis = 45 * 60_000L))).isEqualTo("<1t")
    }

    @Test
    fun `a missing figure has no icon text`() {
        assertThat(iconText(CPU_TEMP, quickStats(cpuTemp = -1.0))).isNull()
        assertThat(iconText(DATA_TODAY, quickStats(), null)).isNull()
        assertThat(iconText(MEMORY, quickStats(usedRamGb = 5.4, totalRamGb = -1.0))).isNull()
        assertThat(iconText(UPTIME, quickStats(uptimeMillis = 0L))).isNull()
    }

    private fun iconText(metric: QuickMetric, stats: SystemStats, dataToday: DataToday? = null) =
        QuickMetricFormatter.iconText(metric, stats, dataToday, labels)

    private fun read(
        metric: QuickMetric,
        stats: SystemStats,
        dataToday: DataToday? = null,
        extendedAccess: Boolean = false,
    ) = QuickMetricFormatter.read(metric, stats, dataToday, extendedAccess, labels)

    private val labels = QuickMetricLabels(
        names = QuickMetric.entries.associateWith { QuickMetricNames(it.name, it.name, it.name) },
        dayUnit = "pv",
        hourUnit = "t",
        minuteUnit = "min",
        mobileShort = "mob",
        wifiShort = "wifi",
        needsExtendedAccess = "vaatii laajennetun käytön",
        needsUsageAccess = "vaatii käyttötietojen luvan",
        notAvailable = "ei saatavilla",
    )
}
