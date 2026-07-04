package com.example.modernwidget.presentation.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modernwidget.R
import com.example.modernwidget.data.DataCounterMode
import com.example.modernwidget.data.UNAVAILABLE_INT
import com.example.modernwidget.data.UNAVAILABLE_TEXT
import com.example.modernwidget.widget.dataAmountText
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

@Composable
internal fun DeviceInfoRow(@StringRes labelRes: Int, value: String) {
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
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
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

internal fun gbTodayText(value: Double): String = dataAmountText(value)

internal fun minutesText(minutes: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", minutes / 60, minutes % 60)
