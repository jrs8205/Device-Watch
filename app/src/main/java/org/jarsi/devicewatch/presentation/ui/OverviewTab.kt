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
import androidx.compose.foundation.layout.PaddingValues
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
import org.jarsi.devicewatch.ui.theme.STATUS_TINT_ALPHA
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
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Widget connection status. The card is tinted with the same status color
        // as its dot — a missing widget is a hint, not an error, and an error-red
        // card next to an amber dot said two different things.
        val statusColor = if (uiState.isWidgetInstalled) statusOkColor() else statusWarnColor()
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(statusColor.copy(alpha = STATUS_TINT_ALPHA))
                    .padding(horizontal = BAND_INSET, vertical = BAND_SPACING)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        // Sits on the first line's baseline: centring it drifts to
                        // the middle of the message once the text wraps.
                        .alignBy { it.measuredHeight }
                        .clip(CircleShape)
                        .background(statusColor)
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }

        // Battery section — the whole card opens the "since charge" page.
        SettingsSectionCard(
            titleRes = R.string.battery_status_section,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = withTapHaptic(onOpenSinceCharge))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
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
                    color = if (currentStats.batteryLevel > 20) {
                        statusOkColor()
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MetricValue(
                        text = "${currentStats.batteryLevel}%",
                        fontSize = HERO_VALUE_SP
                    )
                    MetricLabel(currentStats.batteryStatus, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(12.dp))

            // Three stat columns that wrap onto further lines instead of being
            // squeezed together once the system font makes them too wide.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StackedMetricRow(
                    label = stringResource(R.string.time_remaining),
                    value = currentStats.timeRemainingText,
                    valueSize = SECONDARY_VALUE_SP
                )
                StackedMetricRow(
                    label = stringResource(R.string.temperature),
                    value = "${currentStats.batteryTemp} °C",
                    valueSize = SECONDARY_VALUE_SP
                )
                StackedMetricRow(
                    label = stringResource(R.string.voltage),
                    value = "%.2f V".format(currentStats.batteryVoltage),
                    valueSize = SECONDARY_VALUE_SP
                )
            }
        }

        // Usage counters for the selected period, right under the battery so they
        // are visible without scrolling to the bottom. The whole card opens the
        // Historia page (daily history values + notification log).
        SettingsSectionCard(
            titleRes = if (uiState.dataCounterMode == DataCounterMode.DAY) {
                R.string.usage_counters_section
            } else {
                R.string.usage_counters_section_period
            }
        ) {
            run {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = withTapHaptic(onOpenHistory))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
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
                StackedMetricRow(
                    label = stringResource(R.string.screen_time_total_label),
                    value = if (uiState.screenTimeMillis >= 0L) {
                        durationText(context, uiState.screenTimeMillis)
                    } else {
                        UNAVAILABLE_TEXT
                    }
                )
                if (uiState.unlockCountingSupported) {
                    StackedMetricRow(
                        label = stringResource(R.string.unlock_count_label),
                        value = countOrDashText(uiState.unlockCount)
                    )
                }
                if (uiState.notificationAccessEnabled) {
                    StackedMetricRow(
                        label = stringResource(R.string.notification_count_label),
                        value = countOrDashText(uiState.notificationCount)
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
                StackedMetricRow(
                    label = stringResource(R.string.boot_count_label),
                    value = uiState.bootCount.toString()
                )
                StackedMetricRow(
                    label = stringResource(R.string.charge_count_label),
                    value = uiState.chargeCount.toString()
                )
                // "Previous period vs now": only metrics this device can actually
                // count get a row, and the section hides when none can.
                val hasComparisonRows = uiState.screenTimeMillis >= 0L ||
                    (uiState.usageAccessEnabled && uiState.unlockCountingSupported) ||
                    uiState.notificationAccessEnabled
                uiState.periodComparison?.takeIf { hasComparisonRows }?.let { comparison ->
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline
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
            StackedMetricRow(
                label = stringResource(wifiDataLabelRes(uiState.dataCounterMode)),
                value = gbTodayText(currentStats.wifiBytesTodayGb)
            )
            val mobileLabel = stringResource(simDataLabelRes(uiState.dataCounterMode))
            val simName = currentStats.dataSimName.takeIf { it != UNAVAILABLE_TEXT }
            // With a quota set the row reads "used / quota" (same text the widget shows)
            // and gets a usage bar; without one it stays a plain amount.
            StackedMetricRow(
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
                        .height(METER_HEIGHT)
                        .clip(RoundedCornerShape(METER_RADIUS)),
                    color = if (quotaPercentUsed >= 100) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }

        // Resources (RAM, CPU, storage) — widget parity for people without the widget
        SettingsSectionCard(titleRes = R.string.system_resources_section) {
            Spacer(modifier = Modifier.height(4.dp))

            // RAM
            StackedMetricRow(
                label = stringResource(R.string.ram_title),
                value = "%.1f / %.1f GB (%d%%)".format(
                    currentStats.usedRamGb, currentStats.totalRamGb, currentStats.ramPercent
                ),
                valueColor = meterColor()
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { currentStats.ramPercent.coerceAtLeast(0).toFloat() / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(METER_HEIGHT)
                    .clip(RoundedCornerShape(METER_RADIUS)),
                color = meterColor(),
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(16.dp))

            // CPU load (same data the widget's CPU tile shows)
            StackedMetricRow(
                label = stringResource(R.string.cpu_load_row),
                value = buildString {
                    if (currentStats.cpuLoadPercent >= 0) {
                        append("${currentStats.cpuLoadPercent} %")
                    } else {
                        append(UNAVAILABLE_TEXT)
                    }
                    if (currentStats.cpuFreqGhz >= 0.0) {
                        append(" · ${"%.1f".format(currentStats.cpuFreqGhz)} GHz")
                    }
                },
                valueColor = meterColor()
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { currentStats.cpuLoadPercent.coerceAtLeast(0).toFloat() / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(METER_HEIGHT)
                    .clip(RoundedCornerShape(METER_RADIUS)),
                color = meterColor(),
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(16.dp))

            // Storage (same data the widget's storage tile shows)
            StackedMetricRow(
                label = stringResource(R.string.storage_row),
                value = "%.0f / %.0f GB (%d%%)".format(
                    currentStats.usedStorageGb, currentStats.totalStorageGb, currentStats.storagePercent
                ),
                valueColor = meterColor()
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { currentStats.storagePercent.coerceAtLeast(0).toFloat() / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(METER_HEIGHT)
                    .clip(RoundedCornerShape(METER_RADIUS)),
                color = meterColor(),
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            val freeStorage = currentStats.totalStorageGb - currentStats.usedStorageGb
            Text(
                text = stringResource(R.string.widget_free_value, "%.0f GB".format(freeStorage)),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
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

        // Sized to its label rather than to the screen: a full-bleed button in a
        // page of full-bleed bands reads as another band, not as an action.
        Button(
            onClick = withTapHaptic(onRefresh),
            modifier = Modifier.padding(top = 28.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.refresh_data))
        }

        Text(
            text = stringResource(R.string.last_updated, uiState.lastUpdated),
            modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
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
