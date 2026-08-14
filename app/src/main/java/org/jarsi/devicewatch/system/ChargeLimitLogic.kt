package org.jarsi.devicewatch.system

/**
 * Pure rules for the adjustable charge-limit reminder — no clock, no Android.
 * The reminder fires at most once per plug session, so a charger left connected
 * overnight cannot repeat it on every battery broadcast; [onPowerDisconnected]
 * clears the latch for the next session.
 */
object ChargeLimitLogic {

    data class State(val notifiedThisPlug: Boolean = false)

    data class Decision(val state: State, val notify: Boolean)

    /**
     * [limitPercent] <= 0 disables the reminder. Fires once per plug session when
     * charging and [level] has reached the limit; a limit of 100 or more never
     * fires, because the battery-full notification already covers that case.
     */
    fun onBatteryChanged(
        state: State,
        limitPercent: Int,
        level: Int,
        isPlugged: Boolean,
    ): Decision {
        if (limitPercent <= 0 || limitPercent >= 100) return Decision(state, notify = false)
        if (!isPlugged || level < limitPercent || state.notifiedThisPlug) {
            return Decision(state, notify = false)
        }
        return Decision(State(notifiedThisPlug = true), notify = true)
    }

    /** Resets the once-per-plug-session latch. */
    fun onPowerDisconnected(state: State): State = state.copy(notifiedThisPlug = false)
}
