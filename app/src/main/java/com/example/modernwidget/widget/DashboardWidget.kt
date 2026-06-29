package com.example.modernwidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
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
import androidx.glance.unit.ColorProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import com.example.modernwidget.R
import com.example.modernwidget.system.UNAVAILABLE_DOUBLE
import com.example.modernwidget.system.UNAVAILABLE_INT
import com.example.modernwidget.system.UNAVAILABLE_TEXT
import java.util.Locale

class WidgetColors(
    val cardBackground: ColorProvider,
    val cardBorder: ColorProvider,
    val tileBackground: ColorProvider,
    val tileBorder: ColorProvider,
    val progressTrack: ColorProvider,
    val textPrimary: ColorProvider,
    val textSecondary: ColorProvider,
    val textMuted: ColorProvider,
    val labelText: ColorProvider,
    val capacityText: ColorProvider,
    val bandChipBg: ColorProvider,
    val bandText: ColorProvider,
    
    // Accents
    val batteryAccent: ColorProvider,
    val ramAccent: ColorProvider,
    val cpuAccent: ColorProvider,
    val storageAccent: ColorProvider,
    val healthAccent: ColorProvider,
    val networkAccent: ColorProvider,
    val mobileAccent: ColorProvider,
    val downloadAccent: ColorProvider,
    val uploadAccent: ColorProvider,
    val locationAccent: ColorProvider,
    val nfcAccent: ColorProvider,
    val dndAccent: ColorProvider,
    
    // Live Pill
    val livePillBg: ColorProvider,
    val liveGreen: ColorProvider,
    val liveBolt: ColorProvider,

    // Status Chips
    val dndChipBg: ColorProvider,
    val dndChipBorder: ColorProvider,
    val bluetoothChipBg: ColorProvider,
    val bluetoothChipBorder: ColorProvider,
    val locationChipBg: ColorProvider,
    val locationChipBorder: ColorProvider,
    val nfcChipBg: ColorProvider,
    val nfcChipBorder: ColorProvider,
    val powerSaveChipBg: ColorProvider,
    val powerSaveChipBorder: ColorProvider,
    val airplaneChipBg: ColorProvider,
    val airplaneChipBorder: ColorProvider,

    // Button
    val buttonBg: ColorProvider,
    val buttonText: ColorProvider
)

enum class MetricTileKind {
    Standard,
    Wifi,
    BatteryHealth
}

fun getWidgetColors(isDark: Boolean, opacity: Float): WidgetColors {
    return if (isDark) {
        WidgetColors(
            cardBackground = ColorProvider(Color(0xFF12151A).copy(alpha = opacity)),
            cardBorder = ColorProvider(Color(0x12FFFFFF)),
            tileBackground = ColorProvider(Color(0x0AFFFFFF)), // rgba(255,255,255,0.04)
            tileBorder = ColorProvider(Color(0x0DFFFFFF)),
            progressTrack = ColorProvider(Color(0x14FFFFFF)),
            textPrimary = ColorProvider(Color(0xFFFFFFFF)),
            textSecondary = ColorProvider(Color(0xFF9AA0A8)),
            textMuted = ColorProvider(Color(0xFF7B828C)),
            labelText = ColorProvider(Color(0xFF8B929C)),
            capacityText = ColorProvider(Color(0xFFD7DADE)),
            bandChipBg = ColorProvider(Color(0x2438BDF8)),
            bandText = ColorProvider(Color(0xFF7DD3FC)),
            
            batteryAccent = ColorProvider(Color(0xFF34D399)),
            ramAccent = ColorProvider(Color(0xFFFB923C)),
            cpuAccent = ColorProvider(Color(0xFF22D3EE)),
            storageAccent = ColorProvider(Color(0xFFA78BFA)),
            healthAccent = ColorProvider(Color(0xFF6EE7A8)),
            networkAccent = ColorProvider(Color(0xFF38BDF8)),
            mobileAccent = ColorProvider(Color(0xFF38BDF8)),
            downloadAccent = ColorProvider(Color(0xFF34D399)),
            uploadAccent = ColorProvider(Color(0xFFFB923C)),
            locationAccent = ColorProvider(Color(0xFF34D399)),
            nfcAccent = ColorProvider(Color(0xFF2DD4BF)),
            dndAccent = ColorProvider(Color(0xFFA78BFA)),
            
            livePillBg = ColorProvider(Color(0x1F34D399)),
            liveGreen = ColorProvider(Color(0xFF34D399)),
            liveBolt = ColorProvider(Color(0xFFFBBF24)),

            dndChipBg = ColorProvider(Color(0x24A78BFA)),
            dndChipBorder = ColorProvider(Color(0x47A78BFA)),
            bluetoothChipBg = ColorProvider(Color(0x2438BDF8)),
            bluetoothChipBorder = ColorProvider(Color(0x4738BDF8)),
            locationChipBg = ColorProvider(Color(0x2434D399)),
            locationChipBorder = ColorProvider(Color(0x4734D399)),
            nfcChipBg = ColorProvider(Color(0x242DD4BF)),
            nfcChipBorder = ColorProvider(Color(0x472DD4BF)),
            powerSaveChipBg = ColorProvider(Color(0x2434D399)),
            powerSaveChipBorder = ColorProvider(Color(0x4734D399)),
            airplaneChipBg = ColorProvider(Color(0x24A78BFA)),
            airplaneChipBorder = ColorProvider(Color(0x47A78BFA)),

            buttonBg = ColorProvider(Color(0xFF38BDF8)),
            buttonText = ColorProvider(Color(0xFF0A1722))
        )
    } else {
        WidgetColors(
            cardBackground = ColorProvider(Color(0xFFFFFFFF).copy(alpha = opacity)),
            cardBorder = ColorProvider(Color(0x12000000)),     // rgba(0,0,0,0.07)
            tileBackground = ColorProvider(Color(0x09000000)), // rgba(0,0,0,0.035)
            tileBorder = ColorProvider(Color(0x0F000000)),     // rgba(0,0,0,0.06)
            progressTrack = ColorProvider(Color(0x17000000)),  // rgba(0,0,0,0.09)
            textPrimary = ColorProvider(Color(0xFF1A1D21)),
            textSecondary = ColorProvider(Color(0xFF6B7280)),
            textMuted = ColorProvider(Color(0xFF8A909A)),
            labelText = ColorProvider(Color(0xFF7A818B)),
            capacityText = ColorProvider(Color(0xFF2A2E33)),
            bandChipBg = ColorProvider(Color(0x2438BDF8)),
            bandText = ColorProvider(Color(0xFF0284C7)),
            
            batteryAccent = ColorProvider(Color(0xFF84CC16)),
            ramAccent = ColorProvider(Color(0xFFFB923C)),
            cpuAccent = ColorProvider(Color(0xFF22D3EE)),
            storageAccent = ColorProvider(Color(0xFFA78BFA)),
            healthAccent = ColorProvider(Color(0xFF16A34A)),
            networkAccent = ColorProvider(Color(0xFF0284C7)),
            mobileAccent = ColorProvider(Color(0xFF0284C7)),
            downloadAccent = ColorProvider(Color(0xFF34D399)),
            uploadAccent = ColorProvider(Color(0xFFFB923C)),
            locationAccent = ColorProvider(Color(0xFF16A34A)),
            nfcAccent = ColorProvider(Color(0xFF2DD4BF)),
            dndAccent = ColorProvider(Color(0xFF7C3AED)),
            
            livePillBg = ColorProvider(Color(0x1F15803D)),
            liveGreen = ColorProvider(Color(0xFF15803D)),
            liveBolt = ColorProvider(Color(0xFFD97706)),

            dndChipBg = ColorProvider(Color(0x247C3AED)),
            dndChipBorder = ColorProvider(Color(0x477C3AED)),
            bluetoothChipBg = ColorProvider(Color(0x2438BDF8)),
            bluetoothChipBorder = ColorProvider(Color(0x4738BDF8)),
            locationChipBg = ColorProvider(Color(0x2416A34A)),
            locationChipBorder = ColorProvider(Color(0x4716A34A)),
            nfcChipBg = ColorProvider(Color(0x242DD4BF)),
            nfcChipBorder = ColorProvider(Color(0x472DD4BF)),
            powerSaveChipBg = ColorProvider(Color(0x2484CC16)),
            powerSaveChipBorder = ColorProvider(Color(0x4784CC16)),
            airplaneChipBg = ColorProvider(Color(0x247C3AED)),
            airplaneChipBorder = ColorProvider(Color(0x477C3AED)),

            buttonBg = ColorProvider(Color(0xFF0284C7)),
            buttonText = ColorProvider(Color(0xFFFFFFFF))
        )
    }
}

class DashboardWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }
}

@Composable
fun WidgetContent() {
    val context = androidx.glance.LocalContext.current
    val isDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

    val prefs = currentState<Preferences>()
    val opacity = prefs[RefreshStatsAction.BACKGROUND_OPACITY] ?: (if (isDark) 0.86f else 0.94f)
    val colors = getWidgetColors(isDark, opacity)
    
    val batteryLevel = prefs[RefreshStatsAction.BATTERY_LEVEL] ?: UNAVAILABLE_INT
    val batteryStatus = prefs[RefreshStatsAction.BATTERY_STATUS] ?: UNAVAILABLE_TEXT
    val batteryHealth = prefs[RefreshStatsAction.BATTERY_HEALTH] ?: UNAVAILABLE_TEXT
    val batteryTemp = prefs[RefreshStatsAction.BATTERY_TEMP] ?: UNAVAILABLE_DOUBLE
    val batteryVoltage = prefs[RefreshStatsAction.BATTERY_VOLTAGE] ?: UNAVAILABLE_DOUBLE
    val timeRemaining = prefs[RefreshStatsAction.TIME_REMAINING] ?: UNAVAILABLE_TEXT
    val batteryCycles = prefs[RefreshStatsAction.BATTERY_CYCLE_COUNT] ?: UNAVAILABLE_INT
    val batteryCapacity = prefs[RefreshStatsAction.BATTERY_CAPACITY] ?: UNAVAILABLE_INT

    val totalRam = prefs[RefreshStatsAction.TOTAL_RAM] ?: UNAVAILABLE_DOUBLE
    val usedRam = prefs[RefreshStatsAction.USED_RAM] ?: UNAVAILABLE_DOUBLE
    val ramPercent = prefs[RefreshStatsAction.RAM_PERCENT] ?: UNAVAILABLE_INT

    val cpuCores = prefs[RefreshStatsAction.CPU_CORES] ?: UNAVAILABLE_INT
    val cpuAbi = prefs[RefreshStatsAction.CPU_ABI] ?: UNAVAILABLE_TEXT
    val cpuFreq = prefs[RefreshStatsAction.CPU_FREQ] ?: UNAVAILABLE_DOUBLE
    val cpuLoad = prefs[RefreshStatsAction.CPU_LOAD] ?: UNAVAILABLE_INT
    val cpuLoadLabel = prefs[RefreshStatsAction.CPU_LOAD_LABEL] ?: UNAVAILABLE_TEXT
    val cpuTemp = prefs[RefreshStatsAction.CPU_TEMP] ?: UNAVAILABLE_DOUBLE

    val totalStorage = prefs[RefreshStatsAction.TOTAL_STORAGE] ?: UNAVAILABLE_DOUBLE
    val usedStorage = prefs[RefreshStatsAction.USED_STORAGE] ?: UNAVAILABLE_DOUBLE
    val storagePercent = prefs[RefreshStatsAction.STORAGE_PERCENT] ?: UNAVAILABLE_INT

    val wifiSsid = prefs[RefreshStatsAction.WIFI_SSID] ?: UNAVAILABLE_TEXT
    val wifiBand = prefs[RefreshStatsAction.WIFI_BAND] ?: UNAVAILABLE_TEXT
    val wifiSpeedDown = prefs[RefreshStatsAction.WIFI_SPEED_DOWN] ?: UNAVAILABLE_INT
    val wifiSpeedUp = prefs[RefreshStatsAction.WIFI_SPEED_UP] ?: UNAVAILABLE_INT
    val wifiBytesToday = prefs[RefreshStatsAction.WIFI_BYTES_TODAY] ?: UNAVAILABLE_DOUBLE

    val operatorName = prefs[RefreshStatsAction.OPERATOR_NAME] ?: UNAVAILABLE_TEXT
    val mobileNetworkType = prefs[RefreshStatsAction.MOBILE_NETWORK_TYPE] ?: UNAVAILABLE_TEXT
    val mobileSignalDbm = prefs[RefreshStatsAction.MOBILE_SIGNAL_DBM] ?: UNAVAILABLE_INT
    val mobileDataUsed = prefs[RefreshStatsAction.MOBILE_DATA_USED] ?: UNAVAILABLE_DOUBLE
    val mobileDataTotal = prefs[RefreshStatsAction.MOBILE_DATA_TOTAL] ?: UNAVAILABLE_DOUBLE
    val mobileDataLabel = prefs[RefreshStatsAction.MOBILE_DATA_LABEL] ?: context.getString(R.string.mobile_data_label)

    val uptime = prefs[RefreshStatsAction.UPTIME] ?: UNAVAILABLE_TEXT
    val lastUpdated = prefs[RefreshStatsAction.LAST_UPDATED] ?: UNAVAILABLE_TEXT
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(colors.cardBackground)
            .cornerRadius(30.dp)
            .padding(22.dp)
    ) {
        // Ruudukko 2x3 (6 metriikkalaattaa, gap 12dp)
        Column(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight()
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                MetricTile(
                    title = context.getString(R.string.widget_tile_battery),
                    value = percentText(batteryLevel),
                    subtext = timeRemaining,
                    bottomText = "${formatDouble(batteryTemp, 1, "°C")} · ${formatDouble(batteryVoltage, 2, "V")}",
                    progressPercent = progressPercentOrNull(batteryLevel),
                    standardAccent = colors.batteryAccent,
                    isLimitHigh = false,
                    iconRes = R.drawable.ic_widget_battery,
                    iconColor = ColorProvider(Color(0xFF34D399)),
                    modifier = GlanceModifier.defaultWeight().clickable(
                        actionRunCallback<LaunchSettingsAction>(
                            actionParametersOf(LaunchSettingsAction.SettingsActionKey to "android.intent.action.POWER_USAGE_SUMMARY")
                        )
                    ),
                    colors = colors
                )
                Spacer(modifier = GlanceModifier.width(12.dp))
                val freeRam = if (totalRam >= 0.0 && usedRam >= 0.0) totalRam - usedRam else UNAVAILABLE_DOUBLE
                MetricTile(
                    title = context.getString(R.string.widget_tile_memory),
                    value = percentText(ramPercent),
                    subtext = "${gbText(usedRam)} / ${gbText(totalRam)}",
                    bottomText = context.getString(R.string.widget_free_value, gbText(freeRam)),
                    progressPercent = progressPercentOrNull(ramPercent),
                    standardAccent = colors.ramAccent,
                    isLimitHigh = true,
                    iconRes = R.drawable.ic_widget_memory,
                    modifier = GlanceModifier.defaultWeight().clickable(
                        actionRunCallback<LaunchSettingsAction>(
                            actionParametersOf(LaunchSettingsAction.SettingsActionKey to android.provider.Settings.ACTION_SETTINGS)
                        )
                    ),
                    colors = colors
                )
            }
            Spacer(modifier = GlanceModifier.height(12.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                MetricTile(
                    title = context.getString(R.string.widget_tile_cpu),
                    value = percentText(cpuLoad),
                    subtext = "${coreText(context, cpuCores)} · ${formatDouble(cpuFreq, 1, " GHz")}",
                    bottomText = cpuBottomText(cpuTemp, cpuLoadLabel),
                    progressPercent = progressPercentOrNull(cpuLoad),
                    standardAccent = colors.cpuAccent,
                    isLimitHigh = true,
                    iconRes = R.drawable.ic_widget_cpu,
                    modifier = GlanceModifier.defaultWeight().clickable(
                        actionRunCallback<LaunchSettingsAction>(
                            actionParametersOf(LaunchSettingsAction.SettingsActionKey to android.provider.Settings.ACTION_SETTINGS)
                        )
                    ),
                    colors = colors
                )
                Spacer(modifier = GlanceModifier.width(12.dp))
                val freeStorage = if (totalStorage >= 0.0 && usedStorage >= 0.0) totalStorage - usedStorage else UNAVAILABLE_DOUBLE
                MetricTile(
                    title = context.getString(R.string.widget_tile_storage),
                    value = percentText(storagePercent),
                    subtext = "${gbText(usedStorage, 0)} / ${gbText(totalStorage, 0)}",
                    bottomText = context.getString(R.string.widget_free_value, gbText(freeStorage, 0)),
                    progressPercent = progressPercentOrNull(storagePercent),
                    standardAccent = colors.storageAccent,
                    isLimitHigh = true,
                    iconRes = R.drawable.ic_widget_storage,
                    modifier = GlanceModifier.defaultWeight().clickable(
                        actionRunCallback<LaunchSettingsAction>(
                            actionParametersOf(LaunchSettingsAction.SettingsActionKey to android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                        )
                    ),
                    colors = colors
                )
            }
            Spacer(modifier = GlanceModifier.height(12.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                MetricTile(
                    title = context.getString(R.string.widget_tile_network),
                    value = wifiSsid,
                    subtext = "",
                    bottomText = context.getString(R.string.widget_today_value, gbText(wifiBytesToday)),
                    progressPercent = null,
                    standardAccent = colors.networkAccent,
                    isLimitHigh = false,
                    iconRes = R.drawable.ic_widget_wifi,
                    modifier = GlanceModifier.defaultWeight().clickable(
                        actionRunCallback<LaunchSettingsAction>(
                            actionParametersOf(LaunchSettingsAction.SettingsActionKey to android.provider.Settings.ACTION_WIFI_SETTINGS)
                        )
                    ),
                    colors = colors,
                    kind = MetricTileKind.Wifi,
                    wifiDown = wifiSpeedDown,
                    wifiUp = wifiSpeedUp,
                    wifiBand = wifiBand
                )
                Spacer(modifier = GlanceModifier.width(12.dp))
                MetricTile(
                    title = context.getString(R.string.widget_tile_battery_health),
                    value = batteryHealth,
                    subtext = "",
                    bottomText = cycleText(context, batteryCycles),
                    progressPercent = null,
                    standardAccent = colors.healthAccent,
                    isLimitHigh = false,
                    iconRes = R.drawable.ic_widget_heart,
                    iconColor = ColorProvider(Color(0xFF34D399)),
                    modifier = GlanceModifier.defaultWeight().clickable(
                        actionRunCallback<LaunchSettingsAction>(
                            actionParametersOf(LaunchSettingsAction.SettingsActionKey to "android.intent.action.POWER_USAGE_SUMMARY")
                        )
                    ),
                    colors = colors,
                    kind = MetricTileKind.BatteryHealth,
                    healthCapacity = batteryCapacity
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Full-width mobile network row
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(colors.tileBackground)
                .cornerRadius(20.dp)
                .padding(17.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_cellular),
                    contentDescription = context.getString(R.string.widget_mobile_network),
                    modifier = GlanceModifier.size(30.dp),
                    colorFilter = ColorFilter.tint(colors.mobileAccent)
                )
                Spacer(modifier = GlanceModifier.width(12.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "$operatorName · $mobileNetworkType",
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = context.getString(
                            R.string.widget_signal_line,
                            signalDbmText(context, mobileSignalDbm),
                            signalQualityText(context, mobileSignalDbm)
                        ),
                        style = TextStyle(
                            color = colors.textMuted,
                            fontSize = 13.sp
                        ),
                        maxLines = 1
                    )
                }
                Spacer(modifier = GlanceModifier.width(16.dp))
                Column(horizontalAlignment = Alignment.Horizontal.End) {
                    Text(
                        text = mobileDataText(mobileDataUsed, mobileDataTotal),
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_swap),
                            contentDescription = null,
                            modifier = GlanceModifier.size(15.dp),
                            colorFilter = ColorFilter.tint(colors.textMuted)
                        )
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(
                            text = mobileDataLabel,
                            style = TextStyle(
                                color = colors.textMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = GlanceModifier.height(12.dp))

        // Footer-rivi
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_schedule),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(colors.textMuted)
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = context.getString(R.string.widget_uptime, uptime),
                style = TextStyle(
                    color = colors.textMuted,
                    fontSize = 14.sp
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = context.getString(R.string.widget_updated, lastUpdated),
                style = TextStyle(
                    color = colors.textMuted,
                    fontSize = 14.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
fun ProgressBar(percent: Int, activeColor: ColorProvider, trackColor: ColorProvider) {
    val totalWidth = 100
    val activeWidth = (totalWidth * percent.coerceIn(0, 100)) / 100
    
    Box(
        modifier = GlanceModifier
            .width(totalWidth.dp)
            .height(8.dp)
            .cornerRadius(99.dp)
            .background(trackColor)
    ) {
        Box(
            modifier = GlanceModifier
                .width(activeWidth.dp)
                .height(8.dp)
                .cornerRadius(99.dp)
                .background(activeColor)
        ) {}
    }
}

@Composable
fun MetricTile(
    title: String,
    value: String,
    subtext: String,
    bottomText: String,
    progressPercent: Int?,
    standardAccent: ColorProvider,
    isLimitHigh: Boolean,
    iconRes: Int,
    iconColor: ColorProvider = standardAccent,
    modifier: GlanceModifier = GlanceModifier,
    colors: WidgetColors,
    kind: MetricTileKind = MetricTileKind.Standard,
    wifiDown: Int? = null,
    wifiUp: Int? = null,
    wifiBand: String = UNAVAILABLE_TEXT,
    healthCapacity: Int = UNAVAILABLE_INT
) {
    val activeColor = if (progressPercent != null) {
        getMetricColor(progressPercent, standardAccent, isLimitHigh)
    } else {
        standardAccent
    }

    Column(
        modifier = modifier
            .background(colors.tileBackground)
            .cornerRadius(20.dp)
            .padding(18.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = colors.labelText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = title,
                modifier = GlanceModifier.size(22.dp),
                colorFilter = ColorFilter.tint(iconColor)
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        if (kind == MetricTileKind.Wifi && wifiDown != null && wifiUp != null) {
            Text(
                text = value,
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 2
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            if (wifiBand != UNAVAILABLE_TEXT) {
                Box(
                    modifier = GlanceModifier
                        .background(colors.bandChipBg)
                        .cornerRadius(8.dp)
                        .padding(vertical = 4.dp, horizontal = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = wifiBand,
                        style = TextStyle(
                            color = colors.bandText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = GlanceModifier.defaultWeight())
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text("↓ ", style = TextStyle(color = colors.downloadAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                Text(
                    speedText(wifiDown),
                    style = TextStyle(color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text("↑ ", style = TextStyle(color = colors.uploadAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                Text(
                    speedText(wifiUp),
                    style = TextStyle(color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
            }
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = bottomText,
                style = TextStyle(
                    color = colors.textMuted,
                    fontSize = 13.sp
                ),
                maxLines = 1
            )
        } else if (kind == MetricTileKind.BatteryHealth) {
            Text(
                text = value,
                style = TextStyle(
                    color = colors.healthAccent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(10.dp))
            Column {
                Text(
                    text = androidx.glance.LocalContext.current.getString(R.string.widget_capacity_label),
                    style = TextStyle(
                        color = colors.labelText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    text = healthCapacityText(androidx.glance.LocalContext.current, healthCapacity),
                    style = TextStyle(
                        color = colors.capacityText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = bottomText,
                style = TextStyle(
                    color = colors.textMuted,
                    fontSize = 13.sp
                ),
                maxLines = 1
            )
        } else {
            Text(
                text = value,
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 2
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = subtext,
                style = TextStyle(
                    color = colors.textMuted,
                    fontSize = 13.sp
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            if (progressPercent != null) {
                ProgressBar(progressPercent, activeColor, colors.progressTrack)
                Spacer(modifier = GlanceModifier.height(4.dp))
            }
            Text(
                text = bottomText,
                style = TextStyle(
                    color = colors.textMuted,
                    fontSize = 13.sp
                ),
                maxLines = 1
            )
        }
    }
}

fun getMetricColor(percent: Int, standardAccent: ColorProvider, isLimitHigh: Boolean): ColorProvider {
    return if (isLimitHigh) {
        when {
            percent <= 60 -> standardAccent
            percent <= 85 -> ColorProvider(Color(0xFFFBBF24))
            else -> ColorProvider(Color(0xFFF87171))
        }
    } else {
        when {
            percent >= 50 -> standardAccent
            percent >= 20 -> ColorProvider(Color(0xFFFBBF24))
            else -> ColorProvider(Color(0xFFF87171))
        }
    }
}

fun progressPercentOrNull(value: Int): Int? {
    return value.takeIf { it >= 0 }?.coerceIn(0, 100)
}

fun percentText(value: Int): String {
    return if (value >= 0) "$value%" else UNAVAILABLE_TEXT
}

fun formatDouble(value: Double, decimals: Int, suffix: String = ""): String {
    if (value < 0.0) return UNAVAILABLE_TEXT
    return String.format(Locale.getDefault(), "%.${decimals}f%s", value, suffix)
}

fun gbText(value: Double, decimals: Int = 1): String {
    return if (value >= 0.0) {
        String.format(Locale.getDefault(), "%.${decimals}f GB", value)
    } else {
        UNAVAILABLE_TEXT
    }
}

fun speedText(value: Int): String {
    return if (value >= 0) "$value Mb/s" else UNAVAILABLE_TEXT
}

fun coreText(context: Context, value: Int): String {
    return if (value >= 0) {
        context.resources.getQuantityString(R.plurals.cpu_cores_short, value, value)
    } else {
        UNAVAILABLE_TEXT
    }
}

fun cycleText(context: Context, value: Int): String {
    return if (value >= 0) {
        context.resources.getQuantityString(R.plurals.battery_cycles, value, value)
    } else {
        context.getString(R.string.cycles_unavailable)
    }
}

fun healthCapacityText(context: Context, value: Int): String {
    return if (value >= 0) "$value%" else context.getString(R.string.health_capacity_unavailable)
}

fun cpuBottomText(cpuTemp: Double, cpuLoadLabel: String): String {
    val tempText = formatDouble(cpuTemp, 1, "°C")
    return when {
        cpuLoadLabel == UNAVAILABLE_TEXT -> tempText
        tempText == UNAVAILABLE_TEXT -> cpuLoadLabel
        else -> "$tempText · $cpuLoadLabel"
    }
}

fun signalDbmText(context: Context, value: Int): String {
    return if (value != UNAVAILABLE_INT) {
        "$value dBm"
    } else {
        context.getString(R.string.signal_unavailable, UNAVAILABLE_TEXT)
    }
}

fun signalQualityText(context: Context, value: Int): String {
    return when {
        value == UNAVAILABLE_INT -> UNAVAILABLE_TEXT
        value >= -85 -> context.getString(R.string.signal_strong)
        value >= -95 -> context.getString(R.string.signal_medium)
        else -> context.getString(R.string.signal_weak)
    }
}

fun mobileDataText(usedGb: Double, totalGb: Double): String {
    return when {
        usedGb < 0.0 -> UNAVAILABLE_TEXT
        totalGb > 0.0 -> "${gbText(usedGb)} / ${gbText(totalGb, 0)}"
        else -> gbText(usedGb)
    }
}

@Composable
fun StatusChip(
    name: String,
    isActive: Boolean,
    isKnown: Boolean,
    activeBg: ColorProvider,
    activeBorder: ColorProvider,
    modifier: GlanceModifier = GlanceModifier,
    colors: WidgetColors
) {
    val bg = if (isKnown && isActive) activeBg else colors.tileBackground
    val textColor = if (isKnown && isActive) colors.textPrimary else colors.textMuted
    val prefix = when {
        !isKnown -> "— "
        isActive -> "● "
        else -> "○ "
    }

    Box(
        modifier = modifier
            .background(bg)
            .cornerRadius(14.dp)
            .padding(vertical = 14.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = prefix + name,
            style = TextStyle(
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 2
        )
    }
}
