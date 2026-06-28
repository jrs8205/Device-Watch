package com.example.modernwidget.widget

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

class ToggleDndAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (notificationManager.isNotificationPolicyAccessGranted) {
            val currentFilter = notificationManager.currentInterruptionFilter
            // Jos DND on päällä (Priority/Alarms/None), kytketään se pois (Allow ALL)
            val newFilter = if (currentFilter == NotificationManager.INTERRUPTION_FILTER_NONE ||
                currentFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
                currentFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS
            ) {
                NotificationManager.INTERRUPTION_FILTER_ALL
            } else {
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            }
            try {
                notificationManager.setInterruptionFilter(newFilter)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // Avaa DND-asetussivu lupien antamista varten
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        
        // Päivitetään widgetin tiedot heti
        RefreshStatsAction().onAction(context, glanceId, parameters)
    }
}
