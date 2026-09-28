package org.jarsi.devicewatch.system

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Codex release review: with a quota alert on screen, removing the quota or
 * raising it from 10 to 100 GB left the old alert standing, although it no
 * longer held. The service's answer to a data-settings change starts here.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class DataQuotaSettingsChangeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))

    private fun postQuotaAlert() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        check(DataQuotaNotifier.show(context, threshold = 100, usedGb = 10.5, quotaGb = 10.0))
    }

    @Test
    fun `a data-settings change takes down the alert the old settings raised`() {
        postQuotaAlert()
        assertThat(notifications.allNotifications).hasSize(1)

        onDataSettingsChanged(context) {}

        assertThat(notifications.allNotifications).isEmpty()
    }

    @Test
    fun `the alert is taken down before the re-check, which may post the new settings' own`() {
        postQuotaAlert()
        var shownAtRecheck = -1

        onDataSettingsChanged(context) { shownAtRecheck = notifications.allNotifications.size }

        assertThat(shownAtRecheck).isEqualTo(0)
    }
}
