package org.jarsi.devicewatch.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import org.jarsi.devicewatch.data.SystemStats
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

    /** Shared black-background setting, migrating an existing widget's choice if needed. */
    suspend fun currentBlackBackground(): Boolean?

    /** Saves the shared default and applies it to every installed widget. */
    suspend fun setBlackBackground(black: Boolean)
}

@Singleton
class GlanceWidgetController @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetController {

    override suspend fun pushStats(stats: SystemStats): Boolean =
        WidgetStateUpdater.updateAll(context, stats)

    override suspend fun currentOpacity(): Float? = readFirst(RefreshStatsAction.BACKGROUND_OPACITY)

    override suspend fun setOpacity(opacity: Float) = writeAll(RefreshStatsAction.BACKGROUND_OPACITY, opacity)

    override suspend fun currentBlackBackground(): Boolean? =
        WidgetAppearanceSettings.blackBackground(context)
            ?: readFirst(RefreshStatsAction.BLACK_BACKGROUND)?.let {
                WidgetAppearanceSettings.migrateBlackBackground(context, it)
            }

    override suspend fun setBlackBackground(black: Boolean) {
        WidgetAppearanceSettings.setBlackBackground(context, black)
        writeAll(RefreshStatsAction.BLACK_BACKGROUND, black)
    }

    /** First stored [key], skipping new instances that do not have a value yet. */
    private suspend fun <T> readFirst(key: Preferences.Key<T>): T? {
        val manager = GlanceAppWidgetManager(context)
        val ids = manager.getGlanceIds(DashboardWidget::class.java) +
            manager.getGlanceIds(CompactWidget::class.java)
        for (glanceId in ids) {
            try {
                getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[key]?.let { return it }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A newly added or unreadable instance must not hide an existing choice.
            }
        }
        return null
    }

    /** Writes [value] under [key] to every installed widget and re-renders them. */
    private suspend fun <T> writeAll(key: Preferences.Key<T>, value: T) {
        val manager = GlanceAppWidgetManager(context)
        for (glanceId in manager.getGlanceIds(DashboardWidget::class.java)) {
            write(glanceId, key, value)
            DashboardWidget().update(context, glanceId)
        }
        for (glanceId in manager.getGlanceIds(CompactWidget::class.java)) {
            write(glanceId, key, value)
            CompactWidget().update(context, glanceId)
        }
    }

    private suspend fun <T> write(glanceId: androidx.glance.GlanceId, key: Preferences.Key<T>, value: T) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply { this[key] = value }
        }
    }
}
