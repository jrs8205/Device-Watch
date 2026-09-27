package org.jarsi.devicewatch.system

import org.jarsi.devicewatch.data.AlertStep
import org.jarsi.devicewatch.data.AppSettingsRepository
import org.jarsi.devicewatch.data.HealthAlert

/**
 * Posts an enabled alert once and latches it; re-arms it once its condition has
 * cleared, taking the stale notification down with it. The latch is written only
 * when the alert really went out, and only if the user has not switched the alert
 * since the decision: the switch runs on the main thread, this on the service's,
 * and a latch written after a switch-off would silence the alert switched back on.
 */
class HealthAlertController(
    private val settings: AppSettingsRepository,
    private val notifications: AlertNotifications,
) {
    @Synchronized
    fun apply(alert: HealthAlert, step: (latched: Boolean) -> AlertStep, post: () -> Boolean) {
        if (!settings.alertEnabled(alert)) return
        val generation = settings.alertGeneration(alert)
        when (step(settings.alertLatched(alert))) {
            AlertStep.FIRE -> {
                // Switched off (or off and on) during the post: the notification that
                // just went out belongs to a setting that no longer stands.
                if (post() && !settings.latchAlert(alert, generation)) notifications.cancel(alert)
            }
            AlertStep.REARM -> {
                settings.unlatchAlert(alert)
                notifications.cancel(alert)
            }
            AlertStep.NONE -> Unit
        }
    }
}
