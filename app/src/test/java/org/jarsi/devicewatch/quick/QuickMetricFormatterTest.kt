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
import org.jarsi.devicewatch.data.UNAVAILABLE_DOUBLE
import org.jarsi.devicewatch.data.UNAVAILABLE_INT
import org.jarsi.devicewatch.data.UNAVAILABLE_TEXT
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
        val r = read(BATTERY, stats(batteryLevel = 78, batteryStatus = "Ladataan"))
        assertThat(r.value).isEqualTo("78%")
        assertThat(r.detail).isEqualTo("Ladataan")
        assertThat(r.available).isTrue()
    }

    @Test
    fun `battery temperature uses the locale's decimal separator`() {
        assertThat(read(BATTERY_TEMP, stats(batteryTemp = 31.4)).value).isEqualTo("31,4 °C")
        Locale.setDefault(Locale.US)
        assertThat(read(BATTERY_TEMP, stats(batteryTemp = 31.4)).value).isEqualTo("31.4 °C")
    }

    @Test
    fun `cpu temperature says why it is missing`() {
        assertThat(read(CPU_TEMP, stats(cpuTemp = -1.0), extendedAccess = false).unavailableReason)
            .isEqualTo("vaatii laajennetun käytön")
        assertThat(read(CPU_TEMP, stats(cpuTemp = -1.0), extendedAccess = true).unavailableReason)
            .isEqualTo("ei saatavilla")
        assertThat(read(CPU_TEMP, stats(cpuTemp = 47.2), extendedAccess = false).value).isEqualTo("47,2 °C")
    }

    @Test
    fun `data today without usage access asks for it`() {
        val r = read(DATA_TODAY, stats(), dataToday = null)
        assertThat(r.unavailableReason).isEqualTo("vaatii käyttötietojen luvan")
        assertThat(r.value).isEqualTo("—")
    }

    @Test
    fun `data today totals mobile and wifi`() {
        val r = read(DATA_TODAY, stats(), dataToday = DataToday(mobileGb = 0.3, wifiGb = 1.1))
        assertThat(r.value).isEqualTo("1,4 GB")
        assertThat(r.detail).isEqualTo("mob 307 MB · wifi 1,1 GB")
    }

    @Test
    fun `uptime keeps two units`() {
        assertThat(QuickMetricFormatter.uptimeText(((3 * 24 + 4) * 60 + 5) * 60_000L, "pv", "t", "min"))
            .isEqualTo("3 pv 4 t")
        assertThat(QuickMetricFormatter.uptimeText((4 * 60 + 12) * 60_000L, "pv", "t", "min")).isEqualTo("4 t 12 min")
        assertThat(QuickMetricFormatter.uptimeText(59_000L, "pv", "t", "min")).isEqualTo("0 min")
        assertThat(read(UPTIME, stats(uptimeMillis = 0L)).available).isFalse()
    }

    @Test
    fun `cpu load is a percentage`() {
        assertThat(read(CPU_LOAD, stats(cpuLoadPercent = 23)).value).isEqualTo("23%")
    }

    @Test
    fun `memory and storage show used of total`() {
        assertThat(read(MEMORY, stats(usedRamGb = 5.1, totalRamGb = 7.6)).value).isEqualTo("5,1 / 7,6 GB")
        assertThat(read(STORAGE, stats(usedStorageGb = 84.2, totalStorageGb = 128.0)).value).isEqualTo("84 / 128 GB")
        assertThat(read(MEMORY, stats(usedRamGb = -1.0, totalRamGb = 7.6)).available).isFalse()
    }

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

    private fun stats(
        batteryLevel: Int = UNAVAILABLE_INT,
        batteryStatus: String = UNAVAILABLE_TEXT,
        batteryTemp: Double = UNAVAILABLE_DOUBLE,
        cpuTemp: Double = UNAVAILABLE_DOUBLE,
        cpuLoadPercent: Int = UNAVAILABLE_INT,
        usedRamGb: Double = UNAVAILABLE_DOUBLE,
        totalRamGb: Double = UNAVAILABLE_DOUBLE,
        usedStorageGb: Double = UNAVAILABLE_DOUBLE,
        totalStorageGb: Double = UNAVAILABLE_DOUBLE,
        uptimeMillis: Long = 0L,
    ) = SystemStats(
        batteryLevel = batteryLevel,
        batteryStatus = batteryStatus,
        batteryHealth = UNAVAILABLE_TEXT,
        batteryTemp = batteryTemp,
        batteryVoltage = UNAVAILABLE_DOUBLE,
        timeRemainingText = UNAVAILABLE_TEXT,
        batteryCycleCount = UNAVAILABLE_INT,
        batteryCapacityPercent = UNAVAILABLE_INT,
        totalRamGb = totalRamGb,
        usedRamGb = usedRamGb,
        ramPercent = UNAVAILABLE_INT,
        cpuCores = UNAVAILABLE_INT,
        cpuAbi = UNAVAILABLE_TEXT,
        cpuFreqGhz = UNAVAILABLE_DOUBLE,
        cpuLoadPercent = cpuLoadPercent,
        cpuLoadLabel = UNAVAILABLE_TEXT,
        cpuTemp = cpuTemp,
        totalStorageGb = totalStorageGb,
        usedStorageGb = usedStorageGb,
        storagePercent = UNAVAILABLE_INT,
        wifiSsid = UNAVAILABLE_TEXT,
        wifiSsidName = UNAVAILABLE_TEXT,
        wifiBand = UNAVAILABLE_TEXT,
        wifiSpeedDown = UNAVAILABLE_INT,
        wifiSpeedUp = UNAVAILABLE_INT,
        wifiBytesTodayGb = UNAVAILABLE_DOUBLE,
        wifiDataLabel = UNAVAILABLE_TEXT,
        operatorName = UNAVAILABLE_TEXT,
        mobileNetworkType = UNAVAILABLE_TEXT,
        mobileSignalDbm = UNAVAILABLE_INT,
        mobileDataUsedGb = UNAVAILABLE_DOUBLE,
        mobileDataTotalGb = UNAVAILABLE_DOUBLE,
        mobileDataLabel = UNAVAILABLE_TEXT,
        simOperator = UNAVAILABLE_TEXT,
        simState = UNAVAILABLE_TEXT,
        simSlots = UNAVAILABLE_INT,
        dataSimName = UNAVAILABLE_TEXT,
        networkCountry = UNAVAILABLE_TEXT,
        wifiRssiDbm = UNAVAILABLE_INT,
        wifiLinkSpeedMbps = UNAVAILABLE_INT,
        wifiStandard = UNAVAILABLE_TEXT,
        ipAddress = UNAVAILABLE_TEXT,
        uptimeText = UNAVAILABLE_TEXT,
        uptimeMillis = uptimeMillis,
    )
}
