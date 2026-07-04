package com.example.modernwidget.presentation

import com.example.modernwidget.data.NotificationLog
import com.example.modernwidget.data.NotificationLogEntry
import com.example.modernwidget.data.NotificationStats
import com.example.modernwidget.data.UsageDayTally
import com.example.modernwidget.data.UsageHistory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeHistory : UsageHistory {
        override fun recordUnlocks(day: LocalDate, count: Int) = Unit
        override fun recordScreenTime(day: LocalDate, millis: Long) = Unit
        override fun registerBootCount(day: LocalDate, bootCountTotal: Int) = Unit
        override fun incrementCharge(day: LocalDate) = Unit
        override fun unlocksBetween(start: LocalDate, end: LocalDate) = 0
        override fun screenTimeBetween(start: LocalDate, end: LocalDate) = 0L
        override fun bootsBetween(start: LocalDate, end: LocalDate) = 0
        override fun chargesBetween(start: LocalDate, end: LocalDate) = 0
        override fun purge(today: LocalDate) = Unit
        override fun dailyTallies(start: LocalDate, end: LocalDate): List<UsageDayTally> {
            val days = mutableListOf<UsageDayTally>()
            var d = start
            while (!d.isAfter(end)) {
                days += UsageDayTally(d, unlocks = 1, screenTimeMillis = 60_000L, boots = 0, charges = 2)
                d = d.plusDays(1)
            }
            return days
        }
    }

    private class FakeStats : NotificationStats {
        override fun totalForDay(day: LocalDate) = 7
        override fun countForPackage(packageName: String, day: LocalDate) = 0
        override fun totalBetween(start: LocalDate, end: LocalDate) = 0
        override fun increment(packageName: String, day: LocalDate) = Unit
        override fun purge(today: LocalDate) = Unit
        override fun isListenerEnabled() = true
    }

    private class FakeLog : NotificationLog {
        val stored = mutableListOf(
            NotificationLogEntry(5L, "p", "App", "Title", "Text"),
        )
        override fun append(entry: NotificationLogEntry) { stored += entry }
        override fun entriesNewestFirst() = stored.toList()
    }

    @Test
    fun `load exposes 62 ascending days with notification counts and the log`() = runTest {
        val vm = HistoryViewModel(FakeHistory(), FakeStats(), FakeLog(), dispatcher)
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.days).hasSize(62)
        assertThat(state.days.first().day).isLessThan(state.days.last().day)
        assertThat(state.days.last().notifications).isEqualTo(7)
        assertThat(state.days.last().unlocks).isEqualTo(1)
        assertThat(state.days.last().screenTimeMillis).isEqualTo(60_000L)
        assertThat(state.days.last().charges).isEqualTo(2)
        assertThat(state.logEntries).hasSize(1)
        assertThat(state.listenerEnabled).isTrue()
    }
}
