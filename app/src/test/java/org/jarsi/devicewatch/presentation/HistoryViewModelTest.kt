package org.jarsi.devicewatch.presentation

import org.jarsi.devicewatch.data.BatteryHistory
import org.jarsi.devicewatch.data.BatterySample
import org.jarsi.devicewatch.data.DataUsageSince
import org.jarsi.devicewatch.data.DeviceInfo
import org.jarsi.devicewatch.data.MonthlyDataUsage
import org.jarsi.devicewatch.data.NotificationLog
import org.jarsi.devicewatch.data.NotificationLogEntry
import org.jarsi.devicewatch.data.NotificationStats
import org.jarsi.devicewatch.data.SystemStats
import org.jarsi.devicewatch.data.SystemStatsRepository
import org.jarsi.devicewatch.data.UsageDayTally
import org.jarsi.devicewatch.data.UsageHistory
import app.cash.turbine.test
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
import java.time.YearMonth

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
        var total = 7
        override fun totalForDay(day: LocalDate) = total
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

    private class FakeStatsRepository : SystemStatsRepository {
        /** monthsBack argument of every monthlyDataUsage call, in order. */
        val monthlyCalls = mutableListOf<Int>()
        var allUnavailable = false
        override suspend fun getStats(): SystemStats = error("not used by HistoryViewModel")
        override suspend fun getDeviceInfo(): DeviceInfo = error("not used by HistoryViewModel")
        override suspend fun dataUsedSince(startMillis: Long) = DataUsageSince(0.0, 0.0)
        override suspend fun monthlyDataUsage(monthsBack: Int): List<MonthlyDataUsage> {
            monthlyCalls += monthsBack
            val current = YearMonth.now()
            return (0..monthsBack).map { offset ->
                val month = current.minusMonths(offset.toLong())
                if (allUnavailable) {
                    MonthlyDataUsage(month, mobileGb = -1.0, wifiGb = -1.0)
                } else {
                    MonthlyDataUsage(month, mobileGb = 2.45, wifiGb = 18.2)
                }
            }
        }
    }

    /** In-memory store mirroring the real one: samplesSince filters and sorts ascending. */
    private class FakeBatteryHistory : BatteryHistory {
        private val base = System.currentTimeMillis() - 6L * 60 * 60 * 1000
        private val stored = mutableListOf(
            BatterySample(base + 2 * 60 * 60 * 1000, level = 61, charging = false),
            BatterySample(base, level = 80, charging = true),
            BatterySample(base + 60 * 60 * 1000, level = 72, charging = false),
        )

        /** sinceMillis of the latest samplesSince call, or -1 when never called. */
        var requestedSince = -1L

        override fun record(sample: BatterySample) { stored += sample }
        override fun samplesSince(sinceMillis: Long): List<BatterySample> {
            requestedSince = sinceMillis
            return stored.filter { it.timeMillis >= sinceMillis }.sortedBy { it.timeMillis }
        }
    }

    private fun buildViewModel(
        stats: FakeStats = FakeStats(),
        statsRepository: FakeStatsRepository = FakeStatsRepository(),
        batteryHistory: FakeBatteryHistory = FakeBatteryHistory(),
    ) = HistoryViewModel(FakeHistory(), stats, FakeLog(), statsRepository, batteryHistory, dispatcher)

    @Test
    fun `load exposes 62 ascending days with notification counts and the log`() = runTest {
        val vm = buildViewModel()
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

    @Test
    fun `load exposes the stored battery samples ascending`() = runTest {
        val batteryHistory = FakeBatteryHistory()
        val vm = buildViewModel(batteryHistory = batteryHistory)
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        val samples = vm.uiState.value.batterySamples
        assertThat(samples).hasSize(3)
        assertThat(samples.map { it.timeMillis }).isInOrder()
        assertThat(samples.first().level).isEqualTo(80)
        assertThat(samples.first().charging).isTrue()
    }

    @Test
    fun `load asks for the full retained window and leaves day-week filtering to the UI`() = runTest {
        val batteryHistory = FakeBatteryHistory()
        val vm = buildViewModel(batteryHistory = batteryHistory)
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        val fourteenDays = 14L * 24 * 60 * 60 * 1000
        val window = System.currentTimeMillis() - batteryHistory.requestedSince
        assertThat(window).isAtLeast(fourteenDays)
        assertThat(window).isLessThan(fourteenDays + 60_000L)
    }

    @Test
    fun `load exposes monthly data usage newest first`() = runTest {
        val vm = buildViewModel()
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        val monthly = vm.uiState.value.monthlyUsage
        assertThat(monthly).hasSize(13)
        assertThat(monthly.first().month).isEqualTo(YearMonth.now())
        assertThat(monthly.first().mobileGb).isEqualTo(2.45)
    }

    @Test
    fun `silent reloads reuse past months and re-query only the current month`() = runTest {
        val repository = FakeStatsRepository()
        val vm = buildViewModel(statsRepository = repository)
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(repository.monthlyCalls).containsExactly(12, 0).inOrder()
        assertThat(vm.uiState.value.monthlyUsage).hasSize(13)
    }

    @Test
    fun `forced refresh fetches the full monthly table again`() = runTest {
        val repository = FakeStatsRepository()
        val vm = buildViewModel(statsRepository = repository)
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()
        vm.refresh()
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(repository.monthlyCalls).containsExactly(12, 12).inOrder()
    }

    @Test
    fun `an entirely unavailable monthly cache is not reused`() = runTest {
        // Usage access was missing on the first load and granted before the second.
        val repository = FakeStatsRepository().apply { allUnavailable = true }
        val vm = buildViewModel(statsRepository = repository)
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        repository.allUnavailable = false
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(repository.monthlyCalls).containsExactly(12, 12).inOrder()
        assertThat(vm.uiState.value.monthlyUsage.first().mobileGb).isEqualTo(2.45)
    }

    @Test
    fun `a load call while one is in flight is skipped`() = runTest {
        val repository = FakeStatsRepository()
        val vm = buildViewModel(statsRepository = repository)
        vm.load()
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(repository.monthlyCalls).containsExactly(12)
    }

    @Test
    fun `a pull during an in-flight load runs right after it`() = runTest {
        val repository = FakeStatsRepository()
        val vm = buildViewModel(statsRepository = repository)
        vm.load()
        vm.refresh()
        dispatcher.scheduler.advanceUntilIdle()

        // The pull's full re-fetch is chained after the in-flight load, not dropped.
        assertThat(repository.monthlyCalls).containsExactly(12, 12).inOrder()
        assertThat(vm.uiState.value.isRefreshing).isFalse()
    }

    @Test
    fun `pull refresh drives the indicator until the load lands`() = runTest {
        val vm = buildViewModel()
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        vm.refresh()
        assertThat(vm.uiState.value.isRefreshing).isTrue()
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.uiState.value.isRefreshing).isFalse()
    }

    @Test
    fun `reload refreshes data silently without flashing the loading state`() = runTest {
        val stats = FakeStats()
        val vm = buildViewModel(stats = stats)
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        vm.uiState.test {
            assertThat(awaitItem().days.last().notifications).isEqualTo(7)

            stats.total = 9
            vm.load()
            dispatcher.scheduler.advanceUntilIdle()

            val refreshed = awaitItem()
            assertThat(refreshed.isLoading).isFalse()
            assertThat(refreshed.days.last().notifications).isEqualTo(9)
        }
    }
}
