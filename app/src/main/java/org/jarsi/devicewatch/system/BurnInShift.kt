package org.jarsi.devicewatch.system

/** Where the screensaver content moves to at its next burn-in shift, in dp from centre. */
internal data class BurnInOffset(val xDp: Float, val yDp: Float)

/**
 * Maps two uniform random numbers in 0..1 to the next burn-in shift. GitHub #6: a diagonal
 * shift leaves the clock visibly off-centre sideways, so [verticalOnly] keeps it centred and
 * moves it a little further up and down instead, to keep some of the lost spread.
 */
internal fun burnInOffset(randomX: Double, randomY: Double, verticalOnly: Boolean): BurnInOffset =
    if (verticalOnly) {
        BurnInOffset(0f, spread(randomY, VERTICAL_ONLY_SHIFT_DP))
    } else {
        BurnInOffset(spread(randomX, FREE_SHIFT_DP), spread(randomY, FREE_SHIFT_DP))
    }

private fun spread(random: Double, maxDp: Float): Float = (random * 2 * maxDp - maxDp).toFloat()

private const val FREE_SHIFT_DP = 15f
private const val VERTICAL_ONLY_SHIFT_DP = 20f
