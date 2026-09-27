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

    private val minute = 60_000L
    private val t0 = 1_000_000L

    @Test
    fun `an unplug right after a stored sample still ends the charge, at its own time and level`() {
        // Codex round 8: the charge reminder's 80 % sample is stored, the user unplugs
        // 10 s later; that too-soon flip was dropped and the charge read as running
        // on until the next discharging sample, 20 minutes and two levels later.
        val history = history()
        history.record(sample(t0, level = 20, charging = true))
        history.record(sample(t0 + 60 * minute, level = 80, charging = true))
        history.record(sample(t0 + 60 * minute + 10_000L, level = 80, charging = false))
        history.record(sample(t0 + 80 * minute, level = 78, charging = false))

        val charge = ChargeSessions.from(history.samplesSince(0L), nowMillis = t0 + 81 * minute).single()

        assertThat(charge.endMillis).isEqualTo(t0 + 60 * minute + 10_000L)
        assertThat(charge.endLevel).isEqualTo(80)
    }

    @Test
    fun `a held unplug is read back before a later sample confirms it`() {
        val history = history()
        history.record(sample(t0, level = 80, charging = true))
        history.record(sample(t0 + 10_000L, level = 80, charging = false))

        assertThat(history.samplesSince(0L).last()).isEqualTo(sample(t0 + 10_000L, level = 80, charging = false))
    }

    @Test
    fun `charger bounce still leaves nothing behind`() {
        val history = history()
        val plugged = sample(t0, level = 50, charging = true)
        history.record(plugged)
        history.record(sample(t0 + 5_000L, level = 50, charging = false))
        history.record(sample(t0 + 8_000L, level = 50, charging = true))

        assertThat(history.samplesSince(0L)).containsExactly(plugged)
    }

    @Test
    fun `record throttles samples that arrive too soon`() {
        val history = history()
        val first = sample(1_000L, level = 50)
        history.record(first)
        history.record(sample(2_000L, level = 50))
        assertThat(history.samplesSince(0L)).containsExactly(first)
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
