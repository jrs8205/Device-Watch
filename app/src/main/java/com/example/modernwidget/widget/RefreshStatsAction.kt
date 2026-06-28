package com.example.modernwidget.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.example.modernwidget.system.SystemStatsHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RefreshStatsAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val stats = SystemStatsHelper.getStats(context)
        val now = SimpleDateFormat("HH.mm", Locale.getDefault()).format(Date())

        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[BATTERY_LEVEL] = stats.batteryLevel
                this[BATTERY_STATUS] = stats.batteryStatus
                this[BATTERY_HEALTH] = stats.batteryHealth
                this[BATTERY_TEMP] = stats.batteryTemp
                this[BATTERY_VOLTAGE] = stats.batteryVoltage
                this[TIME_REMAINING] = stats.timeRemainingText
                this[BATTERY_CYCLE_COUNT] = stats.batteryCycleCount
                this[TOTAL_RAM] = stats.totalRamGb
                this[USED_RAM] = stats.usedRamGb
                this[RAM_PERCENT] = stats.ramPercent
                this[CPU_CORES] = stats.cpuCores
                this[CPU_ABI] = stats.cpuAbi
                this[CPU_FREQ] = stats.cpuFreqGhz
                this[CPU_LOAD] = stats.cpuLoadPercent
                this[CPU_TEMP] = stats.cpuTemp
                this[TOTAL_STORAGE] = stats.totalStorageGb
                this[USED_STORAGE] = stats.usedStorageGb
                this[STORAGE_PERCENT] = stats.storagePercent
                this[WIFI_SSID] = stats.wifiSsid
                this[WIFI_SPEED_DOWN] = stats.wifiSpeedDown
                this[WIFI_SPEED_UP] = stats.wifiSpeedUp
                this[WIFI_BYTES_TODAY] = stats.wifiBytesTodayGb
                this[OPERATOR_NAME] = stats.operatorName
                this[MOBILE_NETWORK_TYPE] = stats.mobileNetworkType
                this[MOBILE_SIGNAL_DBM] = stats.mobileSignalDbm
                this[MOBILE_DATA_USED] = stats.mobileDataUsedGb
                this[MOBILE_DATA_TOTAL] = stats.mobileDataTotalGb
                this[IS_DND_ENABLED] = stats.isDndEnabled
                this[IS_BLUETOOTH_ENABLED] = stats.isBluetoothEnabled
                this[IS_LOCATION_ENABLED] = stats.isLocationEnabled
                this[IS_NFC_ENABLED] = stats.isNfcEnabled
                this[IS_POWER_SAVE_MODE] = stats.isPowerSaveMode
                this[IS_AIRPLANE_MODE] = stats.isAirplaneMode
                this[UPTIME] = stats.uptimeText
                this[LAST_UPDATED] = now
            }
        }

        DashboardWidget().update(context, glanceId)
    }

    companion object {
        val BATTERY_LEVEL = intPreferencesKey("battery_level")
        val BATTERY_STATUS = stringPreferencesKey("battery_status")
        val BATTERY_HEALTH = stringPreferencesKey("battery_health")
        val BATTERY_TEMP = doublePreferencesKey("battery_temp")
        val BATTERY_VOLTAGE = doublePreferencesKey("battery_voltage")
        val TIME_REMAINING = stringPreferencesKey("time_remaining")
        val BATTERY_CYCLE_COUNT = intPreferencesKey("battery_cycle_count")
        
        val TOTAL_RAM = doublePreferencesKey("total_ram")
        val USED_RAM = doublePreferencesKey("used_ram")
        val RAM_PERCENT = intPreferencesKey("ram_percent")
        
        val CPU_CORES = intPreferencesKey("cpu_cores")
        val CPU_ABI = stringPreferencesKey("cpu_abi")
        val CPU_FREQ = doublePreferencesKey("cpu_freq")
        val CPU_LOAD = intPreferencesKey("cpu_load")
        val CPU_TEMP = doublePreferencesKey("cpu_temp")
        
        val TOTAL_STORAGE = doublePreferencesKey("total_storage")
        val USED_STORAGE = doublePreferencesKey("used_storage")
        val STORAGE_PERCENT = intPreferencesKey("storage_percent")
        
        val WIFI_SSID = stringPreferencesKey("wifi_ssid")
        val WIFI_SPEED_DOWN = intPreferencesKey("wifi_speed_down")
        val WIFI_SPEED_UP = intPreferencesKey("wifi_speed_up")
        val WIFI_BYTES_TODAY = doublePreferencesKey("wifi_bytes_today")
        
        val OPERATOR_NAME = stringPreferencesKey("operator_name")
        val MOBILE_NETWORK_TYPE = stringPreferencesKey("mobile_network_type")
        val MOBILE_SIGNAL_DBM = intPreferencesKey("mobile_signal_dbm")
        val MOBILE_DATA_USED = doublePreferencesKey("mobile_data_used")
        val MOBILE_DATA_TOTAL = doublePreferencesKey("mobile_data_total")
        
        val IS_DND_ENABLED = booleanPreferencesKey("is_dnd_enabled")
        val IS_BLUETOOTH_ENABLED = booleanPreferencesKey("is_bluetooth_enabled")
        val IS_LOCATION_ENABLED = booleanPreferencesKey("is_location_enabled")
        val IS_NFC_ENABLED = booleanPreferencesKey("is_nfc_enabled")
        val IS_POWER_SAVE_MODE = booleanPreferencesKey("is_power_save_mode")
        val IS_AIRPLANE_MODE = booleanPreferencesKey("is_airplane_mode")
        
        val UPTIME = stringPreferencesKey("uptime")
        val LAST_UPDATED = stringPreferencesKey("last_updated")
        val BACKGROUND_OPACITY = floatPreferencesKey("background_opacity")
    }
}
