package com.example.modernwidget.system

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.modernwidget.data.NotificationCounting
import com.example.modernwidget.data.NotificationStats
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import javax.inject.Inject

/**
 * Counts "real" notifications per day and per app. Ongoing notifications, group
 * summaries and updates to an already-active key are filtered out (see
 * [NotificationCounting.shouldCountNotification]) so the daily number stays
 * believable — unlike raw post counts, which inflate quickly. Counting starts
 * when the user grants notification access; the listener cannot see the past.
 */
@AndroidEntryPoint
class NotificationCounterService : NotificationListenerService() {

    @Inject
    lateinit var notificationStats: NotificationStats

    /** Keys of currently active notifications; a repost to one of these is an update, not new. */
    private val activeKeys = mutableSetOf<String>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeKeys.clear()
        try {
            // Seeding from the already-showing notifications prevents recounting them
            // after every listener reconnect (reboot, access toggled off/on).
            activeNotifications?.forEach { activeKeys.add(it.key) }
        } catch (_: SecurityException) {
            // Binder not fully connected yet; the set just starts empty.
        }
        notificationStats.purge(LocalDate.now())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val isGroupSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        val shouldCount = NotificationCounting.shouldCountNotification(
            isOngoing = sbn.isOngoing,
            isGroupSummary = isGroupSummary,
            keyAlreadyActive = sbn.key in activeKeys,
        )
        activeKeys.add(sbn.key)
        if (shouldCount) {
            notificationStats.increment(sbn.packageName, LocalDate.now())
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let { activeKeys.remove(it.key) }
    }
}
