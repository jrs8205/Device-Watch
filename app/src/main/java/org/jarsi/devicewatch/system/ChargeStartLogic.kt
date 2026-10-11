package org.jarsi.devicewatch.system

/** What to do when charging begins, so the screensaver comes up without waiting for a wake. */
internal enum class ChargeStartAction { NONE, START_DREAM, WAKE_SCREEN }

/**
 * Android starts a screensaver only when the screen times out while charging. With the
 * Always On Display on, plugging in does not wake the phone, so there is no timeout and
 * the screensaver waits until someone wakes the screen. Extended access can ask the dream
 * manager to start it at once; without it, waking the screen lets the lock screen time out
 * into the screensaver a few seconds later. A screen that is already on is left alone:
 * unlocked in use, a call or navigation shown over the lock screen, or the lock screen
 * itself, which now times out into the screensaver by itself. [ourDreamSelected] is null
 * when the screensaver settings could not be read: waking a screen for nothing costs only a
 * few lit seconds.
 */
internal fun chargeStartAction(
    enabled: Boolean,
    screenOn: Boolean,
    dreamShowing: Boolean,
    ourDreamSelected: Boolean?,
    privileged: Boolean,
): ChargeStartAction = when {
    !enabled || screenOn || dreamShowing || ourDreamSelected == false -> ChargeStartAction.NONE
    privileged -> ChargeStartAction.START_DREAM
    else -> ChargeStartAction.WAKE_SCREEN
}

/**
 * Whether Android would show [ours] for a charging phone: screensavers on, "while charging"
 * among the times to start, and [ours] the one selected (a flattened component name). The
 * arguments are the raw values of `screensaver_enabled`, `screensaver_activate_on_sleep` and
 * `screensaver_components`; null or the shell's "null" means unset. An unset switch is on by
 * default, an unset "while charging" is left to Android, and an unset choice is the
 * system's own screensaver.
 */
internal fun ourDreamStartsOnCharge(
    enabled: String?,
    activateOnSleep: String?,
    components: String?,
    ours: String,
): Boolean {
    if (enabled?.trim() == "0" || activateOnSleep?.trim() == "0") return false
    val selected = components?.trim()?.takeUnless { it.isEmpty() || it == "null" } ?: return false
    return selected.split(',').any { expandComponent(it.trim()) == ours }
}

/** "pkg/.Cls" is short for "pkg/pkg.Cls". */
private fun expandComponent(flattened: String): String {
    val pkg = flattened.substringBefore('/')
    val cls = flattened.substringAfter('/', missingDelimiterValue = "")
    return if (cls.startsWith('.')) "$pkg/$pkg$cls" else flattened
}

/** Whether `service call dreams` answered without an exception: its reply starts with a zero. */
internal fun dreamCallSucceeded(output: String?): Boolean =
    output != null && DREAM_CALL_REPLY.find(output)?.groupValues?.get(1) == "00000000"

private val DREAM_CALL_REPLY = Regex("Result: Parcel\\(\\s*([0-9a-fA-F]{8})")
