package org.jarsi.devicewatch.presentation.ui

import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.DataCounterMode
import org.jarsi.devicewatch.data.DataQuotaLogic
import org.jarsi.devicewatch.data.UNAVAILABLE_TEXT
import org.jarsi.devicewatch.presentation.DashboardUiState
import org.jarsi.devicewatch.presentation.PeriodComparison
import org.jarsi.devicewatch.widget.mobileDataText

/**
 * Overview tab: widget status, live battery ring, usage counters for the selected
 * period, full widget-parity system resources and the data counters — so the app
 * shows everything the home-screen widget does, for people who skip the widget.
 */
@Composable
internal fun OverviewTab(
    uiState: DashboardUiState,
    onRefresh: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSinceCharge: () -> Unit,
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
            Row(modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        // Sits on the first line's baseline: centring it drifts to
                        // the middle of the message once the text wraps.
                        .alignBy { it.measuredHeight }
                        .clip(CircleShape)
                        .background(if (uiState.isWidgetInstalled) Color(0xFF4CAF50) else Color(0xFFFF9800))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    modifier = Modifier.alignByBaseline(),
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

        // Battery section — the whole card opens the "since charge" page.
        SettingsSectionCard(
            titleRes = R.string.battery_status_section,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = withTapHaptic(onOpenSinceCharge)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = stringResource(R.string.since_charge_title),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.since_charge_open),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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

            // Three stat columns that wrap onto further lines instead of being
            // squeezed together once the system font makes them too wide.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
        // are visible without scrolling to the bottom. The whole card opens the
        // Historia page (daily history values + notification log).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = withTapHaptic(onOpenHistory))
        ) {
            SettingsSectionCard(
                titleRes = if (uiState.dataCounterMode == DataCounterMode.DAY) {
                    R.string.usage_counters_section
                } else {
                    R.string.usage_counters_section_period
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = stringResource(R.string.history_title),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.history_open),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                    LabelValueRow(
                        label = {
                            Text(
                                text = stringResource(R.string.notification_count_label),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        value = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = UNAVAILABLE_TEXT,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                TextButton(onClick = withTapHaptic {
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
                                    Text(
                                        stringResource(R.string.notification_access_enable),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    )
                }
                DeviceInfoRow(R.string.boot_count_label, uiState.bootCount.toString())
                DeviceInfoRow(R.string.charge_count_label, uiState.chargeCount.toString())
                // "Previous period vs now": only metrics this device can actually
                // count get a row, and the section hides when none can.
                val hasComparisonRows = uiState.screenTimeMillis >= 0L ||
                    (uiState.usageAccessEnabled && uiState.unlockCountingSupported) ||
                    uiState.notificationAccessEnabled
                uiState.periodComparison?.takeIf { hasComparisonRows }?.let { comparison ->
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.comparison_section_label),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.screenTimeMillis >= 0L) {
                        ComparisonRow(
                            labelRes = R.string.screen_time_total_label,
                            previousText = durationText(context, comparison.screenTimePrevMillis),
                            nowText = durationText(context, comparison.screenTimeNowMillis),
                            changePercent = PeriodComparison.changePercent(
                                comparison.screenTimeNowMillis, comparison.screenTimePrevMillis
                            )
                        )
                    }
                    if (uiState.usageAccessEnabled && uiState.unlockCountingSupported) {
                        ComparisonRow(
                            labelRes = R.string.unlock_count_label,
                            previousText = comparison.unlocksPrev.toString(),
                            nowText = comparison.unlocksNow.toString(),
                            changePercent = PeriodComparison.changePercent(
                                comparison.unlocksNow.toLong(), comparison.unlocksPrev.toLong()
                            )
                        )
                    }
                    if (uiState.notificationAccessEnabled) {
                        ComparisonRow(
                            labelRes = R.string.notification_count_label,
                            previousText = comparison.notificationsPrev.toString(),
                            nowText = comparison.notificationsNow.toString(),
                            changePercent = PeriodComparison.changePercent(
                                comparison.notificationsNow.toLong(),
                                comparison.notificationsPrev.toLong()
                            )
                        )
                    }
                    Text(
                        // Day mode really is "yesterday"; in cycle mode a one-day
                        // window is the previous cycle's first day, not yesterday.
                        text = if (uiState.dataCounterMode == DataCounterMode.DAY) {
                            stringResource(R.string.comparison_yesterday)
                        } else {
                            pluralStringResource(
                                R.plurals.comparison_days,
                                comparison.daysCompared,
                                comparison.daysCompared
                            )
                        },
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Data counters for the selected period (day or billing cycle), right under the
        // usage card so both period counters sit together.
        SettingsSectionCard(titleRes = R.string.data_counter_section) {
            DeviceInfoRow(wifiDataLabelRes(uiState.dataCounterMode), gbTodayText(currentStats.wifiBytesTodayGb))
            val mobileLabel = stringResource(simDataLabelRes(uiState.dataCounterMode))
            val simName = currentStats.dataSimName.takeIf { it != UNAVAILABLE_TEXT }
            // With a quota set the row reads "used / quota" (same text the widget shows)
            // and gets a usage bar; without one it stays a plain amount.
            DeviceInfoRow(
                label = if (simName != null) {
                    stringResource(R.string.label_with_sim, mobileLabel, simName)
                } else {
                    mobileLabel
                },
                value = mobileDataText(currentStats.mobileDataUsedGb, currentStats.mobileDataTotalGb)
            )
            val quotaPercentUsed = DataQuotaLogic.percentUsed(
                quotaGb = currentStats.mobileDataTotalGb,
                usedGb = currentStats.mobileDataUsedGb,
            )
            if (quotaPercentUsed != null) {
                LinearProgressIndicator(
                    progress = { (quotaPercentUsed / 100f).coerceAtMost(1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (quotaPercentUsed >= 100) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        // Resources (RAM, CPU, storage) — widget parity for people without the widget
        SettingsSectionCard(titleRes = R.string.system_resources_section) {
            Spacer(modifier = Modifier.height(4.dp))

            // RAM
            LabelValueRow(
                label = {
                    Text(
                        stringResource(R.string.ram_title),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                },
                value = {
                    val usedRamStr = "%.1f".format(currentStats.usedRamGb)
                    val totalRamStr = "%.1f".format(currentStats.totalRamGb)
                    Text(
                        "$usedRamStr / $totalRamStr GB (${currentStats.ramPercent}%)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            )

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
            LabelValueRow(
                label = {
                    Text(
                        stringResource(R.string.cpu_load_row),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                },
                value = {
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
            )

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
            LabelValueRow(
                label = {
                    Text(
                        stringResource(R.string.storage_row),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                },
                value = {
                    val usedStr = "%.0f".format(currentStats.usedStorageGb)
                    val totalStr = "%.0f".format(currentStats.totalStorageGb)
                    Text(
                        "$usedStr / $totalStr GB (${currentStats.storagePercent}%)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            )

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

            // CPU info & Uptime — two caption-over-value blocks that stack when a
            // large font stops them fitting side by side.
            LabelValueRow(
                label = {
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
                },
                value = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(R.string.uptime_label),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(currentStats.uptimeText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = withTapHaptic(onRefresh),
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

@Composable
private fun ComparisonRow(
    @StringRes labelRes: Int,
    previousText: String,
    nowText: String,
    changePercent: Int?,
) {
    LabelValueRow(
        modifier = Modifier.padding(vertical = 3.dp),
        label = {
            Text(
                text = stringResource(labelRes),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        value = {
            // The two figures and the change belong together: they move to the
            // next line as one block rather than breaking apart.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.comparison_values, previousText, nowText),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    // Less is better for all three metrics: growth reads in the error
                    // color, a drop in the primary one, and no baseline stays neutral.
                    text = changePercent?.let {
                        stringResource(R.string.comparison_change, if (it > 0) "+" else "", it)
                    } ?: UNAVAILABLE_TEXT,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        changePercent == null || changePercent == 0 ->
                            MaterialTheme.colorScheme.onSurfaceVariant
                        changePercent > 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    )
}
