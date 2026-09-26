package org.jarsi.devicewatch.system

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How the settings screen tells the monitor that the charge limit changed. A port
 * so [org.jarsi.devicewatch.presentation.DashboardViewModel] stays free of Android.
 */
interface ChargeLimitReminder {
    /** The stored limit was just changed (or switched off); apply it now, not at the next battery broadcast. */
    fun limitChanged()
}

/**
 * Hands the change to [SystemMonitorService], which owns the once-per-plug latch
 * and the sticky battery state. The service is normally already running as a
 * foreground service; if it is not, this starts it, which the app may do while
 * its own screen is in front.
 */
@Singleton
class ServiceChargeLimitReminder @Inject constructor(
    @ApplicationContext private val context: Context,
) : ChargeLimitReminder {

    override fun limitChanged() {
        val intent = Intent(context, SystemMonitorService::class.java)
            .setAction(SystemMonitorService.ACTION_CHARGE_LIMIT_CHANGED)
        ContextCompat.startForegroundService(context, intent)
    }
}
