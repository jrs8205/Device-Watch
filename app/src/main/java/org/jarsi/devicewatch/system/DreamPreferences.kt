package org.jarsi.devicewatch.system

/**
 * Shared SharedPreferences keys for the screensaver (DreamService). Stored in a dedicated
 * prefs file so [MonitorDreamService] can read them synchronously while it attaches its
 * window, and the settings screen can read/write the same values.
 */
object DreamPreferences {
    const val PREFS_NAME = "monitor_dream_preferences"
    // The chosen style's id (DreamStyle.id); missing or unknown means the Glow style.
    const val KEY_STYLE = "dream_style"
    const val KEY_LAYOUT_SWAPPED = "layout_swapped"
    const val KEY_FORCE_PORTRAIT = "force_portrait"
    const val KEY_DIM_SCREENSAVER = "dim_screensaver"

    // Icons of the unread notifications under the clock (needs notification access).
    const val KEY_SHOW_NOTIFICATIONS = "show_notifications"

    // GitHub #6: the burn-in shift moves the content only up and down, never sideways.
    const val KEY_VERTICAL_SHIFT_ONLY = "vertical_shift_only"

    // The phone's name (Android's device name) on a small line above the clock.
    const val KEY_SHOW_DEVICE_NAME = "show_device_name"

    // What else the screensaver shows, as far as the chosen style has a place for it.
    const val KEY_SHOW_SECONDS = "show_seconds"
    const val KEY_SHOW_DATE = "show_date"
    const val KEY_SHOW_ALARM = "show_alarm"
    const val KEY_SHOW_BATTERY_DETAILS = "show_battery_details"
    const val KEY_SHOW_ESTIMATE = "show_estimate"

    // The colour of the clock's seconds (DreamAccent.id); missing or unknown means sky blue.
    const val KEY_ACCENT = "accent_color"

    // Start the screensaver as soon as charging begins on a phone that is not in use.
    const val KEY_START_ON_CHARGE = "start_on_charge"

    // Automatic night dim: dim the screensaver between the selected times of day.
    const val KEY_NIGHT_DIM = "night_dim"
    const val KEY_NIGHT_DIM_START_MINUTES = "night_dim_start_minutes"
    const val KEY_NIGHT_DIM_END_MINUTES = "night_dim_end_minutes"
    // Draw the Night style instead of the chosen one for the hours of the night dim.
    const val KEY_NIGHT_STYLE_AUTO = "night_style_auto"
    const val DEFAULT_NIGHT_DIM_START_MINUTES = 22 * 60
    const val DEFAULT_NIGHT_DIM_END_MINUTES = 7 * 60
}
