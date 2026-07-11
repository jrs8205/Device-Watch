package org.jarsi.devicewatch.widget

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import org.jarsi.devicewatch.di.RepositoryEntryPoint
import dagger.hilt.android.EntryPointAccessors

class RefreshStatsAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val repository = EntryPointAccessors
            .fromApplication(context.applicationContext, RepositoryEntryPoint::class.java)
            .systemStatsRepository()
        WidgetStateUpdater.updateOne(context, glanceId, repository.getStats())
    }

    companion object {
        val BATTERY_LEVEL = intPreferencesKey("battery_level")
        val BATTERY_STATUS = stringPreferencesKey("battery_status")
        val BATTERY_HEALTH = stringPreferencesKey("battery_health")
        val BATTERY_TEMP = doublePreferencesKey("battery_temp")
        val BATTERY_VOLTAGE = doublePreferencesKey("battery_voltage")
        val TIME_REMAINING = stringPreferencesKey("time_remaining")
        val BATTERY_CYCLE_COUNT = intPreferencesKey("battery_cycle_count")
        val BATTERY_CAPACITY = intPreferencesKey("battery_capacity")
        
        val TOTAL_RAM = doublePreferencesKey("total_ram")
        val USED_RAM = doublePreferencesKey("used_ram")
        val RAM_PERCENT = intPreferencesKey("ram_percent")
        
        val CPU_CORES = intPreferencesKey("cpu_cores")
        val CPU_ABI = stringPreferencesKey("cpu_abi")
        val CPU_FREQ = doublePreferencesKey("cpu_freq")
        val CPU_LOAD = intPreferencesKey("cpu_load")
        val CPU_LOAD_LABEL = stringPreferencesKey("cpu_load_label")
        val CPU_TEMP = doublePreferencesKey("cpu_temp")
        
        val TOTAL_STORAGE = doublePreferencesKey("total_storage")
        val USED_STORAGE = doublePreferencesKey("used_storage")
        val STORAGE_PERCENT = intPreferencesKey("storage_percent")
        
        val WIFI_SSID = stringPreferencesKey("wifi_ssid")
        val WIFI_BAND = stringPreferencesKey("wifi_band")
        val WIFI_SPEED_DOWN = intPreferencesKey("wifi_speed_down")
        val WIFI_SPEED_UP = intPreferencesKey("wifi_speed_up")
        val WIFI_BYTES_TODAY = doublePreferencesKey("wifi_bytes_today")
        val WIFI_DATA_LABEL = stringPreferencesKey("wifi_data_label")
        
        val OPERATOR_NAME = stringPreferencesKey("operator_name")
        val MOBILE_NETWORK_TYPE = stringPreferencesKey("mobile_network_type")
        val MOBILE_SIGNAL_DBM = intPreferencesKey("mobile_signal_dbm")
        val MOBILE_DATA_USED = doublePreferencesKey("mobile_data_used")
        val MOBILE_DATA_TOTAL = doublePreferencesKey("mobile_data_total")
        val MOBILE_DATA_LABEL = stringPreferencesKey("mobile_data_label")
        
        val UPTIME = stringPreferencesKey("uptime")
        val LAST_UPDATED = stringPreferencesKey("last_updated")
        val BACKGROUND_OPACITY = floatPreferencesKey("background_opacity")

        /** Pre-formatted screen-time text written by SystemMonitorService (~1/min). */
        val SCREEN_TIME_TODAY = stringPreferencesKey("screen_time_today")
    }
}
