package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class BatteryHistoryCodecTest {

    private fun sample(millis: Long, level: Int = 42, charging: Boolean = false) =
        BatterySample(timeMillis = millis, level = level, charging = charging)

    @Test
    fun `encode-decode round-trips both charging states`() {
        val discharging = sample(1_783_000_000_000L, level = 42, charging = false)
        val charging = sample(1_783_000_060_000L, level = 43, charging = true)
        assertThat(BatteryHistoryCodec.decodeOrNull(BatteryHistoryCodec.encode(discharging)))
            .isEqualTo(discharging)
        assertThat(BatteryHistoryCodec.decodeOrNull(BatteryHistoryCodec.encode(charging)))
            .isEqualTo(charging)
    }

    @Test
    fun `encode produces one line of four numeric fields`() {
        val line = BatteryHistoryCodec.encode(sample(1_783_000_000_000L, level = 42, charging = true))
        assertThat(line).doesNotContain("\n")
        assertThat(line).doesNotContain("\r")
        assertThat(line.split('\t'))
            .containsExactly("v1", "1783000000000", "42", "1").inOrder()
    }

    @Test
    fun `decode rejects malformed lines`() {
        assertThat(BatteryHistoryCodec.decodeOrNull("")).isNull()
        assertThat(BatteryHistoryCodec.decodeOrNull("garbage")).isNull()
        assertThat(BatteryHistoryCodec.decodeOrNull("v9\t1\t42\t0")).isNull()
        assertThat(BatteryHistoryCodec.decodeOrNull("v1\tNaN\t42\t0")).isNull()
        assertThat(BatteryHistoryCodec.decodeOrNull("v1\t1\tfull\t0")).isNull()
        assertThat(BatteryHistoryCodec.decodeOrNull("v1\t1\t42\t2")).isNull()
        assertThat(BatteryHistoryCodec.decodeOrNull("v1\t1\t42")).isNull()
    }

    @Test
    fun `file names are epoch days and retention keeps fourteen days`() {
        val today = LocalDate.of(2026, 8, 14)
        val name = BatteryHistoryCodec.fileNameFor(today)
        assertThat(name).isEqualTo("${today.toEpochDay()}.log")
        assertThat(BatteryHistoryCodec.isRetainedFileName(name, today)).isTrue()
        assertThat(
            BatteryHistoryCodec.isRetainedFileName(
                BatteryHistoryCodec.fileNameFor(today.minusDays(13)), today
            )
        ).isTrue()
        assertThat(
            BatteryHistoryCodec.isRetainedFileName(
                BatteryHistoryCodec.fileNameFor(today.minusDays(14)), today
            )
        ).isFalse()
        assertThat(BatteryHistoryCodec.isRetainedFileName("junk.log", today)).isFalse()
    }

    @Test
    fun `shouldSample stores the very first sample`() {
        assertThat(BatteryHistoryCodec.shouldSample(null, sample(0L))).isTrue()
    }

    @Test
    fun `shouldSample stores a charging state change immediately`() {
        val previous = sample(0L, charging = false)
        assertThat(BatteryHistoryCodec.shouldSample(previous, sample(1_000L, charging = true)))
            .isTrue()
    }

    @Test
    fun `shouldSample stores again once the sample interval has elapsed`() {
        val previous = sample(0L)
        val interval = BatteryHistoryCodec.MIN_SAMPLE_INTERVAL_MS
        assertThat(BatteryHistoryCodec.shouldSample(previous, sample(interval))).isTrue()
        assertThat(BatteryHistoryCodec.shouldSample(previous, sample(interval - 1))).isFalse()
    }

    @Test
    fun `shouldSample stores a level change after a minute`() {
        val previous = sample(0L, level = 42)
        assertThat(BatteryHistoryCodec.shouldSample(previous, sample(60_000L, level = 41)))
            .isTrue()
        assertThat(BatteryHistoryCodec.shouldSample(previous, sample(59_999L, level = 41)))
            .isFalse()
    }

    @Test
    fun `shouldSample skips an unchanged level before the interval`() {
        val previous = sample(0L, level = 42)
        assertThat(BatteryHistoryCodec.shouldSample(previous, sample(120_000L, level = 42)))
            .isFalse()
    }

    @Test
    fun `shouldSample never stores a negative level`() {
        assertThat(BatteryHistoryCodec.shouldSample(null, sample(0L, level = -1))).isFalse()
        assertThat(
            BatteryHistoryCodec.shouldSample(
                sample(0L, level = 42, charging = false),
                sample(3_600_000L, level = -1, charging = true),
            )
        ).isFalse()
    }
}
