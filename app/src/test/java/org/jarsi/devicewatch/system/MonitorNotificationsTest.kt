package org.jarsi.devicewatch.system

import android.app.Application
import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MonitorNotificationsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifications = MonitorNotifications(context)

    @Test
    fun `the expanded notification does not repeat the first line`() {
        // Device test 25.10.: expanded, Android shows the title above the big text,
        // so a big text starting with the title showed the first line twice.
        val n = notifications.live(LiveNotificationText("Akku 83% · Akku 28,0 °C · CPU 35,0 °C", "Data 185 MB · RAM 5,2 / 7,4 GB"))

        assertThat(n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            .isEqualTo("Akku 83% · Akku 28,0 °C · CPU 35,0 °C")
        assertThat(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
            .isEqualTo("Data 185 MB · RAM 5,2 / 7,4 GB")
    }

    @Test
    fun `one line of figures needs no expanded text`() {
        val n = notifications.live(LiveNotificationText("Akku 83% · CPU 23%", null))

        assertThat(n.extras.getCharSequence(Notification.EXTRA_TITLE).toString()).isEqualTo("Akku 83% · CPU 23%")
        assertThat(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)).isNull()
    }

    @Test
    fun `the live notification is on its own channel and the silent one on the old`() {
        assertThat(notifications.live(null).channelId).isEqualTo(MonitorNotifications.LIVE_CHANNEL_ID)
        assertThat(notifications.quiet().channelId).isEqualTo(MonitorNotifications.QUIET_CHANNEL_ID)
    }
}
