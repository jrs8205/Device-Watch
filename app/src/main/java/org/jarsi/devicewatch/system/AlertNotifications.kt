package org.jarsi.devicewatch.system

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jarsi.devicewatch.data.HealthAlert
import javax.inject.Inject

/** Takes an optional alert's notification down; a fake in tests. */
interface AlertNotifications {
    fun cancel(alert: HealthAlert)
}

class SystemAlertNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) : AlertNotifications {
    override fun cancel(alert: HealthAlert) = HealthAlertNotifier.cancel(context, alert)
}
