package org.jarsi.devicewatch.system

import android.graphics.drawable.Icon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One notification currently in the shade, as the listener sees it. Only what the
 * screensaver's notification row needs: no title, no text.
 */
data class ActiveNotification(
    val key: String,
    val packageName: String,
    val postTime: Long,
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
    val isClearable: Boolean,
    val smallIcon: Icon?,
)

/** One icon on the screensaver's notification row: an app and how many of its notifications wait. */
data class NotificationIconGroup(
    val packageName: String,
    val count: Int,
    val newestPostTime: Long,
    val smallIcon: Icon?,
)

/**
 * Process-wide snapshot of the active notifications, kept by [NotificationCounterService]
 * for [MonitorDreamService]. A plain object rather than a Hilt singleton because the dream
 * is not a Hilt entry point. Empty until notification access is granted and the listener
 * connects; cleared when it disconnects.
 */
object ActiveNotificationsStore {
    private val byKey = MutableStateFlow<Map<String, ActiveNotification>>(emptyMap())
    private val _notifications = MutableStateFlow<List<ActiveNotification>>(emptyList())

    val notifications: StateFlow<List<ActiveNotification>> = _notifications.asStateFlow()

    fun replaceAll(active: List<ActiveNotification>) {
        byKey.value = active.associateBy { it.key }
        publish()
    }

    fun put(notification: ActiveNotification) {
        byKey.value = byKey.value + (notification.key to notification)
        publish()
    }

    fun remove(key: String) {
        if (key !in byKey.value) return
        byKey.value = byKey.value - key
        publish()
    }

    fun clear() {
        byKey.value = emptyMap()
        publish()
    }

    private fun publish() {
        _notifications.value = byKey.value.values.toList()
    }
}

/** Whether the screensaver row shows this notification: the ones a person still has to clear. */
internal fun isUnreadForDream(n: ActiveNotification): Boolean =
    !n.isOngoing && !n.isGroupSummary && n.isClearable

/**
 * Collapses the active notifications to one icon per app, newest app first. The icon is
 * the app's newest notification's small icon.
 */
internal fun visibleNotificationGroups(active: List<ActiveNotification>): List<NotificationIconGroup> =
    active.filter(::isUnreadForDream)
        .groupBy { it.packageName }
        .map { (packageName, list) ->
            val newest = list.maxBy { it.postTime }
            NotificationIconGroup(
                packageName = packageName,
                count = list.size,
                newestPostTime = newest.postTime,
                smallIcon = newest.smallIcon,
            )
        }
        .sortedByDescending { it.newestPostTime }

/** Keys of the notifications the row shows, for spotting arrivals between two snapshots. */
internal fun visibleNotificationKeys(active: List<ActiveNotification>): Set<String> =
    active.filter(::isUnreadForDream).mapTo(mutableSetOf()) { it.key }

/** Keys that are visible now and were not before: these pulse the row. */
internal fun newArrivalKeys(previous: Set<String>, current: Set<String>): Set<String> =
    current - previous
