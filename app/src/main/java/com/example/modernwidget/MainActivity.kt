package com.example.modernwidget

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.example.modernwidget.ui.theme.ModernWidgetTheme
import com.example.modernwidget.widget.DashboardWidget
import com.example.modernwidget.widget.RefreshStatsAction
import com.example.modernwidget.widget.WidgetStateUpdater
import com.example.modernwidget.system.SystemStatsHelper
import com.example.modernwidget.system.SystemStats
import com.example.modernwidget.system.SystemMonitorService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
fun SystemDashboardScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var stats by remember { mutableStateOf<SystemStats?>(null) }
    var isWidgetInstalled by remember { mutableStateOf(false) }
    var lastUpdatedTime by remember { mutableStateOf("--:--") }

    // Käynnistetään taustapalvelu
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

    fun refreshStats() {
        coroutineScope.launch {
            val currentStats = SystemStatsHelper.getStats(context)
            stats = currentStats
            lastUpdatedTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            isWidgetInstalled = WidgetStateUpdater.updateAll(context, currentStats)
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
        refreshStats()
    }

    LaunchedEffect(Unit) {
        refreshStats()
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
                    IconButton(onClick = { refreshStats() }) {
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
        stats?.let { currentStats ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Widget-yhteyden tila
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isWidgetInstalled) {
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
                                .background(if (isWidgetInstalled) Color(0xFF4CAF50) else Color(0xFFFF9800))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isWidgetInstalled) {
                                stringResource(R.string.widget_active_message)
                            } else {
                                stringResource(R.string.widget_not_added_message)
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Akku-osio
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

                // Resurssit (RAM & CPU)
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

                        // CPU tiedot & Uptime
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

                // Widgetin Asetukset
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

                        var opacityValue by remember { mutableFloatStateOf(0.86f) }
                        
                        // Luetaan tallennettu tila käynnistyksessä
                        LaunchedEffect(Unit) {
                            val manager = GlanceAppWidgetManager(context)
                            val glanceIds = manager.getGlanceIds(DashboardWidget::class.java)
                            if (glanceIds.isNotEmpty()) {
                                try {
                                    val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceIds.first())
                                    opacityValue = state[RefreshStatsAction.BACKGROUND_OPACITY] ?: 0.86f
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Slider(
                                value = opacityValue,
                                onValueChange = { newValue ->
                                    opacityValue = newValue
                                },
                                onValueChangeFinished = {
                                    coroutineScope.launch {
                                        val manager = GlanceAppWidgetManager(context)
                                        val glanceIds = manager.getGlanceIds(DashboardWidget::class.java)
                                        if (glanceIds.isNotEmpty()) {
                                            for (glanceId in glanceIds) {
                                                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                                                    prefs.toMutablePreferences().apply {
                                                        this[RefreshStatsAction.BACKGROUND_OPACITY] = opacityValue
                                                    }
                                                }
                                                DashboardWidget().update(context, glanceId)
                                            }
                                        }
                                    }
                                },
                                valueRange = 0.30f..1.0f,
                                modifier = Modifier.weight(1f)
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Text(
                                text = "${(opacityValue * 100).toInt()}%",
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
                    onClick = { refreshStats() },
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
                    text = stringResource(R.string.last_updated, lastUpdatedTime),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
