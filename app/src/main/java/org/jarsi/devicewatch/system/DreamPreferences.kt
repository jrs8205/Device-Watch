package org.jarsi.devicewatch.system

/**
 * Shared SharedPreferences keys for the screensaver (DreamService). Stored in a dedicated
 * prefs file so [MonitorDreamService] can read them synchronously while it attaches its
 * window, and the settings screen can read/write the same values.
 */
object DreamPreferences {
    const val PREFS_NAME = "monitor_dream_preferences"
    const val KEY_LAYOUT_SWAPPED = "layout_swapped"
    const val KEY_FORCE_PORTRAIT = "force_portrait"
    const val KEY_DIM_SCREENSAVER = "dim_screensaver"

    // Automatic night dim: dim the screensaver between the selected times of day.
    const val KEY_NIGHT_DIM = "night_dim"
    const val KEY_NIGHT_DIM_START_MINUTES = "night_dim_start_minutes"
    const val KEY_NIGHT_DIM_END_MINUTES = "night_dim_end_minutes"
    const val DEFAULT_NIGHT_DIM_START_MINUTES = 22 * 60
    const val DEFAULT_NIGHT_DIM_END_MINUTES = 7 * 60
}
