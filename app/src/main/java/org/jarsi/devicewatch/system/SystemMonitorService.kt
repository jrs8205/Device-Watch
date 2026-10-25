package org.jarsi.devicewatch.system

import android.app.Service
import android.app.usage.NetworkStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import org.jarsi.devicewatch.R
import android.os.BatteryManager
import java.util.Locale
import org.jarsi.devicewatch.data.HealthAlert
import org.jarsi.devicewatch.data.AlertStep
import org.jarsi.devicewatch.data.AlertLogic
import org.jarsi.devicewatch.data.storageUsedBytes
import org.jarsi.devicewatch.data.AppSettingsRepository
import org.jarsi.devicewatch.data.AppUsageRepository
import org.jarsi.devicewatch.data.BatteryHistory
import org.jarsi.devicewatch.data.BatterySample
import org.jarsi.devicewatch.data.BatteryStatusReader
import org.jarsi.devicewatch.data.ChargeAnchorLogic
import org.jarsi.devicewatch.data.ChargeAnchorStore
import org.jarsi.devicewatch.data.CpuProfiles
import org.jarsi.devicewatch.data.DataQuotaLogic
import org.jarsi.devicewatch.data.QuotaReadingAction
import org.jarsi.devicewatch.data.QuotaSettingsStamp
import org.jarsi.devicewatch.data.SystemStats
import org.jarsi.devicewatch.data.SystemStatsRepository
import org.jarsi.devicewatch.data.UsageHistory
import org.jarsi.devicewatch.data.PrivilegedAccess
import org.jarsi.devicewatch.data.QuickMetric
import org.jarsi.devicewatch.data.recordTotals
import org.jarsi.devicewatch.quick.QuickMetricFormatter
import org.jarsi.devicewatch.quick.QuickStatsSource
import org.jarsi.devicewatch.quick.quickMetricLabels
import org.jarsi.devicewatch.presentation.ui.durationText
import org.jarsi.devicewatch.widget.WidgetStateUpdater
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class SystemMonitorService : Service() {

    @Inject lateinit var repository: SystemStatsRepository
    @Inject lateinit var appUsageRepository: AppUsageRepository
    @Inject lateinit var usageHistory: UsageHistory
    @Inject lateinit var chargeAnchorStore: ChargeAnchorStore
    @Inject lateinit var batteryHistory: BatteryHistory
    @Inject lateinit var appSettings: AppSettingsRepository
    @Inject lateinit var alertNotifications: AlertNotifications
    private val alertController by lazy { HealthAlertController(appSettings, alertNotifications) }
    @Inject lateinit var batteryStatus: BatteryStatusReader
    @Inject lateinit var chargeStartScreensaver: ChargeStartScreensaver
    @Inject lateinit var cpuProfiles: CpuProfiles
    @Inject lateinit var quickStats: QuickStatsSource

    private val notifications by lazy { MonitorNotifications(this) }
    private val live by lazy {
        LiveNotificationController(
            chosen = { appSettings.notificationMetrics() },
            read = ::liveText,
            postLive = { text -> notifications.post(NOTIFICATION_ID, notifications.live(text)) },
            postQuiet = { notifications.post(NOTIFICATION_ID, notifications.quiet()) },
            removeLive = ::replaceWithSilentForeground,
            isShowing = { notifications.isShowing(NOTIFICATION_ID) },
        )
    }

    private var lastUsageRefreshMs = 0L

    /** Latest level from BATTERY_CHANGED; POWER_DISCONNECTED carries no battery extras. */
    @Volatile private var lastBatteryLevel = -1

    /**
     * Charge-reminder latch. In-memory on purpose: only a service restart in the
     * middle of a charge can lose it, and the worst case is one repeated reminder.
     */
    private var chargeLimitState = ChargeLimitLogic.State()

    /** The data-usage watch armed for the next quota alert, if any (see [armUsageCallback]). */
    @Volatile private var usageCallback: NetworkStatsManager.UsageCallback? = null

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Default)
    private var batteryReceiver: BroadcastReceiver? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var configurationReceiver: BroadcastReceiver? = null
    private var updateJob: Job? = null
    @Volatile private var isScreenOn = true

    override fun onCreate() {
        super.onCreate()
        startNotification()
        registerBatteryTracker()
        registerScreenTracker()
        registerConfigurationTracker()
        // START_STICKY can recreate the service while the screen is already off, and
        // SCREEN_ON/OFF are not sticky broadcasts — an assumed-true default would
        // keep the 5 s loop polling until the next real screen event.
        isScreenOn = (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: true
        if (isScreenOn) startUpdateLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CHARGE_LIMIT_CHANGED -> onChargeLimitChanged()
            ACTION_DATA_QUOTA_CHANGED -> onDataQuotaChanged()
            ACTION_NOTIFICATION_METRICS_CHANGED -> onNotificationMetricsChanged()
            ACTION_LIVE_NOTIFICATION_DISMISSED -> live.onDismissed()
        }
        return START_STICKY
    }

    /**
     * Settings just changed the data quota. A fresh reading under the new quota
     * re-runs the check at once, which re-arms (or releases) the usage watch for
     * the new thresholds instead of leaving it on the old quota's until the next
     * screen-on check — possibly hours away with the screen off.
     */
    private fun onDataQuotaChanged() {
        onDataSettingsChanged(applicationContext) {
            serviceScope.launch {
                val stats = updateWidgetStats(applicationContext)
                if (stats != null) maybeNotifyDataQuota(stats)
            }
        }
    }

    /**
     * Settings just changed the figures the notification shows: none puts the
     * silent notification back at once, some post them at once with a fresh
     * reading, whatever a swipe held back.
     */
    private fun onNotificationMetricsChanged() {
        serviceScope.launch {
            if (live.onSelectionChanged()) updateWidgetStats(applicationContext)
        }
    }

    /**
     * Settings just changed the charge limit. The latch belongs to the old limit,
     * so it is cleared, the reminder on screen (if any) advised on the old limit,
     * so it is withdrawn, and the new limit is checked against the sticky battery
     * state right away rather than at the next broadcast — a phone sitting full on
     * the charger may not send one for hours.
     */
    private fun onChargeLimitChanged() {
        chargeLimitState = ChargeLimitLogic.onLimitChanged(chargeLimitState)
        ChargeLimitNotifier.cancel(applicationContext)
        val level = batteryStatus.currentLevel() ?: return
        evaluateChargeLimit(level, plugged = batteryStatus.isCharging())
    }

    /**
     * Runs the reminder rule against one battery reading. The once-per-plug latch is
     * set only after the notification was actually posted: a reminder the system
     * refused (notifications not permitted) must be tried again next time.
     */
    private fun evaluateChargeLimit(level: Int, plugged: Boolean) {
        // The limit is re-read on every call because settings can change while the
        // service runs; SharedPreferences is memory-cached, so this stays cheap.
        val decision = ChargeLimitLogic.onBatteryChanged(
            chargeLimitState,
            limitPercent = appSettings.chargeLimitPercent(),
            level = level,
            isPlugged = plugged,
        )
        if (!decision.notify) return
        // Same quirky-EXTRA_SCALE clamp as the battery sample, so the notification
        // can never claim more than 100 %.
        if (ChargeLimitNotifier.show(applicationContext, level.coerceIn(0, 100))) {
            chargeLimitState = decision.state
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disarmUsageCallback()
        batteryReceiver?.let {
            unregisterReceiver(it)
        }
        screenReceiver?.let {
            unregisterReceiver(it)
        }
        configurationReceiver?.let {
            unregisterReceiver(it)
        }
        stopUpdateLoop()
        serviceJob.cancel()
    }

    private fun registerScreenTracker() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        isScreenOn = true
                        // A live notification swiped away comes back with the screen.
                        live.onScreenOn()
                        startUpdateLoop()
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        isScreenOn = false
                        stopUpdateLoop()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun registerConfigurationTracker() {
        configurationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
                    serviceScope.launch {
                        updateWidgetStats(context)
                    }
                }
            }
        }
        registerReceiver(configurationReceiver, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))
    }

    private fun startUpdateLoop() {
        if (updateJob?.isActive == true) return
        
        updateJob = serviceScope.launch {
            while (isActive && isScreenOn) {
                val stats = updateWidgetStats(this@SystemMonitorService)
                maybeRefreshUsage(stats)
                enforceCpuProfile()
                delay(5000)
            }
        }
    }

    /**
     * Holds the processor profile chosen on the Kernel page: Android's power HAL
     * can put its own value back now and then, and this pass sets the limit
     * again. Only here, in the screen-on loop: nothing runs as root while the
     * screen is off. Without root, or with nothing held, it reads no shell.
     */
    private suspend fun enforceCpuProfile() {
        try {
            cpuProfiles.enforce()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Records today's screen time, unlock count and screen-on time, pushes the screen-time text to the
     * widget and checks the data quota against [stats] (the reading the widget was just
     * updated with; null when that read failed). Throttled to about once a minute: it
     * needs a usage-events pass, which must never run at the 5-second stats cadence.
     */
    private suspend fun maybeRefreshUsage(stats: SystemStats?) {
        val now = SystemClock.elapsedRealtime()
        if (lastUsageRefreshMs != 0L && now - lastUsageRefreshMs < USAGE_REFRESH_INTERVAL_MS) return
        lastUsageRefreshMs = now
        // Before the usage-events pass, which bails out without usage access.
        if (stats != null) {
            maybeNotifyDataQuota(stats)
            storageUsedBytes(stats.usedStorageGb)?.let {
                usageHistory.recordStorageUsed(LocalDate.now(), it, stats.readAtElapsedMillis)
            }
            evaluateStorageAlert(stats)
        }
        try {
            val totals = appUsageRepository.usageTotalsToday() ?: return
            usageHistory.recordTotals(LocalDate.now(), totals)
            WidgetStateUpdater.updateScreenTimeText(
                applicationContext,
                durationText(applicationContext, totals.screenTimeMillis)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopUpdateLoop() {
        updateJob?.cancel()
        updateJob = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startNotification() {
        notifications.ensureChannels()
        val notification = if (appSettings.notificationMetrics().isEmpty()) {
            notifications.quiet()
        } else {
            // The figures follow with the first reading.
            notifications.live(null)
        }
        startForegroundWith(notification)
    }

    private fun startForegroundWith(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * The silent notification could not replace the live one: its channel is
     * blocked, and a post to a blocked channel leaves the old notification up. Only
     * the service itself can take its own notification down, so it drops it and
     * goes foreground again with the silent one, which the blocked channel keeps
     * out of sight. Runs on a change in Settings, with the app in front, where
     * going foreground is allowed.
     */
    private fun replaceWithSilentForeground() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
            startForegroundWith(notifications.quiet())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun registerBatteryTracker() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // Charger-connection tally and the "since charge" anchor live here
                // because this runtime receiver is reliable; manifest-registered
                // POWER_CONNECTED is an implicit broadcast that modern Android does
                // not deliver.
                when (intent.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val level = batteryPercent(intent)
                        if (level >= 0) lastBatteryLevel = level
                        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        chargeAnchorStore.save(
                            ChargeAnchorLogic.onBatteryChanged(
                                chargeAnchorStore.load(),
                                level = level.coerceAtLeast(0),
                                isPlugged = plugged,
                                isFull = status == BatteryManager.BATTERY_STATUS_FULL || level >= 100,
                                nowMillis = System.currentTimeMillis(),
                            )
                        )
                        // The store decides itself which samples are worth appending, but
                        // every call still hits the filesystem (mkdirs + a retention pass
                        // over the day files), so it runs off the main thread. The sample
                        // is timestamped here, at delivery. A quirky EXTRA_SCALE can push
                        // the percentage past 100, which the store's read path would later
                        // drop as corruption, so clamp it too.
                        if (level >= 0) {
                            val sample = BatterySample(
                                timeMillis = System.currentTimeMillis(),
                                level = level.coerceIn(0, 100),
                                charging = plugged,
                                // Tenths of a degree; kept for the charge history's peak.
                                temperatureDeciC = intent
                                    .getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                                    .takeIf { it != Int.MIN_VALUE },
                            )
                            serviceScope.launch {
                                recordBatterySample(sample)
                                evaluateBatteryAlerts(sample)
                            }
                        }
                        evaluateChargeLimit(level, plugged)
                    }
                    Intent.ACTION_POWER_CONNECTED -> {
                        usageHistory.incrementCharge(LocalDate.now())
                        chargeAnchorStore.save(
                            ChargeAnchorLogic.onPowerConnected(chargeAnchorStore.load())
                        )
                        serviceScope.launch(Dispatchers.IO) {
                            // A convenience must never take the monitor down with it.
                            try {
                                chargeStartScreensaver.onPowerConnected()
                            } catch (_: RuntimeException) {
                            }
                        }
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        chargeLimitState = ChargeLimitLogic.onPowerDisconnected(chargeLimitState)
                        // The reminder tells the user to unplug; once they have, it
                        // is stale advice sitting in the shade.
                        ChargeLimitNotifier.cancel(applicationContext)
                        chargeAnchorStore.save(
                            ChargeAnchorLogic.onPowerDisconnected(
                                chargeAnchorStore.load(),
                                level = lastBatteryLevel.coerceAtLeast(0),
                                nowMillis = System.currentTimeMillis(),
                            )
                        )
                    }
                }
                serviceScope.launch {
                    val stats = updateWidgetStats(context)
                    // The 5 s loop stops with the screen (a measured battery saving),
                    // so a quota crossed overnight would otherwise wait for the
                    // morning. Battery broadcasts keep arriving with the screen off
                    // and already pay for this stats read, so the check rides along.
                    if (stats != null) maybeNotifyDataQuota(stats)
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(batteryReceiver, filter)
    }

    /** Pushes a fresh reading to the widgets and returns it; null when the read failed. */
    private suspend fun updateWidgetStats(context: Context): SystemStats? {
        return try {
            val stats = repository.getStats()
            WidgetStateUpdater.updateAll(context.applicationContext, stats)
            refreshLiveNotification(stats)
            stats
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Puts [stats] into the live notification when figures are chosen for it. It
     * rides on every reading the widgets get — the 5 s loop with the screen on and
     * the battery broadcasts — and adds no polling of its own. Today's data is read
     * only when it is one of the figures.
     */
    private suspend fun refreshLiveNotification(stats: SystemStats) {
        try {
            live.refresh(stats)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The figures are a convenience; the monitor must not stop over them.
            e.printStackTrace()
        }
    }

    /** The [chosen] figures of [stats] as the notification's two lines. */
    private suspend fun liveText(chosen: Set<QuickMetric>, stats: SystemStats): LiveNotificationText? {
        val dataToday = if (QuickMetric.DATA_TODAY in chosen) quickStats.dataToday() else null
        val labels = quickMetricLabels(applicationContext)
        val extendedAccess = appSettings.privilegedAccess() != PrivilegedAccess.OFF
        val readings = QuickMetric.entries.filter { it in chosen }
            .map { QuickMetricFormatter.read(it, stats, dataToday, extendedAccess, labels) }
        return liveNotificationText(readings, labels)
    }

    /**
     * Data-quota alerts: 80 % and the limit itself, each at most once per counting
     * period. The latch stays on this device and is keyed by the period start and the
     * quota, so a new day (or billing cycle) re-arms both, a latch written just after
     * the user changed the quota belongs to the old quota only — and a quota switched
     * on mid-period only alerts for what the period has actually used. Synchronized because the
     * screen-on loop and the battery receiver both call it from the service scope,
     * and the latch is read before it is written.
     */
    @Synchronized
    private fun maybeNotifyDataQuota(stats: SystemStats) {
        try {
            // mobileDataTotalGb carries the quota only when one is set AND the
            // period figure is available (usage access granted) — the since-boot
            // fallback must never be compared against a period quota.
            val quotaGb = stats.mobileDataTotalGb
            val action = DataQuotaLogic.classifyReading(
                reading = QuotaSettingsStamp(quotaGb, stats.dataSettingsGeneration),
                current = QuotaSettingsStamp(appSettings.dataQuotaGb(), appSettings.dataSettingsGeneration()),
            )
            when (action) {
                QuotaReadingAction.RELEASE_WATCH -> {
                    disarmUsageCallback()
                    return
                }
                // The quota or its period changed under this reading: a fresh
                // reading under the new settings follows from onDataQuotaChanged.
                QuotaReadingAction.IGNORE -> return
                QuotaReadingAction.CHECK -> Unit
            }
            // The period comes with the reading. Computed here instead, a read that
            // started before midnight and landed after it would latch the new
            // period on the old period's usage.
            val periodStartEpochDay = stats.dataPeriodStartEpochDay
            val pending = DataQuotaLogic.pendingThresholds(
                quotaGb = quotaGb,
                usedGb = stats.mobileDataUsedGb,
                notified80 = appSettings.dataQuotaNotified(periodStartEpochDay, quotaGb, DataQuotaLogic.WARNING_PERCENT),
                notified100 = appSettings.dataQuotaNotified(periodStartEpochDay, quotaGb, DataQuotaLogic.REACHED_PERCENT),
            )
            pending.forEach { threshold ->
                // Latched only once the alert was really posted; a refused one is retried.
                val shown = DataQuotaNotifier.show(
                    applicationContext, threshold, stats.mobileDataUsedGb, quotaGb
                )
                if (shown) appSettings.setDataQuotaNotified(periodStartEpochDay, quotaGb, threshold)
            }
            armUsageCallback(
                quotaGb = quotaGb,
                usedGb = stats.mobileDataUsedGb,
                notified80 = appSettings.dataQuotaNotified(periodStartEpochDay, quotaGb, DataQuotaLogic.WARNING_PERCENT),
                notified100 = appSettings.dataQuotaNotified(periodStartEpochDay, quotaGb, DataQuotaLogic.REACHED_PERCENT),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A quota alert is never worth taking the monitor service down.
            e.printStackTrace()
        }
    }

    /**
     * Has the system watch the mobile counter for the next alert that is still due,
     * so a crossing with the screen off — and no battery broadcast to ride on —
     * alerts all the same. The system calls back once the registered number of
     * bytes has passed since registration, so the threshold is "bytes left to the
     * next check" ([DataQuotaLogic.bytesToNextCheck]) and is re-armed after every
     * check; it stays armed after the period's last alert, so the next period's
     * first crossing is caught too. No polling is added: the screen-off loop stays
     * stopped. Only called under [maybeNotifyDataQuota]'s lock.
     */
    private fun armUsageCallback(
        quotaGb: Double,
        usedGb: Double,
        notified80: Boolean,
        notified100: Boolean,
    ) {
        disarmUsageCallback()
        val bytes = DataQuotaLogic.bytesToNextCheck(quotaGb, usedGb, notified80, notified100)
            ?: return
        val manager = getSystemService(NetworkStatsManager::class.java) ?: return
        val callback = object : NetworkStatsManager.UsageCallback() {
            override fun onThresholdReached(networkType: Int, subscriberId: String?) {
                serviceScope.launch {
                    val fresh = updateWidgetStats(applicationContext)
                    if (fresh != null) maybeNotifyDataQuota(fresh)
                }
            }
        }
        try {
            @Suppress("DEPRECATION") // the network-type overload is the public one
            manager.registerUsageCallback(
                ConnectivityManager.TYPE_MOBILE,
                null,
                bytes,
                callback,
                Handler(Looper.getMainLooper()),
            )
            usageCallback = callback
        } catch (e: Exception) {
            // No usage access, or the system refused: the screen-on loop and the
            // battery broadcasts still carry the check.
            e.printStackTrace()
        }
    }

    private fun disarmUsageCallback() {
        val callback = usageCallback ?: return
        usageCallback = null
        try {
            getSystemService(NetworkStatsManager::class.java)?.unregisterUsageCallback(callback)
        } catch (_: Exception) {
            // Already gone; nothing to release.
        }
    }

    /** Hot-battery and fast-drain alerts from the sample just stored; both off unless switched on. */
    private fun evaluateBatteryAlerts(sample: BatterySample) {
        try {
            applyAlertStep(HealthAlert.HOT_BATTERY, { latched -> AlertLogic.hotBattery(sample.temperatureDeciC, latched) }) {
                val temperature = String.format(Locale.getDefault(), "%.1f °C", (sample.temperatureDeciC ?: 0) / 10.0)
                HealthAlertNotifier.show(
                    applicationContext,
                    HealthAlert.HOT_BATTERY,
                    getString(R.string.alert_hot_title, temperature),
                    getString(R.string.alert_hot_text),
                )
            }
            if (!appSettings.alertEnabled(HealthAlert.FAST_DRAIN)) return
            val drain = AlertLogic.drainPercentPerHour(
                batteryHistory.samplesSince(sample.timeMillis - DRAIN_LOOKBACK_MS),
                sample.timeMillis,
            )
            applyAlertStep(HealthAlert.FAST_DRAIN, { latched -> AlertLogic.fastDrain(drain, sample.charging, latched) }) {
                HealthAlertNotifier.show(
                    applicationContext,
                    HealthAlert.FAST_DRAIN,
                    getString(R.string.alert_drain_title, drain ?: 0),
                    getString(R.string.alert_drain_text),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun evaluateStorageAlert(stats: SystemStats) {
        val free = AlertLogic.freePercent(stats.totalStorageGb, stats.usedStorageGb)
        applyAlertStep(HealthAlert.LOW_STORAGE, { latched -> AlertLogic.lowStorage(free, latched) }) {
            HealthAlertNotifier.show(
                applicationContext,
                HealthAlert.LOW_STORAGE,
                getString(R.string.alert_storage_title, free ?: 0),
                getString(
                    R.string.alert_storage_text,
                    String.format(Locale.getDefault(), "%.1f GB", stats.totalStorageGb - stats.usedStorageGb),
                ),
            )
        }
    }

    private fun applyAlertStep(alert: HealthAlert, step: (latched: Boolean) -> AlertStep, post: () -> Boolean) =
        alertController.apply(alert, step, post)

    /**
     * A battery sample is never worth a crash: the store touches the filesystem, and
     * a full disk would otherwise take the service down on every battery broadcast.
     */
    private fun recordBatterySample(sample: BatterySample) {
        try {
            batteryHistory.record(sample)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun batteryPercent(intent: Intent): Int {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return -1
        return level * 100 / scale
    }

    companion object {
        /** More than the drain window, so the window's first sample is always in the read. */
        private const val DRAIN_LOOKBACK_MS = 61L * 60 * 1000
        private const val NOTIFICATION_ID = 1001
        private const val USAGE_REFRESH_INTERVAL_MS = 60_000L

        /** Start-command actions from [IntentMonitorServiceRelay]: re-check a changed setting now. */
        const val ACTION_CHARGE_LIMIT_CHANGED = "org.jarsi.devicewatch.action.CHARGE_LIMIT_CHANGED"
        const val ACTION_DATA_QUOTA_CHANGED = "org.jarsi.devicewatch.action.DATA_QUOTA_CHANGED"
        const val ACTION_NOTIFICATION_METRICS_CHANGED = "org.jarsi.devicewatch.action.NOTIFICATION_METRICS_CHANGED"
        /** From the live notification's delete intent: the user swiped it away. */
        const val ACTION_LIVE_NOTIFICATION_DISMISSED = "org.jarsi.devicewatch.action.LIVE_NOTIFICATION_DISMISSED"
    }
}
