package org.jarsi.devicewatch.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.jarsi.devicewatch.data.HealthAlert
import org.jarsi.devicewatch.data.storageUsedBytes
import org.jarsi.devicewatch.data.AppSettingsRepository
import org.jarsi.devicewatch.data.AppUsageRepository
import org.jarsi.devicewatch.data.CHARGE_LIMIT_MAX
import org.jarsi.devicewatch.data.CHARGE_LIMIT_MIN
import org.jarsi.devicewatch.data.DATA_QUOTA_MAX_GB
import org.jarsi.devicewatch.data.DATA_QUOTA_MIN_GB
import org.jarsi.devicewatch.data.DataBreakdown
import org.jarsi.devicewatch.data.StorageBreakdown
import org.jarsi.devicewatch.data.DeviceState
import org.jarsi.devicewatch.data.DataCounterMode
import org.jarsi.devicewatch.data.DataPeriodCalculator
import org.jarsi.devicewatch.data.DeviceInfo
import org.jarsi.devicewatch.data.NotificationStats
import org.jarsi.devicewatch.data.SystemStats
import org.jarsi.devicewatch.data.SystemStatsRepository
import org.jarsi.devicewatch.data.UNAVAILABLE_INT
import org.jarsi.devicewatch.data.UsageHistory
import org.jarsi.devicewatch.system.AlertNotifications
import org.jarsi.devicewatch.system.MonitorServiceRelay
import java.time.LocalDate
import java.time.ZoneId
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
    /** Time the display was on in the counting period; -1 without usage access. */
    val screenOnMillis: Long = -1L,
    /** False before Android 9, which logs no screen events: the row is hidden. */
    val screenOnTrackingSupported: Boolean = true,
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
    /** The pre-1.6 look: wallpaper colours and the regular number font. */
    val classicLook: Boolean = false,
    /** The optional alerts the user has switched on. */
    val enabledAlerts: Set<HealthAlert> = emptySet(),
    /** Foreground/background shares and roaming for the counting period; null until read. */
    val dataBreakdown: DataBreakdown? = null,
    /** What the used storage holds; null until read or when unavailable. */
    val storageBreakdown: StorageBreakdown? = null,
    /** Settings and states that change while the app runs; null until read. */
    val deviceState: DeviceState? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: SystemStatsRepository,
    private val widgetController: WidgetController,
    private val settings: AppSettingsRepository,
    private val appUsageRepository: AppUsageRepository,
    private val notificationStats: NotificationStats,
    private val usageHistory: UsageHistory,
    private val monitorRelay: MonitorServiceRelay,
    private val alertNotifications: AlertNotifications,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        // Synchronous prefs reads so the first composition already knows whether
        // to show the intro and which look to draw — no flash of the other one.
        _uiState.update {
            it.copy(
                onboardingCompleted = settings.onboardingShown(),
                classicLook = settings.classicLook(),
                enabledAlerts = HealthAlert.entries.filter(settings::alertEnabled).toSet(),
            )
        }
    }

    /** Marks the first-run intro completed (or skipped). */
    fun completeOnboarding() {
        settings.setOnboardingShown()
        _uiState.update { it.copy(onboardingCompleted = true) }
    }

    /** Persists the look switch; the activity themes the whole tree from this state. */
    fun onClassicLookChange(enabled: Boolean) {
        settings.setClassicLook(enabled)
        _uiState.update { it.copy(classicLook = enabled) }
    }

    fun onAlertToggle(alert: HealthAlert, enabled: Boolean) {
        settings.setAlertEnabled(alert, enabled)
        // A disabled alert is never evaluated again, so nothing else would take
        // down one already in the shade.
        if (!enabled) alertNotifications.cancel(alert)
        _uiState.update {
            it.copy(enabledAlerts = if (enabled) it.enabledAlerts + alert else it.enabledAlerts - alert)
        }
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

    /**
     * Bumped by every refresh; a refresh publishes only while it is still the
     * latest. Refreshes overlap (resume, pull, a counting-mode change), and an
     * older one finishing last would otherwise overwrite the newer figures — a
     * day's data breakdown landing over the billing cycle's. Main thread only.
     */
    private var refreshGeneration = 0

    private suspend fun refreshInternal() {
        val generation = ++refreshGeneration
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
        val supportsScreenOn = appUsageRepository.supportsScreenOnTracking()
        if (hasUsageAccess) {
            appUsageRepository.screenTimeByDay(HISTORY_BACKFILL_DAYS)
                .forEach { (day, millis) -> usageHistory.recordScreenTime(day, millis) }
            appUsageRepository.unlockCountsByDay(HISTORY_BACKFILL_DAYS)
                .forEach { (day, count) -> usageHistory.recordUnlocks(day, count) }
            appUsageRepository.screenOnByDay(HISTORY_BACKFILL_DAYS)
                .forEach { (day, millis) -> usageHistory.recordScreenOn(day, millis) }
            appUsageRepository.usageTotalsToday()?.let { totals ->
                usageHistory.recordScreenTime(today, totals.screenTimeMillis)
                usageHistory.recordUnlocks(today, totals.unlockCount)
            }
        }
        storageUsedBytes(stats.usedStorageGb)?.let { usageHistory.recordStorageUsed(today, it) }
        usageHistory.purge(today)


        val notificationAccess = notificationStats.isListenerEnabled()
        // "Previous period vs now", day-count-aligned. The stores return zeros for
        // days they have nothing on, so a metric's previous value is only trusted
        // once something was recorded on or before the previous window's first
        // day — the same "collected since" rule the history list applies.
        val comparison = PeriodComparison.windows(
            settings.dataCounterMode(), settings.cycleStartDay(), today
        )?.let { w ->
            val oldest = PeriodComparison.oldestRetainedDay(today)
            val screenObserved = usageHistory.screenTimeBetween(oldest, w.previousStart) > 0L
            val unlocksObserved = usageHistory.unlocksBetween(oldest, w.previousStart) > 0
            val notificationsObserved = notificationStats.totalBetween(oldest, w.previousStart) > 0
            PeriodComparisonData(
                screenTimeNowMillis = usageHistory.screenTimeBetween(w.currentStart, w.currentEnd),
                screenTimePrevMillis = if (screenObserved) {
                    usageHistory.screenTimeBetween(w.previousStart, w.previousEnd)
                } else {
                    null
                },
                unlocksNow = usageHistory.unlocksBetween(w.currentStart, w.currentEnd),
                unlocksPrev = if (unlocksObserved) {
                    usageHistory.unlocksBetween(w.previousStart, w.previousEnd)
                } else {
                    null
                },
                notificationsNow = notificationStats.totalBetween(w.currentStart, w.currentEnd),
                notificationsPrev = if (notificationsObserved) {
                    notificationStats.totalBetween(w.previousStart, w.previousEnd)
                } else {
                    null
                },
                daysCompared = w.daysCompared,
            )
        }
        if (generation != refreshGeneration) return
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
                screenOnMillis = if (hasUsageAccess && supportsScreenOn) {
                    usageHistory.screenOnBetween(periodStart, today)
                } else {
                    -1L
                },
                screenOnTrackingSupported = supportsScreenOn,
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

        // The breakdowns scan every app and every UID's traffic, which can take a
        // second or two: the figures above are on screen first, these fill in after.
        val breakdown = repository.dataBreakdown(
            periodStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        val storage = repository.storageBreakdown()
        val deviceState = repository.deviceState()
        if (generation != refreshGeneration) return
        _uiState.update {
            it.copy(dataBreakdown = breakdown, storageBreakdown = storage, deviceState = deviceState)
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

    /**
     * Persists the counter mode, has the monitor re-check the quota against the new
     * period at once, and re-queries stats so the widget shows the new period.
     */
    fun onDataCounterModeSelected(mode: DataCounterMode) {
        settings.setDataCounterMode(mode)
        _uiState.update { it.copy(dataCounterMode = mode) }
        monitorRelay.dataQuotaChanged()
        refresh()
    }

    /** Live-updates the cycle-day slider value without persisting (called on every drag tick). */
    fun onCycleStartDayChange(day: Int) {
        _uiState.update { it.copy(cycleStartDay = day.coerceIn(1, 31)) }
    }

    /** Persists the dragged cycle start day, has the monitor re-check the quota, and re-queries stats. */
    fun commitCycleStartDay() {
        settings.setCycleStartDay(_uiState.value.cycleStartDay)
        monitorRelay.dataQuotaChanged()
        refresh()
    }

    /** Live-updates the charge-reminder value without persisting; 0 switches it off. */
    fun onChargeLimitChange(percent: Int) {
        val value = if (percent <= 0) 0 else percent.coerceIn(CHARGE_LIMIT_MIN, CHARGE_LIMIT_MAX)
        _uiState.update { it.copy(chargeLimitPercent = value) }
    }

    /**
     * Persists the chosen charge-reminder level and has the monitor apply it now:
     * a phone already sitting full on the charger may not send another battery
     * broadcast for a long time, and switching the reminder off must also take
     * down a reminder that is already showing.
     */
    fun onCommitChargeLimit() {
        settings.setChargeLimitPercent(_uiState.value.chargeLimitPercent)
        monitorRelay.chargeLimitChanged()
    }

    /** Live-updates the data-quota slider value without persisting; 0 switches it off. */
    fun onDataQuotaChange(quotaGb: Double) {
        val value = if (quotaGb <= 0.0) 0.0 else quotaGb.coerceIn(DATA_QUOTA_MIN_GB, DATA_QUOTA_MAX_GB)
        _uiState.update { it.copy(dataQuotaGb = value) }
    }

    /**
     * Persists the chosen quota, has the monitor re-check it now (so its usage
     * watch is re-armed for the new thresholds) and re-queries stats so the
     * counters show it right away.
     */
    fun onCommitDataQuota() {
        settings.setDataQuotaGb(_uiState.value.dataQuotaGb)
        monitorRelay.dataQuotaChanged()
        refresh()
    }

    private fun currentTime(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    private companion object {
        /** Android keeps detailed usage events for roughly a week. */
        private const val HISTORY_BACKFILL_DAYS = 7
    }
}
