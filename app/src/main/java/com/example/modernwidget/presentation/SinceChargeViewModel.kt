package com.example.modernwidget.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modernwidget.data.AppScreenTime
import com.example.modernwidget.data.AppUsageRepository
import com.example.modernwidget.data.BatteryStatusReader
import com.example.modernwidget.data.ChargeAnchor
import com.example.modernwidget.data.ChargeAnchorStore
import com.example.modernwidget.data.DonutSegment
import com.example.modernwidget.data.NotificationLog
import com.example.modernwidget.data.NotificationStats
import com.example.modernwidget.data.SystemStatsRepository
import com.example.modernwidget.data.UNAVAILABLE_DOUBLE
import com.example.modernwidget.data.UNAVAILABLE_INT
import com.example.modernwidget.data.UsageEventAggregator
import com.example.modernwidget.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SinceChargeUiState(
    val isLoading: Boolean = true,
    /** Load moment — the "now" end of the window, so elapsed math is consistent. */
    val nowMillis: Long = 0L,
    val anchor: ChargeAnchor? = null,
    val currentLevel: Int? = null,
    val isCharging: Boolean = false,
    val hasUsageAccess: Boolean = true,
    val unlockCount: Int = UNAVAILABLE_INT,
    val notificationCount: Int = UNAVAILABLE_INT,
    val wifiGb: Double = UNAVAILABLE_DOUBLE,
    val mobileGb: Double = UNAVAILABLE_DOUBLE,
    val screenTimes: List<AppScreenTime> = emptyList(),
    val screenTimeSegments: List<DonutSegment> = emptyList(),
    val totalScreenTimeMillis: Long = 0L,
)

/**
 * State for the "since charge" page: everything is queried on demand for the
 * window anchor → now. Without an anchor (fresh install, no battery event seen
 * yet) only the battery snapshot is exposed and the page shows its empty state.
 */
@HiltViewModel
class SinceChargeViewModel @Inject constructor(
    private val chargeAnchorStore: ChargeAnchorStore,
    private val batteryStatus: BatteryStatusReader,
    private val appUsageRepository: AppUsageRepository,
    private val statsRepository: SystemStatsRepository,
    private val notificationStats: NotificationStats,
    private val notificationLog: NotificationLog,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SinceChargeUiState())
    val uiState: StateFlow<SinceChargeUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            // No isLoading toggling here: the initial state already loads, and
            // later calls are silent refreshes (same pattern as HistoryViewModel).
            val state = withContext(dispatcher) {
                val anchor = chargeAnchorStore.load().anchor
                val currentLevel = batteryStatus.currentLevel()
                val isCharging = batteryStatus.isCharging()
                if (anchor == null) {
                    return@withContext SinceChargeUiState(
                        isLoading = false,
                        nowMillis = System.currentTimeMillis(),
                        currentLevel = currentLevel,
                        isCharging = isCharging,
                        hasUsageAccess = appUsageRepository.hasUsageAccess(),
                    )
                }

                val launchers = appUsageRepository.launcherPackages()
                val screenTimes = UsageEventAggregator.excludeLaunchers(
                    appUsageRepository.screenTimeSince(anchor.timeMillis), launchers
                )
                val data = statsRepository.dataUsedSince(anchor.timeMillis)
                val notificationCount = if (notificationStats.isListenerEnabled()) {
                    notificationLog.entriesNewestFirst().count { it.timeMillis >= anchor.timeMillis }
                } else {
                    UNAVAILABLE_INT
                }
                SinceChargeUiState(
                    isLoading = false,
                    nowMillis = System.currentTimeMillis(),
                    anchor = anchor,
                    currentLevel = currentLevel,
                    isCharging = isCharging,
                    hasUsageAccess = appUsageRepository.hasUsageAccess(),
                    unlockCount = appUsageRepository.unlockCountSince(anchor.timeMillis)
                        ?: UNAVAILABLE_INT,
                    notificationCount = notificationCount,
                    wifiGb = data.wifiGb,
                    mobileGb = data.mobileGb,
                    screenTimes = screenTimes,
                    screenTimeSegments = UsageEventAggregator.donutSegments(screenTimes),
                    totalScreenTimeMillis = screenTimes.sumOf { it.foregroundMillis },
                )
            }
            _uiState.value = state
        }
    }
}
