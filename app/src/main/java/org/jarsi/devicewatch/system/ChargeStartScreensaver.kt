package org.jarsi.devicewatch.system

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.jarsi.devicewatch.data.AppSettingsRepository
import org.jarsi.devicewatch.data.PrivilegedAccess
import org.jarsi.devicewatch.data.PrivilegedShell

/**
 * Brings the screensaver up as soon as charging begins on a phone nobody is using, when the
 * user asked for that (see [chargeStartAction] for why Android would otherwise wait).
 */
class ChargeStartScreensaver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSettings: AppSettingsRepository,
    private val shell: PrivilegedShell,
) {

    /** Call off the main thread: the extended-access route waits on the shell. */
    fun onPowerConnected() {
        val prefs = context.getSharedPreferences(DreamPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(DreamPreferences.KEY_START_ON_CHARGE, false)) return
        val power = context.getSystemService(PowerManager::class.java) ?: return
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        // A phone that is unlocked with its screen on is in someone's hands. A phone without
        // a lock screen that Android itself woke for the plug-in looks the same; it then
        // dreams at its own screen timeout, as before.
        val inUse = power.isInteractive && keyguard?.isKeyguardLocked != true

        val shellSelection = if (appSettings.privilegedAccess() != PrivilegedAccess.OFF) {
            shell.run(READ_SELECTION)?.lines()?.let { lines ->
                isOurDreamSelected(lines.getOrNull(0), lines.getOrNull(1), ourDream)
            }
        } else {
            null
        }
        // A shell that did not answer leaves the ordinary route.
        val privileged = shellSelection != null

        val action = chargeStartAction(
            enabled = true,
            inUse = inUse,
            dreamShowing = MonitorDreamService.isDreaming,
            ourDreamSelected = shellSelection ?: readSelectionDirectly(),
            privileged = privileged,
        )
        when (action) {
            ChargeStartAction.NONE -> Unit
            ChargeStartAction.START_DREAM -> if (!dreamCallSucceeded(shell.run(START_DREAM))) wakeScreen(power)
            ChargeStartAction.WAKE_SCREEN -> wakeScreen(power)
        }
    }

    private val ourDream: String =
        ComponentName(context, MonitorDreamService::class.java).flattenToString()

    /** Null when Android does not let an ordinary app read these settings. */
    private fun readSelectionDirectly(): Boolean? = try {
        val resolver = context.contentResolver
        isOurDreamSelected(
            Settings.Secure.getString(resolver, SCREENSAVER_ENABLED),
            Settings.Secure.getString(resolver, SCREENSAVER_COMPONENTS),
            ourDream,
        )
    } catch (_: SecurityException) {
        null
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
        const val SCREENSAVER_COMPONENTS = "screensaver_components"
        const val READ_SELECTION =
            "settings get secure $SCREENSAVER_ENABLED; settings get secure $SCREENSAVER_COMPONENTS"

        /**
         * IDreamManager.dream(), the first method of the interface since Android 4.2; the
         * shell user holds WRITE_DREAM_STATE. `cmd dreams` would be clearer but answers
         * root only.
         */
        const val START_DREAM = "service call dreams 1"

        const val WAKE_LOCK_TAG = "DeviceWatch:chargeStart"
        const val WAKE_HOLD_MILLIS = 1_000L
    }
}
