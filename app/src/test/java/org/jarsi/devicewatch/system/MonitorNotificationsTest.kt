package org.jarsi.devicewatch.system

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MonitorNotificationsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifications = MonitorNotifications(context)

    private val first = "Akku 83% · Akku 28,0 °C · CPU 35,0 °C"
    private val second = "Data 185 MB · RAM 5,2 / 7,4 GB"

    @Test
    fun `expanded, every figure shows once under a short heading`() {
        // Device test 25.10.: expanded, Android shows the title above the big text, and
        // a big text starting with the title showed the first line twice. Final review:
        // a title is one line in both views, so a figure it cuts off must be in the
        // expanded text, under a heading that replaces the title.
        val n = notifications.live(LiveNotificationText(first, second))

        assertThat(n.extras.getCharSequence(Notification.EXTRA_TITLE).toString()).isEqualTo(first)
        assertThat(n.extras.getCharSequence(Notification.EXTRA_TITLE_BIG).toString())
            .isEqualTo(context.getString(R.string.monitor_live_channel_name))
        assertThat(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()).isEqualTo("$first\n$second")
    }

    @Test
    fun `one line of figures can still be expanded to show it whole`() {
        val n = notifications.live(LiveNotificationText(first, null))

        assertThat(n.extras.getCharSequence(Notification.EXTRA_TITLE).toString()).isEqualTo(first)
        assertThat(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()).isEqualTo(first)
    }

    @Test
    fun `with nothing readable the live notification carries the monitor's own words`() {
        val n = notifications.live(null)

        assertThat(n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            .isEqualTo(context.getString(R.string.monitor_notification_title))
        assertThat(n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            .isEqualTo(context.getString(R.string.monitor_notification_text))
    }

    @Test
    fun `a blocked figures channel counts as not posted`() {
        // External review round 1: with only "Live figures" blocked, the app's notifications
        // are still on, so the post counted as made and an unchanged figure stayed off the
        // screen after the channel was allowed again.
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(MonitorNotifications.LIVE_CHANNEL_ID, "Live", NotificationManager.IMPORTANCE_NONE)
        )
        manager.createNotificationChannel(
            NotificationChannel(MonitorNotifications.QUIET_CHANNEL_ID, "Quiet", NotificationManager.IMPORTANCE_MIN)
        )

        assertThat(notifications.post(1001, notifications.live(LiveNotificationText(first, null)))).isFalse()
        assertThat(notifications.post(1001, notifications.quiet())).isTrue()
    }

    @Test
    fun `it tells whether the monitor's notification is still posted`() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications.ensureChannels()
        assertThat(notifications.isShowing(1001)).isFalse()

        notifications.post(1001, notifications.live(LiveNotificationText(first, null)))
        assertThat(notifications.isShowing(1001)).isTrue()

        context.getSystemService(NotificationManager::class.java).cancel(1001)
        assertThat(notifications.isShowing(1001)).isFalse()
    }

    @Test
    fun `the live notification is on its own channel and the silent one on the old`() {
        assertThat(notifications.live(null).channelId).isEqualTo(MonitorNotifications.LIVE_CHANNEL_ID)
        assertThat(notifications.quiet().channelId).isEqualTo(MonitorNotifications.QUIET_CHANNEL_ID)
    }
}
