package org.jarsi.devicewatch.system

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How the settings screen tells the monitor service that a setting it acts on
 * has changed. A port so [org.jarsi.devicewatch.presentation.DashboardViewModel]
 * stays free of Android.
 */
interface MonitorServiceRelay {
    /** The charge limit was changed or switched off; apply it now, not at the next battery broadcast. */
    fun chargeLimitChanged()

    /** The data quota was changed or switched off; re-check it and re-arm the usage watch now. */
    fun dataQuotaChanged()
}

/**
 * Hands each change to [SystemMonitorService] as a start-command action. The
 * service is normally already running as a foreground service; if it is not,
 * this starts it, which the app may do while its own screen is in front.
 */
@Singleton
class IntentMonitorServiceRelay @Inject constructor(
    @ApplicationContext private val context: Context,
) : MonitorServiceRelay {

    override fun chargeLimitChanged() = send(SystemMonitorService.ACTION_CHARGE_LIMIT_CHANGED)

    override fun dataQuotaChanged() = send(SystemMonitorService.ACTION_DATA_QUOTA_CHANGED)

    private fun send(action: String) {
        val intent = Intent(context, SystemMonitorService::class.java).setAction(action)
        ContextCompat.startForegroundService(context, intent)
    }
}
