package org.jarsi.devicewatch.presentation.ui

/**
 * The app's own bounds on the system's font size and display size settings.
 *
 * Every screen is a grid of labels and readouts that must stay on one line
 * each, so the system's larger settings are honoured only up to the point
 * where that still holds: the font may grow to [MAX_FONT_SCALE], and the
 * display size may grow until the layout would be narrower than
 * [MIN_LAYOUT_WIDTH_DP]. Smaller settings pass through untouched, and the
 * display is never shrunk below the device's own default density.
 */
const val MAX_FONT_SCALE = 1.15f

/** The narrowest layout the screens are drawn for, in dp. */
const val MIN_LAYOUT_WIDTH_DP = 400f

fun clampedFontScale(systemScale: Float): Float {
    if (!systemScale.isFinite() || systemScale <= 0f) return 1f
    return systemScale.coerceAtMost(MAX_FONT_SCALE)
}

/**
 * [systemDensity] is the density the current display-size setting gives,
 * [defaultDensity] the device's stable default, [widthPx] the window width.
 */
fun clampedDensity(systemDensity: Float, defaultDensity: Float, widthPx: Int): Float {
    val defaultIsUsable = defaultDensity.isFinite() && defaultDensity > 0f
    if (!systemDensity.isFinite() || systemDensity <= 0f) return if (defaultIsUsable) defaultDensity else 1f
    if (!defaultIsUsable || widthPx <= 0) return systemDensity
    val floorDensity = maxOf(defaultDensity, widthPx / MIN_LAYOUT_WIDTH_DP)
    return systemDensity.coerceAtMost(floorDensity)
}
