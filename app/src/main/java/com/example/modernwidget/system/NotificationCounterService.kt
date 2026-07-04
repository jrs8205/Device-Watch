package com.example.modernwidget.system

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.modernwidget.data.NotificationCounting
import com.example.modernwidget.data.NotificationLog
import com.example.modernwidget.data.NotificationLogEntry
import com.example.modernwidget.data.NotificationStats
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Counts "real" notifications per day and per app. Ongoing notifications, group
 * summaries and updates to an already-active key are filtered out (see
 * [NotificationCounting.shouldCountNotification]) so the daily number stays
 * believable — unlike raw post counts, which inflate quickly. Counting starts
 * when the user grants notification access; the listener cannot see the past.
 * Counted notifications are also appended to the on-device NotificationLog with title and text.
 */
@AndroidEntryPoint
class NotificationCounterService : NotificationListenerService() {

    @Inject
    lateinit var notificationStats: NotificationStats

    @Inject
    lateinit var notificationLog: NotificationLog

    /** File writes happen off the main thread; the listener callback must not block. */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val activeKeys = mutableSetOf<String>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeKeys.clear()
        try {
            activeNotifications?.forEach { activeKeys.add(it.key) }
        } catch (_: SecurityException) {
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
            val entry = NotificationLogEntry(
                timeMillis = sbn.postTime,
                packageName = sbn.packageName,
                appLabel = appLabel(sbn.packageName),
                title = sbn.notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                text = sbn.notification.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            )
            ioScope.launch { notificationLog.append(entry) }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let { activeKeys.remove(it.key) }
    }

    override fun onDestroy() {
        ioScope.cancel()
        super.onDestroy()
    }

    private fun appLabel(packageName: String): String = try {
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(packageName, 0)
        ).toString()
    } catch (_: Exception) {
        packageName
    }
}
