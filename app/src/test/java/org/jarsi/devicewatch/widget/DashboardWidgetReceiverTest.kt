package org.jarsi.devicewatch.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.SourceXml
import org.jarsi.devicewatch.SourceXml.actions
import org.jarsi.devicewatch.system.SystemMonitorService
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The broadcasts that bring the monitor back when nothing of the app is running. The
 * receiver once also listed the charger and the unlock, which Android has not delivered to
 * a manifest receiver since Android 8 (the broadcast history says "skipped by policy:
 * background execution not allowed"), and it did not list the app's own update, after which
 * nothing started the monitor until the app was opened.
 */
@RunWith(RobolectricTestRunner::class)
class DashboardWidgetReceiverTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun monitorStartedBy(action: String): Boolean {
        DashboardWidgetReceiver().onReceive(app, Intent(action))
        return generateSequence { shadowOf(app).nextStartedService }
            .any { it.component?.className == SystemMonitorService::class.java.name }
    }

    @Test
    fun `an update of the app starts the monitor again`() {
        assertThat(monitorStartedBy(Intent.ACTION_MY_PACKAGE_REPLACED)).isTrue()
    }

    @Test
    fun `a start of the phone starts the monitor`() {
        assertThat(monitorStartedBy(Intent.ACTION_BOOT_COMPLETED)).isTrue()
    }

    @Test
    fun `a broadcast the receiver does not ask for starts nothing`() {
        assertThat(monitorStartedBy(Intent.ACTION_TIMEZONE_CHANGED)).isFalse()
    }

    @Test
    fun `the manifest asks only for broadcasts Android delivers to a manifest receiver`() {
        val receiver = SourceXml.component("receiver", ".widget.DashboardWidgetReceiver")

        assertThat(receiver.actions()).containsExactly(
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
