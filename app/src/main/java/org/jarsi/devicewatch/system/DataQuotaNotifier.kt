package org.jarsi.devicewatch.system

import android.Manifest
import android.annotation.SuppressLint
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
import org.jarsi.devicewatch.data.DataQuotaLogic
import org.jarsi.devicewatch.widget.mobileDataText

/**
 * Mobile-data quota alerts. Its own channel, so a user who wants these but not the
 * battery reminders (or the other way round) can silence one without the other; a
 * single notification id, so the "quota reached" alert replaces the 80 % warning.
 */
object DataQuotaNotifier {
    private const val CHANNEL_ID = "data_quota_channel"
    private const val NOTIFICATION_ID = 1004

    /**
     * Posts the alert and reports whether it was actually handed to the system. The
     * caller writes the per-period latch only on true, so an alert refused for a
     * missing permission is retried instead of being marked as delivered.
     */
    @SuppressLint("MissingPermission")
    fun show(context: Context, threshold: Int, usedGb: Double, quotaGb: Double): Boolean {
        if (!canPostNotifications(context)) return false
        createChannel(context)
        if (!canReachUser(context)) return false

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (threshold >= DataQuotaLogic.REACHED_PERCENT) {
            context.getString(R.string.data_quota_reached_title)
        } else {
            context.getString(R.string.data_quota_warning_title, threshold)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            // Same "used / quota" rendering the widget and the overview row use.
            .setContentText(mobileDataText(usedGb, quotaGb))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            // Permission can still be revoked between the check and notify().
            false
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    /** The app-level switch and the channel block both make notify() a silent no-op. */
    private fun canReachUser(context: Context): Boolean {
        val manager = NotificationManagerCompat.from(context)
        return NotificationDelivery.canReachUser(
            appNotificationsEnabled = manager.areNotificationsEnabled(),
            channelImportance = manager.getNotificationChannel(CHANNEL_ID)?.importance,
        )
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.data_quota_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.data_quota_channel_description)
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}

/**
 * The quota, the counter mode or the cycle start day changed. An alert on screen
 * spoke of the old settings: a quota since removed or raised, or another period.
 * It goes first, then [recheck] posts whatever the new settings call for; the
 * other order could take down the new settings' own alert.
 */
internal fun onDataSettingsChanged(context: Context, recheck: () -> Unit) {
    DataQuotaNotifier.cancel(context)
    recheck()
}
