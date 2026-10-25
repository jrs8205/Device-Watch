package org.jarsi.devicewatch.system

import android.app.Application
import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
    fun `the live notification is on its own channel and the silent one on the old`() {
        assertThat(notifications.live(null).channelId).isEqualTo(MonitorNotifications.LIVE_CHANNEL_ID)
        assertThat(notifications.quiet().channelId).isEqualTo(MonitorNotifications.QUIET_CHANNEL_ID)
    }
}
