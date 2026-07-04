package com.example.modernwidget.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class NotificationCountingTest {

    @Test
    fun `given an ongoing notification, when deciding, then it is not counted`() {
        assertThat(
            NotificationCounting.shouldCountNotification(
                isOngoing = true, isGroupSummary = false, keyAlreadyActive = false
            )
        ).isFalse()
    }

    @Test
    fun `given a group summary, when deciding, then it is not counted`() {
        assertThat(
            NotificationCounting.shouldCountNotification(
                isOngoing = false, isGroupSummary = true, keyAlreadyActive = false
            )
        ).isFalse()
    }

    @Test
    fun `given an update to an active notification, when deciding, then it is not counted`() {
        assertThat(
            NotificationCounting.shouldCountNotification(
                isOngoing = false, isGroupSummary = false, keyAlreadyActive = true
            )
        ).isFalse()
    }

    @Test
    fun `given a fresh normal notification, when deciding, then it is counted`() {
        assertThat(
            NotificationCounting.shouldCountNotification(
                isOngoing = false, isGroupSummary = false, keyAlreadyActive = false
            )
        ).isTrue()
    }

    @Test
    fun `given today, when computing retention, then today and yesterday are kept`() {
        val today = LocalDate.of(2026, 7, 4)

        assertThat(NotificationCounting.retainedDays(today))
            .containsExactly(LocalDate.of(2026, 7, 4), LocalDate.of(2026, 7, 3))
    }

    @Test
    fun `given stats keys, when checking retention, then only current keys survive`() {
        val today = LocalDate.of(2026, 7, 4)
        val retained = NotificationCounting.retainedDays(today)
        val todayEpoch = today.toEpochDay()
        val yesterdayEpoch = today.minusDays(1).toEpochDay()
        val oldEpoch = today.minusDays(5).toEpochDay()

        assertThat(NotificationCounting.isRetainedStatsKey("total:$todayEpoch", retained)).isTrue()
        assertThat(
            NotificationCounting.isRetainedStatsKey("pkg:$yesterdayEpoch:com.example.app", retained)
        ).isTrue()
        assertThat(NotificationCounting.isRetainedStatsKey("total:$oldEpoch", retained)).isFalse()
        assertThat(NotificationCounting.isRetainedStatsKey("pkg:$oldEpoch:com.x", retained)).isFalse()
    }

    @Test
    fun `given malformed keys, when checking retention, then they are purged`() {
        val retained = NotificationCounting.retainedDays(LocalDate.of(2026, 7, 4))

        assertThat(NotificationCounting.isRetainedStatsKey("garbage", retained)).isFalse()
        assertThat(NotificationCounting.isRetainedStatsKey("pkg:abc:com.x", retained)).isFalse()
        assertThat(NotificationCounting.isRetainedStatsKey("total:", retained)).isFalse()
    }
}
