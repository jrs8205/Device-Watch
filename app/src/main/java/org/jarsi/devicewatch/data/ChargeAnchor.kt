package org.jarsi.devicewatch.data

/** How the current "since charge" period started. */
enum class ChargeAnchorType { FULL_CHARGE, UNPLUGGED }

/** The moment the current "since charge" period began. */
data class ChargeAnchor(
    val timeMillis: Long,
    val batteryLevel: Int,
    val type: ChargeAnchorType,
)

/**
 * Pure hybrid anchor rules for the "since charge" view — no clock, no Android:
 * reaching full while plugged anchors at that moment (once per plug session);
 * unplugging before full anchors at the unplug moment instead. The
 * [State.fullReachedThisPlug] flag is persisted so a service restart or reboot
 * mid-charge cannot re-anchor on the next repeated "full" event.
 */
object ChargeAnchorLogic {

    data class State(
        val anchor: ChargeAnchor? = null,
        val fullReachedThisPlug: Boolean = false,
    )

    fun onBatteryChanged(
        state: State,
        level: Int,
        isPlugged: Boolean,
        isFull: Boolean,
        nowMillis: Long,
    ): State {
        if (!isPlugged || !isFull || state.fullReachedThisPlug) return state
        return State(
            anchor = ChargeAnchor(nowMillis, level, ChargeAnchorType.FULL_CHARGE),
            fullReachedThisPlug = true,
        )
    }

    fun onPowerConnected(state: State): State {
        return state.copy(fullReachedThisPlug = false)
    }

    fun onPowerDisconnected(state: State, level: Int, nowMillis: Long): State {
        val anchor = if (state.fullReachedThisPlug) {
            state.anchor
        } else {
            ChargeAnchor(nowMillis, level, ChargeAnchorType.UNPLUGGED)
        }
        return State(anchor = anchor, fullReachedThisPlug = false)
    }
}

/** Persisted [ChargeAnchorLogic.State]; written by the monitor service's battery receiver. */
interface ChargeAnchorStore {
    fun load(): ChargeAnchorLogic.State

    fun save(state: ChargeAnchorLogic.State)
}
