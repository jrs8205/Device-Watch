package org.jarsi.devicewatch.overlay

import java.util.Locale

/**
 * Where the floating card sits, as shares of the room it has on the screen: 0 is the
 * left or top edge, 1 the right or bottom. Shares survive a rotation and a change of
 * screen size, so the card stays in its corner.
 */
data class OverlayPosition(val xFraction: Float, val yFraction: Float)

/** A stored "x,y" pair; null unless both are shares in 0..1. */
fun overlayPositionOf(stored: String?): OverlayPosition? {
    val parts = stored.orEmpty().split(',')
    if (parts.size != 2) return null
    val x = parts[0].trim().toFloatOrNull() ?: return null
    val y = parts[1].trim().toFloatOrNull() ?: return null
    if (x !in 0f..1f || y !in 0f..1f) return null
    return OverlayPosition(x, y)
}

/** The position as stored, with a dot for the decimal whatever the phone's language. */
fun overlayPositionToStored(position: OverlayPosition): String =
    String.format(Locale.ROOT, "%.4f,%.4f", position.xFraction, position.yFraction)
