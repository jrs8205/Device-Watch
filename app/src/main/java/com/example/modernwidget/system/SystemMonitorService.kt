package com.example.modernwidget.system

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
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.example.modernwidget.R
import com.example.modernwidget.data.AppUsageRepository
import com.example.modernwidget.data.SystemStatsRepository
import com.example.modernwidget.data.UsageHistory
import com.example.modernwidget.presentation.ui.durationText
import com.example.modernwidget.widget.WidgetStateUpdater
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

    private var lastUsageRefreshMs = 0L

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
        startUpdateLoop()
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
                updateWidgetStats(this@SystemMonitorService)
                maybeRefreshUsage()
                delay(5000)
            }
        }
    }

    /**
     * Computes today's screen time + unlock count and pushes the screen-time text
     * to the widget. Throttled to about once a minute: it needs a usage-events
     * pass, which must never run at the 5-second stats cadence.
     */
    private suspend fun maybeRefreshUsage() {
        val now = SystemClock.elapsedRealtime()
        if (lastUsageRefreshMs != 0L && now - lastUsageRefreshMs < USAGE_REFRESH_INTERVAL_MS) return
        lastUsageRefreshMs = now
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
        val channelId = "system_monitor_channel"
        val channelName = getString(R.string.monitor_channel_name)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.monitor_channel_description)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setContentText(getString(R.string.monitor_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
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
                // Charger-connection tally lives here because this runtime receiver is
                // reliable; manifest-registered POWER_CONNECTED is an implicit broadcast
                // that modern Android does not deliver.
                if (intent.action == Intent.ACTION_POWER_CONNECTED) {
                    usageHistory.incrementCharge(LocalDate.now())
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

    private suspend fun updateWidgetStats(context: Context) {
        try {
            WidgetStateUpdater.updateAll(context.applicationContext, repository.getStats())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val USAGE_REFRESH_INTERVAL_MS = 60_000L
    }
}
