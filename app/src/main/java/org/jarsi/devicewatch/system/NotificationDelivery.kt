package org.jarsi.devicewatch.system

import android.app.NotificationManager

/**
 * Whether a notification handed to the system will actually reach the shade.
 * `notify()` returns normally when the user has switched the app's notifications
 * off, or blocked the channel, so a caller that latches "sent" on a normal
 * return would never try again once the user switches them back on.
 */
object NotificationDelivery {

    /** [channelImportance] is null where channels do not exist (before Android 8). */
    fun canReachUser(appNotificationsEnabled: Boolean, channelImportance: Int?): Boolean =
        appNotificationsEnabled && channelImportance != NotificationManager.IMPORTANCE_NONE
}
