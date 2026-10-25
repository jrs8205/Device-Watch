package org.jarsi.devicewatch.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.jarsi.devicewatch.data.HealthAlert
import org.jarsi.devicewatch.data.QuickMetric
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
import org.jarsi.devicewatch.data.CpuProfileNotice
import org.jarsi.devicewatch.data.CpuProfiles
import org.jarsi.devicewatch.data.DataPeriodCalculator
import org.jarsi.devicewatch.data.DeviceInfo
import org.jarsi.devicewatch.data.NotificationStats
import org.jarsi.devicewatch.data.PrivilegedAccess
import org.jarsi.devicewatch.data.RootAccess
import org.jarsi.devicewatch.data.RootShell
import org.jarsi.devicewatch.data.ShizukuAccess
import org.jarsi.devicewatch.data.ShizukuShell
import org.jarsi.devicewatch.data.SystemStats
import org.jarsi.devicewatch.data.SystemStatsRepository
import org.jarsi.devicewatch.data.UNAVAILABLE_INT
import org.jarsi.devicewatch.data.UsageHistory
import org.jarsi.devicewatch.data.recordTotals
import org.jarsi.devicewatch.system.AlertNotifications
import org.jarsi.devicewatch.system.MonitorServiceRelay
import java.time.LocalDate
import java.time.ZoneId
import org.jarsi.devicewatch.widget.WidgetController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    /** GitHub #2: pure black widget card instead of the theme's dark blue-grey. */
    val widgetBlackBackground: Boolean = false,
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
    val rootStatus: RootStatus = RootStatus.OFF,
    val shizukuStatus: ShizukuStatus = ShizukuStatus.OFF,
    /** Root went off with processor limits still in the kernel: they last until a restart. */
    val cpuCapsKeptUntilRestart: Boolean = false,
    /** The optional alerts the user has switched on. */
    val enabledAlerts: Set<HealthAlert> = emptySet(),
    /** The figures chosen for the monitor's notification; none keeps it silent. */
    val notificationMetrics: Set<QuickMetric> = emptySet(),
    /** Quick Settings tiles draw their figure in place of their icon. */
    val tileValueIcons: Boolean = true,
    /** Foreground/background shares and roaming for the counting period; null until read. */
    val dataBreakdown: DataBreakdown? = null,
    /** What the used storage holds; null until read or when unavailable. */
    val storageBreakdown: StorageBreakdown? = null,
    /** Settings and states that change while the app runs; null until read. */
    val deviceState: DeviceState? = null,
)

/**
 * Where the root switch stands; DENIED and NO_SU are off with a reason to show.
 * RESTORING is on its way off: the phone's own processor limits go back first.
 */
enum class RootStatus { OFF, REQUESTING, ON, DENIED, NO_SU, RESTORING }

/**
 * Where the Shizuku switch stands. DENIED and NOT_RUNNING are off with a reason
 * to show; WAITING is on, with the Shizuku service itself down.
 */
enum class ShizukuStatus { OFF, REQUESTING, ON, WAITING, DENIED, NOT_RUNNING }

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
    private val rootShell: RootShell,
    private val shizukuShell: ShizukuShell,
    private val cpuProfiles: CpuProfiles,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        // Synchronous prefs reads so the first composition already knows whether
        // to show the intro and which look to draw — no flash of the other one.
        val access = settings.privilegedAccess()
        _uiState.update {
            it.copy(
                onboardingCompleted = settings.onboardingShown(),
                classicLook = settings.classicLook(),
                rootStatus = if (access == PrivilegedAccess.ROOT) RootStatus.ON else RootStatus.OFF,
                shizukuStatus = if (access == PrivilegedAccess.SHIZUKU) ShizukuStatus.ON else ShizukuStatus.OFF,
                enabledAlerts = HealthAlert.entries.filter(settings::alertEnabled).toSet(),
                notificationMetrics = settings.notificationMetrics(),
                tileValueIcons = settings.tileValueIcons(),
            )
        }
        viewModelScope.launch {
            // Shizuku's binder arrives a moment after the process starts: that
            // moment is not Shizuku being down.
            if (!shizukuShell.alive.value) delay(SHIZUKU_STARTUP_GRACE_MILLIS)
            shizukuShell.alive.collect(::onShizukuAlive)
        }
        viewModelScope.launch {
            rootShell.lost.collect { lost -> if (lost) onRootLost() }
        }
        viewModelScope.launch {
            settings.privilegedAccessFlow.collect(::onPrivilegedAccess)
        }
        viewModelScope.launch {
            cpuProfiles.status.collect { status ->
                _uiState.update { it.copy(cpuCapsKeptUntilRestart = status.notice == CpuProfileNotice.KEPT_UNTIL_RESTART) }
            }
        }
        // The service already samples every five seconds, even without a widget.
        // Observe those readings instead of repeating the costly usage/storage scans.
        viewModelScope.launch {
            repository.latestStats.filterNotNull().collect(::publishStats)
        }
    }

    private fun publishStats(stats: SystemStats) {
        if (stats.dataSettingsGeneration != settings.dataSettingsGeneration()) return
        _uiState.update { state ->
            if (stats.readAtElapsedMillis < (state.stats?.readAtElapsedMillis ?: -1L)) state
            else state.copy(stats = stats, lastUpdated = currentTime())
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

    /**
     * Numbers the requests for root and for Shizuku. Switched off and on again
     * while the first dialog was still up, both requests would otherwise share
     * one REQUESTING state: the first one's late refusal would mark the second
     * refused, and the second one's grant would then find the switch off and
     * close the very shell it had just been given.
     */
    private var rootRequest = 0
    private var shizukuRequest = 0

    /**
     * Switching root mode on asks the root manager first and persists the setting
     * only once it said yes, so the monitor never polls a shell it was refused.
     */
    fun onRootModeChange(enabled: Boolean) {
        // Already on its way off: neither direction can interrupt the restore.
        if (_uiState.value.rootStatus == RootStatus.RESTORING) return
        if (!enabled) {
            if (settings.privilegedAccess() == PrivilegedAccess.ROOT && profileNeedsRoot()) {
                // The phone's own limits go back first, while root is still the
                // access: once OFF is saved, the gate refuses every root command.
                _uiState.update { it.copy(rootStatus = RootStatus.RESTORING) }
                viewModelScope.launch {
                    // Off is what the user asked for: it lands even if the screen
                    // goes away or the restore fails.
                    withContext(NonCancellable) {
                        restoreStockQuietly()
                        switchRootOff()
                    }
                }
                return
            }
            switchRootOff()
            return
        }
        if (_uiState.value.rootStatus == RootStatus.REQUESTING) return
        _uiState.update { it.copy(rootStatus = RootStatus.REQUESTING) }
        val request = ++rootRequest
        viewModelScope.launch {
            val status = when (rootShell.requestAccess()) {
                RootAccess.GRANTED -> RootStatus.ON
                RootAccess.DENIED -> RootStatus.DENIED
                RootAccess.NO_SU -> RootStatus.NO_SU
            }
            // A later request has taken over: its answer is the one that counts,
            // and this one must not touch the shell that request may hold.
            if (request != rootRequest) return@launch
            // Switched off while the root manager was still asking: off wins.
            if (_uiState.value.rootStatus != RootStatus.REQUESTING) {
                rootShell.close()
                return@launch
            }
            if (status != RootStatus.ON) {
                _uiState.update { it.copy(rootStatus = status) }
                return@launch
            }
            // One shell at a time: root reads everything Shizuku does.
            settings.setPrivilegedAccess(PrivilegedAccess.ROOT)
            shizukuShell.close()
            _uiState.update { it.copy(rootStatus = RootStatus.ON, shizukuStatus = ShizukuStatus.OFF) }
        }
    }

    /** Limits held, or a profile waiting to be set after a restart: root has something to undo. */
    private fun profileNeedsRoot(): Boolean = cpuProfiles.status.value.let { it.holdsCaps || it.waitingAfterRestart }

    private fun rootStatusNow(): RootStatus = when {
        settings.privilegedAccess() == PrivilegedAccess.ROOT -> RootStatus.ON
        rootShell.lost.value -> RootStatus.DENIED
        else -> RootStatus.OFF
    }

    /** A restore that throws still lets root go off; the controller then notes what stays. */
    private suspend fun restoreStockQuietly() {
        try {
            cpuProfiles.restoreStockBeforeRootOff()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun switchRootOff() {
        if (settings.privilegedAccess() == PrivilegedAccess.ROOT) settings.setPrivilegedAccess(PrivilegedAccess.OFF)
        rootShell.close()
        _uiState.update { it.copy(rootStatus = RootStatus.OFF) }
    }

    /**
     * As [onRootModeChange], through Shizuku: the setting is persisted only once
     * Shizuku granted its permission.
     */
    fun onShizukuModeChange(enabled: Boolean) {
        if (!enabled) {
            if (settings.privilegedAccess() == PrivilegedAccess.SHIZUKU) settings.setPrivilegedAccess(PrivilegedAccess.OFF)
            shizukuShell.close()
            _uiState.update { it.copy(shizukuStatus = ShizukuStatus.OFF) }
            return
        }
        if (_uiState.value.shizukuStatus == ShizukuStatus.REQUESTING) return
        _uiState.update { it.copy(shizukuStatus = ShizukuStatus.REQUESTING) }
        val request = ++shizukuRequest
        viewModelScope.launch {
            val status = when (shizukuShell.requestAccess()) {
                ShizukuAccess.GRANTED -> ShizukuStatus.ON
                ShizukuAccess.DENIED -> ShizukuStatus.DENIED
                ShizukuAccess.NOT_RUNNING -> ShizukuStatus.NOT_RUNNING
            }
            // A later request has taken over: its answer is the one that counts.
            if (request != shizukuRequest) return@launch
            // Switched off while Shizuku was still asking: off wins.
            if (_uiState.value.shizukuStatus != ShizukuStatus.REQUESTING) return@launch
            if (status != ShizukuStatus.ON) {
                _uiState.update { it.copy(shizukuStatus = status) }
                return@launch
            }
            // Shizuku takes root's place: the processor limits root set go back first.
            withContext(NonCancellable) {
                val restoring = settings.privilegedAccess() == PrivilegedAccess.ROOT && profileNeedsRoot()
                if (restoring) {
                    // Root is on its way out: the Kernel page closes and the switch says why, as when root is switched off.
                    _uiState.update { it.copy(rootStatus = RootStatus.RESTORING) }
                    restoreStockQuietly()
                }
                // Switched off, or asked again, while the limits were going back: that later word wins.
                if (request != shizukuRequest || _uiState.value.shizukuStatus != ShizukuStatus.REQUESTING) {
                    // Root as it stands now, not as it stood: it may have gone while the limits went back.
                    if (restoring) _uiState.update { it.copy(rootStatus = rootStatusNow()) }
                    return@withContext
                }
                settings.setPrivilegedAccess(PrivilegedAccess.SHIZUKU)
                rootShell.close()
                _uiState.update { it.copy(shizukuStatus = ShizukuStatus.ON, rootStatus = RootStatus.OFF) }
            }
        }
    }

    /**
     * The setting is what every screen shares: root switched off by a restore
     * that outlived the screen that started it, or by the monitor finding root
     * gone, shows on a screen opened since. A switch still asking, or refused,
     * keeps saying so; the setting only ever moves once an answer came.
     */
    private fun onPrivilegedAccess(access: PrivilegedAccess) {
        _uiState.update { state ->
            state.copy(
                rootStatus = when {
                    access == PrivilegedAccess.ROOT ->
                        if (state.rootStatus == RootStatus.RESTORING) RootStatus.RESTORING else RootStatus.ON
                    state.rootStatus == RootStatus.ON || state.rootStatus == RootStatus.RESTORING -> RootStatus.OFF
                    else -> state.rootStatus
                },
                shizukuStatus = when {
                    access == PrivilegedAccess.SHIZUKU ->
                        if (state.shizukuStatus == ShizukuStatus.WAITING) ShizukuStatus.WAITING else ShizukuStatus.ON
                    state.shizukuStatus == ShizukuStatus.ON || state.shizukuStatus == ShizukuStatus.WAITING -> ShizukuStatus.OFF
                    else -> state.shizukuStatus
                },
            )
        }
        // The device info is read once per screen, and one of its facts (the battery's
        // design capacity on a phone that keeps the file from the app) comes through
        // the shell: a switch of access, either way, is a reason to read it again, a
        // read still under way included. The flow's first value is not a switch; the
        // screen asks for the info then. The device state's developer switches come
        // through the shell on Android 17 the same way, and nothing else re-reads the
        // state until the next refresh.
        if (deviceInfoRequested) loadDeviceInfo()
        if (deviceStateRequested) reloadDeviceState()
    }

    /**
     * Root that was granted is gone: revoked, run out, or the root manager removed.
     * The switch goes off and says so, instead of claiming root while every
     * reading has quietly fallen back. The setting itself is switched off where
     * the loss is found ([org.jarsi.devicewatch.data.SelectedPrivilegedShell]),
     * since the monitor runs without a screen.
     */
    private fun onRootLost() {
        viewModelScope.launch { cpuProfiles.onRootGone() }
        if (_uiState.value.rootStatus != RootStatus.ON) return
        rootShell.close()
        _uiState.update { it.copy(rootStatus = RootStatus.DENIED) }
    }

    override fun onCleared() {
        // A request the root manager is still asking about must not turn into a
        // root shell nobody is there to use.
        if (_uiState.value.rootStatus == RootStatus.REQUESTING) rootShell.close()
        super.onCleared()
    }

    /**
     * Shizuku stops at every reboot and stays down until the user starts it; the
     * switch stays on meanwhile and says what it is waiting for.
     */
    private fun onShizukuAlive(alive: Boolean) {
        _uiState.update { state ->
            when {
                state.shizukuStatus == ShizukuStatus.ON && !alive -> state.copy(shizukuStatus = ShizukuStatus.WAITING)
                state.shizukuStatus == ShizukuStatus.WAITING && alive -> state.copy(shizukuStatus = ShizukuStatus.ON)
                else -> state
            }
        }
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

    fun onNotificationMetricToggle(metric: QuickMetric, enabled: Boolean) {
        val chosen = settings.notificationMetrics().let { if (enabled) it + metric else it - metric }
        settings.setNotificationMetrics(chosen)
        monitorRelay.notificationMetricsChanged()
        _uiState.update { it.copy(notificationMetrics = chosen) }
    }

    // Tiles read the setting each time the panel opens, so nothing else needs telling.
    fun onTileValueIconsChange(enabled: Boolean) {
        settings.setTileValueIcons(enabled)
        _uiState.update { it.copy(tileValueIcons = enabled) }
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
        publishStats(stats)
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
            appUsageRepository.usageTotalsToday()?.let { totals -> usageHistory.recordTotals(today, totals) }
        }
        storageUsedBytes(stats.usedStorageGb)?.let { usageHistory.recordStorageUsed(today, it, stats.readAtElapsedMillis) }
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
                isWidgetInstalled = widgetInstalled,
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
        // A refresh already overtaken must not number a state read of its own: the
        // newer refresh's read would then look overtaken too, and nobody would publish.
        if (generation != refreshGeneration) return
        deviceStateRequested = true
        val stateGeneration = ++deviceStateGeneration
        val deviceState = repository.deviceState()
        if (generation != refreshGeneration) return
        _uiState.update {
            it.copy(
                dataBreakdown = breakdown,
                storageBreakdown = storage,
                // Access switched under this read: the read that switch started is the newer one.
                deviceState = if (stateGeneration == deviceStateGeneration) deviceState else it.deviceState,
            )
        }
    }

    /** Whether a refresh has read the device state; a switch of access reads it again from then on. */
    private var deviceStateRequested = false

    /** Counts the device-state reads, so a read overtaken by a newer one (access switched under it) is not shown. */
    private var deviceStateGeneration = 0

    /**
     * Reads the device state again when extended access is switched: the developer
     * switches Android 17 hides from apps come through the shell, so the rows
     * change with the access. Only the latest read's result reaches the screen.
     */
    private fun reloadDeviceState() {
        val generation = ++deviceStateGeneration
        viewModelScope.launch {
            val state = repository.deviceState()
            if (generation == deviceStateGeneration) _uiState.update { it.copy(deviceState = state) }
        }
    }

    /** Whether the screen has asked for the device info; a switch of access reads it again from then on. */
    private var deviceInfoRequested = false

    /** Counts the reads, so a read overtaken by a newer one (access switched under it) is not shown. */
    private var deviceInfoGeneration = 0

    /**
     * Loads the static device facts (build, SoC, display, memory, the battery's design
     * capacity): once per screen, and again when extended access is switched, since
     * the design capacity may come through the shell. Only the latest read's result
     * reaches the screen: an older one that finishes later was made under the old access.
     */
    fun loadDeviceInfo() {
        deviceInfoRequested = true
        val generation = ++deviceInfoGeneration
        viewModelScope.launch {
            val info = repository.getDeviceInfo()
            if (generation == deviceInfoGeneration) _uiState.update { it.copy(deviceInfo = info) }
        }
    }

    /** Loads widget opacity and the shared black-background setting. */
    fun loadWidgetAppearance() {
        viewModelScope.launch {
            widgetController.currentOpacity()?.let { saved ->
                _uiState.update { it.copy(widgetOpacity = saved) }
            }
            widgetController.currentBlackBackground()?.let { saved ->
                _uiState.update { it.copy(widgetBlackBackground = saved) }
            }
        }
    }

    /** Persists the black-background switch to every installed widget and re-renders them. */
    fun onWidgetBlackBackgroundChange(black: Boolean) {
        _uiState.update { it.copy(widgetBlackBackground = black) }
        viewModelScope.launch {
            widgetController.setBlackBackground(black)
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

        /** How long Shizuku's binder may take to arrive after the process started. */
        private const val SHIZUKU_STARTUP_GRACE_MILLIS = 2_000L
    }
}
