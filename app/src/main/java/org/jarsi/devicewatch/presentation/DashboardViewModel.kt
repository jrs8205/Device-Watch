package org.jarsi.devicewatch.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.jarsi.devicewatch.data.AppSettingsRepository
import org.jarsi.devicewatch.data.AppUsageRepository
import org.jarsi.devicewatch.data.CHARGE_LIMIT_MAX
import org.jarsi.devicewatch.data.CHARGE_LIMIT_MIN
import org.jarsi.devicewatch.data.DATA_QUOTA_MAX_GB
import org.jarsi.devicewatch.data.DATA_QUOTA_MIN_GB
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
import kotlinx.coroutines.delay
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

/** The reload is often near-instant; without a floor the pull indicator only flickers. */
internal const val MIN_PULL_INDICATOR_MS = 800L

/** Keeps the pull indicator visible for at least [MIN_PULL_INDICATOR_MS] from [pullStartMillis]. */
internal suspend fun delayForPullIndicator(pullStartMillis: Long) {
    val elapsed = System.currentTimeMillis() - pullStartMillis
    if (elapsed < MIN_PULL_INDICATOR_MS) delay(MIN_PULL_INDICATOR_MS - elapsed)
}

data class DashboardUiState(
    val stats: SystemStats? = null,
    val deviceInfo: DeviceInfo? = null,
    /** True only while a pull-to-refresh-initiated reload is running. */
    val isRefreshing: Boolean = false,
    val isWidgetInstalled: Boolean = false,
    val lastUpdated: String = "--:--",
    val widgetOpacity: Float = DEFAULT_WIDGET_OPACITY,
    val dataCounterMode: DataCounterMode = DataCounterMode.DAY,
    val cycleStartDay: Int = 1,
    /** Charge-reminder level in percent; 0 = reminder off. */
    val chargeLimitPercent: Int = 0,
    /** Mobile-data allowance per counting period in GB; 0 = no quota. */
    val dataQuotaGb: Double = 0.0,
    // Usage counters, scoped to the selected counting period (day or billing cycle).
    val screenTimeMillis: Long = -1L,
    val unlockCount: Int = UNAVAILABLE_INT,
    val notificationCount: Int = UNAVAILABLE_INT,
    val bootCount: Int = 0,
    val chargeCount: Int = 0,
    val unlockCountingSupported: Boolean = true,
    /** "Previous period vs now" totals for the usage card; null until computed. */
    val periodComparison: PeriodComparisonData? = null,
    val usageAccessEnabled: Boolean = false,
    val notificationAccessEnabled: Boolean = false,
    /** null until read from settings; false shows the first-run intro. */
    val onboardingCompleted: Boolean? = null,
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

    init {
        // Synchronous prefs read so the first composition already knows whether
        // to show the intro — no main-UI flash before it.
        _uiState.update { it.copy(onboardingCompleted = settings.onboardingShown()) }
    }

    /** Marks the first-run intro completed (or skipped). */
    fun completeOnboarding() {
        settings.setOnboardingShown()
        _uiState.update { it.copy(onboardingCompleted = true) }
    }

    /** Reads fresh stats, pushes them to every installed widget, and updates the screen. */
    fun refresh() {
        viewModelScope.launch { refreshInternal() }
    }

    /** Pull-to-refresh entry: the same reload, but drives the pull indicator. */
    fun pullRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val startMillis = System.currentTimeMillis()
            try {
                refreshInternal()
            } finally {
                delayForPullIndicator(startMillis)
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private suspend fun refreshInternal() {
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
        // "Previous period vs now", day-count-aligned; the stores return zeros for
        // days they have nothing on, and the UI hides rows the device cannot count.
        val comparison = PeriodComparison.windows(
            settings.dataCounterMode(), settings.cycleStartDay(), today
        )?.let { w ->
            PeriodComparisonData(
                screenTimeNowMillis = usageHistory.screenTimeBetween(w.currentStart, w.currentEnd),
                screenTimePrevMillis = usageHistory.screenTimeBetween(w.previousStart, w.previousEnd),
                unlocksNow = usageHistory.unlocksBetween(w.currentStart, w.currentEnd),
                unlocksPrev = usageHistory.unlocksBetween(w.previousStart, w.previousEnd),
                notificationsNow = notificationStats.totalBetween(w.currentStart, w.currentEnd),
                notificationsPrev = notificationStats.totalBetween(w.previousStart, w.previousEnd),
                daysCompared = w.daysCompared,
            )
        }
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
                periodComparison = comparison,
                notificationAccessEnabled = notificationAccess,
                notificationCount = if (notificationAccess) {
                    notificationStats.totalBetween(periodStart, today)
                } else {
                    UNAVAILABLE_INT
                },
            )
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

    /**
     * Loads the saved counter mode, cycle start day, charge reminder and data quota
     * into the state.
     */
    fun loadDataCounterSettings() {
        _uiState.update {
            it.copy(
                dataCounterMode = settings.dataCounterMode(),
                cycleStartDay = settings.cycleStartDay(),
                chargeLimitPercent = settings.chargeLimitPercent(),
                dataQuotaGb = settings.dataQuotaGb(),
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

    /** Live-updates the charge-reminder value without persisting; 0 switches it off. */
    fun onChargeLimitChange(percent: Int) {
        val value = if (percent <= 0) 0 else percent.coerceIn(CHARGE_LIMIT_MIN, CHARGE_LIMIT_MAX)
        _uiState.update { it.copy(chargeLimitPercent = value) }
    }

    /** Persists the chosen charge-reminder level; the monitor service reads it live. */
    fun onCommitChargeLimit() {
        settings.setChargeLimitPercent(_uiState.value.chargeLimitPercent)
    }

    /** Live-updates the data-quota slider value without persisting; 0 switches it off. */
    fun onDataQuotaChange(quotaGb: Double) {
        val value = if (quotaGb <= 0.0) 0.0 else quotaGb.coerceIn(DATA_QUOTA_MIN_GB, DATA_QUOTA_MAX_GB)
        _uiState.update { it.copy(dataQuotaGb = value) }
    }

    /** Persists the chosen quota and re-queries stats so the counters show it right away. */
    fun onCommitDataQuota() {
        settings.setDataQuotaGb(_uiState.value.dataQuotaGb)
        refresh()
    }

    private fun currentTime(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    private companion object {
        /** Android keeps detailed usage events for roughly a week. */
        private const val HISTORY_BACKFILL_DAYS = 7
    }
}
