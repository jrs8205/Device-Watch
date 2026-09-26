package org.jarsi.devicewatch.system

import android.app.NotificationManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NotificationDeliveryTest {

    @Test
    fun `reaches the user when the app and the channel are allowed`() {
        assertThat(
            NotificationDelivery.canReachUser(
                appNotificationsEnabled = true,
                channelImportance = NotificationManager.IMPORTANCE_DEFAULT,
            )
        ).isTrue()
    }

    @Test
    fun `does not reach the user when the app's notifications are off`() {
        assertThat(
            NotificationDelivery.canReachUser(
                appNotificationsEnabled = false,
                channelImportance = NotificationManager.IMPORTANCE_DEFAULT,
            )
        ).isFalse()
    }

    @Test
    fun `does not reach the user when the channel is blocked`() {
        assertThat(
            NotificationDelivery.canReachUser(
                appNotificationsEnabled = true,
                channelImportance = NotificationManager.IMPORTANCE_NONE,
            )
        ).isFalse()
    }

    @Test
    fun `reaches the user when there is no channel to block`() {
        // Before Android 8 there are no channels; only the app-level switch counts.
        assertThat(
            NotificationDelivery.canReachUser(appNotificationsEnabled = true, channelImportance = null)
        ).isTrue()
    }
}
