package org.jarsi.devicewatch.data

import android.content.Context
import org.jarsi.devicewatch.Bidi
import org.jarsi.devicewatch.R

/** "0.5 GB of 1.8 GB": the swap in use out of all there is. */
internal fun swapText(context: Context, usedGb: Double, totalGb: Double): String =
    context.getString(R.string.swap_used_of, gigabytesText(usedGb), gigabytesText(totalGb))

/** "12.4 GB free of 59.5 GB": the room left on a memory card or a USB drive. */
internal fun volumeFreeText(context: Context, freeGb: Double, totalGb: Double): String =
    context.getString(R.string.volume_free_of, gigabytesText(freeGb), gigabytesText(totalGb))

/**
 * "3 / 4": the signal's bars out of the four there are. One left-to-right piece: the two
 * numbers have no letter to take a direction from, and a line read from the right turns
 * them round.
 */
internal fun signalBarsText(level: Int): String = Bidi.ltr("$level / 4")

/** One left-to-right piece: after an Arabic word the unit would otherwise leave its number. */
private fun gigabytesText(gb: Double): String = Bidi.ltr("%.1f GB".format(gb))
