package org.jarsi.devicewatch.system.dream

import android.content.SharedPreferences
import org.jarsi.devicewatch.system.DreamPreferences

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
) {
    /** Whether the field is drawn: the style has a place for it and its switch is on. */
    fun shows(field: DreamField): Boolean = style.supports(field) && when (field) {
        DreamField.DEVICE_NAME -> showDeviceName
        DreamField.NOTIFICATIONS -> showNotifications
        else -> true
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
    )
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
