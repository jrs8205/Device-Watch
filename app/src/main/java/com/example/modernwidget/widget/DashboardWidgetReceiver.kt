package com.example.modernwidget.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.example.modernwidget.system.SystemMonitorService
import com.example.modernwidget.system.SystemStatsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = DashboardWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val action = intent.action
        
        // Jos puhelin käynnistetään uudelleen, käynnistetään taustapalvelu automaattisesti
        if (action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, SystemMonitorService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Päivitetään widget myös muiden whitelistattujen lähetysten yhteydessä varmuuden vuoksi
        if (action == Intent.ACTION_POWER_CONNECTED ||
            action == Intent.ACTION_POWER_DISCONNECTED ||
            action == Intent.ACTION_USER_PRESENT ||
            action == "android.appwidget.action.APPWIDGET_UPDATE"
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val manager = GlanceAppWidgetManager(context)
                    val glanceIds = manager.getGlanceIds(DashboardWidget::class.java)
                    if (glanceIds.isNotEmpty()) {
                        val stats = SystemStatsHelper.getStats(context)
                        val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

                        for (glanceId in glanceIds) {
                            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                                prefs.toMutablePreferences().apply {
                                    this[RefreshStatsAction.BATTERY_LEVEL] = stats.batteryLevel
                                    this[RefreshStatsAction.BATTERY_STATUS] = stats.batteryStatus
                                    this[RefreshStatsAction.BATTERY_HEALTH] = stats.batteryHealth
                                    this[RefreshStatsAction.BATTERY_TEMP] = stats.batteryTemp
                                    this[RefreshStatsAction.BATTERY_VOLTAGE] = stats.batteryVoltage
                                    this[RefreshStatsAction.TIME_REMAINING] = stats.timeRemainingText
                                    this[RefreshStatsAction.TOTAL_RAM] = stats.totalRamGb
                                    this[RefreshStatsAction.USED_RAM] = stats.usedRamGb
                                    this[RefreshStatsAction.RAM_PERCENT] = stats.ramPercent
                                    this[RefreshStatsAction.CPU_CORES] = stats.cpuCores
                                    this[RefreshStatsAction.CPU_ABI] = stats.cpuAbi
                                    this[RefreshStatsAction.UPTIME] = stats.uptimeText
                                    this[RefreshStatsAction.LAST_UPDATED] = now
                                }
                            }
                            glanceAppWidget.update(context, glanceId)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult?.finish()
                }
            }
        }
    }
}
