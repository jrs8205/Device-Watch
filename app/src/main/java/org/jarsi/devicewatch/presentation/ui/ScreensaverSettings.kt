package org.jarsi.devicewatch.presentation.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.system.DreamPreferences

/** Space between two rows of one group, and between two groups of the section. */
private val ROW_GAP = 16.dp
private val GROUP_GAP = 24.dp

/**
 * The screensaver's settings. They live in the screensaver's own preferences file, which it
 * reads when it starts, so this section keeps its own state rather than going through the
 * dashboard's view model. Groups, top to bottom: shown information, orientation, dimming,
 * movement, start.
 */
@Composable
internal fun ScreensaverSettingsSection(
    notificationAccessEnabled: Boolean,
    onOpenDreamSettings: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(DreamPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    }
    val showDeviceName = rememberDreamSwitch(prefs, DreamPreferences.KEY_SHOW_DEVICE_NAME, true)
    val showNotifications = rememberDreamSwitch(prefs, DreamPreferences.KEY_SHOW_NOTIFICATIONS, true)
    val layoutSwapped = rememberDreamSwitch(prefs, DreamPreferences.KEY_LAYOUT_SWAPPED, false)
    val forcePortrait = rememberDreamSwitch(prefs, DreamPreferences.KEY_FORCE_PORTRAIT, false)
    val dim = rememberDreamSwitch(prefs, DreamPreferences.KEY_DIM_SCREENSAVER, false)
    val nightDim = rememberDreamSwitch(prefs, DreamPreferences.KEY_NIGHT_DIM, false)
    val verticalShiftOnly = rememberDreamSwitch(prefs, DreamPreferences.KEY_VERTICAL_SHIFT_ONLY, false)
    val startOnCharge = rememberDreamSwitch(prefs, DreamPreferences.KEY_START_ON_CHARGE, false)

    var nightDimStartMinutes by remember {
        mutableIntStateOf(
            prefs.getInt(
                DreamPreferences.KEY_NIGHT_DIM_START_MINUTES,
                DreamPreferences.DEFAULT_NIGHT_DIM_START_MINUTES
            )
        )
    }
    var nightDimEndMinutes by remember {
        mutableIntStateOf(
            prefs.getInt(
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
                prefs.edit()
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
                prefs.edit()
                    .putInt(DreamPreferences.KEY_NIGHT_DIM_END_MINUTES, minutes)
                    .apply()
                showNightDimEndPicker = false
            },
            onDismiss = { showNightDimEndPicker = false }
        )
    }

    SettingsSectionCard(titleRes = R.string.screensaver_settings_section) {
        Spacer(modifier = Modifier.height(4.dp))

        // Shown information
        SettingsToggleRow(
            titleRes = R.string.dream_device_name_title,
            descriptionRes = R.string.dream_device_name_description,
            checked = showDeviceName.checked,
            onCheckedChange = showDeviceName::set,
        )

        Spacer(modifier = Modifier.height(ROW_GAP))

        SettingsToggleRow(
            titleRes = R.string.dream_notifications_title,
            descriptionRes = R.string.dream_notifications_description,
            checked = showNotifications.checked,
            onCheckedChange = showNotifications::set,
        )

        if (showNotifications.checked && !notificationAccessEnabled) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.dream_notifications_access_missing),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(GROUP_GAP))

        // Orientation
        SettingsToggleRow(
            titleRes = R.string.dream_flip_title,
            descriptionRes = R.string.dream_flip_description,
            checked = layoutSwapped.checked,
            onCheckedChange = layoutSwapped::set,
        )

        Spacer(modifier = Modifier.height(ROW_GAP))

        SettingsToggleRow(
            titleRes = R.string.force_portrait_title,
            descriptionRes = R.string.force_portrait_description,
            checked = forcePortrait.checked,
            onCheckedChange = forcePortrait::set,
        )

        Spacer(modifier = Modifier.height(GROUP_GAP))

        // Dimming
        SettingsToggleRow(
            titleRes = R.string.dim_screensaver_title,
            descriptionRes = R.string.dim_screensaver_description,
            checked = dim.checked,
            onCheckedChange = dim::set,
        )

        Spacer(modifier = Modifier.height(ROW_GAP))

        SettingsToggleRow(
            titleRes = R.string.night_dim_title,
            descriptionRes = R.string.night_dim_description,
            checked = nightDim.checked,
            onCheckedChange = nightDim::set,
        )

        if (nightDim.checked) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = withTapHaptic { showNightDimStartPicker = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        text = stringResource(
                            R.string.night_dim_end_label,
                            minutesText(nightDimEndMinutes)
                        ),
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(GROUP_GAP))

        // Movement
        SettingsToggleRow(
            titleRes = R.string.dream_vertical_shift_title,
            descriptionRes = R.string.dream_vertical_shift_description,
            checked = verticalShiftOnly.checked,
            onCheckedChange = verticalShiftOnly::set,
        )

        Spacer(modifier = Modifier.height(GROUP_GAP))

        // Start
        SettingsToggleRow(
            titleRes = R.string.dream_start_on_charge_title,
            descriptionRes = R.string.dream_start_on_charge_description,
            checked = startOnCharge.checked,
            onCheckedChange = startOnCharge::set,
        )

        Spacer(modifier = Modifier.height(ROW_GAP))

        OutlinedButton(
            onClick = withTapHaptic(onOpenDreamSettings),
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
}

/** One on/off setting of the screensaver: shown from the file and written straight back. */
private class DreamSwitch(
    private val prefs: SharedPreferences,
    private val key: String,
    initial: Boolean,
) {
    var checked by mutableStateOf(initial)
        private set

    fun set(value: Boolean) {
        checked = value
        prefs.edit().putBoolean(key, value).apply()
    }
}

@Composable
private fun rememberDreamSwitch(prefs: SharedPreferences, key: String, default: Boolean): DreamSwitch =
    remember(prefs, key) { DreamSwitch(prefs, key, prefs.getBoolean(key, default)) }
