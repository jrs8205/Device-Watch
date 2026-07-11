package org.jarsi.devicewatch.data

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
    fun `given today, when computing retention, then the last 62 days are kept`() {
        val today = LocalDate.of(2026, 7, 4)

        val retained = NotificationCounting.retainedDays(today)

        assertThat(retained).hasSize(62)
        assertThat(retained).contains(today)
        assertThat(retained).contains(today.minusDays(1))
        assertThat(retained).contains(today.minusDays(61))
        assertThat(retained).doesNotContain(today.minusDays(62))
    }

    @Test
    fun `given stats keys, when checking retention, then only current keys survive`() {
        val today = LocalDate.of(2026, 7, 4)
        val retained = NotificationCounting.retainedDays(today)
        val todayEpoch = today.toEpochDay()
        val recentEpoch = today.minusDays(30).toEpochDay()
        val oldEpoch = today.minusDays(70).toEpochDay()

        assertThat(NotificationCounting.isRetainedStatsKey("total:$todayEpoch", retained)).isTrue()
        assertThat(
            NotificationCounting.isRetainedStatsKey("pkg:$recentEpoch:com.example.app", retained)
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
