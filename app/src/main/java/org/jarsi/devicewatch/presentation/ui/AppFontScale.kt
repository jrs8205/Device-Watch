package org.jarsi.devicewatch.presentation.ui

/**
 * The bottom navigation bar is the one in-app surface that can neither scroll nor
 * grow: four items share a bar of fixed height, so at the system's largest font
 * the labels wrap to two lines and every item re-centres its own taller column —
 * which leaves the icons sitting at different heights. Capping the scale for the
 * bar alone keeps the labels on one line; the scrolling screens keep the full
 * system scale, where a larger font costs nothing but scrolling.
 */
const val MAX_NAV_BAR_FONT_SCALE = 1.3f

fun clampedNavBarFontScale(systemScale: Float): Float {
    if (!systemScale.isFinite() || systemScale <= 0f) return 1f
    return systemScale.coerceAtMost(MAX_NAV_BAR_FONT_SCALE)
}
