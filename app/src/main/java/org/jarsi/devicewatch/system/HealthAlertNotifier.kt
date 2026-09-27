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
import org.jarsi.devicewatch.data.HealthAlert

/**
 * The optional alerts: a hot battery, low storage and a fast drain. One channel
 * for the three, apart from the battery and data ones, and one notification id
 * per alert so each replaces only itself.
 */
object HealthAlertNotifier {
    private const val CHANNEL_ID = "device_alerts_channel"

    private fun notificationId(alert: HealthAlert): Int = when (alert) {
        HealthAlert.HOT_BATTERY -> 1005
        HealthAlert.LOW_STORAGE -> 1006
        HealthAlert.FAST_DRAIN -> 1007
    }

    /** Posts the alert and reports whether it reached the system; the caller latches only on true. */
    @SuppressLint("MissingPermission")
    fun show(context: Context, alert: HealthAlert, title: String, text: String): Boolean {
        if (!canPostNotifications(context)) return false
        createChannel(context)
        if (!canReachUser(context)) return false

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId(alert), notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    /** Takes down an alert whose condition has cleared: it would be stale advice in the shade. */
    fun cancel(context: Context, alert: HealthAlert) {
        NotificationManagerCompat.from(context).cancel(notificationId(alert))
    }

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

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
            context.getString(R.string.alert_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.alert_channel_description)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
