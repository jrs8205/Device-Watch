package com.example.modernwidget.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.example.modernwidget.system.SystemMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DashboardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = DashboardWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val action = intent.action
        
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.appwidget.action.APPWIDGET_UPDATE" ||
            action == Intent.ACTION_USER_PRESENT ||
            action == Intent.ACTION_CONFIGURATION_CHANGED
        ) {
            startMonitorService(context)
        }

        if (action == Intent.ACTION_POWER_CONNECTED ||
            action == Intent.ACTION_POWER_DISCONNECTED ||
            action == Intent.ACTION_USER_PRESENT ||
            action == "android.appwidget.action.APPWIDGET_UPDATE" ||
            action == Intent.ACTION_CONFIGURATION_CHANGED
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    WidgetStateUpdater.updateAll(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult?.finish()
                }
            }
        }
    }

    private fun startMonitorService(context: Context) {
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
}
