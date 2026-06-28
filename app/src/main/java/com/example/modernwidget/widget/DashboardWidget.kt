package com.example.modernwidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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

class WidgetColors(
    val cardBackground: ColorProvider,
    val cardBorder: ColorProvider,
    val tileBackground: ColorProvider,
    val tileBorder: ColorProvider,
    val progressTrack: ColorProvider,
    val textPrimary: ColorProvider,
    val textSecondary: ColorProvider,
    val textMuted: ColorProvider,
    
    // Accents
    val batteryAccent: ColorProvider,
    val ramAccent: ColorProvider,
    val cpuAccent: ColorProvider,
    val storageAccent: ColorProvider,
    val healthAccent: ColorProvider,
    val networkAccent: ColorProvider,
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

fun getWidgetColors(isDark: Boolean, opacity: Float): WidgetColors {
    return if (isDark) {
        WidgetColors(
            cardBackground = ColorProvider(Color(0xFF12151A).copy(alpha = opacity)),
            cardBorder = ColorProvider(Color(0x3BFFFFFF)),     // rgba(255,255,255,0.23)
            tileBackground = ColorProvider(Color(0x0AFFFFFF)), // rgba(255,255,255,0.04)
            tileBorder = ColorProvider(Color(0x2BFFFFFF)),     // rgba(255,255,255,0.17)
            progressTrack = ColorProvider(Color(0x30FFFFFF)),  // rgba(255,255,255,0.19)
            textPrimary = ColorProvider(Color(0xFFFFFFFF)),
            textSecondary = ColorProvider(Color(0xFF9AA0A8)),
            textMuted = ColorProvider(Color(0xFF7B828C)),
            
            batteryAccent = ColorProvider(Color(0xFF34D399)),
            ramAccent = ColorProvider(Color(0xFFF59E0B)),
            cpuAccent = ColorProvider(Color(0xFF2DD4BF)),
            storageAccent = ColorProvider(Color(0xFF8B5CF6)),
            healthAccent = ColorProvider(Color(0xFF34D399)),
            networkAccent = ColorProvider(Color(0xFF38BDF8)),
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
            
            batteryAccent = ColorProvider(Color(0xFF84CC16)),
            ramAccent = ColorProvider(Color(0xFFF59E0B)),
            cpuAccent = ColorProvider(Color(0xFF06B6D4)),
            storageAccent = ColorProvider(Color(0xFF7C3AED)),
            healthAccent = ColorProvider(Color(0xFF16A34A)),
            networkAccent = ColorProvider(Color(0xFF38BDF8)),
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
    
    val batteryLevel = prefs[RefreshStatsAction.BATTERY_LEVEL] ?: 64
    val batteryStatus = prefs[RefreshStatsAction.BATTERY_STATUS] ?: "Akkuvirta"
    val batteryHealth = prefs[RefreshStatsAction.BATTERY_HEALTH] ?: "Hyvä"
    val batteryTemp = prefs[RefreshStatsAction.BATTERY_TEMP] ?: 29.5
    val batteryVoltage = prefs[RefreshStatsAction.BATTERY_VOLTAGE] ?: 4.01
    val timeRemaining = prefs[RefreshStatsAction.TIME_REMAINING] ?: "11t 53m jäljellä"
    val batteryCycles = prefs[RefreshStatsAction.BATTERY_CYCLE_COUNT] ?: 312

    val totalRam = prefs[RefreshStatsAction.TOTAL_RAM] ?: 7.4
    val usedRam = prefs[RefreshStatsAction.USED_RAM] ?: 5.7
    val ramPercent = prefs[RefreshStatsAction.RAM_PERCENT] ?: 76

    val cpuCores = prefs[RefreshStatsAction.CPU_CORES] ?: 9
    val cpuAbi = prefs[RefreshStatsAction.CPU_ABI] ?: "arm64-v8a"
    val cpuFreq = prefs[RefreshStatsAction.CPU_FREQ] ?: 2.1
    val cpuLoad = prefs[RefreshStatsAction.CPU_LOAD] ?: 23
    val cpuTemp = prefs[RefreshStatsAction.CPU_TEMP] ?: 42.0

    val totalStorage = prefs[RefreshStatsAction.TOTAL_STORAGE] ?: 128.0
    val usedStorage = prefs[RefreshStatsAction.USED_STORAGE] ?: 89.0
    val storagePercent = prefs[RefreshStatsAction.STORAGE_PERCENT] ?: 70

    val wifiSsid = prefs[RefreshStatsAction.WIFI_SSID] ?: "Koti_5G"
    val wifiSpeedDown = prefs[RefreshStatsAction.WIFI_SPEED_DOWN] ?: 48
    val wifiSpeedUp = prefs[RefreshStatsAction.WIFI_SPEED_UP] ?: 12
    val wifiBytesToday = prefs[RefreshStatsAction.WIFI_BYTES_TODAY] ?: 1.8

    val operatorName = prefs[RefreshStatsAction.OPERATOR_NAME] ?: "DNA"
    val mobileNetworkType = prefs[RefreshStatsAction.MOBILE_NETWORK_TYPE] ?: "5G"
    val mobileSignalDbm = prefs[RefreshStatsAction.MOBILE_SIGNAL_DBM] ?: -87
    val mobileDataUsed = prefs[RefreshStatsAction.MOBILE_DATA_USED] ?: 4.2
    val mobileDataTotal = prefs[RefreshStatsAction.MOBILE_DATA_TOTAL] ?: 20.0

    val isDnd = prefs[RefreshStatsAction.IS_DND_ENABLED] ?: true
    val isBluetooth = prefs[RefreshStatsAction.IS_BLUETOOTH_ENABLED] ?: true
    val isLocation = prefs[RefreshStatsAction.IS_LOCATION_ENABLED] ?: true
    val isNfc = prefs[RefreshStatsAction.IS_NFC_ENABLED] ?: true
    val isPowerSave = prefs[RefreshStatsAction.IS_POWER_SAVE_MODE] ?: false
    val isAirplane = prefs[RefreshStatsAction.IS_AIRPLANE_MODE] ?: false

    val uptime = prefs[RefreshStatsAction.UPTIME] ?: "39t 34m"
    val lastUpdated = prefs[RefreshStatsAction.LAST_UPDATED] ?: "--.--"
    val showWifiInRow = prefs[ToggleConnectionRowAction.SHOW_WIFI_IN_CONNECTION_ROW] ?: false

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(colors.cardBackground)
            .cornerRadius(30.dp)
            .padding(24.dp)
    ) {
        // Header-rivi
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = "⚡ Järjestelmämonitori",
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            
            // "live"-pilleri
            Row(
                verticalAlignment = Alignment.Vertical.CenterVertically,
                modifier = GlanceModifier
                    .background(colors.livePillBg)
                    .cornerRadius(99.dp)
                    .padding(vertical = 8.dp, horizontal = 13.dp)
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(8.dp)
                        .cornerRadius(4.dp)
                        .background(colors.liveGreen)
                ) {}
                Spacer(modifier = GlanceModifier.width(7.dp))
                Text(
                    text = "live",
                    style = TextStyle(
                        color = colors.liveGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(16.dp))

        // Ruudukko 2x3 (6 metriikkalaattaa, gap 12dp)
        Column(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                val tempStr = "%.1f".format(batteryTemp)
                val voltStr = "%.2f".format(batteryVoltage)
                MetricTile(
                    title = "AKKU",
                    value = "$batteryLevel%",
                    subtext = timeRemaining,
                    bottomText = "${tempStr}°C · ${voltStr}V",
                    progressPercent = batteryLevel,
                    standardAccent = colors.batteryAccent,
                    isLimitHigh = false,
                    modifier = GlanceModifier.defaultWeight().clickable(
                        actionRunCallback<LaunchSettingsAction>(
                            actionParametersOf(LaunchSettingsAction.SettingsActionKey to "android.intent.action.POWER_USAGE_SUMMARY")
                        )
                    ),
                    colors = colors
                )
                Spacer(modifier = GlanceModifier.width(12.dp))
                val usedRamStr = "%.1f".format(usedRam)
                val totalRamStr = "%.1f".format(totalRam)
                val freeRamStr = "%.1f".format(totalRam - usedRam)
                MetricTile(
                    title = "MUISTI",
                    value = "$ramPercent%",
                    subtext = "$usedRamStr / $totalRamStr GB",
                    bottomText = "$freeRamStr GB vapaana",
                    progressPercent = ramPercent,
                    standardAccent = colors.ramAccent,
                    isLimitHigh = true,
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
                val freqStr = "%.1f".format(cpuFreq)
                val cpuTempStr = "%.1f".format(cpuTemp)
                MetricTile(
                    title = "SUORITIN",
                    value = "$cpuLoad%",
                    subtext = "$cpuCores ydintä · $freqStr GHz",
                    bottomText = "$cpuTempStr°C",
                    progressPercent = cpuLoad,
                    standardAccent = colors.cpuAccent,
                    isLimitHigh = true,
                    modifier = GlanceModifier.defaultWeight().clickable(
                        actionRunCallback<LaunchSettingsAction>(
                            actionParametersOf(LaunchSettingsAction.SettingsActionKey to android.provider.Settings.ACTION_SETTINGS)
                        )
                    ),
                    colors = colors
                )
                Spacer(modifier = GlanceModifier.width(12.dp))
                val usedStorageStr = "%.0f".format(usedStorage)
                val totalStorageStr = "%.0f".format(totalStorage)
                val freeStorageStr = "%.0f".format(totalStorage - usedStorage)
                MetricTile(
                    title = "TALLENNUS",
                    value = "$storagePercent%",
                    subtext = "$usedStorageStr / $totalStorageStr GB",
                    bottomText = "$freeStorageStr GB vapaana",
                    progressPercent = storagePercent,
                    standardAccent = colors.storageAccent,
                    isLimitHigh = true,
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
                val bytesStr = "%.1f".format(wifiBytesToday)
                MetricTile(
                    title = "VERKKO",
                    value = wifiSsid,
                    subtext = "",
                    bottomText = "$bytesStr GB tänään",
                    progressPercent = null,
                    standardAccent = colors.networkAccent,
                    isLimitHigh = false,
                    modifier = GlanceModifier.defaultWeight().clickable(
                        actionRunCallback<LaunchSettingsAction>(
                            actionParametersOf(LaunchSettingsAction.SettingsActionKey to android.provider.Settings.ACTION_WIFI_SETTINGS)
                        )
                    ),
                    colors = colors,
                    wifiDown = wifiSpeedDown,
                    wifiUp = wifiSpeedUp
                )
                Spacer(modifier = GlanceModifier.width(12.dp))
                MetricTile(
                    title = "AKUN TERVEYS",
                    value = if (batteryHealth == "Hyvä") "Hyvä" else "Normaali",
                    subtext = "",
                    bottomText = "$batteryCycles lataussykliä",
                    progressPercent = null,
                    standardAccent = colors.healthAccent,
                    isLimitHigh = false,
                    modifier = GlanceModifier.defaultWeight().clickable(
                        actionRunCallback<LaunchSettingsAction>(
                            actionParametersOf(LaunchSettingsAction.SettingsActionKey to "android.intent.action.POWER_USAGE_SUMMARY")
                        )
                    ),
                    colors = colors,
                    healthCapacity = 96
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(12.dp))

        // Yhteydet & tilat -laatta (täysleveä)
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(colors.tileBackground)
                .cornerRadius(20.dp)
                .padding(17.dp)
        ) {
            // Kaksitoiminen yhteysrivi (Wi-Fi tai Mobiiliverkko)
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                if (showWifiInRow) {
                    // Wi-Fi tiedot vasemmalla
                    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Text(
                            text = "📶 ",
                            style = TextStyle(
                                color = colors.networkAccent,
                                fontSize = 20.sp
                            )
                        )
                        Column {
                            Text(
                                text = "Wi-Fi · $wifiSsid",
                                style = TextStyle(
                                    color = colors.textPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "↓$wifiSpeedDown · ↑$wifiSpeedUp Mb/s",
                                style = TextStyle(
                                    color = colors.textMuted,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                } else {
                    // Mobiiliverkko tiedot vasemmalla
                    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Text(
                            text = "📶 ",
                            style = TextStyle(
                                color = colors.networkAccent,
                                fontSize = 20.sp
                            )
                        )
                        Column {
                            Text(
                                text = "$operatorName · $mobileNetworkType",
                                style = TextStyle(
                                    color = colors.textPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            val signalStrengthText = when {
                                mobileSignalDbm >= -85 -> "vahva"
                                mobileSignalDbm >= -95 -> "keskitaso"
                                else -> "heikko"
                            }
                            Text(
                                text = "$mobileSignalDbm dBm · $signalStrengthText",
                                style = TextStyle(
                                    color = colors.textMuted,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                // Keskellä oleva vaihtopainike (ikoni)
                Box(
                    modifier = GlanceModifier
                        .size(32.dp)
                        .cornerRadius(16.dp)
                        .background(colors.tileBorder)
                        .clickable(actionRunCallback<ToggleConnectionRowAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⇆",
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                if (showWifiInRow) {
                    // Wi-Fi kulutus oikealla
                    Column(horizontalAlignment = Alignment.Horizontal.End) {
                        val wifiBytesStr = "%.1f".format(wifiBytesToday)
                        Text(
                            text = "$wifiBytesStr GB",
                            style = TextStyle(
                                color = colors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "WIFI-DATA",
                            style = TextStyle(
                                color = colors.textMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                } else {
                    // Mobiilidata kulutus oikealla
                    Column(horizontalAlignment = Alignment.Horizontal.End) {
                        Text(
                            text = "$mobileDataUsed / ${mobileDataTotal.toInt()} GB",
                            style = TextStyle(
                                color = colors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "MOBIILIDATA",
                            style = TextStyle(
                                color = colors.textMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(14.dp))
            
            // Ohut jakoviiva
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.tileBorder)
            ) {}

            Spacer(modifier = GlanceModifier.height(14.dp))

            // Tilasirut (kaksi 3-siruista riviä, väli 10dp)
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                StatusChip(
                    name = "DND", 
                    isActive = isDnd, 
                    activeBg = colors.dndChipBg, 
                    activeBorder = colors.dndChipBorder, 
                    modifier = GlanceModifier.defaultWeight().clickable(actionRunCallback<ToggleDndAction>()), 
                    colors = colors
                )
                Spacer(modifier = GlanceModifier.width(10.dp))
                StatusChip(
                    name = "Bluetooth", 
                    isActive = isBluetooth, 
                    activeBg = colors.bluetoothChipBg, 
                    activeBorder = colors.bluetoothChipBorder, 
                    modifier = GlanceModifier.defaultWeight().clickable(
                        actionRunCallback<LaunchSettingsAction>(
                            actionParametersOf(LaunchSettingsAction.SettingsActionKey to android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                        )
                    ), 
                    colors = colors
                )
                Spacer(modifier = GlanceModifier.width(10.dp))
                StatusChip(
                    name = "Sijainti", 
                    isActive = isLocation, 
                    activeBg = colors.locationChipBg, 
                    activeBorder = colors.locationChipBorder, 
                    modifier = GlanceModifier.defaultWeight().clickable(
                        actionRunCallback<LaunchSettingsAction>(
                            actionParametersOf(LaunchSettingsAction.SettingsActionKey to android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        )
                    ), 
                    colors = colors
                )
            }
            Spacer(modifier = GlanceModifier.height(10.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                StatusChip(
                    name = "NFC", 
                    isActive = isNfc, 
                    activeBg = colors.nfcChipBg, 
                    activeBorder = colors.nfcChipBorder, 
                    modifier = GlanceModifier.defaultWeight().clickable(
                        actionRunCallback<LaunchSettingsAction>(
                            actionParametersOf(LaunchSettingsAction.SettingsActionKey to android.provider.Settings.ACTION_NFC_SETTINGS)
                        )
                    ), 
                    colors = colors
                )
                Spacer(modifier = GlanceModifier.width(10.dp))
                StatusChip(
                    name = "Säästö", 
                    isActive = isPowerSave, 
                    activeBg = colors.powerSaveChipBg, 
                    activeBorder = colors.powerSaveChipBorder, 
                    modifier = GlanceModifier.defaultWeight().clickable(
                        actionRunCallback<LaunchSettingsAction>(
                            actionParametersOf(LaunchSettingsAction.SettingsActionKey to android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS)
                        )
                    ), 
                    colors = colors
                )
                Spacer(modifier = GlanceModifier.width(10.dp))
                StatusChip(
                    name = "Lentotila", 
                    isActive = isAirplane, 
                    activeBg = colors.airplaneChipBg, 
                    activeBorder = colors.airplaneChipBorder, 
                    modifier = GlanceModifier.defaultWeight().clickable(
                        actionRunCallback<LaunchSettingsAction>(
                            actionParametersOf(LaunchSettingsAction.SettingsActionKey to android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                        )
                    ), 
                    colors = colors
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(18.dp))

        // Footer-rivi: 🕒 Uptime 39t 34m · Päivitetty 14.07
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = "🕒 Uptime $uptime",
                style = TextStyle(
                    color = colors.textMuted,
                    fontSize = 14.sp
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "Päivitetty $lastUpdated",
                style = TextStyle(
                    color = colors.textMuted,
                    fontSize = 14.sp
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(18.dp))

        // Painike "Päivitä tiedot" (Box-pohjainen dynaaminen painike)
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(48.dp)
                .cornerRadius(18.dp)
                .background(colors.buttonBg)
                .clickable(actionRunCallback<RefreshStatsAction>()),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = "↻  ",
                    style = TextStyle(
                        color = colors.buttonText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Päivitä tiedot",
                    style = TextStyle(
                        color = colors.buttonText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
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
    modifier: GlanceModifier = GlanceModifier,
    colors: WidgetColors,
    wifiDown: Int? = null,
    wifiUp: Int? = null,
    healthCapacity: Int? = null
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
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 11.dp)
    ) {
        // Otsikko
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(4.dp))

        if (title == "VERKKO" && wifiDown != null && wifiUp != null) {
            // Verkko-laatan erikoislayout
            Text(
                text = value,
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text("↓ ", style = TextStyle(color = colors.batteryAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                Text("$wifiDown Mb/s", style = TextStyle(color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            }
            Spacer(modifier = GlanceModifier.height(2.dp))
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text("↑ ", style = TextStyle(color = colors.ramAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                Text("$wifiUp Mb/s", style = TextStyle(color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            }
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = bottomText,
                style = TextStyle(
                    color = colors.textMuted,
                    fontSize = 13.sp
                )
            )
        } else if (title == "AKUN TERVEYS" && healthCapacity != null) {
            // Akun terveys -laatan erikoislayout
            Text(
                text = value,
                style = TextStyle(
                    color = colors.batteryAccent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Row(verticalAlignment = Alignment.Vertical.Bottom) {
                Text(
                    text = "$healthCapacity",
                    style = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "% kapasiteetti",
                    style = TextStyle(
                        color = colors.textMuted,
                        fontSize = 13.sp
                    )
                )
            }
            Spacer(modifier = GlanceModifier.height(6.dp))
            ProgressBar(healthCapacity, activeColor, colors.progressTrack)
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = bottomText,
                style = TextStyle(
                    color = colors.textMuted,
                    fontSize = 13.sp
                )
            )
        } else {
            // Normaali laatta
            Text(
                text = value,
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = subtext,
                style = TextStyle(
                    color = colors.textMuted,
                    fontSize = 13.sp
                )
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            if (progressPercent != null) {
                ProgressBar(progressPercent, activeColor, colors.progressTrack)
                Spacer(modifier = GlanceModifier.height(4.dp))
            }
            Text(
                text = bottomText,
                style = TextStyle(
                    color = colors.textMuted,
                    fontSize = 13.sp
                )
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

@Composable
fun StatusChip(
    name: String,
    isActive: Boolean,
    activeBg: ColorProvider,
    activeBorder: ColorProvider,
    modifier: GlanceModifier = GlanceModifier,
    colors: WidgetColors
) {
    val bg = if (isActive) activeBg else colors.tileBackground
    val textColor = if (isActive) colors.textPrimary else colors.textMuted

    Box(
        modifier = modifier
            .background(bg)
            .cornerRadius(14.dp)
            .padding(vertical = 14.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = (if (isActive) "● " else "○ ") + name,
            style = TextStyle(
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
