package com.example.modernwidget.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.example.modernwidget.data.SystemStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single write path for the Glance widget state. Callers obtain fresh [SystemStats]
 * from [com.example.modernwidget.data.SystemStatsRepository] and hand them here; this
 * object only serializes them into DataStore and asks Glance to re-render.
 */
object WidgetStateUpdater {

    /** Writes [stats] to every installed widget; returns whether any widget exists. */
    suspend fun updateAll(context: Context, stats: SystemStats): Boolean {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(DashboardWidget::class.java)
        val now = currentTime()

        for (glanceId in glanceIds) {
            writeAndUpdate(context, glanceId, stats, now)
        }

        return glanceIds.isNotEmpty()
    }

    /** Writes [stats] to a single widget instance. */
    suspend fun updateOne(context: Context, glanceId: GlanceId, stats: SystemStats) {
        writeAndUpdate(context, glanceId, stats, currentTime())
    }

    private suspend fun writeAndUpdate(
        context: Context,
        glanceId: GlanceId,
        stats: SystemStats,
        timestamp: String
    ) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                writeStats(stats, timestamp)
            }
        }
        DashboardWidget().update(context, glanceId)
    }

    private fun MutablePreferences.writeStats(stats: SystemStats, timestamp: String) {
        this[RefreshStatsAction.BATTERY_LEVEL] = stats.batteryLevel
        this[RefreshStatsAction.BATTERY_STATUS] = stats.batteryStatus
        this[RefreshStatsAction.BATTERY_HEALTH] = stats.batteryHealth
        this[RefreshStatsAction.BATTERY_TEMP] = stats.batteryTemp
        this[RefreshStatsAction.BATTERY_VOLTAGE] = stats.batteryVoltage
        this[RefreshStatsAction.TIME_REMAINING] = stats.timeRemainingText
        this[RefreshStatsAction.BATTERY_CYCLE_COUNT] = stats.batteryCycleCount
        this[RefreshStatsAction.BATTERY_CAPACITY] = stats.batteryCapacityPercent
        this[RefreshStatsAction.TOTAL_RAM] = stats.totalRamGb
        this[RefreshStatsAction.USED_RAM] = stats.usedRamGb
        this[RefreshStatsAction.RAM_PERCENT] = stats.ramPercent
        this[RefreshStatsAction.CPU_CORES] = stats.cpuCores
        this[RefreshStatsAction.CPU_ABI] = stats.cpuAbi
        this[RefreshStatsAction.CPU_FREQ] = stats.cpuFreqGhz
        this[RefreshStatsAction.CPU_LOAD] = stats.cpuLoadPercent
        this[RefreshStatsAction.CPU_LOAD_LABEL] = stats.cpuLoadLabel
        this[RefreshStatsAction.CPU_TEMP] = stats.cpuTemp
        this[RefreshStatsAction.TOTAL_STORAGE] = stats.totalStorageGb
        this[RefreshStatsAction.USED_STORAGE] = stats.usedStorageGb
        this[RefreshStatsAction.STORAGE_PERCENT] = stats.storagePercent
        this[RefreshStatsAction.WIFI_SSID] = stats.wifiSsid
        this[RefreshStatsAction.WIFI_BAND] = stats.wifiBand
        this[RefreshStatsAction.WIFI_SPEED_DOWN] = stats.wifiSpeedDown
        this[RefreshStatsAction.WIFI_SPEED_UP] = stats.wifiSpeedUp
        this[RefreshStatsAction.WIFI_BYTES_TODAY] = stats.wifiBytesTodayGb
        this[RefreshStatsAction.OPERATOR_NAME] = stats.operatorName
        this[RefreshStatsAction.MOBILE_NETWORK_TYPE] = stats.mobileNetworkType
        this[RefreshStatsAction.MOBILE_SIGNAL_DBM] = stats.mobileSignalDbm
        this[RefreshStatsAction.MOBILE_DATA_USED] = stats.mobileDataUsedGb
        this[RefreshStatsAction.MOBILE_DATA_TOTAL] = stats.mobileDataTotalGb
        this[RefreshStatsAction.MOBILE_DATA_LABEL] = stats.mobileDataLabel
        this[RefreshStatsAction.UPTIME] = stats.uptimeText
        this[RefreshStatsAction.LAST_UPDATED] = timestamp
    }

    private fun currentTime(): String {
        return SimpleDateFormat("HH.mm", Locale.getDefault()).format(Date())
    }
}
