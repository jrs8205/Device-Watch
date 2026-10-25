package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class QuickMetricTest {

    @Test
    fun `stored selection keeps known names and skips unknown ones`() {
        assertThat(quickMetricsOf("BATTERY,FOO,CPU_LOAD")).containsExactly(QuickMetric.BATTERY, QuickMetric.CPU_LOAD)
        assertThat(quickMetricsOf(null)).isEmpty()
        assertThat(quickMetricsOf("")).isEmpty()
    }

    @Test
    fun `selection is stored in list order`() {
        assertThat(quickMetricsToStored(setOf(QuickMetric.STORAGE, QuickMetric.BATTERY))).isEqualTo("BATTERY,STORAGE")
    }

    @Test
    fun `single widget without a stored metric shows the battery`() {
        assertThat(singleMetricOf(null)).isEqualTo(QuickMetric.BATTERY)
        assertThat(singleMetricOf("GONE")).isEqualTo(QuickMetric.BATTERY)
        assertThat(singleMetricOf("MEMORY")).isEqualTo(QuickMetric.MEMORY)
    }

    @Test
    fun `data today needs both figures`() {
        assertThat(dataTodayOf(DataUsageSince(wifiGb = 1.1, mobileGb = 0.3)))
            .isEqualTo(DataToday(mobileGb = 0.3, wifiGb = 1.1))
        assertThat(dataTodayOf(DataUsageSince(wifiGb = -1.0, mobileGb = 0.3))).isNull()
        assertThat(dataTodayOf(DataUsageSince(wifiGb = 1.1, mobileGb = -1.0))).isNull()
    }

    @Test
    fun `today starts at local midnight`() {
        val zone = ZoneId.of("Europe/Helsinki")
        assertThat(startOfTodayMillis(LocalDate.of(2026, 10, 25), zone))
            .isEqualTo(ZonedDateTime.of(2026, 10, 25, 0, 0, 0, 0, zone).toInstant().toEpochMilli())
    }
}
