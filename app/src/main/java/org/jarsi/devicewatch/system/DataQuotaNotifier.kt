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

    @SuppressLint("MissingPermission")
    fun show(context: Context, threshold: Int, usedGb: Double, quotaGb: Double) {
        if (!canPostNotifications(context)) return
        createChannel(context)

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

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission can still be revoked between the check and notify().
        }
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
            context.getString(R.string.data_quota_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.data_quota_channel_description)
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
