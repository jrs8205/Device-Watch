package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.presentation.FakeUsageHistory
import org.junit.Test
import java.time.LocalDate

class UsageHistoryLogicTest {

    @Test
    fun `today's totals record the screen-on time along with screen time and unlocks`() {
        // Codex round 10: only the Home refresh recorded screen-on, so a week without
        // opening the app left days of it uncollected while the service kept running.
        val history = FakeUsageHistory()
        val day = LocalDate.of(2026, 9, 28)

        history.recordTotals(day, UsageTotals(screenTimeMillis = 3_000L, unlockCount = 4, screenOnMillis = 5_000L))

        assertThat(history.screen).containsExactly(day, 3_000L)
        assertThat(history.unlocks).containsExactly(day, 4)
        assertThat(history.screenOn).containsExactly(day, 5_000L)
    }

    @Test
    fun `an unknown screen-on time leaves the stored one alone`() {
        val history = FakeUsageHistory()
        val day = LocalDate.of(2026, 9, 28)
        history.recordScreenOn(day, 7_000L)

        history.recordTotals(day, UsageTotals(screenTimeMillis = 3_000L, unlockCount = 4, screenOnMillis = null))

        assertThat(history.screenOn).containsExactly(day, 7_000L)
    }

    @Test
    fun `given no baseline, when computing boot delta, then zero`() {
        assertThat(bootCountDelta(lastRegistered = -1, current = 120)).isEqualTo(0)
    }

    @Test
    fun `given an unchanged counter, when computing boot delta, then zero`() {
        // Android re-delivers BOOT_COMPLETED after app updates; BOOT_COUNT stays put.
        assertThat(bootCountDelta(lastRegistered = 120, current = 120)).isEqualTo(0)
    }

    @Test
    fun `given real boots, when computing boot delta, then the difference`() {
        assertThat(bootCountDelta(lastRegistered = 120, current = 121)).isEqualTo(1)
        assertThat(bootCountDelta(lastRegistered = 120, current = 123)).isEqualTo(3)
    }

    @Test
    fun `given a counter reset, when computing boot delta, then zero`() {
        assertThat(bootCountDelta(lastRegistered = 120, current = 2)).isEqualTo(0)
    }

    @Test
    fun `used storage is kept in bytes, and an unknown reading is not kept`() {
        assertThat(storageUsedBytes(1.5)).isEqualTo(1_610_612_736L)
        assertThat(storageUsedBytes(-1.0)).isNull()
        assertThat(storageUsedBytes(0.0)).isNull()
    }

    @Test
    fun `given history keys, when checking retention, then baseline and current days survive`() {
        val today = LocalDate.of(2026, 7, 4)
        val retained = NotificationCounting.retainedDays(today)
        val todayEpoch = today.toEpochDay()
        val oldEpoch = today.minusDays(70).toEpochDay()

        assertThat(isRetainedHistoryKey(KEY_LAST_BOOT_COUNT, retained)).isTrue()
        assertThat(isRetainedHistoryKey("unlocks:$todayEpoch", retained)).isTrue()
        assertThat(isRetainedHistoryKey("screen:$todayEpoch", retained)).isTrue()
        assertThat(isRetainedHistoryKey("storage:$todayEpoch", retained)).isTrue()
        // 1.6.0 development builds kept a wall-clock stamp here; it is dropped now.
        assertThat(isRetainedHistoryKey("storageat:$todayEpoch", retained)).isFalse()
        assertThat(isRetainedHistoryKey("boots:$oldEpoch", retained)).isFalse()
        assertThat(isRetainedHistoryKey("garbage", retained)).isFalse()
        assertThat(isRetainedHistoryKey("charges:abc", retained)).isFalse()
    }
}
