package org.jarsi.devicewatch.system

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.jarsi.devicewatch.data.AppSettingsRepository
import org.jarsi.devicewatch.data.PrivilegedAccess
import org.jarsi.devicewatch.data.PrivilegedShell

/**
 * Brings the screensaver up as soon as charging begins on a phone whose screen is off, when
 * the user asked for that (see [chargeStartAction] for why Android would otherwise wait).
 */
class ChargeStartScreensaver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSettings: AppSettingsRepository,
    private val shell: PrivilegedShell,
) {

    private val ourDream: String =
        ComponentName(context, MonitorDreamService::class.java).flattenToString()

    /** Call off the main thread: the extended-access route waits on the shell. */
    fun onPowerConnected() {
        val prefs = context.getSharedPreferences(DreamPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(DreamPreferences.KEY_START_ON_CHARGE, false)) return
        val power = context.getSystemService(PowerManager::class.java) ?: return
        // Interactive also covers a screensaver Android is just starting by itself.
        fun busy() = power.isInteractive || MonitorDreamService.isDreaming
        // Before the shell is touched at all: a busy shell can take seconds to answer.
        if (busy()) return

        val shellSelection = if (appSettings.privilegedAccess() != PrivilegedAccess.OFF) {
            shell.run(READ_SELECTION)?.lines()?.let { lines ->
                chargeDreamSelection(lines.getOrNull(0), lines.getOrNull(1), lines.getOrNull(2), ourDream)
            }
        } else {
            null
        }

        // Read again after the shell: someone may have picked the phone up meanwhile.
        val action = chargeStartAction(
            enabled = true,
            screenOn = power.isInteractive,
            dreamShowing = MonitorDreamService.isDreaming,
            selection = shellSelection ?: readSelectionDirectly(),
            // A shell that did not answer leaves the ordinary route.
            privileged = shellSelection != null,
        )
        when (action) {
            ChargeStartAction.NONE -> Unit
            // The command checks the screen again itself, once it has its turn in the shell.
            ChargeStartAction.START_DREAM -> {
                val outcome = dreamCallOutcome(shell.run(startDreamCommand(Build.VERSION.SDK_INT), START_TIMEOUT_MILLIS))
                if (outcome == DreamCallOutcome.FAILED && !busy()) wakeScreen(power)
            }
            ChargeStartAction.WAKE_SCREEN -> wakeScreen(power)
        }
    }

    /** Uncertain when Android does not let an ordinary app read these settings. */
    private fun readSelectionDirectly(): ChargeDream = try {
        val resolver = context.contentResolver
        chargeDreamSelection(
            Settings.Secure.getString(resolver, SCREENSAVER_ENABLED),
            Settings.Secure.getString(resolver, SCREENSAVER_ACTIVATE_ON_SLEEP),
            Settings.Secure.getString(resolver, SCREENSAVER_COMPONENTS),
            ourDream,
        )
    } catch (_: SecurityException) {
        ChargeDream.UNKNOWN
    }

    /**
     * A screen-on wake lock that turns the screen on is the one wake an app has. Android 14
     * prepared a TURN_SCREEN_ON permission for it, but leaves it unenforced (the change is
     * enabled only from target SDK 10000). Held a moment only: the lock screen's own short
     * timeout then runs into the screensaver.
     */
    @Suppress("DEPRECATION")
    private fun wakeScreen(power: PowerManager) {
        power.newWakeLock(
            PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            WAKE_LOCK_TAG,
        ).acquire(WAKE_HOLD_MILLIS)
    }

    private companion object {
        const val SCREENSAVER_ENABLED = "screensaver_enabled"
        const val SCREENSAVER_ACTIVATE_ON_SLEEP = "screensaver_activate_on_sleep"
        const val SCREENSAVER_COMPONENTS = "screensaver_components"
        const val READ_SELECTION = "settings get secure $SCREENSAVER_ENABLED; " +
            "settings get secure $SCREENSAVER_ACTIVATE_ON_SLEEP; " +
            "settings get secure $SCREENSAVER_COMPONENTS"

        /** Before Android 14 the command wakes the screen and waits up to a second for it. */
        const val START_TIMEOUT_MILLIS = 5_000L

        const val WAKE_LOCK_TAG = "DeviceWatch:chargeStart"
        const val WAKE_HOLD_MILLIS = 1_000L
    }
}
