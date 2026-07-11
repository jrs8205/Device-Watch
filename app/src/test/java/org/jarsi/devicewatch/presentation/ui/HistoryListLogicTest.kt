package com.example.modernwidget.presentation.ui

import com.example.modernwidget.presentation.HistoryDay
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class HistoryListLogicTest {

    private fun day(offset: Long, screen: Long = 0L, notifications: Int = 0) = HistoryDay(
        day = LocalDate.of(2026, 7, 1).plusDays(offset),
        screenTimeMillis = screen,
        unlocks = 0,
        notifications = notifications,
        boots = 0,
        charges = 0,
    )

    @Test
    fun `list starts from the first day with data and is newest first`() {
        val days = listOf(day(0), day(1, notifications = 5), day(2), day(3, notifications = 2))

        val visible = daysNewestFirstSinceFirstData(days, HistoryMetric.Notifications)

        assertThat(visible.map { it.day.dayOfMonth }).containsExactly(4, 3, 2).inOrder()
    }

    @Test
    fun `zero days after the first data day are kept as real zeros`() {
        val days = listOf(day(0, screen = 60_000L), day(1), day(2, screen = 30_000L))

        val visible = daysNewestFirstSinceFirstData(days, HistoryMetric.ScreenTime)

        assertThat(visible).hasSize(3)
        assertThat(visible[1].screenTimeMillis).isEqualTo(0L)
    }

    @Test
    fun `metric with no data at all yields an empty list`() {
        val days = listOf(day(0, screen = 60_000L), day(1, screen = 30_000L))

        val visible = daysNewestFirstSinceFirstData(days, HistoryMetric.Boots)

        assertThat(visible).isEmpty()
    }
}
