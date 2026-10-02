package org.jarsi.devicewatch.presentation.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.BatteryUsage
import org.jarsi.devicewatch.data.BatteryUsageReport
import org.jarsi.devicewatch.data.IdleDrain
import org.jarsi.devicewatch.data.WakeCategory
import org.jarsi.devicewatch.data.WakeEntry
import java.util.Locale

/**
 * The since-charge page's view of Android's own battery statistics, which only a
 * privileged shell (Shizuku or root) can read. The figures are the system's raw
 * ones, so every section says in plain words what it shows and how to read it:
 * a pace per hour and how long a full battery would last at it, rather than
 * bare mAh and mA.
 */
@Composable
internal fun BatteryDrainCard(report: BatteryUsageReport) {
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val usage = report.usage

    SettingsSectionCard(titleRes = R.string.battery_usage_section) {
        SectionNote(stringResource(R.string.battery_usage_period, durationText(context, usage.onBatteryMillis)))
        StackedMetricRow(
            label = stringResource(R.string.battery_usage_drained),
            value = listOfNotNull(
                mahText(usage.dischargeMah, locale),
                usage.dischargePercent?.let {
                    stringResource(R.string.battery_usage_of_battery, percentText(it, locale))
                },
            ).joinToString(" · ")
        )
        DrainRow(
            labelRes = R.string.battery_usage_screen_on,
            mah = usage.screenOnDischargeMah,
            percentPerHour = usage.screenOnPercentPerHour,
            millis = usage.screenOnMillis,
            verdictRes = null,
        )
        DrainRow(
            labelRes = R.string.battery_usage_screen_off,
            mah = usage.screenOffDischargeMah,
            percentPerHour = usage.screenOffPercentPerHour,
            millis = usage.screenOffMillis,
            verdictRes = when (usage.idleDrain) {
                IdleDrain.LOW -> R.string.battery_usage_idle_low
                IdleDrain.NORMAL -> R.string.battery_usage_idle_normal
                IdleDrain.HIGH -> R.string.battery_usage_idle_high
                null -> null
            },
            verdictIsWarning = usage.idleDrain == IdleDrain.HIGH,
        )
    }
}

/**
 * "7.7 % per hour", then what that came from and what it means: how much went
 * in how long, and how long a full battery would last at that pace.
 */
@Composable
private fun DrainRow(
    @StringRes labelRes: Int,
    mah: Double,
    percentPerHour: Double?,
    millis: Long,
    @StringRes verdictRes: Int?,
    verdictIsWarning: Boolean = false,
) {
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val amount = mahText(mah, locale)
    val duration = durationText(context, millis)
    val fullBatteryHours = BatteryUsage.fullBatteryHours(percentPerHour)

    Column {
        StackedMetricRow(
            label = stringResource(labelRes),
            value = if (percentPerHour != null) {
                stringResource(R.string.battery_usage_rate, percentText(percentPerHour, locale))
            } else {
                amount
            }
        )
        DetailText(
            when {
                percentPerHour == null -> stringResource(R.string.battery_usage_too_short, duration)
                fullBatteryHours == null -> stringResource(R.string.battery_usage_rate_from, amount, duration)
                else -> stringResource(
                    R.string.battery_usage_rate_detail, amount, duration, hoursText(fullBatteryHours, locale)
                )
            }
        )
        if (verdictRes != null) {
            DetailText(
                text = stringResource(verdictRes),
                color = if (verdictIsWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
internal fun BatteryAppsCard(report: BatteryUsageReport) {
    val locale = LocalLocale.current.platformLocale

    SettingsSectionCard(titleRes = R.string.battery_usage_apps_section) {
        SectionNote(stringResource(R.string.battery_usage_apps_note))
        report.apps.forEach { app ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = app.label,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    maxLines = 1,
                    text = percentText(app.share * 100.0, locale, decimals = 0),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    maxLines = 1,
                    text = mahText(app.mah, locale),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun BatteryWakeCard(report: BatteryUsageReport) {
    SettingsSectionCard(titleRes = R.string.battery_usage_wake_section) {
        SectionNote(stringResource(R.string.battery_usage_wake_note))
        WakeList(R.string.battery_usage_app_wake_locks, report.appWakeLocks)
        WakeList(R.string.battery_usage_kernel_wake_locks, report.kernelWakeLocks)
        WakeList(R.string.battery_usage_wakeup_reasons, report.wakeupReasons)
    }
}

@Composable
private fun WakeList(@StringRes titleRes: Int, entries: List<WakeEntry>) {
    if (entries.isEmpty()) return
    val context = LocalContext.current
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        text = stringResource(titleRes),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    entries.forEach { entry ->
        // An app's name or a part of the phone when known; the kernel's own name otherwise.
        val plainName = entry.owner ?: entry.category?.let { stringResource(wakeCategoryRes(it)) }
        Column(modifier = Modifier.padding(vertical = 5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plainName ?: entry.names.first(),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    maxLines = 1,
                    text = holdDurationText(context, entry.millis),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            // The technical names, for whoever wants to look one up.
            val technical = if (plainName != null) entry.names.take(TECHNICAL_NAMES_SHOWN).joinToString(", ") else null
            val times = if (entry.count > 0) {
                pluralStringResource(R.plurals.battery_usage_times, entry.count, entry.count)
            } else {
                null
            }
            val detail = listOfNotNull(times, technical).joinToString(" · ")
            if (detail.isNotEmpty()) DetailText(detail)
        }
    }
}

@StringRes
private fun wakeCategoryRes(category: WakeCategory): Int = when (category) {
    WakeCategory.SLEEP_INTERRUPTED -> R.string.wake_sleep_interrupted
    WakeCategory.APPS -> R.string.wake_apps
    WakeCategory.SCREEN -> R.string.wake_screen
    WakeCategory.SENSORS -> R.string.wake_sensors
    WakeCategory.WIFI -> R.string.wake_wifi
    WakeCategory.BLUETOOTH -> R.string.wake_bluetooth
    WakeCategory.MODEM -> R.string.wake_modem
    WakeCategory.ALARM -> R.string.wake_alarm
    WakeCategory.CHARGING -> R.string.wake_charging
    WakeCategory.NFC -> R.string.wake_nfc
    WakeCategory.INPUT -> R.string.wake_input
    WakeCategory.AUDIO -> R.string.wake_audio
    WakeCategory.LOCATION -> R.string.wake_location
}

/** The sentence under a section's title that says what the section shows. */
@Composable
private fun SectionNote(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun DetailText(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        text = text,
        fontSize = 12.sp,
        color = color
    )
}

private const val TECHNICAL_NAMES_SHOWN = 2

/** Whole mAh from a hundred up, a decimal below, two below one. */
internal fun mahText(mah: Double, locale: Locale): String = when {
    mah >= 100.0 -> String.format(locale, "%.0f mAh", mah)
    mah >= 1.0 -> String.format(locale, "%.1f mAh", mah)
    else -> String.format(locale, "%.2f mAh", mah)
}

internal fun percentText(percent: Double, locale: Locale, decimals: Int = 1): String =
    String.format(locale, "%.${decimals}f %%", percent)

/** "13 h" from ten hours up, "7.5 h" below. */
internal fun hoursText(hours: Double, locale: Locale): String =
    if (hours >= 10.0) String.format(locale, "%.0f h", hours) else String.format(locale, "%.1f h", hours)

/** "1h 2m", "1 min 27 s" or "6 s". */
internal fun holdDurationText(context: Context, millis: Long): String {
    val seconds = millis / 1000L
    return when {
        seconds >= 3600L -> durationText(context, millis)
        seconds >= 60L -> "${seconds / 60L} min ${seconds % 60L} s"
        else -> "$seconds s"
    }
}
