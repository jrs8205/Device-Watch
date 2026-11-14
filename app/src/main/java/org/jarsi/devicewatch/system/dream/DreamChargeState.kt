package org.jarsi.devicewatch.system.dream

import android.os.BatteryManager
import org.jarsi.devicewatch.R

/**
 * What the screensaver's battery block says about the charger. The bolt pulses only in
 * [CHARGING]; the other states show a steady icon.
 */
internal enum class DreamChargeState(val icon: String, @param:androidx.annotation.StringRes val label: Int) {
    /** The battery is filling. */
    CHARGING("⚡", R.string.dream_charging),

    /** On the charger at 100 %. */
    FULL("⚡", R.string.dream_battery_full),

    /** On the charger without filling: a charge limit, a pause for heat, or a weak charger. */
    HELD("🔌", R.string.dream_plugged_not_charging),

    ON_BATTERY("🔋", R.string.dream_on_battery);

    val pulses: Boolean get() = this == CHARGING
}

/**
 * The charge state from the battery broadcast's status, plug and level. A full battery on
 * the charger is [DreamChargeState.FULL] whatever the status says: phones go on reporting
 * CHARGING at 100 % for a while. FULL below 100 % is a phone held at its charge limit.
 */
internal fun dreamChargeState(status: Int, plugged: Boolean, level: Int): DreamChargeState {
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
    val full = status == BatteryManager.BATTERY_STATUS_FULL
    return when {
        level >= 100 && (plugged || charging || full) -> DreamChargeState.FULL
        charging -> DreamChargeState.CHARGING
        plugged || full -> DreamChargeState.HELD
        else -> DreamChargeState.ON_BATTERY
    }
}
