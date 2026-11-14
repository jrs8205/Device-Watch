package org.jarsi.devicewatch.presentation.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.system.DreamPreferences
import org.jarsi.devicewatch.system.dream.DreamOptions
import org.jarsi.devicewatch.system.dream.DreamPreview
import org.jarsi.devicewatch.system.dream.DreamStyle
import org.jarsi.devicewatch.system.dream.readDreamOptions
import org.jarsi.devicewatch.system.dream.rememberSampleDreamUiState

/** Space between two rows of one group, and between two groups of the section. */
private val ROW_GAP = 16.dp
private val GROUP_GAP = 24.dp

/**
 * The screensaver's settings. They live in the screensaver's own preferences file, which it
 * reads when it starts, so this section keeps its own state rather than going through the
 * dashboard's view model. Groups, top to bottom: the style, shown information, orientation,
 * dimming, movement, start.
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
    val nightStyleAuto = rememberDreamSwitch(prefs, DreamPreferences.KEY_NIGHT_STYLE_AUTO, false)
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

    var style by remember { mutableStateOf(readDreamOptions(prefs).style) }
    // What the thumbnails draw with: the same options the screensaver would read right now.
    val previewOptions = DreamOptions(
        style = style,
        verticalShiftOnly = verticalShiftOnly.checked,
        showDeviceName = showDeviceName.checked,
        showNotifications = showNotifications.checked,
    )

    SettingsSectionCard(titleRes = R.string.screensaver_settings_section) {
        Spacer(modifier = Modifier.height(4.dp))

        DreamStylePicker(
            selected = style,
            options = previewOptions,
            onSelect = { chosen ->
                style = chosen
                prefs.edit().putString(DreamPreferences.KEY_STYLE, chosen.id).apply()
            },
        )

        Spacer(modifier = Modifier.height(GROUP_GAP))

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

        Spacer(modifier = Modifier.height(ROW_GAP))

        // The Night style follows the night dim's hours, so it needs the night dim.
        SettingsToggleRow(
            titleRes = R.string.dream_night_style_title,
            descriptionRes = R.string.dream_night_style_description,
            checked = nightStyleAuto.checked,
            onCheckedChange = nightStyleAuto::set,
            enabled = nightDim.checked,
        )

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

/** Width of one thumbnail in the style picker. */
private val STYLE_THUMBNAIL_WIDTH = 112.dp
private val STYLE_THUMBNAIL_SHAPE = RoundedCornerShape(12.dp)

/**
 * The styles side by side, each as a thumbnail drawn by the screensaver's own code with its
 * name under it; the chosen one is framed. The row scrolls sideways when it does not fit.
 */
@Composable
private fun DreamStylePicker(
    selected: DreamStyle,
    options: DreamOptions,
    onSelect: (DreamStyle) -> Unit,
) {
    val sample = rememberSampleDreamUiState()
    Text(
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        text = stringResource(R.string.dream_style_title),
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(10.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (style in DreamStyle.entries) {
            val isSelected = style == selected
            Column(
                modifier = Modifier
                    .width(STYLE_THUMBNAIL_WIDTH)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = withTapHaptic { onSelect(style) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DreamPreview(
                    style = style,
                    options = options,
                    state = sample,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(STYLE_THUMBNAIL_SHAPE)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = STYLE_THUMBNAIL_SHAPE,
                        ),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    text = stringResource(dreamStyleNameRes(style)),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@StringRes
private fun dreamStyleNameRes(style: DreamStyle): Int = when (style) {
    DreamStyle.GLOW -> R.string.dream_style_glow
    DreamStyle.MINIMAL -> R.string.dream_style_minimal
    DreamStyle.NIGHT -> R.string.dream_style_night
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
