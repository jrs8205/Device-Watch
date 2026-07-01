package com.example.modernwidget.system

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
}
