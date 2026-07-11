package org.jarsi.devicewatch.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The battery's state right now, for the "since charge" view. A port (like
 * [org.jarsi.devicewatch.widget.WidgetController]) so the ViewModel stays
 * Context-free and JVM-testable.
 */
interface BatteryStatusReader {
    /** Battery percent 0–100, or null when the sticky broadcast is unavailable. */
    fun currentLevel(): Int?

    /** Whether a charger is connected right now. */
    fun isCharging(): Boolean
}

/** Reads the sticky ACTION_BATTERY_CHANGED broadcast — no receiver registration needed. */
@Singleton
class BatteryStatusReaderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : BatteryStatusReader {

    private fun sticky(): Intent? =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    override fun currentLevel(): Int? {
        val intent = sticky() ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return level * 100 / scale
    }

    override fun isCharging(): Boolean =
        sticky()?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)?.let { it != 0 } ?: false
}
