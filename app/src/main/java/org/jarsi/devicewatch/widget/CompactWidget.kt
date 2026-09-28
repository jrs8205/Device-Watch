package org.jarsi.devicewatch.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
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
import org.jarsi.devicewatch.data.DataSpan
import org.jarsi.devicewatch.data.SystemStatsRepository
import org.jarsi.devicewatch.data.UNAVAILABLE_DOUBLE
import org.jarsi.devicewatch.data.UNAVAILABLE_INT
import org.jarsi.devicewatch.data.UNAVAILABLE_TEXT
import org.jarsi.devicewatch.system.SystemMonitorService
import java.util.Locale
import javax.inject.Inject

/**
 * 2×2 battery-and-data variant of the dashboard widget. Reads the same
 * per-instance preference state [WidgetStateUpdater] writes for every widget,
 * so it needs no data path of its own.
 */
class CompactWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    // The cell text is fitted to the widget's real width, which only Exact reports.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        WidgetStateUpdater.initializeAppearance(context, id)
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
 * no room for the reader to infer what a bare number means. The data cells are
 * headed with the span their figure covers (today, the billing period, since
 * boot) and leave the network to their icon, which also names it to a screen
 * reader: a cell has no room for both words. The layout holds
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
    val blackBackground = prefs[RefreshStatsAction.BLACK_BACKGROUND] ?: false
    val colors = getWidgetColors(isDark, opacity, blackBackground)

    val batteryLevel = prefs[RefreshStatsAction.BATTERY_LEVEL] ?: UNAVAILABLE_INT
    val uptimeMillis = prefs[RefreshStatsAction.UPTIME_MILLIS] ?: -1L
    val mobileDataUsed = prefs[RefreshStatsAction.MOBILE_DATA_USED] ?: UNAVAILABLE_DOUBLE
    val wifiBytes = prefs[RefreshStatsAction.WIFI_BYTES_TODAY] ?: UNAVAILABLE_DOUBLE
    val mobileSpan = dataSpanOf(prefs[RefreshStatsAction.MOBILE_DATA_SPAN])
    val wifiSpan = dataSpanOf(prefs[RefreshStatsAction.WIFI_DATA_SPAN])
    val cellWidthDp = (LocalSize.current.width - widgetDp(12f) * 2 - widgetDp(8f)).value / 2

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(colors.cardBackground)
            .cornerRadius(widgetDp(30f))
            .padding(widgetDp(12f))
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
                widthDp = cellWidthDp,
                modifier = GlanceModifier.defaultWeight()
            )
            Spacer(modifier = GlanceModifier.width(widgetDp(8f)))
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
                widthDp = cellWidthDp,
                modifier = GlanceModifier.defaultWeight()
            )
        }
        Spacer(modifier = GlanceModifier.height(widgetDp(8f)))
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            CompactCell(
                label = context.getString(compactDataHeading(mobileSpan, R.string.widget_tile_mobile)),
                value = compactDataAmountText(mobileDataUsed),
                valueColor = colors.textPrimary,
                iconRes = R.drawable.ic_widget_cellular,
                iconColor = colors.mobileAccent,
                iconDescription = context.getString(R.string.widget_tile_mobile),
                colors = colors,
                widthDp = cellWidthDp,
                modifier = GlanceModifier.defaultWeight()
            )
            Spacer(modifier = GlanceModifier.width(widgetDp(8f)))
            CompactCell(
                label = context.getString(compactDataHeading(wifiSpan, R.string.widget_tile_wifi)),
                value = compactDataAmountText(wifiBytes),
                valueColor = colors.textPrimary,
                iconRes = R.drawable.ic_widget_wifi,
                iconColor = colors.networkAccent,
                iconDescription = context.getString(R.string.widget_tile_wifi),
                colors = colors,
                widthDp = cellWidthDp,
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
    widthDp: Float,
    modifier: GlanceModifier = GlanceModifier,
    /** Needed when the heading does not name what the icon shows. */
    iconDescription: String? = null,
) {
    val iconDp = 11f
    val labelWidthDp = widthDp - widgetDp(iconDp + 4f).value
    Column(modifier = modifier.fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = iconDescription,
                colorFilter = ColorFilter.tint(iconColor),
                modifier = GlanceModifier.size(widgetDp(iconDp))
            )
            Spacer(modifier = GlanceModifier.width(widgetDp(4f)))
            Text(
                text = label,
                style = TextStyle(
                    fontSize = spForDp(fittedTextDp(label, widgetTextDp(9f), labelWidthDp)),
                    fontWeight = FontWeight.Bold,
                    color = colors.labelText
                ),
                maxLines = 1
            )
        }
        Text(
            text = value,
            style = TextStyle(
                fontSize = spForDp(fittedTextDp(value, widgetTextDp(16f), widthDp)),
                fontWeight = FontWeight.Bold,
                color = valueColor
            ),
            maxLines = 1
        )
    }
}

/** A stored [DataSpan] name; UNKNOWN for none (state written before 1.6.0) or a name no longer known. */
fun dataSpanOf(name: String?): DataSpan = DataSpan.entries.firstOrNull { it.name == name } ?: DataSpan.UNKNOWN

/** The heading of a compact data cell: the span its figure covers, or [networkHeading] when that is unknown. */
fun compactDataHeading(span: DataSpan, networkHeading: Int): Int = when (span) {
    DataSpan.TODAY -> R.string.widget_span_today
    DataSpan.PERIOD -> R.string.widget_span_period
    DataSpan.SINCE_BOOT -> R.string.widget_span_since_boot
    DataSpan.UNKNOWN -> networkHeading
}

/**
 * Data amount for a compact cell: at most three significant digits, so the
 * widest figure is "512 MB" or "100 GB" rather than the large widget's
 * "100.00 GB".
 */
fun compactDataAmountText(gbValue: Double): String {
    if (gbValue < 0.0) return UNAVAILABLE_TEXT
    return when {
        gbValue * 1024.0 < 999.5 -> dataAmountText(gbValue)
        gbValue < 9.95 -> String.format(Locale.getDefault(), "%.1f GB", gbValue)
        else -> String.format(Locale.getDefault(), "%.0f GB", gbValue)
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
