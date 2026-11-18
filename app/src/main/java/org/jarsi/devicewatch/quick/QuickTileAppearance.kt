package org.jarsi.devicewatch.quick

import android.os.Build
import android.service.quicksettings.Tile

/** What a figure's tile shows: its label, the line under it (Android 10+) and its state. */
data class QuickTileAppearance(val label: String, val subtitle: String?, val state: Int)

/**
 * A figure's tile. It is an information tile, never a switch, so it stays
 * inactive (grey) unless the user chose otherwise: beside the phone's own tiles
 * a grey one looks switched off, and [highlighted] gives a tile with a reading
 * the colour of a switched-on tile (GitHub #11). A figure that cannot be read
 * greys the tile out further and says why. Before Android 10 a tile has no
 * subtitle, and the value joins the label.
 */
fun quickTileAppearance(
    reading: QuickMetricReading,
    names: QuickMetricNames,
    sdkInt: Int,
    highlighted: Boolean = false,
): QuickTileAppearance {
    val state = when {
        !reading.available -> Tile.STATE_UNAVAILABLE
        highlighted -> Tile.STATE_ACTIVE
        else -> Tile.STATE_INACTIVE
    }
    return if (sdkInt >= Build.VERSION_CODES.Q) {
        QuickTileAppearance(names.tileLabel, reading.unavailableReason ?: reading.value, state)
    } else {
        QuickTileAppearance("${names.tileLabel} ${reading.value}", null, state)
    }
}
