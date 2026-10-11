package org.jarsi.devicewatch.data

/**
 * The screen's default density in dpi, or null when it cannot be told. Android's figure
 * (DisplayMetrics.DENSITY_DEVICE_STABLE) comes from ro.sf.lcd_density and falls back to
 * 160 dpi when an app cannot read that property, which some phones do not let it: a
 * Fairphone 6 gives 160 against its real 480. A display-size setting moves the density
 * only so far either way, so a ratio beyond that means the figure is not this screen's
 * default either (a Samsung running its panel below native resolution reports the native
 * density).
 */
fun knownDefaultDensityDpi(stableDpi: Int, currentDpi: Int): Int? {
    if (stableDpi <= 0 || currentDpi <= 0) return null
    if (stableDpi == ANDROID_FALLBACK_DPI && currentDpi != ANDROID_FALLBACK_DPI) return null
    val ratio = currentDpi.toDouble() / stableDpi
    return stableDpi.takeIf { ratio in DISPLAY_SIZE_RATIOS }
}

/** DisplayMetrics.DENSITY_DEFAULT, what Android reports when it has no figure of its own. */
private const val ANDROID_FALLBACK_DPI = 160

/** Wider than any display-size setting offers (AOSP: 0.85x to 1.5x). */
private val DISPLAY_SIZE_RATIOS = 0.75..1.6
