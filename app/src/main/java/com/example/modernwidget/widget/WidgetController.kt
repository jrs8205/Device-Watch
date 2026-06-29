package com.example.modernwidget.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.example.modernwidget.data.SystemStats
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Presentation-facing port over the home screen widget. Keeps Glance and Context
 * out of the ViewModel so the ViewModel stays a pure-JVM unit under test.
 */
interface WidgetController {
    /** Pushes fresh [stats] to every installed widget; returns whether any widget exists. */
    suspend fun pushStats(stats: SystemStats): Boolean

    /** Saved background opacity of the first widget, or null when no widget is installed. */
    suspend fun currentOpacity(): Float?

    /** Persists [opacity] to every installed widget and re-renders them. */
    suspend fun setOpacity(opacity: Float)
}

@Singleton
class GlanceWidgetController @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetController {

    override suspend fun pushStats(stats: SystemStats): Boolean =
        WidgetStateUpdater.updateAll(context, stats)

    override suspend fun currentOpacity(): Float? {
        val manager = GlanceAppWidgetManager(context)
        val glanceId = manager.getGlanceIds(DashboardWidget::class.java).firstOrNull() ?: return null
        return try {
            getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[
                RefreshStatsAction.BACKGROUND_OPACITY
            ]
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun setOpacity(opacity: Float) {
        val manager = GlanceAppWidgetManager(context)
        for (glanceId in manager.getGlanceIds(DashboardWidget::class.java)) {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[RefreshStatsAction.BACKGROUND_OPACITY] = opacity
                }
            }
            DashboardWidget().update(context, glanceId)
        }
    }
}
