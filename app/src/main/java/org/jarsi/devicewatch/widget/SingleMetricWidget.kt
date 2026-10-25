package org.jarsi.devicewatch.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jarsi.devicewatch.MainActivity
import org.jarsi.devicewatch.data.QuickMetric
import org.jarsi.devicewatch.data.SystemStatsRepository
import org.jarsi.devicewatch.data.UNAVAILABLE_INT
import org.jarsi.devicewatch.data.UNAVAILABLE_TEXT
import org.jarsi.devicewatch.data.singleMetricOf
import org.jarsi.devicewatch.quick.quickMetricNameRes
import org.jarsi.devicewatch.system.SystemMonitorService
import javax.inject.Inject

/**
 * A 1×1 widget showing one figure of the user's choice (GitHub #7), picked in
 * [SingleMetricConfigActivity] when it is added. [WidgetStateUpdater] writes the
 * formatted figure into its state with every reading the other widgets get.
 */
class SingleMetricWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    // The value is fitted to the widget's real width, which only Exact reports.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        WidgetStateUpdater.initializeAppearance(context, id)
        provideContent {
            GlanceTheme {
                SingleMetricWidgetContent()
            }
        }
    }
}

@AndroidEntryPoint
class SingleMetricWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = SingleMetricWidget()

    @Inject lateinit var repository: SystemStatsRepository

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        // As for the compact widget: a home screen with only these still needs the monitor.
        if (intent.action == "android.appwidget.action.APPWIDGET_UPDATE") {
            startMonitorService(context)
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    WidgetStateUpdater.updateAll(context.applicationContext, repository.getStats())
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult?.finish()
                }
            }
        }
    }

    private fun startMonitorService(context: Context) {
        try {
            context.startForegroundService(Intent(context, SystemMonitorService::class.java))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * The figure's name, its value as large as the width allows, and a line under it:
 * the detail (charging, the mobile/Wi-Fi split) or why the figure is missing.
 */
@Composable
fun SingleMetricWidgetContent() {
    val spaceScale = widgetSpaceScale(LocalSize.current.height.value, singleNeededHeightDp(widgetFontScale()))
    CompositionLocalProvider(LocalWidgetSpaceScale provides spaceScale) {
        SingleContent()
    }
}

/** The height the content needs at full size, in dp: padding, the name, the value and the line under it. */
fun singleNeededHeightDp(fontScale: Float): Float {
    fun line(dp: Float) = widgetLineDp(dp, fontScale)
    return 10f * 2 + line(9f) + line(22f) + line(9f)
}

@Composable
private fun SingleContent() {
    val context = androidx.glance.LocalContext.current
    val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    val prefs = currentState<Preferences>()
    val opacity = prefs[RefreshStatsAction.BACKGROUND_OPACITY] ?: (if (isDark) 0.86f else 0.94f)
    val blackBackground = prefs[RefreshStatsAction.BLACK_BACKGROUND] ?: false
    val colors = getWidgetColors(isDark, opacity, blackBackground)

    val metric = singleMetricOf(prefs[RefreshStatsAction.SINGLE_METRIC])
    val label = context.getString(quickMetricNameRes(metric).second).uppercase()
    val value = prefs[RefreshStatsAction.SINGLE_VALUE] ?: UNAVAILABLE_TEXT
    val reason = prefs[RefreshStatsAction.SINGLE_REASON]
    val under = reason ?: prefs[RefreshStatsAction.SINGLE_DETAIL]
    val batteryLevel = prefs[RefreshStatsAction.BATTERY_LEVEL] ?: UNAVAILABLE_INT
    val valueColor = when {
        reason != null || value == UNAVAILABLE_TEXT -> colors.textMuted
        // The battery keeps the other widgets' low-level warning colour.
        metric == QuickMetric.BATTERY && batteryLevel >= 0 ->
            getMetricColor(batteryLevel, colors.batteryAccent, isLimitHigh = false)
        else -> colors.textPrimary
    }
    val widthDp = (LocalSize.current.width - widgetDp(10f) * 2).value

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(colors.cardBackground)
            .cornerRadius(widgetDp(20f))
            .padding(widgetDp(10f))
            .clickable(actionStartActivity(MainActivity.openOverviewIntent(context)))
    ) {
        val labelFit = fittedName(label, widgetTextDp(9f), widthDp)
        Text(
            text = labelFit.text,
            style = TextStyle(
                fontSize = spForDp(labelFit.sizeDp),
                fontWeight = FontWeight.Bold,
                color = colors.labelText
            ),
            maxLines = 1
        )
        Text(
            text = value,
            style = TextStyle(
                fontSize = spForDp(fittedTextDp(value, widgetTextDp(22f), widthDp)),
                fontWeight = FontWeight.Bold,
                color = valueColor
            ),
            maxLines = 1
        )
        if (under != null) {
            Text(
                text = under,
                style = TextStyle(
                    fontSize = spForDp(widgetTextDp(9f)),
                    color = colors.textMuted
                ),
                maxLines = 2
            )
        }
    }
}
