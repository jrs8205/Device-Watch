package org.jarsi.devicewatch.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import org.jarsi.devicewatch.data.SystemStatsRepository
import org.jarsi.devicewatch.data.UsageHistory
import org.jarsi.devicewatch.system.SystemMonitorService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class DashboardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = DashboardWidget()

    @Inject lateinit var repository: SystemStatsRepository
    @Inject lateinit var usageHistory: UsageHistory

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val action = intent.action

        // Restart tally via BOOT_COUNT deltas: every receiver wake registers the
        // current counter, so real boots are counted exactly once even though
        // Android also re-delivers BOOT_COMPLETED after each app update.
        try {
            val bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
            usageHistory.registerBootCount(LocalDate.now(), bootCount)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.appwidget.action.APPWIDGET_UPDATE" ||
            action == Intent.ACTION_USER_PRESENT
        ) {
            startMonitorService(context)
        }

        if (action == Intent.ACTION_POWER_CONNECTED ||
            action == Intent.ACTION_POWER_DISCONNECTED ||
            action == Intent.ACTION_USER_PRESENT ||
            action == "android.appwidget.action.APPWIDGET_UPDATE"
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    WidgetStateUpdater.updateAll(context.applicationContext, repository.getStats())
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
