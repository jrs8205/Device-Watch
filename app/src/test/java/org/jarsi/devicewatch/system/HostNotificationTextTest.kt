package org.jarsi.devicewatch.system

import android.Manifest
import android.app.Application
import android.app.LocaleManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.HealthAlert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Every notification the app posts, on an Arabic phone while the app's own texts are English.
 * The shade draws them in the direction of the phone's language, so each title and text has to
 * arrive as a left-to-right piece, as the monitor notification's figures already did.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, shadows = [HostNotificationTextTest.ArabicPhone::class])
class HostNotificationTextTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager: NotificationManager get() = context.getSystemService(NotificationManager::class.java)

    @Before
    fun allowNotifications() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun assertKept(notification: Notification, vararg keys: String) {
        for (key in keys) {
            val text = notification.extras.getCharSequence(key)?.toString()
            assertWithMessage(key).that(text).isNotNull()
            for (line in text!!.split('\n')) {
                assertWithMessage("$key: $line").that(line.first().code).isEqualTo(0x2066)
                assertWithMessage("$key: $line").that(line.last().code).isEqualTo(0x2069)
            }
        }
    }

    private fun posted(): Notification = shadowOf(manager).allNotifications.single().also { manager.cancelAll() }

    @Test
    fun `the monitor's notifications are kept, with and without figures`() {
        val notifications = MonitorNotifications(context)

        assertKept(notifications.quiet(), Notification.EXTRA_TITLE, Notification.EXTRA_TEXT)
        assertKept(notifications.live(null), Notification.EXTRA_TITLE, Notification.EXTRA_TEXT)
        assertKept(
            notifications.live(LiveNotificationText("Battery 83% · 28.0 °C", "RAM 5.2 / 7.4 GB")),
            Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT, Notification.EXTRA_TITLE_BIG,
        )
    }

    @Test
    fun `the battery, charge, data and health notifications are kept`() {
        BatteryFullNotifier.show(context)
        assertKept(posted(), Notification.EXTRA_TITLE, Notification.EXTRA_TEXT)

        assertThat(ChargeLimitNotifier.show(context, levelPercent = 80)).isTrue()
        assertKept(posted(), Notification.EXTRA_TITLE, Notification.EXTRA_TEXT)

        assertThat(DataQuotaNotifier.show(context, threshold = 80, usedGb = 1.6, quotaGb = 2.0)).isTrue()
        assertKept(posted(), Notification.EXTRA_TITLE, Notification.EXTRA_TEXT)

        val title = hotBatteryTitle(context, temperatureDeciC = 453)
        assertThat(HealthAlertNotifier.show(context, HealthAlert.HOT_BATTERY, title, context.getString(R.string.action_cancel))).isTrue()
        assertKept(posted(), Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT)
    }

    /** A phone set to Arabic, whatever language the app under test runs in. */
    @Implements(LocaleManager::class)
    class ArabicPhone {
        @Suppress("unused")
        @Implementation
        fun getSystemLocales(): LocaleList = LocaleList.forLanguageTags("ar-EG")
    }
}
