package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class UsageHistoryLogicTest {

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
    fun `given history keys, when checking retention, then baseline and current days survive`() {
        val today = LocalDate.of(2026, 7, 4)
        val retained = NotificationCounting.retainedDays(today)
        val todayEpoch = today.toEpochDay()
        val oldEpoch = today.minusDays(70).toEpochDay()

        assertThat(isRetainedHistoryKey(KEY_LAST_BOOT_COUNT, retained)).isTrue()
        assertThat(isRetainedHistoryKey("unlocks:$todayEpoch", retained)).isTrue()
        assertThat(isRetainedHistoryKey("screen:$todayEpoch", retained)).isTrue()
        assertThat(isRetainedHistoryKey("boots:$oldEpoch", retained)).isFalse()
        assertThat(isRetainedHistoryKey("garbage", retained)).isFalse()
        assertThat(isRetainedHistoryKey("charges:abc", retained)).isFalse()
    }
}
