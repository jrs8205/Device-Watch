package com.example.modernwidget.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.annotation.StringRes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.modernwidget.R
import com.example.modernwidget.data.ChargeAnchorType
import com.example.modernwidget.data.UNAVAILABLE_TEXT
import com.example.modernwidget.presentation.SinceChargeUiState
import com.example.modernwidget.presentation.SinceChargeViewModel
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

/** How often the open page silently re-reads the battery snapshot and usage. */
private const val SINCE_CHARGE_REFRESH_MS = 15_000L

/**
 * Full-screen "since charge" page reached from the Overview battery card:
 * a summary of the period since the battery was last charged full (or the
 * charger unplugged) plus per-app screen time over that window. Real per-app
 * battery attribution needs a privileged permission, so this page shows honest
 * usage numbers instead of invented percentages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SinceChargePage(
    onBack: () -> Unit,
    viewModel: SinceChargeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Initial load + silent refresh while the page stays open, so the elapsed
    // time, battery level and usage keep moving.
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.load()
            delay(SINCE_CHARGE_REFRESH_MS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.since_charge_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val anchor = uiState.anchor
            if (anchor == null) {
                if (!uiState.isLoading) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.since_charge_empty),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item(key = "summary") { SinceChargeSummaryCard(uiState) }

                // The notices live outside the screen-time card: exactly when there is
                // no data to draw, the explanation must still be visible.
                if (SinceChargeNotices.showUsageAccessNotice(hasAnchor = true, hasUsageAccess = uiState.hasUsageAccess)) {
                    item(key = "usage_access") {
                        DataQualityNotice(R.string.since_charge_usage_access_missing)
                    }
                }

                if (uiState.screenTimeSegments.isNotEmpty()) {
                    item(key = "screen_time") { SinceChargeScreenTimeCard(uiState) }
                }

                if (SinceChargeNotices.showStaleNotice(hasAnchor = true, nowMillis = uiState.nowMillis, anchorMillis = anchor.timeMillis)) {
                    item(key = "stale") {
                        DataQualityNotice(R.string.since_charge_stale_note)
                    }
                }
            }
        }
    }
}

@Composable
private fun SinceChargeSummaryCard(uiState: SinceChargeUiState) {
    val context = LocalContext.current
    val anchor = uiState.anchor ?: return

    SettingsSectionCard(titleRes = R.string.since_charge_summary_section) {
        val stamp = anchorTimeText(anchor.timeMillis)
        Text(
            text = when (anchor.type) {
                ChargeAnchorType.FULL_CHARGE ->
                    stringResource(R.string.since_charge_full_anchor, stamp)
                ChargeAnchorType.UNPLUGGED ->
                    stringResource(R.string.since_charge_unplug_anchor, anchor.batteryLevel, stamp)
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        val elapsedMillis = (uiState.nowMillis - anchor.timeMillis).coerceAtLeast(0L)
        DeviceInfoRow(R.string.since_charge_elapsed, durationText(context, elapsedMillis))
        DeviceInfoRow(
            R.string.since_charge_battery_now,
            uiState.currentLevel?.let { "$it %" } ?: UNAVAILABLE_TEXT
        )
        if (uiState.isCharging) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.since_charge_charging_now),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val drop = uiState.currentLevel?.let { anchor.batteryLevel - it }
            if (drop != null && drop >= 0) {
                DeviceInfoRow(R.string.since_charge_used, "$drop %")
                val hours = elapsedMillis / 3_600_000.0
                if (drop >= 1 && hours >= 0.5) {
                    DeviceInfoRow(
                        R.string.since_charge_avg_drain,
                        String.format(LocalLocale.current.platformLocale, "%.1f %%/h", drop / hours)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        DeviceInfoRow(R.string.unlock_count_label, countOrDashText(uiState.unlockCount))
        DeviceInfoRow(R.string.notification_count_label, countOrDashText(uiState.notificationCount))
        DeviceInfoRow(
            R.string.since_charge_wifi,
            if (uiState.wifiGb >= 0.0) gbTodayText(uiState.wifiGb) else UNAVAILABLE_TEXT
        )
        DeviceInfoRow(
            R.string.since_charge_mobile,
            if (uiState.mobileGb >= 0.0) gbTodayText(uiState.mobileGb) else UNAVAILABLE_TEXT
        )
    }
}

@Composable
private fun SinceChargeScreenTimeCard(uiState: SinceChargeUiState) {
    val context = LocalContext.current

    SettingsSectionCard(
        titleRes = R.string.screen_time_section,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val colors = donutSegmentColors(uiState.screenTimeSegments)
        Spacer(modifier = Modifier.height(4.dp))
        ScreenTimeDonut(
            segments = uiState.screenTimeSegments,
            colors = colors,
            totalText = durationText(context, uiState.totalScreenTimeMillis),
        )
        Spacer(modifier = Modifier.height(12.dp))
        uiState.screenTimeSegments.forEachIndexed { index, segment ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(colors[index])
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = segment.label ?: stringResource(R.string.screen_time_others),
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = durationText(context, segment.millis),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${(segment.fraction * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DataQualityNotice(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Time for today's anchors, localized "weekday day month" + time for older ones. */
@Composable
private fun anchorTimeText(timeMillis: Long): String {
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    return remember(timeMillis, locale) {
        val timeText = android.text.format.DateFormat.getTimeFormat(context).format(Date(timeMillis))
        val day = Instant.ofEpochMilli(timeMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        if (day == LocalDate.now()) {
            timeText
        } else {
            val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
            "${day.format(DateTimeFormatter.ofPattern(pattern, locale))} $timeText"
        }
    }
}
