package org.jarsi.devicewatch.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import org.jarsi.devicewatch.R
import android.os.BatteryManager
import org.jarsi.devicewatch.data.AppSettingsRepository
import org.jarsi.devicewatch.data.AppUsageRepository
import org.jarsi.devicewatch.data.BatteryHistory
import org.jarsi.devicewatch.data.BatterySample
import org.jarsi.devicewatch.data.ChargeAnchorLogic
import org.jarsi.devicewatch.data.ChargeAnchorStore
import org.jarsi.devicewatch.data.DataPeriodCalculator
import org.jarsi.devicewatch.data.DataQuotaLogic
import org.jarsi.devicewatch.data.SystemStats
import org.jarsi.devicewatch.data.SystemStatsRepository
import org.jarsi.devicewatch.data.UsageHistory
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

    private var lastUsageRefreshMs = 0L

    /** Latest level from BATTERY_CHANGED; POWER_DISCONNECTED carries no battery extras. */
    @Volatile private var lastBatteryLevel = -1

    /**
     * Charge-reminder latch. In-memory on purpose: only a service restart in the
     * middle of a charge can lose it, and the worst case is one repeated reminder.
     */
    private var chargeLimitState = ChargeLimitLogic.State()

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
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
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
                delay(5000)
            }
        }
    }

    /**
     * Computes today's screen time + unlock count, pushes the screen-time text to the
     * widget and checks the data quota against [stats] (the reading the widget was just
     * updated with; null when that read failed). Throttled to about once a minute: it
     * needs a usage-events pass, which must never run at the 5-second stats cadence.
     */
    private suspend fun maybeRefreshUsage(stats: SystemStats?) {
        val now = SystemClock.elapsedRealtime()
        if (lastUsageRefreshMs != 0L && now - lastUsageRefreshMs < USAGE_REFRESH_INTERVAL_MS) return
        lastUsageRefreshMs = now
        // Before the usage-events pass, which bails out without usage access.
        if (stats != null) maybeNotifyDataQuota(stats)
        try {
            val totals = appUsageRepository.usageTotalsToday() ?: return
            val today = LocalDate.now()
            usageHistory.recordScreenTime(today, totals.screenTimeMillis)
            usageHistory.recordUnlocks(today, totals.unlockCount)
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
        // IMPORTANCE_MIN keeps the mandatory foreground-service notification out of the
        // status bar and collapsed at the bottom of the silent section. A channel's
        // importance cannot be lowered after creation, so this is a NEW channel id and
        // the old LOW-importance channel is deleted.
        val channelId = "system_monitor_channel_min"
        val channelName = getString(R.string.monitor_channel_name)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.deleteNotificationChannel("system_monitor_channel")
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.monitor_channel_description)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setContentText(getString(R.string.monitor_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()

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
                            )
                            serviceScope.launch { recordBatterySample(sample) }
                        }
                        // Charge reminder. The limit is re-read here because settings
                        // can change while the service runs; SharedPreferences is
                        // memory-cached, so this stays cheap on the receiver thread.
                        val decision = ChargeLimitLogic.onBatteryChanged(
                            chargeLimitState,
                            limitPercent = appSettings.chargeLimitPercent(),
                            level = level,
                            isPlugged = plugged,
                        )
                        chargeLimitState = decision.state
                        if (decision.notify) {
                            // Same quirky-EXTRA_SCALE clamp as the sample above, so the
                            // notification can never claim more than 100 %.
                            ChargeLimitNotifier.show(applicationContext, level.coerceIn(0, 100))
                        }
                    }
                    Intent.ACTION_POWER_CONNECTED -> {
                        usageHistory.incrementCharge(LocalDate.now())
                        chargeAnchorStore.save(
                            ChargeAnchorLogic.onPowerConnected(chargeAnchorStore.load())
                        )
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
                    updateWidgetStats(context)
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
            stats
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Data-quota alerts: 80 % and the limit itself, each at most once per counting
     * period. The latch lives in settings and is keyed by the period start, so a new
     * day (or billing cycle) re-arms both — and a quota switched on mid-period only
     * alerts for what the period has actually used.
     */
    private fun maybeNotifyDataQuota(stats: SystemStats) {
        try {
            // mobileDataTotalGb carries the quota only when one is set AND the
            // period figure is available (usage access granted) — the since-boot
            // fallback must never be compared against a period quota.
            val quotaGb = stats.mobileDataTotalGb
            if (quotaGb <= 0.0) return
            val periodStartEpochDay = DataPeriodCalculator.periodStart(
                appSettings.dataCounterMode(), appSettings.cycleStartDay(), LocalDate.now()
            ).toEpochDay()
            val pending = DataQuotaLogic.pendingThresholds(
                quotaGb = quotaGb,
                usedGb = stats.mobileDataUsedGb,
                notified80 = appSettings.dataQuotaNotified(periodStartEpochDay, DataQuotaLogic.WARNING_PERCENT),
                notified100 = appSettings.dataQuotaNotified(periodStartEpochDay, DataQuotaLogic.REACHED_PERCENT),
            )
            pending.forEach { threshold ->
                DataQuotaNotifier.show(applicationContext, threshold, stats.mobileDataUsedGb, quotaGb)
                appSettings.setDataQuotaNotified(periodStartEpochDay, threshold)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A quota alert is never worth taking the monitor service down.
            e.printStackTrace()
        }
    }

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
        private const val NOTIFICATION_ID = 1001
        private const val USAGE_REFRESH_INTERVAL_MS = 60_000L
    }
}
