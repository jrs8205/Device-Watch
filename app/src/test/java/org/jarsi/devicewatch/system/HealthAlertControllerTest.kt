package org.jarsi.devicewatch.system

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.data.AlertStep
import org.jarsi.devicewatch.data.AppSettingsRepositoryImpl
import org.jarsi.devicewatch.data.HealthAlert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthAlertControllerTest {

    private val settings = AppSettingsRepositoryImpl(ApplicationProvider.getApplicationContext())
    private val cancelled = mutableListOf<HealthAlert>()
    private val controller = HealthAlertController(settings, object : AlertNotifications {
        override fun cancel(alert: HealthAlert) {
            cancelled += alert
        }
    })
    private val alert = HealthAlert.HOT_BATTERY

    @Test
    fun `a posted alert is latched`() {
        settings.setAlertEnabled(alert, true)

        controller.apply(alert, { AlertStep.FIRE }) { true }

        assertThat(settings.alertLatched(alert)).isTrue()
        assertThat(cancelled).isEmpty()
    }

    @Test
    fun `an alert switched off while it was being posted is neither latched nor left showing`() {
        // Codex round 8: the switch-off cleared the latch, then the post finished and
        // wrote it back, so the alert switched on again stayed silent.
        settings.setAlertEnabled(alert, true)

        controller.apply(alert, { AlertStep.FIRE }) {
            settings.setAlertEnabled(alert, false)
            true
        }

        assertThat(settings.alertLatched(alert)).isFalse()
        assertThat(cancelled).containsExactly(alert)
    }

    @Test
    fun `switched off and on again during the post, the alert can fire again`() {
        settings.setAlertEnabled(alert, true)
        controller.apply(alert, { AlertStep.FIRE }) {
            settings.setAlertEnabled(alert, false)
            settings.setAlertEnabled(alert, true)
            true
        }

        var posts = 0
        controller.apply(alert, { latched -> if (latched) AlertStep.NONE else AlertStep.FIRE }) {
            posts++
            true
        }

        assertThat(posts).isEqualTo(1)
        assertThat(settings.alertLatched(alert)).isTrue()
    }

    @Test
    fun `a refused post is not latched, so the next reading tries again`() {
        settings.setAlertEnabled(alert, true)

        controller.apply(alert, { AlertStep.FIRE }) { false }

        assertThat(settings.alertLatched(alert)).isFalse()
    }

    @Test
    fun `re-arming clears the latch and takes the notification down`() {
        settings.setAlertEnabled(alert, true)
        controller.apply(alert, { AlertStep.FIRE }) { true }

        controller.apply(alert, { AlertStep.REARM }) { error("not posted when re-arming") }

        assertThat(settings.alertLatched(alert)).isFalse()
        assertThat(cancelled).containsExactly(alert)
    }

    @Test
    fun `a switched-off alert is not looked at`() {
        controller.apply(alert, { error("not evaluated while off") }) { error("not posted while off") }
    }
}
