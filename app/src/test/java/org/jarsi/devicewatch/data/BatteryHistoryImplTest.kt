package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class BatteryHistoryImplTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val today = LocalDate.of(2026, 8, 14)
    private val interval = BatteryHistoryCodec.MIN_SAMPLE_INTERVAL_MS

    private fun sample(millis: Long, level: Int = 50, charging: Boolean = false) =
        BatterySample(timeMillis = millis, level = level, charging = charging)

    private fun history(day: LocalDate = today) = BatteryHistoryImpl(tmp.root) { day }

    @Test
    fun `record then read returns the samples oldest first`() {
        val history = history()
        val first = sample(1_000L, level = 50)
        val second = sample(1_000L + interval, level = 49)
        history.record(first)
        history.record(second)
        assertThat(history.samplesSince(0L)).containsExactly(first, second).inOrder()
    }

    @Test
    fun `samples from several days are returned ascending`() {
        val first = sample(1_000L, level = 60)
        val second = sample(1_000L + 86_400_000L, level = 55)
        history(today.minusDays(1)).record(first)
        val todayHistory = history(today)
        todayHistory.record(second)
        assertThat(todayHistory.samplesSince(0L)).containsExactly(first, second).inOrder()
    }

    @Test
    fun `samplesSince drops samples older than the cutoff`() {
        val history = history()
        val old = sample(1_000L, level = 50)
        val recent = sample(1_000L + interval, level = 49)
        history.record(old)
        history.record(recent)
        assertThat(history.samplesSince(recent.timeMillis)).containsExactly(recent)
    }

    @Test
    fun `record throttles samples that arrive too soon`() {
        val history = history()
        history.record(sample(1_000L, level = 50))
        history.record(sample(2_000L, level = 50))
        assertThat(history.samplesSince(0L)).hasSize(1)
    }

    @Test
    fun `record ignores a negative level`() {
        val history = history()
        history.record(sample(1_000L, level = -1))
        assertThat(history.samplesSince(0L)).isEmpty()
    }

    @Test
    fun `record purges files older than retention`() {
        val old = tmp.newFile(BatteryHistoryCodec.fileNameFor(today.minusDays(20)))
        history().record(sample(1_000L))
        assertThat(old.exists()).isFalse()
    }

    @Test
    fun `read skips corrupted lines`() {
        val stored = sample(5_000L, level = 30, charging = true)
        tmp.newFile(BatteryHistoryCodec.fileNameFor(today))
            .writeText("garbage\n" + BatteryHistoryCodec.encode(stored) + "\n")
        assertThat(history().samplesSince(0L)).containsExactly(stored)
    }

    @Test
    fun `read returns nothing when nothing was recorded`() {
        assertThat(history().samplesSince(0L)).isEmpty()
        val neverWritten = BatteryHistoryImpl(File(tmp.root, "not-created")) { today }
        assertThat(neverWritten.samplesSince(0L)).isEmpty()
    }
}
