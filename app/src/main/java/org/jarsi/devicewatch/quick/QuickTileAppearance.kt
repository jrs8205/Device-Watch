package org.jarsi.devicewatch.quick

import android.os.Build
import android.service.quicksettings.Tile

/** What a figure's tile shows: its label, the line under it (Android 10+) and its state. */
data class QuickTileAppearance(val label: String, val subtitle: String?, val state: Int)

/**
 * A figure's tile. It is an information tile, never a switch, so it stays
 * inactive (grey); a figure that cannot be read greys it out further and says
 * why. Before Android 10 a tile has no subtitle, and the value joins the label.
 */
fun quickTileAppearance(reading: QuickMetricReading, names: QuickMetricNames, sdkInt: Int): QuickTileAppearance {
    val state = if (reading.available) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
    return if (sdkInt >= Build.VERSION_CODES.Q) {
        QuickTileAppearance(names.tileLabel, reading.unavailableReason ?: reading.value, state)
    } else {
        QuickTileAppearance("${names.tileLabel} ${reading.value}", null, state)
    }
}
