package org.jarsi.devicewatch.presentation.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.jarsi.devicewatch.BuildConfig
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.CHARGE_LIMIT_MAX
import org.jarsi.devicewatch.data.CHARGE_LIMIT_MIN
import org.jarsi.devicewatch.data.DEFAULT_CHARGE_LIMIT_PERCENT
import org.jarsi.devicewatch.data.DataCounterMode
import org.jarsi.devicewatch.presentation.DashboardUiState
import org.jarsi.devicewatch.system.DreamPreferences
import kotlin.math.roundToInt

/**
 * Settings tab: data counter period, charge reminder, widget opacity, screensaver
 * and special access.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsTab(
    uiState: DashboardUiState,
    onWidgetOpacityChange: (Float) -> Unit,
    onCommitWidgetOpacity: () -> Unit,
    onDataCounterModeSelected: (DataCounterMode) -> Unit,
    onCycleStartDayChange: (Int) -> Unit,
    onCommitCycleStartDay: () -> Unit,
    onChargeLimitChange: (Int) -> Unit,
    onCommitChargeLimit: () -> Unit,
    onShowIntro: () -> Unit,
) {
    val context = LocalContext.current

    // Runtime-permission status mirrors the intro's permission page, so Settings
    // shows all three grants — not just the two special-access ones.
    var runtimePermissionsGranted by remember {
        mutableStateOf(missingRuntimePermissions(context).isEmpty())
    }
    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        runtimePermissionsGranted = missingRuntimePermissions(context).isEmpty()
    }
    LifecycleResumeEffect(Unit) {
        runtimePermissionsGranted = missingRuntimePermissions(context).isEmpty()
        onPauseOrDispose { }
    }

    val dreamPrefs = remember(context) {
        context.getSharedPreferences(DreamPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    }
    var forcePortraitScreensaver by remember {
        mutableStateOf(dreamPrefs.getBoolean(DreamPreferences.KEY_FORCE_PORTRAIT, false))
    }
    var dimScreensaver by remember {
        mutableStateOf(dreamPrefs.getBoolean(DreamPreferences.KEY_DIM_SCREENSAVER, false))
    }
    var nightDimScreensaver by remember {
        mutableStateOf(dreamPrefs.getBoolean(DreamPreferences.KEY_NIGHT_DIM, false))
    }
    var nightDimStartMinutes by remember {
        mutableIntStateOf(
            dreamPrefs.getInt(
                DreamPreferences.KEY_NIGHT_DIM_START_MINUTES,
                DreamPreferences.DEFAULT_NIGHT_DIM_START_MINUTES
            )
        )
    }
    var nightDimEndMinutes by remember {
        mutableIntStateOf(
            dreamPrefs.getInt(
                DreamPreferences.KEY_NIGHT_DIM_END_MINUTES,
                DreamPreferences.DEFAULT_NIGHT_DIM_END_MINUTES
            )
        )
    }
    var showNightDimStartPicker by remember { mutableStateOf(false) }
    var showNightDimEndPicker by remember { mutableStateOf(false) }

    if (showNightDimStartPicker) {
        NightDimTimePickerDialog(
            initialMinutes = nightDimStartMinutes,
            onConfirm = { minutes ->
                nightDimStartMinutes = minutes
                dreamPrefs.edit()
                    .putInt(DreamPreferences.KEY_NIGHT_DIM_START_MINUTES, minutes)
                    .apply()
                showNightDimStartPicker = false
            },
            onDismiss = { showNightDimStartPicker = false }
        )
    }
    if (showNightDimEndPicker) {
        NightDimTimePickerDialog(
            initialMinutes = nightDimEndMinutes,
            onConfirm = { minutes ->
                nightDimEndMinutes = minutes
                dreamPrefs.edit()
                    .putInt(DreamPreferences.KEY_NIGHT_DIM_END_MINUTES, minutes)
                    .apply()
                showNightDimEndPicker = false
            },
            onDismiss = { showNightDimEndPicker = false }
        )
    }

    fun openSpecialAccessSettings(action: String) {
        try {
            context.startActivity(Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Android 12+ ships a Privacy Dashboard activity behind a non-SDK action string;
    // fall back to the plain privacy settings when it is missing (OEM/older Android).
    fun openPrivacyDashboard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                context.startActivity(Intent("android.settings.PRIVACY_DASHBOARD").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                return
            } catch (_: Exception) {
                // fall through to the generic privacy settings
            }
        }
        openSpecialAccessSettings(Settings.ACTION_PRIVACY_SETTINGS)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Data counter period
        SettingsSectionCard(titleRes = R.string.data_counter_section) {
            Spacer(modifier = Modifier.height(4.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                DataCounterMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = uiState.dataCounterMode == mode,
                        onClick = withTapHaptic { onDataCounterModeSelected(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = DataCounterMode.entries.size
                        )
                    ) {
                        Text(
                            text = stringResource(
                                if (mode == DataCounterMode.DAY) {
                                    R.string.data_counter_mode_day
                                } else {
                                    R.string.data_counter_mode_cycle
                                }
                            ),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (uiState.dataCounterMode == DataCounterMode.BILLING_CYCLE) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.data_counter_cycle_start, uiState.cycleStartDay),
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Slider(
                    value = uiState.cycleStartDay.toFloat(),
                    onValueChange = { onCycleStartDayChange(it.roundToInt()) },
                    onValueChangeFinished = withTapHaptic(onCommitCycleStartDay),
                    valueRange = 1f..31f,
                    steps = 29,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.data_counter_description),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Charge reminder
        SettingsSectionCard(titleRes = R.string.battery_section_title) {
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.charge_limit_title),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.charge_limit_description),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = uiState.chargeLimitPercent > 0,
                    onCheckedChange = withTapHaptic { checked ->
                        onChargeLimitChange(if (checked) DEFAULT_CHARGE_LIMIT_PERCENT else 0)
                        onCommitChargeLimit()
                    }
                )
            }

            if (uiState.chargeLimitPercent > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.charge_limit_value, uiState.chargeLimitPercent),
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Slider(
                    value = uiState.chargeLimitPercent.toFloat(),
                    onValueChange = { onChargeLimitChange(it.roundToInt()) },
                    onValueChangeFinished = withTapHaptic(onCommitChargeLimit),
                    valueRange = CHARGE_LIMIT_MIN.toFloat()..CHARGE_LIMIT_MAX.toFloat(),
                    steps = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Widget settings
        SettingsSectionCard(titleRes = R.string.widget_settings_section) {
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.widget_opacity_title),
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Slider(
                    value = uiState.widgetOpacity,
                    onValueChange = onWidgetOpacityChange,
                    onValueChangeFinished = withTapHaptic(onCommitWidgetOpacity),
                    valueRange = 0.30f..1.0f,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "${(uiState.widgetOpacity * 100).toInt()}%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(42.dp)
                )
            }

            Text(
                text = stringResource(R.string.widget_opacity_description),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Screensaver settings
        SettingsSectionCard(titleRes = R.string.screensaver_settings_section) {
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.force_portrait_title),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.force_portrait_description),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = forcePortraitScreensaver,
                    onCheckedChange = withTapHaptic { checked ->
                        forcePortraitScreensaver = checked
                        dreamPrefs.edit()
                            .putBoolean(DreamPreferences.KEY_FORCE_PORTRAIT, checked)
                            .apply()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dim_screensaver_title),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.dim_screensaver_description),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = dimScreensaver,
                    onCheckedChange = withTapHaptic { checked ->
                        dimScreensaver = checked
                        dreamPrefs.edit()
                            .putBoolean(DreamPreferences.KEY_DIM_SCREENSAVER, checked)
                            .apply()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.night_dim_title),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.night_dim_description),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = nightDimScreensaver,
                    onCheckedChange = withTapHaptic { checked ->
                        nightDimScreensaver = checked
                        dreamPrefs.edit()
                            .putBoolean(DreamPreferences.KEY_NIGHT_DIM, checked)
                            .apply()
                    }
                )
            }

            if (nightDimScreensaver) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = withTapHaptic { showNightDimStartPicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.night_dim_start_label,
                                minutesText(nightDimStartMinutes)
                            ),
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = withTapHaptic { showNightDimEndPicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.night_dim_end_label,
                                minutesText(nightDimEndMinutes)
                            ),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = withTapHaptic { openSpecialAccessSettings(Settings.ACTION_DREAM_SETTINGS) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.open_screensaver_settings), fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.screensaver_charging_hint),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Special access — the dot is green when granted, red when missing
        SettingsSectionCard(titleRes = R.string.special_access_section) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AccessStatusDot(granted = runtimePermissionsGranted)
                Spacer(modifier = Modifier.width(10.dp))
                OutlinedButton(
                    onClick = withTapHaptic {
                        val missing = missingRuntimePermissions(context)
                        when {
                            missing.isEmpty() -> Unit
                            context.runtimePermissionsPermanentlyDenied(missing) ->
                                context.openAppDetailsSettings()
                            else -> {
                                context.markRuntimePermissionsRequested()
                                runtimePermissionLauncher.launch(missing)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.onboarding_runtime_permissions_label), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AccessStatusDot(granted = uiState.usageAccessEnabled)
                Spacer(modifier = Modifier.width(10.dp))
                OutlinedButton(
                    onClick = withTapHaptic { openSpecialAccessSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.usage_access_button), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AccessStatusDot(granted = uiState.notificationAccessEnabled)
                Spacer(modifier = Modifier.width(10.dp))
                OutlinedButton(
                    onClick = withTapHaptic {
                        openSpecialAccessSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.notification_access_button), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.notification_access_hint),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = withTapHaptic { openPrivacyDashboard() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.privacy_dashboard_button), fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = withTapHaptic(onShowIntro),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.settings_show_intro), fontSize = 12.sp)
            }
        }

        Text(
            text = stringResource(
                R.string.app_version_line,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
            ),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun AccessStatusDot(granted: Boolean) {
    // Color is the only visual signal, so TalkBack needs the state spelled out.
    val stateText = stringResource(
        if (granted) R.string.access_state_granted else R.string.access_state_missing
    )
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (granted) Color(0xFF4CAF50) else Color(0xFFF44336))
            .semantics { contentDescription = stateText }
    )
}
