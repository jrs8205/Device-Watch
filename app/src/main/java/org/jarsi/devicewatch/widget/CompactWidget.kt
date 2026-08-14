package org.jarsi.devicewatch.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jarsi.devicewatch.MainActivity
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.SystemStatsRepository
import org.jarsi.devicewatch.data.UNAVAILABLE_DOUBLE
import org.jarsi.devicewatch.data.UNAVAILABLE_INT
import org.jarsi.devicewatch.data.UNAVAILABLE_TEXT
import org.jarsi.devicewatch.system.SystemMonitorService
import javax.inject.Inject

/**
 * 2×2 battery-and-data variant of the dashboard widget. Reads the same
 * per-instance preference state [WidgetStateUpdater] writes for every widget,
 * so it needs no data path of its own.
 */
class CompactWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                CompactWidgetContent()
            }
        }
    }
}

@AndroidEntryPoint
class CompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = CompactWidget()

    @Inject lateinit var repository: SystemStatsRepository

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        // A compact-only home screen still needs the monitor service for data.
        // BOOT_COUNT registration stays with DashboardWidgetReceiver alone so
        // restarts are never double-counted.
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
        val serviceIntent = Intent(context, SystemMonitorService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * Four labelled quadrants: battery, uptime, mobile data and Wi-Fi data. Every
 * figure carries its own heading, because a 2×2 widget read at arm's length has
 * no room for the reader to infer what a bare number means. The layout holds
 * exactly four short lines so it survives a raised system font size — see
 * [widgetSp] for the cap that keeps it from clipping.
 */
@Composable
fun CompactWidgetContent() {
    val context = androidx.glance.LocalContext.current
    val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    val prefs = currentState<Preferences>()
    val opacity = prefs[RefreshStatsAction.BACKGROUND_OPACITY] ?: (if (isDark) 0.86f else 0.94f)
    val colors = getWidgetColors(isDark, opacity)

    val batteryLevel = prefs[RefreshStatsAction.BATTERY_LEVEL] ?: UNAVAILABLE_INT
    val uptimeMillis = prefs[RefreshStatsAction.UPTIME_MILLIS] ?: -1L
    val mobileDataUsed = prefs[RefreshStatsAction.MOBILE_DATA_USED] ?: UNAVAILABLE_DOUBLE
    val wifiBytes = prefs[RefreshStatsAction.WIFI_BYTES_TODAY] ?: UNAVAILABLE_DOUBLE

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(colors.cardBackground)
            .cornerRadius(30.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            CompactCell(
                label = context.getString(R.string.widget_tile_battery),
                value = percentText(batteryLevel),
                valueColor = if (batteryLevel >= 0) {
                    getMetricColor(batteryLevel, colors.batteryAccent, isLimitHigh = false)
                } else {
                    colors.textMuted
                },
                iconRes = R.drawable.ic_widget_battery,
                iconColor = colors.batteryAccent,
                colors = colors,
                modifier = GlanceModifier.defaultWeight()
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            CompactCell(
                label = context.getString(R.string.widget_tile_uptime),
                value = compactUptimeText(
                    millis = uptimeMillis,
                    dayUnit = context.getString(R.string.unit_day_short),
                    hourUnit = context.getString(R.string.unit_hour_short),
                    minuteUnit = context.getString(R.string.unit_minute_short),
                ),
                valueColor = colors.textPrimary,
                iconRes = R.drawable.ic_widget_schedule,
                iconColor = colors.cpuAccent,
                colors = colors,
                modifier = GlanceModifier.defaultWeight()
            )
        }
        Spacer(modifier = GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            CompactCell(
                label = context.getString(R.string.widget_tile_mobile),
                value = dataAmountText(mobileDataUsed),
                valueColor = colors.textPrimary,
                iconRes = R.drawable.ic_widget_cellular,
                iconColor = colors.mobileAccent,
                colors = colors,
                modifier = GlanceModifier.defaultWeight()
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            CompactCell(
                label = context.getString(R.string.widget_tile_wifi),
                value = dataAmountText(wifiBytes),
                valueColor = colors.textPrimary,
                iconRes = R.drawable.ic_widget_wifi,
                iconColor = colors.networkAccent,
                colors = colors,
                modifier = GlanceModifier.defaultWeight()
            )
        }
    }
}

@Composable
private fun CompactCell(
    label: String,
    value: String,
    valueColor: ColorProvider,
    iconRes: Int,
    iconColor: ColorProvider,
    colors: WidgetColors,
    modifier: GlanceModifier = GlanceModifier,
) {
    Column(modifier = modifier.fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(iconColor),
                modifier = GlanceModifier.size(11.dp)
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = label,
                style = TextStyle(
                    fontSize = widgetSp(9f),
                    fontWeight = FontWeight.Bold,
                    color = colors.labelText
                ),
                maxLines = 1
            )
        }
        Text(
            text = value,
            style = TextStyle(
                fontSize = widgetSp(16f),
                fontWeight = FontWeight.Bold,
                color = valueColor
            ),
            maxLines = 1
        )
    }
}

/**
 * Uptime in a single unit — the widest this cell ever gets is "22 pv". Dropping
 * the smaller unit is deliberate: minute-level uptime matters on the large
 * widget, not in a quadrant three characters wide.
 */
fun compactUptimeText(
    millis: Long,
    dayUnit: String,
    hourUnit: String,
    minuteUnit: String,
): String {
    if (millis < 0L) return UNAVAILABLE_TEXT
    val minutes = millis / 60_000L
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days >= 1 -> "$days $dayUnit"
        hours >= 1 -> "$hours $hourUnit"
        else -> "$minutes $minuteUnit"
    }
}
