package org.jarsi.devicewatch.system

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.data.QuickMetric
import org.jarsi.devicewatch.data.QuickMetric.BATTERY
import org.jarsi.devicewatch.data.QuickMetric.BATTERY_TEMP
import org.jarsi.devicewatch.data.QuickMetric.CPU_LOAD
import org.jarsi.devicewatch.data.QuickMetric.CPU_TEMP
import org.jarsi.devicewatch.data.QuickMetric.DATA_TODAY
import org.jarsi.devicewatch.data.QuickMetric.MEMORY
import org.jarsi.devicewatch.data.UNAVAILABLE_TEXT
import org.jarsi.devicewatch.quick.QuickMetricLabels
import org.jarsi.devicewatch.quick.QuickMetricNames
import org.jarsi.devicewatch.quick.QuickMetricReading
import org.junit.Test

class LiveNotificationTest {

    @Test
    fun `three figures on the first line and the rest on the second`() {
        val t = liveNotificationText(
            readings(
                BATTERY to "78%", BATTERY_TEMP to "31,4 °C", CPU_LOAD to "23%",
                MEMORY to "5,1 / 7,6 GB", DATA_TODAY to "1,4 GB",
            ),
            labels,
        )!!
        assertThat(t.title).isEqualTo("Akku 78% · Akku 31,4 °C · CPU 23%")
        assertThat(t.text).isEqualTo("RAM 5,1 / 7,6 GB · Data 1,4 GB")
        assertThat(t.bigText).isEqualTo("Akku 78% · Akku 31,4 °C · CPU 23%\nRAM 5,1 / 7,6 GB · Data 1,4 GB")
    }

    @Test
    fun `a missing figure is left out and the next moves up`() {
        val t = liveNotificationText(
            readings(BATTERY to "78%", CPU_TEMP to null, CPU_LOAD to "23%", MEMORY to "5,1 / 7,6 GB"),
            labels,
        )!!
        assertThat(t.title).isEqualTo("Akku 78% · CPU 23% · RAM 5,1 / 7,6 GB")
        assertThat(t.text).isNull()
        assertThat(t.bigText).isEqualTo("Akku 78% · CPU 23% · RAM 5,1 / 7,6 GB")
    }

    @Test
    fun `nothing readable gives no live text`() {
        assertThat(liveNotificationText(readings(CPU_TEMP to null), labels)).isNull()
        assertThat(liveNotificationText(emptyList(), labels)).isNull()
    }

    @Test
    fun `the gate posts new text once`() {
        val g = LiveNotificationGate()
        assertThat(g.shouldPost("a")).isTrue()
        assertThat(g.shouldPost("a")).isFalse()
        assertThat(g.shouldPost("b")).isTrue()
    }

    @Test
    fun `a swipe holds the notification until the screen comes on`() {
        val g = LiveNotificationGate()
        g.shouldPost("a")
        g.onDismissed()
        assertThat(g.shouldPost("b")).isFalse()
        g.onScreenOn()
        assertThat(g.shouldPost("b")).isTrue()
    }

    @Test
    fun `a settings change posts at once even after a swipe and with the same text`() {
        val g = LiveNotificationGate()
        g.shouldPost("a")
        g.onDismissed()
        g.onSettingsChanged()
        assertThat(g.shouldPost("a")).isTrue()
    }

    @Test
    fun `screen on reposts the same text`() {
        val g = LiveNotificationGate()
        g.shouldPost("a")
        g.onScreenOn()
        assertThat(g.shouldPost("a")).isTrue()
        assertThat(g.shouldPost("a")).isFalse()
    }

    private fun readings(vararg figures: Pair<QuickMetric, String?>) = figures.map { (metric, value) ->
        if (value == null) QuickMetricReading(metric, UNAVAILABLE_TEXT, null, "ei saatavilla")
        else QuickMetricReading(metric, value, null, null)
    }

    private val labels = QuickMetricLabels(
        names = mapOf(
            BATTERY to "Akku", BATTERY_TEMP to "Akku", CPU_TEMP to "CPU", DATA_TODAY to "Data",
            QuickMetric.UPTIME to "Päällä", CPU_LOAD to "CPU", MEMORY to "RAM", QuickMetric.STORAGE to "Tila",
        ).mapValues { (metric, short) -> QuickMetricNames(metric.name, metric.name, short) },
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
