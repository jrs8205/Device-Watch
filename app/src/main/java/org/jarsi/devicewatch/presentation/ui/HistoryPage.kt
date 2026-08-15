package org.jarsi.devicewatch.presentation.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.MonthlyDataUsage
import org.jarsi.devicewatch.data.NotificationLogEntry
import org.jarsi.devicewatch.presentation.HistoryDay
import org.jarsi.devicewatch.presentation.HistoryViewModel
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

/** How often the open page silently re-reads counts and the log. */
private const val REFRESH_INTERVAL_MS = 15_000L

/**
 * Full-screen "Historia" page reached from the Overview usage-counters card:
 * per-metric daily values for the retained 62 days (newest first, starting from
 * the first day the metric was collected) plus the on-device notification log,
 * grouped by day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryPage(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val view = LocalView.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Initial load + silent refresh while the page stays open, so new
    // notifications show up without leaving and re-entering the page.
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.load()
            delay(REFRESH_INTERVAL_MS)
        }
    }

    var selectedMetric by rememberSaveable { mutableStateOf(HistoryMetric.ScreenTime) }
    var batteryRange by rememberSaveable { mutableStateOf(BatteryChartRange.Day) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = withTapHaptic(onBack)) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                    }
                },
                actions = {
                    var exportMenuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = withTapHaptic { exportMenuOpen = true }) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(R.string.export_menu_description)
                        )
                    }
                    DropdownMenu(
                        expanded = exportMenuOpen,
                        onDismissRequest = { exportMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_report_html)) },
                            onClick = withTapHaptic {
                                exportMenuOpen = false
                                viewModel.exportHtmlReport(context)
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_usage_csv)) },
                            onClick = withTapHaptic {
                                exportMenuOpen = false
                                viewModel.exportUsageHistoryCsv(context)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_log_csv)) },
                            onClick = withTapHaptic {
                                exportMenuOpen = false
                                viewModel.exportNotificationLogCsv(context)
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = {
                view.performTapHaptic()
                viewModel.refresh()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                // Held back until the first load finishes so the chart's empty text
                // doesn't flash while the samples are still being read.
                if (uiState.batterySamples.isNotEmpty() || !uiState.isLoading) {
                    item(key = "battery_chart") {
                        SettingsSectionCard(titleRes = R.string.history_battery_section) {
                            BatteryRangeChipRow(selected = batteryRange, onSelect = { batteryRange = it })
                            Spacer(modifier = Modifier.height(12.dp))
                            BatteryChart(samples = uiState.batterySamples, range = batteryRange)
                        }
                    }
                }

                val visibleDays = daysNewestFirstSinceFirstData(uiState.days, selectedMetric)
                item(key = "day_list") {
                    SettingsSectionCard(titleRes = R.string.history_days_section) {
                        MetricChipRow(selected = selectedMetric, onSelect = { selectedMetric = it })
                        Spacer(modifier = Modifier.height(12.dp))
                        if (visibleDays.isEmpty()) {
                            if (!uiState.isLoading) {
                                Text(
                                    text = stringResource(R.string.history_metric_empty),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            HistoryDayList(days = visibleDays, metric = selectedMetric)
                        }
                    }
                }

                if (uiState.monthlyUsage.isNotEmpty()) {
                    item(key = "monthly_data") {
                        SettingsSectionCard(titleRes = R.string.history_monthly_data_section) {
                        MonthlyDataSection(
                            monthly = uiState.monthlyUsage,
                            onOpenUsageAccess = {
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                        )
                        }
                    }
                }

                item(key = "log_header") {
                    Text(
                        text = stringResource(R.string.history_log_section),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = SECTION_TITLE_TRACKING,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = BAND_INSET, end = BAND_INSET, top = BAND_SPACING, bottom = 12.dp)
                    )
                }

                if (uiState.logEntries.isEmpty()) {
                    if (!uiState.isLoading) {
                        item(key = "log_empty") {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = BAND_INSET)) {
                                Text(
                                    text = stringResource(R.string.history_log_empty),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!uiState.listenerEnabled) {
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
                                        Text(stringResource(R.string.notification_access_enable), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val grouped = uiState.logEntries.groupBy {
                        Instant.ofEpochMilli(it.timeMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    grouped.forEach { (day, entries) ->
                        item(key = "day_header_$day") {
                            Text(
                                text = dayHeaderText(day),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = BAND_INSET, end = BAND_INSET, top = 8.dp)
                            )
                        }
                        items(entries) { entry ->
                            NotificationLogRow(entry, modifier = Modifier.padding(horizontal = BAND_INSET))
                        }
                    }
                }
            }
        }
    }
}

/** "Today" / "Yesterday" / a localized medium date for a notification log day header. */
@Composable
private fun dayHeaderText(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> stringResource(R.string.history_today)
        today.minusDays(1) -> stringResource(R.string.history_yesterday)
        else -> day.format(mediumDateFormatter())
    }
}

/** Localized medium-length date formatter, tracking Compose's observable locale. */
@Composable
private fun mediumDateFormatter(): DateTimeFormatter {
    val locale = LocalLocale.current.platformLocale
    return remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
}

/** Localized "weekday + day + month" formatter (e.g. "la 5. heinäk." / "Sat, Jul 5"). */
@Composable
private fun weekdayDateFormatter(): DateTimeFormatter {
    val locale = LocalLocale.current.platformLocale
    return remember(locale) {
        DateTimeFormatter.ofPattern(
            android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMM"),
            locale
        )
    }
}

/** Localized standalone "month + year" formatter (e.g. "elokuu 2026" / "August 2026"). */
@Composable
private fun monthYearFormatter(): DateTimeFormatter {
    val locale = LocalLocale.current.platformLocale
    return remember(locale) {
        DateTimeFormatter.ofPattern(
            android.text.format.DateFormat.getBestDateTimePattern(locale, "LLLLyyyy"),
            locale
        )
    }
}

/**
 * Monthly Wi-Fi/mobile totals served straight from Android's own network stats,
 * newest first, current month in bold. Months past Android's retention render a
 * dash. Deliberately no data-SIM name here: these are device-wide aggregates and
 * the active data SIM may have changed mid-history.
 */
@Composable
private fun MonthlyDataSection(
    monthly: List<MonthlyDataUsage>,
    onOpenUsageAccess: () -> Unit,
) {
    val accessMissing = monthly.all { it.mobileGb < 0.0 && it.wifiGb < 0.0 }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (accessMissing) {
            Text(
                text = stringResource(R.string.history_monthly_usage_access_missing),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = withTapHaptic(onOpenUsageAccess)) {
                Text(stringResource(R.string.usage_access_button), fontSize = 12.sp)
            }
        } else {
            val monthFormatter = monthYearFormatter()
            val currentMonth = YearMonth.now()
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Spacer(modifier = Modifier.weight(1.1f))
                Text(
                    text = stringResource(R.string.history_monthly_mobile_column),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.1f)
                )
                Text(
                    text = stringResource(R.string.history_monthly_wifi_column),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.8f)
                )
            }
            monthly.forEach { month ->
                val isCurrent = month.month == currentMonth
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = month.month.format(monthFormatter)
                            .replaceFirstChar { it.uppercaseChar() },
                        fontSize = 13.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1.1f)
                    )
                    Text(
                        text = gbTodayText(month.mobileGb),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1.1f)
                    )
                    Text(
                        text = gbTodayText(month.wifiGb),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.history_monthly_metered_note),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BatteryRangeChipRow(selected: BatteryChartRange, onSelect: (BatteryChartRange) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BatteryChartRange.entries.forEach { range ->
            FilterChip(
                selected = range == selected,
                onClick = withTapHaptic { onSelect(range) },
                label = { Text(stringResource(range.labelRes), fontSize = 12.sp) }
            )
        }
    }
}

@Composable
private fun MetricChipRow(selected: HistoryMetric, onSelect: (HistoryMetric) -> Unit) {
    // Wrapping beats a horizontal scroll here: at a large system font the later
    // chips would sit off-screen with nothing hinting they exist.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HistoryMetric.entries.forEach { metric ->
            FilterChip(
                selected = metric == selected,
                onClick = withTapHaptic { onSelect(metric) },
                label = { Text(stringResource(metric.labelRes), fontSize = 12.sp) }
            )
        }
    }
}

/**
 * Exact daily values for the selected metric, newest first. The caption tells
 * since when the metric has been collected, so short histories don't read as
 * missing data.
 */
@Composable
private fun HistoryDayList(days: List<HistoryDay>, metric: HistoryMetric) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val weekdayFormatter = weekdayDateFormatter()
    val collectedSince = days.last().day.format(mediumDateFormatter())

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.history_collected_since, collectedSince),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        days.forEach { day ->
            val isToday = day.day == today
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (day.day) {
                        today -> stringResource(R.string.history_today)
                        today.minusDays(1) -> stringResource(R.string.history_yesterday)
                        else -> day.day.format(weekdayFormatter)
                    },
                    fontSize = 13.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (metric == HistoryMetric.ScreenTime) {
                        durationText(context, day.screenTimeMillis)
                    } else {
                        metric.valueOf(day).toString()
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun NotificationLogRow(entry: NotificationLogEntry, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // The exact message can't be reopened later — a notification's content
    // PendingIntent is app-private and dies with the notification — so tapping
    // opens the app that posted it. Rows of uninstalled apps aren't tappable.
    val launchIntent = remember(entry.packageName) {
        context.packageManager.getLaunchIntentForPackage(entry.packageName)
    }
    val openApp = withTapHaptic {
        try {
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = launchIntent != null, onClick = openApp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        AppIcon(entry.packageName, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.appLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = remember(entry.timeMillis) {
                        android.text.format.DateFormat.getTimeFormat(context).format(Date(entry.timeMillis))
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = entry.text,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        if (launchIntent != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(R.drawable.ic_open_in_new),
                contentDescription = stringResource(R.string.history_log_open_app),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .size(16.dp)
            )
        }
    }
}
