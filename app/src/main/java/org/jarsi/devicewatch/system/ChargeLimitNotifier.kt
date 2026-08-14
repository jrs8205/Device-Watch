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

/**
 * "You can unplug now" reminder for the user-chosen charge level. Shares the
 * battery-alerts channel with [BatteryFullNotifier] but uses its own notification
 * id, so a full-charge alert never replaces this one.
 */
object ChargeLimitNotifier {
    private const val CHANNEL_ID = "battery_full_channel"
    private const val NOTIFICATION_ID = 1003

    @SuppressLint("MissingPermission")
    fun show(context: Context, levelPercent: Int) {
        if (!canPostNotifications(context)) return
        createChannel(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(
                context.getString(R.string.charge_limit_notification_title, levelPercent)
            )
            .setContentText(context.getString(R.string.charge_limit_notification_text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission can still be revoked between the check and notify().
        }
    }

    /** Clears the reminder once the charger is out — its advice no longer applies. */
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

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.battery_full_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.battery_full_channel_description)
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
