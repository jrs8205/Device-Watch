package com.example.modernwidget.presentation.ui

import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.modernwidget.R
import com.example.modernwidget.data.NotificationLogEntry
import com.example.modernwidget.presentation.HistoryDay
import com.example.modernwidget.presentation.HistoryViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

/** Metric shown by the history bar chart; label reuses the Overview usage-counter strings. */
internal enum class HistoryMetric(@StringRes val labelRes: Int) {
    ScreenTime(R.string.screen_time_total_label),
    Unlocks(R.string.unlock_count_label),
    Notifications(R.string.notification_count_label),
    Boots(R.string.boot_count_label),
    Charges(R.string.charge_count_label);

    fun valueOf(day: HistoryDay): Long = when (this) {
        ScreenTime -> day.screenTimeMillis
        Unlocks -> day.unlocks.toLong()
        Notifications -> day.notifications.toLong()
        Boots -> day.boots.toLong()
        Charges -> day.charges.toLong()
    }
}

/**
 * Full-screen "Historia" page reached from the Overview usage-counters card:
 * a 62-day bar chart per metric plus the on-device notification log, grouped by day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryPage(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    var selectedMetric by rememberSaveable { mutableStateOf(HistoryMetric.ScreenTime) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title), fontWeight = FontWeight.Bold) },
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
            item(key = "metric_chips") {
                MetricChipRow(selected = selectedMetric, onSelect = { selectedMetric = it })
            }

            item(key = "chart") {
                HistoryBarChart(days = uiState.days, metric = selectedMetric)
            }

            item(key = "log_header") {
                Text(
                    text = stringResource(R.string.history_log_section),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (uiState.logEntries.isEmpty()) {
                item(key = "log_empty") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.history_log_empty),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!uiState.listenerEnabled) {
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
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(entries) { entry -> NotificationLogRow(entry) }
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

@Composable
private fun MetricChipRow(selected: HistoryMetric, onSelect: (HistoryMetric) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HistoryMetric.entries.forEach { metric ->
            FilterChip(
                selected = metric == selected,
                onClick = { onSelect(metric) },
                label = { Text(stringResource(metric.labelRes), fontSize = 12.sp) }
            )
        }
    }
}

private val BAR_WIDTH = 10.dp
private val BAR_GAP = 4.dp
private val CHART_HEIGHT = 160.dp

/**
 * Horizontally scrollable bar chart, one bar per day, scaled to the max value of
 * the selected metric. Tapping a bar selects it (shown in tertiary) and reveals
 * its date + value above the chart.
 */
@Composable
private fun HistoryBarChart(days: List<HistoryDay>, metric: HistoryMetric) {
    val context = LocalContext.current
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val barStride = BAR_WIDTH + BAR_GAP
    val maxValue = (days.maxOfOrNull { metric.valueOf(it) } ?: 0L).coerceAtLeast(1L)
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val dateFormatter = mediumDateFormatter()

    Column(modifier = Modifier.fillMaxWidth()) {
        val selectedDay = selectedIndex?.let { days.getOrNull(it) }
        val selectionText = selectedDay?.let { day ->
            val valueText = if (metric == HistoryMetric.ScreenTime) {
                durationText(context, day.screenTimeMillis)
            } else {
                metric.valueOf(day).toString()
            }
            val dateText = day.day.format(dateFormatter)
            "$dateText  •  $valueText"
        } ?: ""
        Text(
            text = selectionText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            minLines = 1,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState(initial = Int.MAX_VALUE))
        ) {
            Canvas(
                modifier = Modifier
                    .width(barStride * days.size)
                    .height(CHART_HEIGHT)
                    .pointerInput(days, metric) {
                        detectTapGestures { offset ->
                            val index = (offset.x / barStride.toPx()).toInt()
                            if (index in days.indices) {
                                selectedIndex = index
                            }
                        }
                    }
            ) {
                val strideExactPx = barStride.toPx()
                val barWidthPx = BAR_WIDTH.toPx()
                days.forEachIndexed { index, day ->
                    val value = metric.valueOf(day)
                    val heightFraction = (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
                    val barHeight = size.height * heightFraction
                    val left = index * strideExactPx
                    val top = size.height - barHeight
                    drawRect(
                        color = if (index == selectedIndex) tertiaryColor else primaryColor,
                        topLeft = Offset(left, top),
                        size = Size(barWidthPx, barHeight)
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationLogRow(entry: NotificationLogEntry) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
    }
}
