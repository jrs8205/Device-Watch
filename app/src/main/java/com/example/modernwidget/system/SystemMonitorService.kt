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
import androidx.core.app.NotificationCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.example.modernwidget.widget.DashboardWidget
import com.example.modernwidget.widget.RefreshStatsAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SystemMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var batteryReceiver: BroadcastReceiver? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var updateJob: kotlinx.coroutines.Job? = null
    private var isScreenOn = true

    override fun onCreate() {
        super.onCreate()
        startNotification()
        registerBatteryTracker()
        registerScreenTracker()
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
        stopUpdateLoop()
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

    private fun startUpdateLoop() {
        if (updateJob?.isActive == true) return
        
        updateJob = serviceScope.launch {
            while (isScreenOn) {
                updateWidgetStats(this@SystemMonitorService)
                kotlinx.coroutines.delay(5000) // Päivitys 5 sekunnin välein
            }
        }
    }

    private fun stopUpdateLoop() {
        updateJob?.cancel()
        updateJob = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startNotification() {
        val channelId = "system_monitor_channel"
        val channelName = "Järjestelmän valvonta"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Päivittää järjestelmän ja akun tiedot reaaliajassa widgettiin."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Järjestelmävahti päällä")
            .setContentText("Seurataan laitteen tilaa ja päivitetään widgettiä.")
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
                updateWidgetStats(context)
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(batteryReceiver, filter)
    }

    private fun updateWidgetStats(context: Context) {
        serviceScope.launch {
            try {
                val stats = SystemStatsHelper.getStats(context)
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(DashboardWidget::class.java)
                val now = SimpleDateFormat("HH.mm", Locale.getDefault()).format(Date())

                if (glanceIds.isNotEmpty()) {
                    for (glanceId in glanceIds) {
                        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                            prefs.toMutablePreferences().apply {
                                this[RefreshStatsAction.BATTERY_LEVEL] = stats.batteryLevel
                                this[RefreshStatsAction.BATTERY_STATUS] = stats.batteryStatus
                                this[RefreshStatsAction.BATTERY_HEALTH] = stats.batteryHealth
                                this[RefreshStatsAction.BATTERY_TEMP] = stats.batteryTemp
                                this[RefreshStatsAction.BATTERY_VOLTAGE] = stats.batteryVoltage
                                this[RefreshStatsAction.TIME_REMAINING] = stats.timeRemainingText
                                this[RefreshStatsAction.BATTERY_CYCLE_COUNT] = stats.batteryCycleCount
                                this[RefreshStatsAction.TOTAL_RAM] = stats.totalRamGb
                                this[RefreshStatsAction.USED_RAM] = stats.usedRamGb
                                this[RefreshStatsAction.RAM_PERCENT] = stats.ramPercent
                                this[RefreshStatsAction.CPU_CORES] = stats.cpuCores
                                this[RefreshStatsAction.CPU_ABI] = stats.cpuAbi
                                this[RefreshStatsAction.CPU_FREQ] = stats.cpuFreqGhz
                                this[RefreshStatsAction.CPU_LOAD] = stats.cpuLoadPercent
                                this[RefreshStatsAction.CPU_TEMP] = stats.cpuTemp
                                this[RefreshStatsAction.TOTAL_STORAGE] = stats.totalStorageGb
                                this[RefreshStatsAction.USED_STORAGE] = stats.usedStorageGb
                                this[RefreshStatsAction.STORAGE_PERCENT] = stats.storagePercent
                                this[RefreshStatsAction.WIFI_SSID] = stats.wifiSsid
                                this[RefreshStatsAction.WIFI_SPEED_DOWN] = stats.wifiSpeedDown
                                this[RefreshStatsAction.WIFI_SPEED_UP] = stats.wifiSpeedUp
                                this[RefreshStatsAction.WIFI_BYTES_TODAY] = stats.wifiBytesTodayGb
                                this[RefreshStatsAction.OPERATOR_NAME] = stats.operatorName
                                this[RefreshStatsAction.MOBILE_NETWORK_TYPE] = stats.mobileNetworkType
                                this[RefreshStatsAction.MOBILE_SIGNAL_DBM] = stats.mobileSignalDbm
                                this[RefreshStatsAction.MOBILE_DATA_USED] = stats.mobileDataUsedGb
                                this[RefreshStatsAction.MOBILE_DATA_TOTAL] = stats.mobileDataTotalGb
                                this[RefreshStatsAction.IS_DND_ENABLED] = stats.isDndEnabled
                                this[RefreshStatsAction.IS_BLUETOOTH_ENABLED] = stats.isBluetoothEnabled
                                this[RefreshStatsAction.IS_LOCATION_ENABLED] = stats.isLocationEnabled
                                this[RefreshStatsAction.IS_NFC_ENABLED] = stats.isNfcEnabled
                                this[RefreshStatsAction.IS_POWER_SAVE_MODE] = stats.isPowerSaveMode
                                this[RefreshStatsAction.IS_AIRPLANE_MODE] = stats.isAirplaneMode
                                this[RefreshStatsAction.UPTIME] = stats.uptimeText
                                this[RefreshStatsAction.LAST_UPDATED] = now
                            }
                        }
                        DashboardWidget().update(context, glanceId)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
