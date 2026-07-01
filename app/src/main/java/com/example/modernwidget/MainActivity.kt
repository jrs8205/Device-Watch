package com.example.modernwidget

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.modernwidget.data.UNAVAILABLE_INT
import com.example.modernwidget.data.UNAVAILABLE_TEXT
import com.example.modernwidget.presentation.DashboardViewModel
import com.example.modernwidget.system.DreamPreferences
import com.example.modernwidget.system.SystemMonitorService
import java.util.Locale
import com.example.modernwidget.ui.theme.ModernWidgetTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ModernWidgetTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SystemDashboardScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemDashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val dreamPrefs = remember(context) {
        context.getSharedPreferences(DreamPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    }
    var forcePortraitScreensaver by remember {
        mutableStateOf(dreamPrefs.getBoolean(DreamPreferences.KEY_FORCE_PORTRAIT, false))
    }
    var dimScreensaver by remember {
        mutableStateOf(dreamPrefs.getBoolean(DreamPreferences.KEY_DIM_SCREENSAVER, false))
    }

    // Start the background monitoring service (independent of the notification permission result).
    fun startSystemMonitorService() {
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

    fun missingRuntimePermissions(): Array<String> {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        return permissions
            .filter { permission ->
                ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
            }
            .toTypedArray()
    }

    fun openSpecialAccessSettings(action: String) {
        try {
            context.startActivity(Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        startSystemMonitorService()
        viewModel.refresh()
    }

    LaunchedEffect(Unit) {
        viewModel.refresh()
        viewModel.loadWidgetOpacity()
        viewModel.loadDeviceInfo()
        val missingPermissions = missingRuntimePermissions()
        if (missingPermissions.isNotEmpty()) {
            runtimePermissionLauncher.launch(missingPermissions)
        } else {
            startSystemMonitorService()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.main_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        val currentStats = uiState.stats
        if (currentStats != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Widget connection status
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.isWidgetInstalled) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                        }
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isWidgetInstalled) Color(0xFF4CAF50) else Color(0xFFFF9800))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (uiState.isWidgetInstalled) {
                                stringResource(R.string.widget_active_message)
                            } else {
                                stringResource(R.string.widget_not_added_message)
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Battery section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.battery_status_section),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(160.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { currentStats.batteryLevel.toFloat() / 100f },
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 10.dp,
                                color = if (currentStats.batteryLevel > 20) Color(0xFF4CAF50) else Color(0xFFF44336),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${currentStats.batteryLevel}%",
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentStats.batteryStatus,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.time_remaining), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(currentStats.timeRemainingText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.temperature), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${currentStats.batteryTemp} °C", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.voltage), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val voltStr = "%.2f".format(currentStats.batteryVoltage)
                                Text("${voltStr} V", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Resources (RAM & CPU)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            stringResource(R.string.system_resources_section),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // RAM
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.ram_title), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            val usedRamStr = "%.1f".format(currentStats.usedRamGb)
                            val totalRamStr = "%.1f".format(currentStats.totalRamGb)
                            Text(
                                "$usedRamStr / $totalRamStr GB (${currentStats.ramPercent}%)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { currentStats.ramPercent.toFloat() / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))

                        // CPU info & Uptime
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(stringResource(R.string.cpu_cores_label), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    context.resources.getQuantityString(
                                        R.plurals.cpu_cores_value,
                                        currentStats.cpuCores,
                                        currentStats.cpuCores,
                                        currentStats.cpuAbi
                                    ),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(stringResource(R.string.uptime_label), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(currentStats.uptimeText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Device info
                uiState.deviceInfo?.let { info ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                stringResource(R.string.device_info_section),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            DeviceInfoRow(R.string.device_info_manufacturer, info.manufacturer)
                            DeviceInfoRow(R.string.device_info_model, info.model)
                            DeviceInfoRow(R.string.device_info_codename, info.codename)
                            DeviceInfoRow(R.string.device_info_android, info.androidVersion)
                            DeviceInfoRow(R.string.device_info_security_patch, info.securityPatch)
                            DeviceInfoRow(R.string.device_info_build, info.buildNumber)
                            DeviceInfoRow(R.string.device_info_bootloader, info.bootloader)
                            DeviceInfoRow(R.string.device_info_radio, info.radioVersion)
                            DeviceInfoRow(R.string.device_info_soc, info.soc)
                            DeviceInfoRow(R.string.device_info_abis, info.supportedAbis)
                            DeviceInfoRow(R.string.device_info_kernel, info.kernelVersion)
                            DeviceInfoRow(R.string.device_info_gpu, info.gpuRenderer)
                            DeviceInfoRow(R.string.device_info_gl, info.glVersion)
                            DeviceInfoRow(R.string.device_info_resolution, info.screenResolution)
                            DeviceInfoRow(R.string.device_info_density, info.screenDensity)
                            DeviceInfoRow(R.string.device_info_physical_size, info.physicalSize)
                            DeviceInfoRow(R.string.device_info_refresh, info.refreshRate)
                            DeviceInfoRow(R.string.device_info_hdr, info.hdr)
                            DeviceInfoRow(R.string.device_info_ram, info.totalRam)
                            DeviceInfoRow(R.string.device_info_storage, info.totalStorage)
                            DeviceInfoRow(R.string.device_info_battery_tech, info.batteryTechnology)
                            DeviceInfoRow(R.string.device_info_battery_capacity, info.batteryCapacityMah)
                        }
                    }

                    // Cameras
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                stringResource(R.string.camera_info_section),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            DeviceInfoRow(R.string.camera_count, info.cameraCount)
                            DeviceInfoRow(R.string.camera_rear, info.rearCamera)
                            DeviceInfoRow(R.string.camera_front, info.frontCamera)
                            DeviceInfoRow(R.string.camera_flash, info.cameraFlash)
                        }
                    }

                    // Sensors
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                stringResource(R.string.sensors_section),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            DeviceInfoRow(R.string.sensors_count, info.sensorCount)
                            DeviceInfoRow(R.string.sensors_present, info.sensors)
                        }
                    }

                    // System
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                stringResource(R.string.system_info_section),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            DeviceInfoRow(R.string.system_locale, info.locale)
                            DeviceInfoRow(R.string.system_timezone, info.timezone)
                            DeviceInfoRow(R.string.system_webview, info.webViewVersion)
                            DeviceInfoRow(R.string.system_play_services, info.playServicesVersion)
                            DeviceInfoRow(R.string.system_features, info.deviceFeatures)
                        }
                    }
                }

                // SIM & mobile network
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            stringResource(R.string.sim_info_section),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        DeviceInfoRow(R.string.sim_operator, currentStats.operatorName)
                        DeviceInfoRow(R.string.sim_country, currentStats.networkCountry)
                        DeviceInfoRow(R.string.sim_network, currentStats.mobileNetworkType)
                        DeviceInfoRow(R.string.sim_signal, dbmText(currentStats.mobileSignalDbm))
                        DeviceInfoRow(R.string.sim_status, currentStats.simState)
                        DeviceInfoRow(R.string.sim_slots, countText(currentStats.simSlots))
                        DeviceInfoRow(R.string.sim_data, gbTodayText(currentStats.mobileDataUsedGb))
                    }
                }

                // Wi-Fi
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            stringResource(R.string.wifi_info_section),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        DeviceInfoRow(R.string.wifi_name, currentStats.wifiSsidName)
                        DeviceInfoRow(R.string.wifi_band_label, currentStats.wifiBand)
                        DeviceInfoRow(R.string.wifi_standard, currentStats.wifiStandard)
                        DeviceInfoRow(R.string.wifi_signal, dbmText(currentStats.wifiRssiDbm))
                        DeviceInfoRow(R.string.wifi_link_speed, mbpsText(currentStats.wifiLinkSpeedMbps))
                        DeviceInfoRow(R.string.wifi_ip, currentStats.ipAddress)
                        DeviceInfoRow(R.string.wifi_data, gbTodayText(currentStats.wifiBytesTodayGb))
                        uiState.deviceInfo?.let { info ->
                            DeviceInfoRow(R.string.wifi_vpn, info.vpnActive)
                            DeviceInfoRow(R.string.wifi_dns, info.dnsServers)
                        }
                    }
                }

                // Widget settings
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            stringResource(R.string.widget_settings_section),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.widget_opacity_title),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Slider(
                                value = uiState.widgetOpacity,
                                onValueChange = { viewModel.onWidgetOpacityChange(it) },
                                onValueChangeFinished = { viewModel.commitWidgetOpacity() },
                                valueRange = 0.30f..1.0f,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = "${(uiState.widgetOpacity * 100).toInt()}%",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(42.dp)
                            )
                        }

                        Text(
                            text = stringResource(R.string.widget_opacity_description),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Screensaver settings
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            stringResource(R.string.screensaver_settings_section),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.force_portrait_title),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.force_portrait_description),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = forcePortraitScreensaver,
                                onCheckedChange = { checked ->
                                    forcePortraitScreensaver = checked
                                    dreamPrefs.edit()
                                        .putBoolean(DreamPreferences.KEY_FORCE_PORTRAIT, checked)
                                        .apply()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.dim_screensaver_title),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.dim_screensaver_description),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = dimScreensaver,
                                onCheckedChange = { checked ->
                                    dimScreensaver = checked
                                    dreamPrefs.edit()
                                        .putBoolean(DreamPreferences.KEY_DIM_SCREENSAVER, checked)
                                        .apply()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = { openSpecialAccessSettings(Settings.ACTION_DREAM_SETTINGS) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.open_screensaver_settings), fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.screensaver_charging_hint),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            stringResource(R.string.special_access_section),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { openSpecialAccessSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.usage_access_button), fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.refresh() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.refresh_data))
                }

                Text(
                    text = stringResource(R.string.last_updated, uiState.lastUpdated),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

private fun dbmText(value: Int): String =
    if (value == UNAVAILABLE_INT) UNAVAILABLE_TEXT else "$value dBm"

private fun mbpsText(value: Int): String =
    if (value == UNAVAILABLE_INT) UNAVAILABLE_TEXT else "$value Mbps"

private fun countText(value: Int): String =
    if (value <= 0) UNAVAILABLE_TEXT else value.toString()

private fun gbTodayText(value: Double): String =
    if (value < 0.0) UNAVAILABLE_TEXT else String.format(Locale.US, "%.2f GB", value)

@Composable
private fun DeviceInfoRow(@StringRes labelRes: Int, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = stringResource(labelRes),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
