package org.jarsi.devicewatch.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun CompactWidgetContent() {
    val context = androidx.glance.LocalContext.current
    val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    val prefs = currentState<Preferences>()
    val opacity = prefs[RefreshStatsAction.BACKGROUND_OPACITY] ?: (if (isDark) 0.86f else 0.94f)
    val colors = getWidgetColors(isDark, opacity)

    val batteryLevel = prefs[RefreshStatsAction.BATTERY_LEVEL] ?: UNAVAILABLE_INT
    val batteryStatus = prefs[RefreshStatsAction.BATTERY_STATUS] ?: UNAVAILABLE_TEXT
    val mobileDataUsed = prefs[RefreshStatsAction.MOBILE_DATA_USED] ?: UNAVAILABLE_DOUBLE
    val mobileDataTotal = prefs[RefreshStatsAction.MOBILE_DATA_TOTAL] ?: UNAVAILABLE_DOUBLE
    val mobileDataLabel = prefs[RefreshStatsAction.MOBILE_DATA_LABEL]
        ?: context.getString(R.string.mobile_data_label)
    val wifiBytesToday = prefs[RefreshStatsAction.WIFI_BYTES_TODAY] ?: UNAVAILABLE_DOUBLE

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(colors.cardBackground)
            .cornerRadius(30.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth()
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_battery),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.batteryAccent),
                modifier = GlanceModifier.size(16.dp)
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = percentText(batteryLevel),
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = batteryStatus,
                style = TextStyle(fontSize = 10.sp, color = colors.textMuted)
            )
        }
        Spacer(modifier = GlanceModifier.height(6.dp))
        ProgressBar(
            percent = batteryLevel,
            activeColor = getMetricColor(batteryLevel, colors.batteryAccent, isLimitHigh = false),
            trackColor = colors.progressTrack
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth()
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_cellular),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.mobileAccent),
                modifier = GlanceModifier.size(14.dp)
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = mobileDataLabel,
                style = TextStyle(fontSize = 10.sp, color = colors.labelText)
            )
        }
        Text(
            text = mobileDataText(mobileDataUsed, mobileDataTotal),
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth()
        ) {
            Text(
                text = context.getString(R.string.widget_tile_network),
                style = TextStyle(fontSize = 10.sp, color = colors.labelText)
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = dataAmountText(wifiBytesToday),
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.capacityText
                )
            )
        }
    }
}
