package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class TrafficMeterTest {

    private val fi = Locale.forLanguageTag("fi-FI")

    @Test
    fun `the rate is the bytes moved per second between two readings`() {
        val rate = TrafficMeter.rate(
            TrafficCounters(elapsedMillis = 10_000L, rxBytes = 1_000_000L, txBytes = 50_000L),
            TrafficCounters(elapsedMillis = 12_000L, rxBytes = 3_000_000L, txBytes = 60_000L),
        )

        assertThat(rate).isEqualTo(TrafficRate(downBytesPerSecond = 1_000_000L, upBytesPerSecond = 5_000L))
    }

    @Test
    fun `counters that went backwards give no rate`() {
        // The kernel counters restart when an interface comes back; the drop is no traffic.
        val rate = TrafficMeter.rate(
            TrafficCounters(10_000L, rxBytes = 9_000_000L, txBytes = 50_000L),
            TrafficCounters(11_000L, rxBytes = 1_000L, txBytes = 60_000L),
        )

        assertThat(rate).isNull()
    }

    @Test
    fun `readings without time between them give no rate`() {
        assertThat(TrafficMeter.rate(TrafficCounters(5_000L, 1L, 1L), TrafficCounters(5_000L, 9L, 9L))).isNull()
    }

    @Test
    fun `the history keeps the last minute, newest last, and its peaks`() {
        var traffic = LiveTraffic()
        repeat(70) { second ->
            traffic = TrafficMeter.next(traffic, TrafficRate(second * 1_000L, 10L))
        }

        assertThat(traffic.history).hasSize(TrafficMeter.HISTORY_SIZE)
        assertThat(traffic.latest).isEqualTo(TrafficRate(69_000L, 10L))
        assertThat(traffic.history.first().downBytesPerSecond).isEqualTo(10_000L)
        assertThat(traffic.peakDownBytesPerSecond).isEqualTo(69_000L)
        assertThat(traffic.peakUpBytesPerSecond).isEqualTo(10L)
    }

    @Test
    fun `rates read in kilobits below a megabit, in megabits above`() {
        assertThat(TrafficMeter.rateText(0L, fi)).isEqualTo("0 kb/s")
        assertThat(TrafficMeter.rateText(10_625L, fi)).isEqualTo("85 kb/s")
        assertThat(TrafficMeter.rateText(150_000L, fi)).isEqualTo("1,2 Mb/s")
        assertThat(TrafficMeter.rateText(12_500_000L, fi)).isEqualTo("100 Mb/s")
    }
}
