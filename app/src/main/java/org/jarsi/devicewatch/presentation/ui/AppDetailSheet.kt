package org.jarsi.devicewatch.presentation.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.AppPackageFacts
import org.jarsi.devicewatch.data.AppPackageLogic
import org.jarsi.devicewatch.data.AppUsageDetail
import org.jarsi.devicewatch.data.InstallSource
import org.jarsi.devicewatch.data.PermissionCategory
import org.jarsi.devicewatch.data.UNAVAILABLE_INT
import org.jarsi.devicewatch.data.UNAVAILABLE_TEXT
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
        // Five stacked readouts outgrow the sheet's height in landscape or a split
        // screen; the sheet itself does not scroll its content.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = detail.packageName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
            detail.lastBackgroundMillis?.let { background ->
                StackedMetricRow(
                    label = stringResource(R.string.app_detail_last_background),
                    value = lastUsedText(background)
                )
            }
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

            detail.facts?.let { facts -> AppFactsSection(detail.packageName, facts) }
        }
    }
}

/** Version, install, source and permission facts, with a way into the app's own settings. */
@Composable
private fun AppFactsSection(packageName: String, facts: AppPackageFacts) {
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    fun dateText(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormatter)

    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = stringResource(R.string.app_detail_about_section),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = SECTION_TITLE_TRACKING,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(modifier = Modifier.height(4.dp))
    AppFact(
        R.string.app_detail_version,
        facts.versionName?.let { "$it (${facts.versionCode})" } ?: facts.versionCode.toString(),
    )
    facts.installedMillis?.let { AppFact(R.string.app_detail_installed, dateText(it)) }
    facts.updatedMillis?.let { AppFact(R.string.app_detail_updated, dateText(it)) }
    AppFact(
        R.string.app_detail_source,
        when (val source = facts.installSource) {
            is InstallSource.App -> source.label
            InstallSource.Adb -> stringResource(R.string.app_detail_source_adb)
            InstallSource.ApkFile -> stringResource(R.string.app_detail_source_apk)
            InstallSource.Preinstalled -> stringResource(R.string.app_detail_source_preinstalled)
            InstallSource.Unknown -> stringResource(R.string.app_detail_source_unknown)
        },
    )
    AppFact(R.string.app_detail_target_android, androidLevelText(facts.targetSdk))
    facts.minSdk?.let { AppFact(R.string.app_detail_min_android, androidLevelText(it)) }
    val granted = facts.grantedCategories.map { stringResource(permissionCategoryLabel(it)) }
    AppFact(
        R.string.app_detail_granted_permissions,
        if (granted.isEmpty()) stringResource(R.string.app_detail_granted_none) else granted.joinToString(", "),
    )
    AppFact(R.string.app_detail_requested_permissions, facts.requestedPermissionCount.toString())
    TextButton(
        onClick = withTapHaptic {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        },
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(stringResource(R.string.app_detail_open_settings), fontSize = 13.sp)
    }
}

/** Caption over a settled fact; the value wraps rather than cutting a list of permissions short. */
@Composable
private fun AppFact(@StringRes labelRes: Int, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        MetricLabel(stringResource(labelRes))
        Text(text = value, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun androidLevelText(level: Int): String =
    AppPackageLogic.androidRelease(level)?.let { "Android $it (API $level)" } ?: "API $level"

@StringRes
private fun permissionCategoryLabel(category: PermissionCategory): Int = when (category) {
    PermissionCategory.LOCATION -> R.string.permission_group_location
    PermissionCategory.CAMERA -> R.string.permission_group_camera
    PermissionCategory.MICROPHONE -> R.string.permission_group_microphone
    PermissionCategory.CONTACTS -> R.string.permission_group_contacts
    PermissionCategory.CALENDAR -> R.string.permission_group_calendar
    PermissionCategory.PHONE -> R.string.permission_group_phone
    PermissionCategory.CALL_LOG -> R.string.permission_group_call_log
    PermissionCategory.SMS -> R.string.permission_group_sms
    PermissionCategory.FILES_AND_MEDIA -> R.string.permission_group_files
    PermissionCategory.BODY_SENSORS -> R.string.permission_group_body_sensors
    PermissionCategory.PHYSICAL_ACTIVITY -> R.string.permission_group_activity
    PermissionCategory.NEARBY_DEVICES -> R.string.permission_group_nearby
    PermissionCategory.NOTIFICATIONS -> R.string.permission_group_notifications
}
