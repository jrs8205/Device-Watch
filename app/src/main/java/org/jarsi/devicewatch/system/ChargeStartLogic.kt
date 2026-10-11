package org.jarsi.devicewatch.system

/** What to do when charging begins, so the screensaver comes up without waiting for a wake. */
internal enum class ChargeStartAction { NONE, START_DREAM, WAKE_SCREEN }

/** Whether Android would show our screensaver on a charging phone, as far as can be told. */
internal enum class ChargeDream { OURS, NOT_OURS, UNKNOWN }

/**
 * Android starts a screensaver only when the screen times out while charging. With the
 * Always On Display on, plugging in does not wake the phone, so there is no timeout and
 * the screensaver waits until someone wakes the screen. Extended access can ask the dream
 * manager to start it at once; without it, waking the screen lets the lock screen time out
 * into the screensaver a few seconds later. A screen that is already on is left alone:
 * unlocked in use, a call or navigation shown over the lock screen, or the lock screen
 * itself, which now times out into the screensaver by itself.
 *
 * The dream manager starts whatever is selected without asking whether it should start
 * while charging, so it is used only when [selection] is certain. An uncertain one gets
 * the wake, after which Android's own timeout applies Android's own rules; waking a screen
 * for nothing costs only a few lit seconds.
 */
internal fun chargeStartAction(
    enabled: Boolean,
    screenOn: Boolean,
    dreamShowing: Boolean,
    selection: ChargeDream,
    privileged: Boolean,
): ChargeStartAction = when {
    !enabled || screenOn || dreamShowing || selection == ChargeDream.NOT_OURS -> ChargeStartAction.NONE
    privileged && selection == ChargeDream.OURS -> ChargeStartAction.START_DREAM
    else -> ChargeStartAction.WAKE_SCREEN
}

/**
 * Reads Android's screensaver settings for a charging phone. The arguments are the raw
 * values of `screensaver_enabled`, `screensaver_activate_on_sleep` and
 * `screensaver_components`; null or the shell's "null" means unset. Android shows the first
 * screensaver of the list (DreamManagerService.chooseDreamForUser), and no choice at all
 * means the system's own. Unset switches fall back to defaults that differ by device (AOSP
 * leaves "while charging" off, a Pixel turns it on), so they make the answer uncertain.
 */
internal fun chargeDreamSelection(
    enabled: String?,
    activateOnSleep: String?,
    components: String?,
    ours: String,
): ChargeDream {
    val switchedOn = enabled.setting()
    val onCharge = activateOnSleep.setting()
    if (switchedOn == "0" || onCharge == "0") return ChargeDream.NOT_OURS
    val first = components.setting()?.substringBefore(',')?.trim()
    if (first == null || expandComponent(first) != ours) return ChargeDream.NOT_OURS
    return if (switchedOn == "1" && onCharge == "1") ChargeDream.OURS else ChargeDream.UNKNOWN
}

private fun String?.setting(): String? = this?.trim()?.takeUnless { it.isEmpty() || it == "null" }

/** "pkg/.Cls" is short for "pkg/pkg.Cls". */
private fun expandComponent(flattened: String): String {
    val pkg = flattened.substringBefore('/')
    val cls = flattened.substringAfter('/', missingDelimiterValue = "")
    return if (cls.startsWith('.')) "$pkg/$pkg$cls" else flattened
}

/**
 * The shell command that starts the screensaver. It looks at the screen itself, after it
 * got its turn in the shell: a poll may hold the shell for seconds, and the phone may have
 * been picked up meanwhile. IDreamManager.dream() is the interface's first method since
 * Android 4.2 and the shell user holds WRITE_DREAM_STATE (`cmd dreams` answers root only).
 * Before Android 14 the dream manager can start a dream only from an awake screen
 * (PowerGroup.dreamLocked), so the command wakes it first and waits for that.
 */
internal fun startDreamCommand(sdkInt: Int): String {
    val start = if (sdkInt >= ANDROID_14) {
        DREAM_CALL
    } else {
        "input keyevent KEYCODE_WAKEUP; $WAIT_UNTIL_AWAKE; $DREAM_CALL"
    }
    return "case \"\$($READ_WAKEFULNESS)\" in *=Asleep|*=Dozing) $start ;; *) echo $SCREEN_ON_MARKER ;; esac"
}

internal enum class DreamCallOutcome { STARTED, SCREEN_ON, FAILED }

/** Reads [startDreamCommand]'s output: a dream call answers with a zero when it went through. */
internal fun dreamCallOutcome(output: String?): DreamCallOutcome = when {
    output == null -> DreamCallOutcome.FAILED
    output.contains(SCREEN_ON_MARKER) -> DreamCallOutcome.SCREEN_ON
    DREAM_CALL_REPLY.find(output)?.groupValues?.get(1) == "00000000" -> DreamCallOutcome.STARTED
    else -> DreamCallOutcome.FAILED
}

private const val ANDROID_14 = 34
private const val DREAM_CALL = "service call dreams 1"
private const val READ_WAKEFULNESS = "dumpsys power | grep -m1 -o 'mWakefulness=[A-Za-z]*'"
private const val WAIT_UNTIL_AWAKE =
    "for i in 1 2 3 4 5; do dumpsys power | grep -q 'mWakefulness=Awake' && break; sleep 0.2; done"
private const val SCREEN_ON_MARKER = "dw-screen-on"
private val DREAM_CALL_REPLY = Regex("Result: Parcel\\(\\s*([0-9a-fA-F]{8})")
