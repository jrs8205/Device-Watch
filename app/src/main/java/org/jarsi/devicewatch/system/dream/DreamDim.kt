package org.jarsi.devicewatch.system.dream

import android.content.SharedPreferences
import org.jarsi.devicewatch.system.DreamPreferences
import java.util.Calendar

/** Minutes since local midnight, for the night-dim window check. */
internal fun currentMinuteOfDay(): Int {
    val calendar = Calendar.getInstance()
    return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
}

/**
 * Effective dim state: the manual dim switch, or the automatic night window when enabled.
 * Pure apart from the SharedPreferences reads so the window logic itself is unit-testable
 * via [isInNightWindow].
 */
internal fun shouldDimNow(prefs: SharedPreferences, nowMinutes: Int): Boolean =
    prefs.getBoolean(DreamPreferences.KEY_DIM_SCREENSAVER, false) || nightWindowActive(prefs, nowMinutes)

/** Whether the automatic night dim is switched on and [nowMinutes] falls inside its window. */
internal fun nightWindowActive(prefs: SharedPreferences, nowMinutes: Int): Boolean {
    if (!prefs.getBoolean(DreamPreferences.KEY_NIGHT_DIM, false)) return false
    return isInNightWindow(
        nowMinutes,
        prefs.getInt(
            DreamPreferences.KEY_NIGHT_DIM_START_MINUTES,
            DreamPreferences.DEFAULT_NIGHT_DIM_START_MINUTES
        ),
        prefs.getInt(
            DreamPreferences.KEY_NIGHT_DIM_END_MINUTES,
            DreamPreferences.DEFAULT_NIGHT_DIM_END_MINUTES
        )
    )
}

/**
 * Whether [nowMinutes] (minutes since midnight) falls inside the window from [startMinutes]
 * (inclusive) to [endMinutes] (exclusive). A window that crosses midnight (e.g. 22:00–07:00)
 * is handled; an empty window (start == end) never matches.
 */
internal fun isInNightWindow(nowMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean {
    if (startMinutes == endMinutes) return false
    return if (startMinutes < endMinutes) {
        nowMinutes >= startMinutes && nowMinutes < endMinutes
    } else {
        nowMinutes >= startMinutes || nowMinutes < endMinutes
    }
}

/**
 * The style to draw right now. With the automatic Night style switched on, the chosen style
 * gives way to Night for the hours of the night dim and comes back when they end.
 */
internal fun effectiveDreamStyle(
    selected: DreamStyle,
    nightStyleAuto: Boolean,
    nightWindowActive: Boolean,
): DreamStyle = if (nightStyleAuto && nightWindowActive) DreamStyle.NIGHT else selected
