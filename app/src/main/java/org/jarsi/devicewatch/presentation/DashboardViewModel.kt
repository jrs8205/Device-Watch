package org.jarsi.devicewatch.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.jarsi.devicewatch.data.AppSettingsRepository
import org.jarsi.devicewatch.data.AppUsageRepository
import org.jarsi.devicewatch.data.DataCounterMode
import org.jarsi.devicewatch.data.DataPeriodCalculator
import org.jarsi.devicewatch.data.DeviceInfo
import org.jarsi.devicewatch.data.NotificationStats
import org.jarsi.devicewatch.data.SystemStats
import org.jarsi.devicewatch.data.SystemStatsRepository
import org.jarsi.devicewatch.data.UNAVAILABLE_INT
import org.jarsi.devicewatch.data.UsageHistory
import java.time.LocalDate
import org.jarsi.devicewatch.widget.WidgetController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

const val DEFAULT_WIDGET_OPACITY = 0.86f

data class DashboardUiState(
    val stats: SystemStats? = null,
    val deviceInfo: DeviceInfo? = null,
    val isWidgetInstalled: Boolean = false,
    val lastUpdated: String = "--:--",
    val widgetOpacity: Float = DEFAULT_WIDGET_OPACITY,
    val dataCounterMode: DataCounterMode = DataCounterMode.DAY,
    val cycleStartDay: Int = 1,
    // Usage counters, scoped to the selected counting period (day or billing cycle).
    val screenTimeMillis: Long = -1L,
    val unlockCount: Int = UNAVAILABLE_INT,
    val notificationCount: Int = UNAVAILABLE_INT,
    val bootCount: Int = 0,
    val chargeCount: Int = 0,
    val unlockCountingSupported: Boolean = true,
    val usageAccessEnabled: Boolean = false,
    val notificationAccessEnabled: Boolean = false,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: SystemStatsRepository,
    private val widgetController: WidgetController,
    private val settings: AppSettingsRepository,
    private val appUsageRepository: AppUsageRepository,
    private val notificationStats: NotificationStats,
    private val usageHistory: UsageHistory,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /** Reads fresh stats, pushes them to every installed widget, and updates the screen. */
    fun refresh() {
        viewModelScope.launch {
            val stats = repository.getStats()
            val widgetInstalled = widgetController.pushStats(stats)

            // Usage counters cover the same period as the data counters. Android keeps
            // no long history for these, so daily values are recorded into our own
            // store: unlock counts and per-day screen time are backfilled from the
            // ~7 days Android remembers, today's values come from a precise event
            // pass, and boots/charges are incremented as they happen elsewhere.
            val today = LocalDate.now()
            val periodStart = DataPeriodCalculator.periodStart(
                settings.dataCounterMode(), settings.cycleStartDay(), today
            )
            val hasUsageAccess = appUsageRepository.hasUsageAccess()
            val supportsUnlocks = appUsageRepository.supportsUnlockCounting()
            if (hasUsageAccess) {
                appUsageRepository.screenTimeByDay(HISTORY_BACKFILL_DAYS)
                    .forEach { (day, millis) -> usageHistory.recordScreenTime(day, millis) }
                appUsageRepository.unlockCountsByDay(HISTORY_BACKFILL_DAYS)
                    .forEach { (day, count) -> usageHistory.recordUnlocks(day, count) }
                appUsageRepository.usageTotalsToday()?.let { totals ->
                    usageHistory.recordScreenTime(today, totals.screenTimeMillis)
                    usageHistory.recordUnlocks(today, totals.unlockCount)
                }
            }
            usageHistory.purge(today)

            val notificationAccess = notificationStats.isListenerEnabled()
            _uiState.update {
                it.copy(
                    stats = stats,
                    isWidgetInstalled = widgetInstalled,
                    lastUpdated = currentTime(),
                    usageAccessEnabled = hasUsageAccess,
                    unlockCountingSupported = supportsUnlocks,
                    screenTimeMillis = if (hasUsageAccess) {
                        usageHistory.screenTimeBetween(periodStart, today)
                    } else {
                        -1L
                    },
                    unlockCount = if (hasUsageAccess && supportsUnlocks) {
                        usageHistory.unlocksBetween(periodStart, today)
                    } else {
                        UNAVAILABLE_INT
                    },
                    bootCount = usageHistory.bootsBetween(periodStart, today),
                    chargeCount = usageHistory.chargesBetween(periodStart, today),
                    notificationAccessEnabled = notificationAccess,
                    notificationCount = if (notificationAccess) {
                        notificationStats.totalBetween(periodStart, today)
                    } else {
                        UNAVAILABLE_INT
                    },
                )
            }
        }
    }

    /** Loads the static, root-free device facts once (build, SoC, display, memory). */
    fun loadDeviceInfo() {
        viewModelScope.launch {
            val info = repository.getDeviceInfo()
            _uiState.update { it.copy(deviceInfo = info) }
        }
    }

    /** Loads the saved widget opacity from the first installed widget, if any. */
    fun loadWidgetOpacity() {
        viewModelScope.launch {
            widgetController.currentOpacity()?.let { saved ->
                _uiState.update { it.copy(widgetOpacity = saved) }
            }
        }
    }

    /** Live-updates the slider value without persisting (called on every drag tick). */
    fun onWidgetOpacityChange(value: Float) {
        _uiState.update { it.copy(widgetOpacity = value) }
    }

    /** Persists the current opacity to every installed widget and re-renders them. */
    fun commitWidgetOpacity() {
        val opacity = _uiState.value.widgetOpacity
        viewModelScope.launch {
            widgetController.setOpacity(opacity)
        }
    }

    /** Loads the saved data-counter mode and billing-cycle start day into the state. */
    fun loadDataCounterSettings() {
        _uiState.update {
            it.copy(
                dataCounterMode = settings.dataCounterMode(),
                cycleStartDay = settings.cycleStartDay(),
            )
        }
    }

    /** Persists the counter mode and re-queries stats so the widget shows the new period. */
    fun onDataCounterModeSelected(mode: DataCounterMode) {
        settings.setDataCounterMode(mode)
        _uiState.update { it.copy(dataCounterMode = mode) }
        refresh()
    }

    /** Live-updates the cycle-day slider value without persisting (called on every drag tick). */
    fun onCycleStartDayChange(day: Int) {
        _uiState.update { it.copy(cycleStartDay = day.coerceIn(1, 31)) }
    }

    /** Persists the dragged cycle start day and re-queries stats for the new period. */
    fun commitCycleStartDay() {
        settings.setCycleStartDay(_uiState.value.cycleStartDay)
        refresh()
    }

    private fun currentTime(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    private companion object {
        /** Android keeps detailed usage events for roughly a week. */
        private const val HISTORY_BACKFILL_DAYS = 7
    }
}
