package org.jarsi.devicewatch.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.selection.selectable
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.QuickMetric
import org.jarsi.devicewatch.data.singleMetricOf
import org.jarsi.devicewatch.quick.QuickEntryPoint
import org.jarsi.devicewatch.quick.quickMetricTitleRes
import org.jarsi.devicewatch.ui.theme.ModernWidgetTheme

/**
 * Where the single-figure widget gets its figure: opened by the launcher when the
 * widget is added, and again by a long press on it (Android 12+). Leaving without
 * a choice cancels the widget when it is being added; reconfigured, it keeps its figure.
 */
class SingleMetricConfigActivity : ComponentActivity() {

    private var current by mutableStateOf<QuickMetric?>(null)
    private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val entry = EntryPointAccessors.fromApplication(applicationContext, QuickEntryPoint::class.java)
        lifecycleScope.launch {
            current = try {
                val glanceId = GlanceAppWidgetManager(this@SingleMetricConfigActivity).getGlanceIdBy(appWidgetId)
                singleMetricOf(
                    getAppWidgetState(this@SingleMetricConfigActivity, PreferencesGlanceStateDefinition, glanceId)
                        [RefreshStatsAction.SINGLE_METRIC]
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                QuickMetric.BATTERY
            }
        }
        setContent {
            ModernWidgetTheme(classicLook = entry.appSettings().classicLook()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MetricPicker(selected = current, onPick = { choose(appWidgetId, it) })
                }
            }
        }
    }

    private fun choose(appWidgetId: Int, metric: QuickMetric) {
        if (saving) return
        saving = true
        current = metric
        val entry = EntryPointAccessors.fromApplication(applicationContext, QuickEntryPoint::class.java)
        lifecycleScope.launch {
            try {
                val glanceId = GlanceAppWidgetManager(this@SingleMetricConfigActivity).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(this@SingleMetricConfigActivity, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply { this[RefreshStatsAction.SINGLE_METRIC] = metric.name }
                }
                WidgetStateUpdater.updateAll(applicationContext, entry.quickStatsSource().stats())
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            }
            finish()
        }
    }
}

@androidx.compose.runtime.Composable
private fun MetricPicker(selected: QuickMetric?, onPick: (QuickMetric) -> Unit) {
    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .selectableGroup()
    ) {
        Text(
            text = stringResource(R.string.single_widget_pick_title),
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(16.dp))
        QuickMetric.entries.forEach { metric ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = metric == selected, role = Role.RadioButton, onClick = { onPick(metric) })
                    .padding(vertical = 12.dp)
            ) {
                RadioButton(selected = metric == selected, onClick = null)
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(quickMetricTitleRes(metric)),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
