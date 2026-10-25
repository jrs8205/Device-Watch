package org.jarsi.devicewatch.system

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.jarsi.devicewatch.MainActivity
import org.jarsi.devicewatch.R

/**
 * The monitor's foreground-service notification in its two forms: silent at the
 * bottom of the shade (the default), or the figures chosen in Settings among the
 * other notifications (GitHub #7). A channel's importance cannot be raised once
 * created, so each form has its own channel; switching re-posts the same id on
 * the other one.
 */
class MonitorNotifications(private val context: Context) {

    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // IMPORTANCE_MIN keeps the silent form out of the status bar and collapsed at the
        // bottom of the silent section. Importance cannot be lowered after creation either,
        // which is why the old LOW-importance channel this one replaced is deleted.
        manager.deleteNotificationChannel(OLD_CHANNEL_ID)
        manager.createNotificationChannel(
            NotificationChannel(
                QUIET_CHANNEL_ID,
                context.getString(R.string.monitor_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = context.getString(R.string.monitor_channel_description)
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                LIVE_CHANNEL_ID,
                context.getString(R.string.monitor_live_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.monitor_live_channel_description)
                setShowBadge(false)
            }
        )
    }

    fun quiet(): Notification = NotificationCompat.Builder(context, QUIET_CHANNEL_ID)
        .setContentTitle(context.getString(R.string.monitor_notification_title))
        .setContentText(context.getString(R.string.monitor_notification_text))
        .setSmallIcon(R.drawable.ic_stat_device_watch)
        .setContentIntent(openOverview())
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setOngoing(true)
        .setSilent(true)
        .setShowWhen(false)
        .setVisibility(NotificationCompat.VISIBILITY_SECRET)
        .build()

    /** The chosen figures; with nothing readable ([text] null), the monitor's own words. */
    fun live(text: LiveNotificationText?): Notification {
        val builder = NotificationCompat.Builder(context, LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_device_watch)
            .setContentIntent(openOverview())
            .setDeleteIntent(dismissed())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
        if (text == null) {
            builder
                .setContentTitle(context.getString(R.string.monitor_notification_title))
                .setContentText(context.getString(R.string.monitor_notification_text))
        } else {
            // A title is one line in both views and cuts off a long first line, so the
            // expanded view swaps it for a short heading and wraps every figure under
            // it, each once.
            builder
                .setContentTitle(text.title)
                .setContentText(text.text)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(context.getString(R.string.monitor_live_channel_name))
                        .bigText(text.content)
                )
        }
        return builder.build()
    }

    /**
     * Replaces the posted notification; returns whether it went out. Without the
     * permission, with the app's notifications switched off or with the
     * notification's own channel blocked (all of which notify() ignores silently),
     * nothing is posted.
     */
    fun post(id: Int, notification: Notification): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = NotificationManagerCompat.from(context)
        val reachable = NotificationDelivery.canReachUser(
            appNotificationsEnabled = manager.areNotificationsEnabled(),
            channelImportance = manager.getNotificationChannel(notification.channelId)?.importance,
        )
        if (!reachable) return false
        return try {
            manager.notify(id, notification)
            true
        } catch (_: SecurityException) {
            // Permission can still be revoked between the check and notify().
            false
        }
    }

    /** Whether notification [id] is still posted; assumed so when the system cannot say. */
    fun isShowing(id: Int): Boolean = try {
        context.getSystemService(NotificationManager::class.java)?.activeNotifications?.any { it.id == id } ?: true
    } catch (_: Exception) {
        true
    }

    private fun openOverview(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN_OVERVIEW,
        MainActivity.openOverviewIntent(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Tells the service the live notification was swiped away, so it is held back until the screen comes on. */
    private fun dismissed(): PendingIntent = PendingIntent.getService(
        context,
        REQUEST_DISMISSED,
        Intent(context, SystemMonitorService::class.java).setAction(SystemMonitorService.ACTION_LIVE_NOTIFICATION_DISMISSED),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val QUIET_CHANNEL_ID = "system_monitor_channel_min"
        const val LIVE_CHANNEL_ID = "system_monitor_channel_live"
        private const val OLD_CHANNEL_ID = "system_monitor_channel"
        private const val REQUEST_OPEN_OVERVIEW = 1101
        private const val REQUEST_DISMISSED = 1102
    }
}
