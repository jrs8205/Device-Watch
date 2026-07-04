package com.example.modernwidget.presentation.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modernwidget.R
import com.example.modernwidget.data.DataCounterMode
import com.example.modernwidget.data.UNAVAILABLE_TEXT
import com.example.modernwidget.presentation.DashboardUiState

/**
 * Overview tab: widget status, live battery ring, usage counters for the selected
 * period, full widget-parity system resources and the data counters — so the app
 * shows everything the home-screen widget does, for people who skip the widget.
 */
@Composable
internal fun OverviewTab(
    uiState: DashboardUiState,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val currentStats = uiState.stats ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
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
        SettingsSectionCard(
            titleRes = R.string.battery_status_section,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(4.dp))

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
                    Text(
                        stringResource(R.string.time_remaining),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(currentStats.timeRemainingText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.temperature),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("${currentStats.batteryTemp} °C", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.voltage),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val voltStr = "%.2f".format(currentStats.batteryVoltage)
                    Text("$voltStr V", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Usage counters for the selected period, right under the battery so they
        // are visible without scrolling to the bottom.
        SettingsSectionCard(
            titleRes = if (uiState.dataCounterMode == DataCounterMode.DAY) {
                R.string.usage_counters_section
            } else {
                R.string.usage_counters_section_period
            }
        ) {
            DeviceInfoRow(
                R.string.screen_time_total_label,
                if (uiState.screenTimeMillis >= 0L) {
                    durationText(context, uiState.screenTimeMillis)
                } else {
                    UNAVAILABLE_TEXT
                }
            )
            if (uiState.unlockCountingSupported) {
                DeviceInfoRow(R.string.unlock_count_label, countOrDashText(uiState.unlockCount))
            }
            if (uiState.notificationAccessEnabled) {
                DeviceInfoRow(
                    R.string.notification_count_label,
                    countOrDashText(uiState.notificationCount)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.notification_count_label),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = UNAVAILABLE_TEXT,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(onClick = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }) {
                        Text(stringResource(R.string.notification_access_enable), fontSize = 12.sp)
                    }
                }
            }
            DeviceInfoRow(R.string.boot_count_label, uiState.bootCount.toString())
            DeviceInfoRow(R.string.charge_count_label, uiState.chargeCount.toString())
        }

        // Resources (RAM, CPU, storage) — widget parity for people without the widget
        SettingsSectionCard(titleRes = R.string.system_resources_section) {
            Spacer(modifier = Modifier.height(4.dp))

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
                progress = { currentStats.ramPercent.coerceAtLeast(0).toFloat() / 100f },
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

            // CPU load (same data the widget's CPU tile shows)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.cpu_load_row), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                val cpuValue = buildString {
                    if (currentStats.cpuLoadPercent >= 0) {
                        append("${currentStats.cpuLoadPercent} %")
                    } else {
                        append(UNAVAILABLE_TEXT)
                    }
                    if (currentStats.cpuFreqGhz >= 0.0) {
                        append(" · ${"%.1f".format(currentStats.cpuFreqGhz)} GHz")
                    }
                }
                Text(
                    cpuValue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { currentStats.cpuLoadPercent.coerceAtLeast(0).toFloat() / 100f },
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

            // Storage (same data the widget's storage tile shows)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.storage_row), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                val usedStr = "%.0f".format(currentStats.usedStorageGb)
                val totalStr = "%.0f".format(currentStats.totalStorageGb)
                Text(
                    "$usedStr / $totalStr GB (${currentStats.storagePercent}%)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { currentStats.storagePercent.coerceAtLeast(0).toFloat() / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            val freeStorage = currentStats.totalStorageGb - currentStats.usedStorageGb
            Text(
                text = stringResource(R.string.widget_free_value, "%.0f GB".format(freeStorage)),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
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
                    Text(
                        stringResource(R.string.cpu_cores_label),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    Text(
                        stringResource(R.string.uptime_label),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(currentStats.uptimeText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Data counters for the selected period (day or billing cycle)
        SettingsSectionCard(titleRes = R.string.data_counter_section) {
            DeviceInfoRow(wifiDataLabelRes(uiState.dataCounterMode), gbTodayText(currentStats.wifiBytesTodayGb))
            DeviceInfoRow(simDataLabelRes(uiState.dataCounterMode), gbTodayText(currentStats.mobileDataUsedGb))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onRefresh,
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
}
