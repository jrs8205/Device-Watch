package org.jarsi.devicewatch.system.dream

import android.content.SharedPreferences
import android.content.res.Configuration
import android.service.dreams.DreamService
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * The running screensaver's content: the collected state drawn in the chosen style.
 * [options] were read once when the screensaver started; only the dim settings in [prefs]
 * are read again, once a minute.
 */
@Composable
internal fun DreamHost(service: DreamService, prefs: SharedPreferences, options: DreamOptions) {
    val state = rememberDreamUiState(service, options) { minuteOfDay ->
        service.setScreenBright(!shouldDimNow(prefs, minuteOfDay))
    }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    DreamScreen(
        state = state,
        options = options,
        landscape = landscape,
        onTap = { service.finish() },
    )
}
