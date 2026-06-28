package com.example.modernwidget.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition

class ToggleConnectionRowAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val current = prefs[SHOW_WIFI_IN_CONNECTION_ROW] ?: false
            prefs.toMutablePreferences().apply {
                this[SHOW_WIFI_IN_CONNECTION_ROW] = !current
            }
        }
        DashboardWidget().update(context, glanceId)
    }

    companion object {
        val SHOW_WIFI_IN_CONNECTION_ROW = booleanPreferencesKey("show_wifi_in_connection_row")
    }
}
