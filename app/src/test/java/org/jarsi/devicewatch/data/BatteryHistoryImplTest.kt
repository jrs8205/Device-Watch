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

    /** The monotonic clock the store reads; [put] moves it along with the sample time. */
    private var elapsed = 0L

    private fun history(day: LocalDate = today) = BatteryHistoryImpl(tmp.root, { day }, { elapsed })

    /** Records [sample] with the monotonic clock at [elapsedAt], by default in step with the wall clock. */
    private fun BatteryHistoryImpl.put(sample: BatterySample, elapsedAt: Long = sample.timeMillis) {
        elapsed = elapsedAt
        record(sample)
    }

    @Test
    fun `record then read returns the samples oldest first`() {
        val history = history()
        val first = sample(1_000L, level = 50)
        val second = sample(1_000L + interval, level = 49)
        history.put(first)
        history.put(second)
        assertThat(history.samplesSince(0L)).containsExactly(first, second).inOrder()
    }

    @Test
    fun `samples from several days are returned ascending`() {
        val first = sample(1_000L, level = 60)
        val second = sample(1_000L + 86_400_000L, level = 55)
        history(today.minusDays(1)).put(first)
        val todayHistory = history(today)
        todayHistory.put(second)
        assertThat(todayHistory.samplesSince(0L)).containsExactly(first, second).inOrder()
    }

    @Test
    fun `samplesSince drops samples older than the cutoff`() {
        val history = history()
        val old = sample(1_000L, level = 50)
        val recent = sample(1_000L + interval, level = 49)
        history.put(old)
        history.put(recent)
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
        history.put(sample(t0, level = 20, charging = true))
        history.put(sample(t0 + 60 * minute, level = 80, charging = true))
        history.put(sample(t0 + 60 * minute + 10_000L, level = 80, charging = false))
        history.put(sample(t0 + 80 * minute, level = 78, charging = false))

        val charge = ChargeSessions.from(history.samplesSince(0L), nowMillis = t0 + 81 * minute).single()

        assertThat(charge.endMillis).isEqualTo(t0 + 60 * minute + 10_000L)
        assertThat(charge.endLevel).isEqualTo(80)
    }

    @Test
    fun `a held unplug is read back before a later sample confirms it`() {
        val history = history()
        history.put(sample(t0, level = 80, charging = true))
        history.put(sample(t0 + 10_000L, level = 80, charging = false))

        assertThat(history.samplesSince(0L).last()).isEqualTo(sample(t0 + 10_000L, level = 80, charging = false))
    }

    @Test
    fun `charger bounce still leaves nothing behind`() {
        val history = history()
        val plugged = sample(t0, level = 50, charging = true)
        history.put(plugged)
        history.put(sample(t0 + 5_000L, level = 50, charging = false))
        history.put(sample(t0 + 8_000L, level = 50, charging = true))

        assertThat(history.samplesSince(0L)).containsExactly(plugged)
    }

    @Test
    fun `bounce with several updates in between still leaves one charge`() {
        // Codex round 9: unplug +5 s, another unplugged update +6 s, plugged again
        // +8 s; the second update confirmed the unplug at once and split the charge.
        val history = history()
        val plugged = sample(t0, level = 50, charging = true)
        history.put(plugged)
        history.put(sample(t0 + 5_000L, level = 50, charging = false))
        history.put(sample(t0 + 6_000L, level = 50, charging = false))
        history.put(sample(t0 + 8_000L, level = 50, charging = true))
        history.put(sample(t0 + 30 * minute, level = 70, charging = true))

        assertThat(history.samplesSince(0L).map { it.charging }).containsExactly(true, true).inOrder()
    }

    @Test
    fun `an unplug that outlasts the bounce window stays, even when the next update is the replug`() {
        // Codex round 10: unplugged 10 s after a stored sample and plugged back in five
        // minutes later with no update in between; the replug dropped the unplug as
        // bounce and the two charges read as one.
        val history = history()
        history.put(sample(t0, level = 20, charging = true))
        history.put(sample(t0 + 60 * minute, level = 80, charging = true))
        history.put(sample(t0 + 60 * minute + 10_000L, level = 80, charging = false))
        history.put(sample(t0 + 65 * minute, level = 79, charging = true))
        history.put(sample(t0 + 90 * minute, level = 90, charging = true))

        val charges = ChargeSessions.from(history.samplesSince(0L), nowMillis = t0 + 91 * minute)

        assertThat(charges).hasSize(2)
        assertThat(charges.last().endMillis).isEqualTo(t0 + 60 * minute + 10_000L)
    }

    @Test
    fun `a clock set back while an unplug is held does not stop the history`() {
        // Codex round 10: the held unplug waited for a later sample a bounce window
        // after it on the wall clock; set back an hour, every later sample was earlier
        // than the held one and nothing was stored until the clock caught up.
        val history = history()
        history.put(sample(t0 + 2 * 60 * minute, level = 80, charging = true), elapsedAt = 0L)
        history.put(sample(t0 + 2 * 60 * minute + 10_000L, level = 80, charging = false), elapsedAt = 10_000L)
        history.put(sample(t0 + 60 * minute + 11 * minute, level = 78, charging = false), elapsedAt = 11 * minute)
        history.put(sample(t0 + 60 * minute + 30 * minute, level = 74, charging = false), elapsedAt = 30 * minute)

        assertThat(history.samplesSince(0L).map { it.level }).containsExactly(78, 74, 80, 80).inOrder()
    }

    @Test
    fun `a bounce is told by the monotonic clock, not the wall clock`() {
        // The wall clock jumps forward between unplug and replug; the charger was out
        // for three seconds.
        val history = history()
        val plugged = sample(t0, level = 50, charging = true)
        history.put(plugged, elapsedAt = 0L)
        history.put(sample(t0 + 5_000L, level = 50, charging = false), elapsedAt = 5_000L)
        history.put(sample(t0 + 60 * minute, level = 50, charging = true), elapsedAt = 8_000L)

        assertThat(history.samplesSince(0L).map { it.charging }).doesNotContain(false)
    }

    @Test
    fun `record throttles samples that arrive too soon`() {
        val history = history()
        val first = sample(1_000L, level = 50)
        history.put(first)
        history.put(sample(2_000L, level = 50))
        assertThat(history.samplesSince(0L)).containsExactly(first)
    }

    @Test
    fun `record ignores a negative level`() {
        val history = history()
        history.put(sample(1_000L, level = -1))
        assertThat(history.samplesSince(0L)).isEmpty()
    }

    @Test
    fun `record purges files older than retention`() {
        val old = tmp.newFile(BatteryHistoryCodec.fileNameFor(today.minusDays(20)))
        history().put(sample(1_000L))
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
        val neverWritten = BatteryHistoryImpl(File(tmp.root, "not-created"), { today }, { elapsed })
        assertThat(neverWritten.samplesSince(0L)).isEmpty()
    }
}
