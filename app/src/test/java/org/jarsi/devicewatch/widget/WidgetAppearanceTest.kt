package org.jarsi.devicewatch.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WidgetAppearanceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val controller = GlanceWidgetController(context)

    private fun addWidget(id: Int, receiver: Class<*>): GlanceId {
        val info = AppWidgetProviderInfo().apply { provider = ComponentName(context, receiver) }
        val manager = AppWidgetManager.getInstance(context)
        shadowOf(manager).addInstalledProvider(info)
        shadowOf(manager).addBoundWidget(id, info)
        return GlanceAppWidgetManager(context).getGlanceIdBy(id)
    }

    private suspend fun background(id: GlanceId): Boolean? =
        getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[RefreshStatsAction.BLACK_BACKGROUND]

    private suspend fun legacyBackground(id: GlanceId, black: Boolean) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) {
            it.toMutablePreferences().apply { this[RefreshStatsAction.BLACK_BACKGROUND] = black }
        }
    }

    @Test
    fun `a newly added compact widget inherits the dashboard black background`() = runBlocking {
        androidx.work.WorkManager.initialize(context, androidx.work.Configuration.Builder().build())
        val dashboard = addWidget(101, DashboardWidgetReceiver::class.java)
        controller.setBlackBackground(true)
        assertThat(background(dashboard)).isTrue()

        val compact = addWidget(102, CompactWidgetReceiver::class.java)
        WidgetStateUpdater.initializeAppearance(context, compact)

        assertThat(background(compact)).isTrue()
        assertThat(GlanceWidgetController(context).currentBlackBackground()).isTrue()
    }

    @Test
    fun `a choice saved without widgets survives controller recreation and new instances`() = runBlocking {
        controller.setBlackBackground(true)
        assertThat(GlanceWidgetController(context).currentBlackBackground()).isTrue()
        val dashboard = addWidget(103, DashboardWidgetReceiver::class.java)
        val compact = addWidget(104, CompactWidgetReceiver::class.java)
        WidgetStateUpdater.initializeAppearance(context, dashboard)
        WidgetStateUpdater.initializeAppearance(context, compact)
        assertThat(background(dashboard)).isTrue()
        assertThat(background(compact)).isTrue()
    }

    @Test
    fun `an empty dashboard does not hide a legacy compact widget choice`() = runBlocking {
        addWidget(105, DashboardWidgetReceiver::class.java)
        val compact = addWidget(106, CompactWidgetReceiver::class.java)
        legacyBackground(compact, true)

        assertThat(controller.currentBlackBackground()).isTrue()
        assertThat(WidgetAppearanceSettings.blackBackground(context)).isTrue()
    }

    @Test
    fun `turning black off overrides old instance preferences on initialization`() = runBlocking {
        controller.setBlackBackground(false)
        val compact = addWidget(107, CompactWidgetReceiver::class.java)
        legacyBackground(compact, true)
        WidgetStateUpdater.initializeAppearance(context, compact)
        assertThat(background(compact)).isFalse()
    }

    @Test
    fun `legacy migration cannot overwrite a newer user choice`() {
        WidgetAppearanceSettings.setBlackBackground(context, false)
        assertThat(WidgetAppearanceSettings.migrateBlackBackground(context, true)).isFalse()
        assertThat(WidgetAppearanceSettings.blackBackground(context)).isFalse()
    }
}
