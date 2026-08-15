package org.jarsi.devicewatch.presentation.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.DataCounterMode
import org.jarsi.devicewatch.data.LastUsedTier
import org.jarsi.devicewatch.data.UNAVAILABLE_INT
import org.jarsi.devicewatch.data.UNAVAILABLE_TEXT
import org.jarsi.devicewatch.data.UsageEventAggregator
import org.jarsi.devicewatch.widget.dataAmountText
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * Shared section card used by every dashboard tab: rounded card, muted 11sp
 * uppercase-style title, then the section [content].
 */
@Composable
internal fun SettingsSectionCard(
    @StringRes titleRes: Int,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = horizontalAlignment
        ) {
            Text(
                stringResource(titleRes),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * True when a label and its value still fit side by side on one line.
 *
 * A plain `Row` cannot decide this: it hands the label the full width first, so a
 * label that grew with the system font leaves the value squeezed into whatever is
 * left — no gap between the two, and the value broken across lines mid-value.
 */
internal fun labelAndValueFitOnOneLine(
    labelWidth: Int,
    valueWidth: Int,
    gapWidth: Int,
    availableWidth: Int,
): Boolean = labelWidth + gapWidth + valueWidth <= availableWidth

/**
 * Label on the left, value on the right — until the two no longer fit on one line,
 * at which point the value drops onto its own line, still right-aligned. Each part
 * keeps its full intrinsic width whenever it can, so nothing is ever cut in half
 * by the other.
 */
@Composable
internal fun LabelValueRow(
    modifier: Modifier = Modifier,
    horizontalGap: Dp = 12.dp,
    verticalGap: Dp = 2.dp,
    label: @Composable () -> Unit,
    value: @Composable () -> Unit,
) {
    Layout(
        contents = listOf(label, value),
        modifier = modifier.fillMaxWidth()
    ) { (labelMeasurables, valueMeasurables), constraints ->
        val labelMeasurable = labelMeasurables.first()
        val valueMeasurable = valueMeasurables.first()
        val availableWidth = constraints.maxWidth
        val gap = horizontalGap.roundToPx()

        fun childConstraints(width: Int) = Constraints(
            minWidth = 0,
            maxWidth = width.coerceAtLeast(0),
            minHeight = 0,
            maxHeight = constraints.maxHeight
        )

        val sideBySide = !constraints.hasBoundedWidth || labelAndValueFitOnOneLine(
            labelWidth = labelMeasurable.maxIntrinsicWidth(constraints.maxHeight),
            valueWidth = valueMeasurable.maxIntrinsicWidth(constraints.maxHeight),
            gapWidth = gap,
            availableWidth = availableWidth
        )

        if (sideBySide) {
            val valuePlaceable = valueMeasurable.measure(childConstraints(availableWidth))
            val labelPlaceable =
                labelMeasurable.measure(childConstraints(availableWidth - gap - valuePlaceable.width))
            val width = if (constraints.hasBoundedWidth) {
                availableWidth
            } else {
                labelPlaceable.width + gap + valuePlaceable.width
            }
            layout(width, maxOf(labelPlaceable.height, valuePlaceable.height)) {
                labelPlaceable.place(0, 0)
                valuePlaceable.place(width - valuePlaceable.width, 0)
            }
        } else {
            val labelPlaceable = labelMeasurable.measure(childConstraints(availableWidth))
            val valuePlaceable = valueMeasurable.measure(childConstraints(availableWidth))
            val stackGap = verticalGap.roundToPx()
            layout(availableWidth, labelPlaceable.height + stackGap + valuePlaceable.height) {
                labelPlaceable.place(0, 0)
                valuePlaceable.place(
                    availableWidth - valuePlaceable.width,
                    labelPlaceable.height + stackGap
                )
            }
        }
    }
}

/**
 * Setting title, its explanation and the switch that turns it on. The switch is
 * pinned beside the title rather than centred against the whole block: a long
 * explanation at a large system font otherwise leaves it floating in the middle
 * of a paragraph, far from the thing it controls.
 */
@Composable
internal fun SettingsToggleRow(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(descriptionRes),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = withTapHaptic(onCheckedChange))
    }
}

@Composable
internal fun DeviceInfoRow(@StringRes labelRes: Int, value: String) {
    DeviceInfoRow(label = stringResource(labelRes), value = value)
}

@Composable
internal fun DeviceInfoRow(label: String, value: String) {
    LabelValueRow(
        modifier = Modifier.padding(vertical = 5.dp),
        label = {
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        value = {
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End
            )
        }
    )
}

/**
 * Label-above/full-width-value layout for long list-like values (sensor lists,
 * driver strings): a half-width right-aligned column wraps them raggedly, with
 * trailing commas at the right edge and one-word orphan lines.
 */
@Composable
internal fun DeviceInfoRowLong(@StringRes labelRes: Int, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Text(
            text = stringResource(labelRes),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NightDimTimePickerDialog(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = withTapHaptic { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = withTapHaptic(onDismiss)) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        text = { TimePicker(state = state) }
    )
}

/** Row label for Wi-Fi data usage that matches the selected counter period. */
@StringRes
internal fun wifiDataLabelRes(mode: DataCounterMode): Int =
    if (mode == DataCounterMode.DAY) R.string.wifi_data else R.string.wifi_data_period

/** Row label for mobile data usage that matches the selected counter period. */
@StringRes
internal fun simDataLabelRes(mode: DataCounterMode): Int =
    if (mode == DataCounterMode.DAY) R.string.sim_data else R.string.sim_data_period

internal fun dbmText(value: Int): String =
    if (value == UNAVAILABLE_INT) UNAVAILABLE_TEXT else "$value dBm"

internal fun mbpsText(value: Int): String =
    if (value == UNAVAILABLE_INT) UNAVAILABLE_TEXT else "$value Mbps"

internal fun countText(value: Int): String =
    if (value <= 0) UNAVAILABLE_TEXT else value.toString()

/** Count where zero is a real value; only the sentinel renders a dash. */
internal fun countOrDashText(value: Int): String =
    if (value >= 0) value.toString() else UNAVAILABLE_TEXT

internal fun gbTodayText(value: Double): String = dataAmountText(value)

internal fun minutesText(minutes: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", minutes / 60, minutes % 60)

/** Adaptive MB/GB text for a raw byte count (reuses the widget's formatter). */
internal fun bytesText(bytes: Long): String = dataAmountText(bytes / GB_BYTES)

/** Compact duration like "2h 14m" (locale strings), or "34 min" under one hour. */
internal fun durationText(context: Context, millis: Long): String {
    val totalMinutes = millis / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) context.getString(R.string.uptime_short, hours, minutes) else "$minutes min"
}

/** Whole days since the last use, or null when never used. */
internal fun daysSinceLastUse(lastUsedEpochMillis: Long?): Int? =
    UsageEventAggregator.daysSinceLastUse(
        lastUsedEpochMillis, LocalDate.now(), ZoneId.systemDefault()
    )

/**
 * "Never used" / "Today 10.54" / "N days ago" for a last-use timestamp. Today's
 * clock time follows the system 12/24-hour setting automatically
 * (DateFormat.getTimeFormat honors it and the locale).
 */
@Composable
internal fun lastUsedText(lastUsedEpochMillis: Long?): String {
    val context = LocalContext.current
    val days = daysSinceLastUse(lastUsedEpochMillis)
    return when {
        days == null -> stringResource(R.string.last_used_never)
        days == 0 -> stringResource(
            R.string.last_used_today_at,
            remember(lastUsedEpochMillis) {
                android.text.format.DateFormat.getTimeFormat(context)
                    .format(Date(lastUsedEpochMillis!!))
            }
        )
        else -> pluralStringResource(R.plurals.last_used_days, days, days)
    }
}

/**
 * Highlight color for the last-opened list: STALE (over Google's ~3-month app
 * hibernation threshold, or never used) in error red, AGING (1–3 months) amber,
 * recent in the normal muted ink.
 */
@Composable
internal fun lastUsedTierColor(tier: LastUsedTier): Color = when (tier) {
    LastUsedTier.STALE -> MaterialTheme.colorScheme.error
    LastUsedTier.AGING -> if (isSystemInDarkTheme()) Color(0xFFEDA100) else Color(0xFF9A6700)
    LastUsedTier.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** App launcher icon loaded once per package; falls back to a plain circle. */
@Composable
internal fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName)
                .toBitmap(ICON_PIXELS, ICON_PIXELS)
                .asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier)
    } else {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
    }
}

private const val GB_BYTES = 1024.0 * 1024.0 * 1024.0
private const val ICON_PIXELS = 96
