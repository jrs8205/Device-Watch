package org.jarsi.devicewatch.system.dream

import android.content.SharedPreferences
import android.content.res.Configuration
import android.service.dreams.DreamService
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration

/**
 * The running screensaver's content: the collected state drawn in the chosen style.
 * [options] were read once when the screensaver started; only the dim settings in [prefs]
 * are read again, once a minute. The same check decides when the Night style takes over.
 */
@Composable
internal fun DreamHost(service: DreamService, prefs: SharedPreferences, options: DreamOptions) {
    // Whether the night dim's hours are on: checked when the screensaver starts and then
    // once a minute, like the dimming itself.
    var nightNow by remember { mutableStateOf(nightWindowActive(prefs, currentMinuteOfDay())) }
    val state = rememberDreamUiState(service, options) { minuteOfDay ->
        val dimNow = shouldDimNow(prefs, minuteOfDay)
        service.setScreenBright(!dimNow)
        service.window?.setDreamBrightness(dreamWindowBrightness(options, dimNow))
        nightNow = nightWindowActive(prefs, minuteOfDay)
    }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    DreamScreen(
        state = state,
        options = options.copy(style = effectiveDreamStyle(options.style, options.nightStyleAuto, nightNow)),
        landscape = landscape,
        onTap = { service.finish() },
    )
}
