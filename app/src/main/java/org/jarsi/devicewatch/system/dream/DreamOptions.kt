package org.jarsi.devicewatch.system.dream

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import org.jarsi.devicewatch.system.DreamPreferences

/**
 * The colour of the clock's seconds. A ready-made handful rather than a colour picker:
 * each one sits well beside the battery's colours. [id] is what the settings store.
 */
internal enum class DreamAccent(val id: String, val color: Color) {
    SKY("sky", Color(0xFF38BDF8)),
    TEAL("teal", Color(0xFF2DD4BF)),
    VIOLET("violet", Color(0xFFA78BFA)),
    ROSE("rose", Color(0xFFF472B6)),
    NEUTRAL("neutral", Color(0xFFE5E7EB)),
}

internal fun dreamAccentOf(id: String?): DreamAccent =
    DreamAccent.entries.firstOrNull { it.id == id } ?: DreamAccent.SKY

/**
 * What the user chose for the screensaver, read once when it starts. The settings preview
 * builds the same object from the settings page's state.
 */
internal data class DreamOptions(
    val style: DreamStyle = DreamStyle.GLOW,
    val layoutSwapped: Boolean = false,
    val verticalShiftOnly: Boolean = false,
    val showDeviceName: Boolean = true,
    val showNotifications: Boolean = true,
    val showSeconds: Boolean = true,
    val showDate: Boolean = true,
    val showAlarm: Boolean = true,
    /** Volts, temperature and charging power, as far as the style has them. */
    val showBatteryDetails: Boolean = true,
    /** When the battery is expected to be full. */
    val showEstimate: Boolean = true,
    val accent: DreamAccent = DreamAccent.SKY,
    /** The next calendar events on the Gauges style; off until the user switches it on. */
    val showCalendar: Boolean = false,
    /** Whether a calendar row shows the event's title or only when it is. */
    val showCalendarTitles: Boolean = true,
    /** Switch to the Night style for the hours of the night dim. */
    val nightStyleAuto: Boolean = false,
    /** The screensaver's own screen brightness in per cent; null keeps the phone's. */
    val brightnessPercent: Int? = null,
) {
    /** Whether the field is drawn: the style has a place for it and its switch is on. */
    fun shows(field: DreamField): Boolean = style.supports(field) && when (field) {
        DreamField.DEVICE_NAME -> showDeviceName
        DreamField.SECONDS -> showSeconds
        DreamField.DATE -> showDate
        DreamField.ALARM -> showAlarm
        DreamField.NOTIFICATIONS -> showNotifications
        DreamField.BATTERY_DETAILS -> showBatteryDetails
        DreamField.ESTIMATE -> showEstimate
        DreamField.CALENDAR -> showCalendar
    }
}

/**
 * Reads the options. The settings file travels in Android's backup, so a value may be
 * missing or of another type; each then falls back to its default instead of stopping
 * the screensaver.
 */
internal fun readDreamOptions(prefs: SharedPreferences): DreamOptions {
    val defaults = DreamOptions()
    return DreamOptions(
        style = dreamStyleOf(prefs.stringOrNull(DreamPreferences.KEY_STYLE)),
        layoutSwapped = prefs.booleanOr(DreamPreferences.KEY_LAYOUT_SWAPPED, defaults.layoutSwapped),
        verticalShiftOnly = prefs.booleanOr(DreamPreferences.KEY_VERTICAL_SHIFT_ONLY, defaults.verticalShiftOnly),
        showDeviceName = prefs.booleanOr(DreamPreferences.KEY_SHOW_DEVICE_NAME, defaults.showDeviceName),
        showNotifications = prefs.booleanOr(DreamPreferences.KEY_SHOW_NOTIFICATIONS, defaults.showNotifications),
        showSeconds = prefs.booleanOr(DreamPreferences.KEY_SHOW_SECONDS, defaults.showSeconds),
        showDate = prefs.booleanOr(DreamPreferences.KEY_SHOW_DATE, defaults.showDate),
        showAlarm = prefs.booleanOr(DreamPreferences.KEY_SHOW_ALARM, defaults.showAlarm),
        showBatteryDetails = prefs.booleanOr(DreamPreferences.KEY_SHOW_BATTERY_DETAILS, defaults.showBatteryDetails),
        showEstimate = prefs.booleanOr(DreamPreferences.KEY_SHOW_ESTIMATE, defaults.showEstimate),
        accent = dreamAccentOf(prefs.stringOrNull(DreamPreferences.KEY_ACCENT)),
        showCalendar = prefs.booleanOr(DreamPreferences.KEY_SHOW_CALENDAR, defaults.showCalendar),
        showCalendarTitles = prefs.booleanOr(DreamPreferences.KEY_SHOW_CALENDAR_TITLES, defaults.showCalendarTitles),
        nightStyleAuto = prefs.booleanOr(DreamPreferences.KEY_NIGHT_STYLE_AUTO, defaults.nightStyleAuto),
        brightnessPercent = if (prefs.booleanOr(DreamPreferences.KEY_OWN_BRIGHTNESS, false)) {
            storedBrightnessPercent(prefs)
        } else {
            null
        },
    )
}

/**
 * The stored brightness level, 1..100, whether or not the screensaver's own brightness is
 * switched on: the settings page shows it either way. Missing or of another type, it is
 * the default.
 */
internal fun storedBrightnessPercent(prefs: SharedPreferences): Int {
    val stored = try {
        prefs.getInt(DreamPreferences.KEY_BRIGHTNESS_PERCENT, DreamPreferences.DEFAULT_BRIGHTNESS_PERCENT)
    } catch (_: ClassCastException) {
        DreamPreferences.DEFAULT_BRIGHTNESS_PERCENT
    }
    return stored.coerceIn(1, 100)
}

private fun SharedPreferences.booleanOr(key: String, default: Boolean): Boolean =
    try {
        getBoolean(key, default)
    } catch (_: ClassCastException) {
        default
    }

private fun SharedPreferences.stringOrNull(key: String): String? =
    try {
        getString(key, null)
    } catch (_: ClassCastException) {
        null
    }
