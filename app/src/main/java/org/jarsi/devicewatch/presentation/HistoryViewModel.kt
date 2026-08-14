package org.jarsi.devicewatch.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.jarsi.devicewatch.data.BatteryHistory
import org.jarsi.devicewatch.data.BatteryHistoryCodec
import org.jarsi.devicewatch.data.BatterySample
import org.jarsi.devicewatch.data.MonthlyDataUsage
import org.jarsi.devicewatch.data.NotificationLog
import org.jarsi.devicewatch.data.NotificationLogEntry
import org.jarsi.devicewatch.data.NotificationStats
import org.jarsi.devicewatch.data.SystemStatsRepository
import org.jarsi.devicewatch.data.UsageHistory
import org.jarsi.devicewatch.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class HistoryDay(
    val day: LocalDate,
    val screenTimeMillis: Long,
    val unlocks: Int,
    val notifications: Int,
    val boots: Int,
    val charges: Int,
)

data class HistoryUiState(
    val isLoading: Boolean = true,
    /** True only while a pull-to-refresh-initiated load is running. */
    val isRefreshing: Boolean = false,
    val days: List<HistoryDay> = emptyList(),
    val monthlyUsage: List<MonthlyDataUsage> = emptyList(),
    val logEntries: List<NotificationLogEntry> = emptyList(),
    val listenerEnabled: Boolean = false,
    /** The store's whole retained window, ascending; the chart picks its own range out of it. */
    val batterySamples: List<BatterySample> = emptyList(),
)

/**
 * State for the Historia page: 62-day tallies + monthly data usage + the
 * notification log + the retained battery history.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val usageHistory: UsageHistory,
    private val notificationStats: NotificationStats,
    private val notificationLog: NotificationLog,
    private val statsRepository: SystemStatsRepository,
    private val batteryHistory: BatteryHistory,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var pendingMonthlyRefresh = false
    private var pullStartMillis = 0L

    /**
     * Pull-to-refresh entry: also re-fetches the full monthly table. The flag is
     * set BEFORE load()'s overlap guard so a pull colliding with a 15 s poll tick
     * still shows the indicator; a skipped full re-fetch is caught up by the next
     * tick via [pendingMonthlyRefresh].
     */
    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        pullStartMillis = System.currentTimeMillis()
        pendingMonthlyRefresh = true
        load()
    }

    fun load(refreshMonthlyUsage: Boolean = false) {
        // Overlapping loads must not race: a stale poll finishing after a pull
        // would write pre-refresh monthly data back over the fresh table. A pull
        // that lands mid-poll is chained to run right after, not dropped.
        if (loadJob?.isActive == true) {
            if (pendingMonthlyRefresh) {
                val activeJob = loadJob
                viewModelScope.launch {
                    activeJob?.join()
                    load()
                }
            }
            return
        }
        val forceMonthly = refreshMonthlyUsage || pendingMonthlyRefresh
        pendingMonthlyRefresh = false
        loadJob = viewModelScope.launch {
            try {
                // Loading is only surfaced on the very first load; later calls are
                // silent refreshes so the open page never flashes its empty states.
                _uiState.update { it.copy(isLoading = it.days.isEmpty()) }
                val previousMonthly = _uiState.value.monthlyUsage
                val state = withContext(dispatcher) {
                    val today = LocalDate.now()
                    val start = today.minusDays(61)
                    val days = usageHistory.dailyTallies(start, today).map { tally ->
                        HistoryDay(
                            day = tally.day,
                            screenTimeMillis = tally.screenTimeMillis,
                            unlocks = tally.unlocks,
                            notifications = notificationStats.totalForDay(tally.day),
                            boots = tally.boots,
                            charges = tally.charges,
                        )
                    }
                    HistoryUiState(
                        isLoading = false,
                        days = days,
                        monthlyUsage = resolveMonthlyUsage(forceMonthly, previousMonthly),
                        logEntries = notificationLog.entriesNewestFirst(),
                        listenerEnabled = notificationStats.isListenerEnabled(),
                        batterySamples = batteryHistory.samplesSince(
                            System.currentTimeMillis() - BATTERY_WINDOW_MILLIS
                        ),
                    )
                }
                // Preserve the pull flag across the whole-state write; the finally
                // block clears it after the indicator's minimum display time.
                _uiState.update { current -> state.copy(isRefreshing = current.isRefreshing) }
            } finally {
                // A pull queued behind this load keeps the indicator up — the
                // chained follow-up load clears it when the pull actually ran.
                if (!pendingMonthlyRefresh) {
                    if (_uiState.value.isRefreshing) delayForPullIndicator(pullStartMillis)
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }
        }
    }

    /**
     * Past months are immutable, so a valid cache only re-queries the live current
     * month on each poll. A full re-fetch happens when forced (pull), when nothing
     * is cached, when the cache is entirely unavailable (usage access was granted
     * after it was built), or after a month rollover.
     */
    private suspend fun resolveMonthlyUsage(
        force: Boolean,
        previous: List<MonthlyDataUsage>,
    ): List<MonthlyDataUsage> {
        val cacheUsable = !force &&
            previous.isNotEmpty() &&
            previous.first().month == YearMonth.now() &&
            !previous.all { it.mobileGb < 0.0 && it.wifiGb < 0.0 }
        return if (cacheUsable) {
            statsRepository.monthlyDataUsage(monthsBack = 0) + previous.drop(1)
        } else {
            statsRepository.monthlyDataUsage()
        }
    }

    private companion object {
        /** Everything the store keeps (14 d); the day/week chart ranges are cut from this. */
        const val BATTERY_WINDOW_MILLIS = BatteryHistoryCodec.RETENTION_DAYS * 24 * 60 * 60 * 1000
    }
}
