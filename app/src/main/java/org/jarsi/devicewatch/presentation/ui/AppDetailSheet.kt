package org.jarsi.devicewatch.presentation.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.AppUsageDetail
import org.jarsi.devicewatch.data.UNAVAILABLE_INT
import org.jarsi.devicewatch.data.UNAVAILABLE_TEXT

/** Bottom sheet with the per-app usage details assembled by AppsViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppDetailSheet(
    detail: AppUsageDetail,
    onDismiss: () -> Unit,
    onEnableNotifications: () -> Unit,
) {
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(detail.packageName, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = detail.label,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = detail.packageName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            StackedMetricRow(
                label = stringResource(R.string.app_detail_screen_time),
                value = durationText(context, detail.foregroundMillisToday)
            )
            StackedMetricRow(
                label = stringResource(R.string.app_detail_launches),
                value = detail.launchCountToday.toString()
            )
            StackedMetricRow(
                label = stringResource(R.string.app_detail_last_opened),
                value = lastUsedText(detail.lastOpenedEpochMillis)
            )
            StackedMetricRow(
                label = stringResource(R.string.app_detail_data),
                value = bytesText(detail.dataBytesToday)
            )

            if (detail.notificationsToday == UNAVAILABLE_INT) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    StackedMetricRow(
                        label = stringResource(R.string.app_detail_notifications),
                        value = UNAVAILABLE_TEXT
                    )
                    TextButton(
                        onClick = withTapHaptic(onEnableNotifications),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(stringResource(R.string.notification_access_enable), fontSize = 13.sp)
                    }
                }
            } else {
                StackedMetricRow(
                    label = stringResource(R.string.app_detail_notifications),
                    value = detail.notificationsToday.toString()
                )
            }
        }
    }
}
