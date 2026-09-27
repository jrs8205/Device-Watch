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
import androidx.compose.ui.text.style.TextOverflow
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.ChargeSource
import org.jarsi.devicewatch.data.ChargingState
import org.jarsi.devicewatch.data.DataCounterMode
import org.jarsi.devicewatch.data.DataQuotaLogic
import org.jarsi.devicewatch.data.ThermalLevel
import org.jarsi.devicewatch.data.TrafficSplit
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
            horizontalAlignment = Alignment.CenterHorizontally,
            trailing = {
                SectionLink(
                    text = stringResource(R.string.since_charge_title),
                    contentDescription = stringResource(R.string.since_charge_open),
                    onClick = onOpenSinceCharge
                )
            }
        ) {

            // One colour for the ring and the status dot: they say the same thing.
            val batteryColor = if (currentStats.batteryLevel > 20) {
                statusOkColor()
            } else {
                MaterialTheme.colorScheme.error
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp)
            ) {
                CircularProgressIndicator(
                    progress = { currentStats.batteryLevel.toFloat() / 100f },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 10.dp,
                    color = batteryColor,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MetricValue(
                        text = "${currentStats.batteryLevel}%",
                        fontSize = HERO_VALUE_SP
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(batteryColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        MetricLabel(currentStats.batteryStatus, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(12.dp))

            // A two-column grid: the values that only appear in some states (the
            // power source, the system's estimate) fill the next cell instead of
            // squeezing a single row, and the columns stay aligned.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StackedMetricRow(
                    label = stringResource(R.string.time_remaining),
                    value = currentStats.timeRemainingText,
                    valueSize = SECONDARY_VALUE_SP,
                    modifier = Modifier.weight(1f)
                )
                StackedMetricRow(
                    label = stringResource(R.string.temperature),
                    value = "%.1f °C".format(currentStats.batteryTemp),
                    valueSize = SECONDARY_VALUE_SP,
                    modifier = Modifier.weight(1f)
                )
                StackedMetricRow(
                    label = stringResource(R.string.voltage),
                    value = "%.2f V".format(currentStats.batteryVoltage),
                    valueSize = SECONDARY_VALUE_SP,
                    modifier = Modifier.weight(1f)
                )
                if (currentStats.systemEstimateText != UNAVAILABLE_TEXT) {
                    StackedMetricRow(
                        label = stringResource(
                            if (currentStats.systemEstimatePersonalized) {
                                R.string.system_estimate_personalized_label
                            } else {
                                R.string.system_estimate_label
                            }
                        ),
                        value = currentStats.systemEstimateText,
                        valueSize = SECONDARY_VALUE_SP,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Only while plugged in: on battery there is no source to name.
                chargeSourceRes(currentStats.chargeSource)?.let { sourceRes ->
                    StackedMetricRow(
                        label = stringResource(R.string.charge_source_label),
                        value = stringResource(sourceRes),
                        valueSize = SECONDARY_VALUE_SP,
                        modifier = Modifier.weight(1f)
                    )
                }
                // How the phone charges (adaptive, held for battery life, paused by
                // temperature) matters only while plugged in.
                if (currentStats.chargeSource != ChargeSource.NONE) {
                    chargingStateRes(currentStats.chargingState)?.let { stateRes ->
                        StackedMetricRow(
                            label = stringResource(R.string.charging_state_label),
                            value = stringResource(stateRes),
                            valueSize = SECONDARY_VALUE_SP,
                            valueColor = when (currentStats.chargingState) {
                                ChargingState.TOO_COLD, ChargingState.TOO_HOT -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
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
            },
            trailing = {
                SectionLink(
                    text = stringResource(R.string.history_title),
                    contentDescription = stringResource(R.string.history_open),
                    onClick = onOpenHistory
                )
            }
        ) {
            run {
                StackedMetricRow(
                    label = stringResource(R.string.screen_time_total_label),
                    value = if (uiState.screenTimeMillis >= 0L) {
                        durationText(context, uiState.screenTimeMillis)
                    } else {
                        UNAVAILABLE_TEXT
                    }
                )
                // The display itself, not app use: time on the lock screen or
                // the home screen counts here but not in screen time.
                StackedMetricRow(
                    label = stringResource(R.string.screen_on_label),
                    value = if (uiState.screenOnMillis >= 0L) {
                        durationText(context, uiState.screenOnMillis)
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
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
                // "Previous period vs now": a row needs a metric this device can
                // count and a previous window the store actually observed (null
                // otherwise); the section hides when no row is left.
                uiState.periodComparison?.let { comparison ->
                    val screenPrev = comparison.screenTimePrevMillis
                        ?.takeIf { uiState.screenTimeMillis >= 0L }
                    val unlocksPrev = comparison.unlocksPrev
                        ?.takeIf { uiState.usageAccessEnabled && uiState.unlockCountingSupported }
                    val notificationsPrev = comparison.notificationsPrev
                        ?.takeIf { uiState.notificationAccessEnabled }
                    if (screenPrev == null && unlocksPrev == null && notificationsPrev == null) {
                        return@let
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        text = stringResource(R.string.comparison_section_label),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (screenPrev != null) {
                        ComparisonRow(
                            labelRes = R.string.screen_time_total_label,
                            previousText = durationText(context, screenPrev),
                            nowText = durationText(context, comparison.screenTimeNowMillis),
                            changePercent = PeriodComparison.changePercent(
                                comparison.screenTimeNowMillis, screenPrev
                            )
                        )
                    }
                    if (unlocksPrev != null) {
                        ComparisonRow(
                            labelRes = R.string.unlock_count_label,
                            previousText = unlocksPrev.toString(),
                            nowText = comparison.unlocksNow.toString(),
                            changePercent = PeriodComparison.changePercent(
                                comparison.unlocksNow.toLong(), unlocksPrev.toLong()
                            )
                        )
                    }
                    if (notificationsPrev != null) {
                        ComparisonRow(
                            labelRes = R.string.notification_count_label,
                            previousText = notificationsPrev.toString(),
                            nowText = comparison.notificationsNow.toString(),
                            changePercent = PeriodComparison.changePercent(
                                comparison.notificationsNow.toLong(),
                                notificationsPrev.toLong()
                            )
                        )
                    }
                    Text(
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
            uiState.dataBreakdown?.wifi?.let { DataSplitCaption(it) }
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
            uiState.dataBreakdown?.mobile?.let { DataSplitCaption(it) }
            uiState.dataBreakdown?.mobileRoamingBytes?.takeIf { it > 0 }?.let { roamingBytes ->
                DataCaption(
                    stringResource(R.string.data_roaming_caption, gbTodayText(roamingBytes / GB_BYTES))
                )
            }
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

            // How close the phone is to throttling itself for heat: the level the
            // system reports and, where available, the share of the threshold.
            thermalLevelRes(currentStats.thermalLevel)?.let { levelRes ->
                Spacer(modifier = Modifier.height(12.dp))
                val level = stringResource(levelRes)
                StackedMetricRow(
                    label = stringResource(R.string.thermal_label),
                    value = if (currentStats.thermalHeadroomPercent >= 0) {
                        stringResource(R.string.thermal_value, level, currentStats.thermalHeadroomPercent)
                    } else {
                        level
                    },
                    valueSize = SECONDARY_VALUE_SP,
                    valueColor = if (currentStats.thermalLevel >= ThermalLevel.SEVERE) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                // Android gives no temperature for the limit, only the share of it:
                // say what the limit is, in the same caption style as "4 GB free".
                if (currentStats.thermalHeadroomPercent >= 0) {
                    Text(
                        text = stringResource(R.string.thermal_limit_caption),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                text = stringResource(R.string.widget_free_value, "%.0f GB".format(freeStorage)),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            // What the used space holds, the same split as Settings' storage page.
            uiState.storageBreakdown?.let { storage ->
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = 2,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    listOf(
                        R.string.storage_apps to storage.appsBytes,
                        R.string.storage_cache to storage.cacheBytes,
                        R.string.storage_images to storage.imageBytes,
                        R.string.storage_video to storage.videoBytes,
                        R.string.storage_audio to storage.audioBytes,
                        R.string.storage_other to storage.otherBytes,
                    ).forEach { (labelRes, bytes) ->
                        StackedMetricRow(
                            label = stringResource(labelRes),
                            value = gbTodayText(bytes / GB_BYTES),
                            valueSize = STORAGE_PART_SP,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            context.resources.getQuantityString(
                                R.plurals.cpu_cores_value,
                                currentStats.cpuCores,
                                currentStats.cpuCores,
                                currentStats.cpuAbi
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            currentStats.uptimeText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            )

            // How much of the uptime the phone spent in deep sleep: a phone that
            // rarely sleeps is a phone something keeps awake.
            if (currentStats.deepSleepText != UNAVAILABLE_TEXT) {
                Spacer(modifier = Modifier.height(8.dp))
                StackedMetricRow(
                    label = stringResource(R.string.deep_sleep_label),
                    value = currentStats.deepSleepText,
                    valueSize = SECONDARY_VALUE_SP
                )
            }
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            text = stringResource(R.string.last_updated, uiState.lastUpdated),
            modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** How much of a network's traffic ran with its app in front, in the caption style. */
@Composable
private fun DataSplitCaption(split: TrafficSplit) {
    DataCaption(stringResource(R.string.data_split_caption, split.foregroundPercent, split.backgroundPercent))
}

@Composable
private fun DataCaption(text: String) {
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    )
}

private const val GB_BYTES = 1024.0 * 1024.0 * 1024.0

private val STORAGE_PART_SP = 16.sp

/** Label for the system's thermal level; null when the system reports none. */
private fun thermalLevelRes(level: ThermalLevel): Int? = when (level) {
    ThermalLevel.UNKNOWN -> null
    ThermalLevel.NONE -> R.string.thermal_none
    ThermalLevel.LIGHT -> R.string.thermal_light
    ThermalLevel.MODERATE -> R.string.thermal_moderate
    ThermalLevel.SEVERE -> R.string.thermal_severe
    ThermalLevel.CRITICAL -> R.string.thermal_critical
    ThermalLevel.EMERGENCY -> R.string.thermal_emergency
    ThermalLevel.SHUTDOWN -> R.string.thermal_shutdown
}

/** Label for how the battery is being charged; null when the platform says nothing. */
private fun chargingStateRes(state: ChargingState): Int? = when (state) {
    ChargingState.UNKNOWN -> null
    ChargingState.NORMAL -> R.string.charging_state_normal
    ChargingState.TOO_COLD -> R.string.charging_state_too_cold
    ChargingState.TOO_HOT -> R.string.charging_state_too_hot
    ChargingState.LONG_LIFE -> R.string.charging_state_long_life
    ChargingState.ADAPTIVE -> R.string.charging_state_adaptive
}

/** Label for what the phone is plugged into; null on battery. */
private fun chargeSourceRes(source: ChargeSource): Int? = when (source) {
    ChargeSource.NONE -> null
    ChargeSource.AC -> R.string.charge_source_ac
    ChargeSource.USB -> R.string.charge_source_usb
    ChargeSource.WIRELESS -> R.string.charge_source_wireless
    ChargeSource.DOCK -> R.string.charge_source_dock
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    text = stringResource(R.string.comparison_values, previousText, nowText),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
