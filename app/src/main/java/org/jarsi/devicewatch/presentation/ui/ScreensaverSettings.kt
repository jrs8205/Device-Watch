package org.jarsi.devicewatch.presentation.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.DeviceLocalFlags
import org.jarsi.devicewatch.system.DreamPreferences
import org.jarsi.devicewatch.system.dream.DreamAccent
import org.jarsi.devicewatch.system.dream.DreamField
import org.jarsi.devicewatch.system.dream.DreamOptions
import org.jarsi.devicewatch.system.dream.DreamPreview
import org.jarsi.devicewatch.system.dream.DreamStyle
import org.jarsi.devicewatch.system.dream.dreamBrightnessOf
import org.jarsi.devicewatch.system.dream.readDreamOptions
import org.jarsi.devicewatch.system.dream.rememberSampleDreamUiState
import org.jarsi.devicewatch.system.dream.storedBrightnessPercent
import org.jarsi.devicewatch.system.dream.supports
import kotlin.math.roundToInt

/** Space between two rows of one group, and between two groups of the section. */
private val ROW_GAP = 16.dp
private val GROUP_GAP = 24.dp

/**
 * The screensaver's settings. They live in the screensaver's own preferences file, which it
 * reads when it starts, so this section keeps its own state rather than going through the
 * dashboard's view model. Groups, top to bottom: the style, the chosen style's own options,
 * shown information, the accent colour, orientation, dimming, movement, start.
 */
@Composable
internal fun ScreensaverSettingsSection(
    notificationAccessEnabled: Boolean,
    onOpenDreamSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(DreamPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    }
    val showDeviceName = rememberDreamSwitch(prefs, DreamPreferences.KEY_SHOW_DEVICE_NAME, true)
    val showNotifications = rememberDreamSwitch(prefs, DreamPreferences.KEY_SHOW_NOTIFICATIONS, true)
    val showSeconds = rememberDreamSwitch(prefs, DreamPreferences.KEY_SHOW_SECONDS, true)
    val showDate = rememberDreamSwitch(prefs, DreamPreferences.KEY_SHOW_DATE, true)
    val showAlarm = rememberDreamSwitch(prefs, DreamPreferences.KEY_SHOW_ALARM, true)
    val showBatteryDetails = rememberDreamSwitch(prefs, DreamPreferences.KEY_SHOW_BATTERY_DETAILS, true)
    val showEstimate = rememberDreamSwitch(prefs, DreamPreferences.KEY_SHOW_ESTIMATE, true)
    val layoutSwapped = rememberDreamSwitch(prefs, DreamPreferences.KEY_LAYOUT_SWAPPED, false)
    val forcePortrait = rememberDreamSwitch(prefs, DreamPreferences.KEY_FORCE_PORTRAIT, false)
    val dim = rememberDreamSwitch(prefs, DreamPreferences.KEY_DIM_SCREENSAVER, false)
    val nightDim = rememberDreamSwitch(prefs, DreamPreferences.KEY_NIGHT_DIM, false)
    val nightStyleAuto = rememberDreamSwitch(prefs, DreamPreferences.KEY_NIGHT_STYLE_AUTO, false)
    val verticalShiftOnly = rememberDreamSwitch(prefs, DreamPreferences.KEY_VERTICAL_SHIFT_ONLY, false)
    val startOnCharge = rememberDreamSwitch(prefs, DreamPreferences.KEY_START_ON_CHARGE, false)
    val showCalendar = rememberDreamSwitch(prefs, DreamPreferences.KEY_SHOW_CALENDAR, false)
    val showCalendarTitles = rememberDreamSwitch(prefs, DreamPreferences.KEY_SHOW_CALENDAR_TITLES, true)
    val ownBrightness = rememberDreamSwitch(prefs, DreamPreferences.KEY_OWN_BRIGHTNESS, false)
    var brightnessPercent by remember { mutableIntStateOf(storedBrightnessPercent(prefs)) }
    // While the brightness slider moves, this page itself is shown at the chosen level:
    // there is no other way to judge a brightness than to see it.
    val pageWindow = remember(context) { (context as? Activity)?.window }

    // The calendar permission is asked for here and nowhere else, and only when the calendar
    // rows are switched on. It can also be granted or taken away on Android's own page, so
    // it is read again on every return.
    var calendarGranted by remember { mutableStateOf(hasCalendarPermission(context)) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        calendarGranted = hasCalendarPermission(context)
    }
    LifecycleResumeEffect(Unit) {
        calendarGranted = hasCalendarPermission(context)
        onPauseOrDispose { }
    }

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

    val stored = remember(prefs) { readDreamOptions(prefs) }
    var style by remember { mutableStateOf(stored.style) }
    var accent by remember { mutableStateOf(stored.accent) }
    // What the thumbnails draw with: the same options the screensaver would read right now.
    val previewOptions = DreamOptions(
        style = style,
        verticalShiftOnly = verticalShiftOnly.checked,
        showDeviceName = showDeviceName.checked,
        showNotifications = showNotifications.checked,
        showSeconds = showSeconds.checked,
        showDate = showDate.checked,
        showAlarm = showAlarm.checked,
        showBatteryDetails = showBatteryDetails.checked,
        showEstimate = showEstimate.checked,
        accent = accent,
        showCalendar = showCalendar.checked,
        showCalendarTitles = showCalendarTitles.checked,
    )

    SettingsSectionCard(titleRes = R.string.screensaver_settings_section, modifier = modifier) {
        Spacer(modifier = Modifier.height(4.dp))

        DreamStylePicker(
            selected = style,
            options = previewOptions,
            onSelect = { chosen ->
                style = chosen
                prefs.edit().putString(DreamPreferences.KEY_STYLE, chosen.id).apply()
            },
        )

        // The chosen style's own options.
        if (style == DreamStyle.GAUGES) {
            Spacer(modifier = Modifier.height(GROUP_GAP))

            SettingsToggleRow(
                titleRes = R.string.dream_calendar_title,
                descriptionRes = R.string.dream_calendar_description,
                checked = showCalendar.checked,
                onCheckedChange = { checked ->
                    showCalendar.set(checked)
                    if (checked && !hasCalendarPermission(context)) {
                        if (calendarPermissionDeniedForGood(context)) {
                            // Android no longer shows its dialog: only its own page can grant it.
                            context.openAppDetailsSettings()
                        } else {
                            markCalendarPermissionRequested(context)
                            calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                        }
                    }
                },
            )

            if (showCalendar.checked && !calendarGranted) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.dream_calendar_permission_missing),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(ROW_GAP))

            SettingsToggleRow(
                titleRes = R.string.dream_calendar_titles_title,
                descriptionRes = R.string.dream_calendar_titles_description,
                checked = showCalendarTitles.checked,
                onCheckedChange = showCalendarTitles::set,
                enabled = showCalendar.checked,
            )
        }

        Spacer(modifier = Modifier.height(GROUP_GAP))

        // Shown information. A switch for something the chosen style has no place for is
        // dimmed: it keeps its value for the styles that do.
        SettingsToggleRow(
            titleRes = R.string.dream_device_name_title,
            descriptionRes = R.string.dream_device_name_description,
            checked = showDeviceName.checked,
            onCheckedChange = showDeviceName::set,
            enabled = style.supports(DreamField.DEVICE_NAME),
        )

        Spacer(modifier = Modifier.height(ROW_GAP))

        SettingsToggleRow(
            titleRes = R.string.dream_show_seconds,
            descriptionRes = null,
            checked = showSeconds.checked,
            onCheckedChange = showSeconds::set,
            enabled = style.supports(DreamField.SECONDS),
        )

        Spacer(modifier = Modifier.height(ROW_GAP))

        SettingsToggleRow(
            titleRes = R.string.dream_show_date,
            descriptionRes = null,
            checked = showDate.checked,
            onCheckedChange = showDate::set,
            enabled = style.supports(DreamField.DATE),
        )

        Spacer(modifier = Modifier.height(ROW_GAP))

        SettingsToggleRow(
            titleRes = R.string.dream_show_alarm,
            descriptionRes = null,
            checked = showAlarm.checked,
            onCheckedChange = showAlarm::set,
            enabled = style.supports(DreamField.ALARM),
        )

        Spacer(modifier = Modifier.height(ROW_GAP))

        SettingsToggleRow(
            titleRes = R.string.dream_show_battery_details,
            descriptionRes = null,
            checked = showBatteryDetails.checked,
            onCheckedChange = showBatteryDetails::set,
            enabled = style.supports(DreamField.BATTERY_DETAILS),
        )

        Spacer(modifier = Modifier.height(ROW_GAP))

        SettingsToggleRow(
            titleRes = R.string.dream_show_estimate,
            descriptionRes = null,
            checked = showEstimate.checked,
            onCheckedChange = showEstimate::set,
            enabled = style.supports(DreamField.ESTIMATE),
        )

        Spacer(modifier = Modifier.height(ROW_GAP))

        SettingsToggleRow(
            titleRes = R.string.dream_notifications_title,
            descriptionRes = R.string.dream_notifications_description,
            checked = showNotifications.checked,
            onCheckedChange = showNotifications::set,
            enabled = style.supports(DreamField.NOTIFICATIONS),
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

        // Accent colour: the colour of the seconds, so it has a use only where they show.
        DreamAccentPicker(
            selected = accent,
            enabled = style.supports(DreamField.SECONDS) && showSeconds.checked,
            onSelect = { chosen ->
                accent = chosen
                prefs.edit().putString(DreamPreferences.KEY_ACCENT, chosen.id).apply()
            },
        )

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

        // Brightness and dimming
        SettingsToggleRow(
            titleRes = R.string.dream_brightness_title,
            descriptionRes = R.string.dream_brightness_description,
            checked = ownBrightness.checked,
            onCheckedChange = ownBrightness::set,
        )

        if (ownBrightness.checked) {
            // The slider can go away in the middle of a move, with no word of the move having
            // ended: the switch above turned off with another finger, or the page closed.
            // Whenever it goes, the page gives the phone's brightness back, and a level that
            // was never stored is forgotten, so the slider comes back where the screensaver's
            // level is.
            DisposableEffect(pageWindow) {
                onDispose {
                    pageWindow?.showBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                    brightnessPercent = storedBrightnessPercent(prefs)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                text = stringResource(R.string.dream_brightness_level, brightnessPercent),
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Slider(
                value = brightnessPercent.toFloat(),
                onValueChange = { value ->
                    brightnessPercent = value.roundToInt()
                    pageWindow?.showBrightness(dreamBrightnessOf(brightnessPercent))
                },
                onValueChangeFinished = withTapHaptic {
                    prefs.edit().putInt(DreamPreferences.KEY_BRIGHTNESS_PERCENT, brightnessPercent).apply()
                    pageWindow?.showBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                },
                valueRange = 1f..100f,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.dream_brightness_hint),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(ROW_GAP))

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

/** Whether the calendar permission has been asked for on this phone; not backed up. */
/** Shows the settings page itself at [brightness], or at the phone's own again. */
private fun Window.showBrightness(brightness: Float) {
    attributes = attributes.apply { screenBrightness = brightness }
}

private const val KEY_CALENDAR_PERMISSION_REQUESTED = "calendar_permission_requested"

private fun hasCalendarPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

private fun markCalendarPermissionRequested(context: Context) {
    DeviceLocalFlags.prefs(context).edit().putBoolean(KEY_CALENDAR_PERMISSION_REQUESTED, true).apply()
}

/**
 * True when Android will not show its permission dialog any more: it has been asked for
 * before and there is no rationale to show. Then only the app's page in Android's settings
 * can grant it.
 */
private fun calendarPermissionDeniedForGood(context: Context): Boolean {
    val activity = context as? Activity ?: return false
    val askedBefore = DeviceLocalFlags.prefs(context).getBoolean(KEY_CALENDAR_PERMISSION_REQUESTED, false)
    return askedBefore && !activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)
}

/** Size of one swatch in the accent row: also its touch target. */
private val ACCENT_SWATCH_SIZE = 40.dp

/**
 * The accent colours as a row of round swatches; the chosen one has a ring round it. Each
 * swatch is named by its colour for a screen reader. The whole row is dimmed when the
 * accent has nothing to colour.
 */
@Composable
private fun DreamAccentPicker(
    selected: DreamAccent,
    enabled: Boolean,
    onSelect: (DreamAccent) -> Unit,
) {
    Text(
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        text = stringResource(R.string.dream_accent_title),
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(10.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .alpha(if (enabled) 1f else 0.38f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (accent in DreamAccent.entries) {
            val isSelected = accent == selected
            val name = stringResource(dreamAccentNameRes(accent))
            Box(
                modifier = Modifier
                    .size(ACCENT_SWATCH_SIZE)
                    .clip(CircleShape)
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = withTapHaptic { onSelect(accent) },
                    )
                    .semantics { contentDescription = name }
                    .border(
                        width = 2.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = CircleShape,
                    )
                    .padding(5.dp)
                    .background(accent.color, CircleShape),
            )
        }
    }
}

@StringRes
private fun dreamAccentNameRes(accent: DreamAccent): Int = when (accent) {
    DreamAccent.SKY -> R.string.dream_accent_sky
    DreamAccent.TEAL -> R.string.dream_accent_teal
    DreamAccent.VIOLET -> R.string.dream_accent_violet
    DreamAccent.ROSE -> R.string.dream_accent_rose
    DreamAccent.NEUTRAL -> R.string.dream_accent_neutral
}

@StringRes
private fun dreamStyleNameRes(style: DreamStyle): Int = when (style) {
    DreamStyle.GLOW -> R.string.dream_style_glow
    DreamStyle.MINIMAL -> R.string.dream_style_minimal
    DreamStyle.NIGHT -> R.string.dream_style_night
    DreamStyle.RING -> R.string.dream_style_ring
    DreamStyle.GAUGES -> R.string.dream_style_gauges
    DreamStyle.EDGE -> R.string.dream_style_edge
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

/**
 * The switch for [key]. The settings file travels in Android's backup, so the stored value
 * may be of another type: it then reads as [default], as it does in the screensaver, and
 * using the switch writes a proper value over it.
 */
@Composable
private fun rememberDreamSwitch(prefs: SharedPreferences, key: String, default: Boolean): DreamSwitch =
    remember(prefs, key) {
        val stored = try {
            prefs.getBoolean(key, default)
        } catch (_: ClassCastException) {
            default
        }
        DreamSwitch(prefs, key, stored)
    }
