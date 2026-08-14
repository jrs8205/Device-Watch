package org.jarsi.devicewatch.presentation.ui

import androidx.annotation.StringRes
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.BatterySample

/** Time window the battery chart draws. */
internal enum class BatteryChartRange(val hours: Long) { Day(24), Week(7 * 24) }

/** Chip label for the range selector. */
@get:StringRes
internal val BatteryChartRange.labelRes: Int
    get() = when (this) {
        BatteryChartRange.Day -> R.string.history_battery_day
        BatteryChartRange.Week -> R.string.history_battery_week
    }

/** Length of the drawn window in milliseconds. */
internal fun BatteryChartRange.windowMillis(): Long = hours * 60 * 60 * 1000

/** One battery reading in chart space. */
internal data class ChartPoint(val xFraction: Float, val yFraction: Float, val charging: Boolean)

/**
 * Pure geometry for the battery chart, kept out of the composable so it can be
 * unit-tested. Everything is expressed in raw 0..1 fractions and never in
 * pixels: x grows with time (0 = window start, 1 = now) and y grows with charge
 * (0 = empty, 1 = full). Compose's y axis points down, so the composable — not
 * this file — flips y when it converts to draw space.
 */
internal object BatteryChartLogic {

    /**
     * The samples inside `[nowMillis - range, nowMillis]` mapped to chart space,
     * input order preserved (the store hands them over ascending). Samples from
     * before the window and any with a clock-skewed future timestamp are dropped.
     */
    fun points(
        samples: List<BatterySample>,
        range: BatteryChartRange,
        nowMillis: Long,
    ): List<ChartPoint> {
        val windowMillis = range.windowMillis()
        val startMillis = nowMillis - windowMillis
        return samples.inWindow(startMillis, nowMillis).map { sample ->
            ChartPoint(
                xFraction = (sample.timeMillis - startMillis).toFloat() / windowMillis.toFloat(),
                yFraction = sample.level / 100f,
                charging = sample.charging,
            )
        }
    }

    /**
     * Contiguous charging runs as x-fraction ranges, for shading behind the line.
     * A run reaches to the first reading that is no longer charging, so the shaded
     * band covers the whole rising leg of the line — and a single charging sample
     * still gets a visible width instead of a zero-wide sliver.
     */
    fun chargingSpans(points: List<ChartPoint>): List<ClosedFloatingPointRange<Float>> {
        val spans = mutableListOf<ClosedFloatingPointRange<Float>>()
        var runStart: Float? = null
        for (point in points) {
            if (point.charging) {
                if (runStart == null) runStart = point.xFraction
            } else {
                runStart?.let { spans += it..point.xFraction }
                runStart = null
            }
        }
        // A run still open at the end (still charging now) reaches the last point.
        runStart?.let { spans += it..points.last().xFraction }
        return spans
    }

    /**
     * Indices into the in-window samples (i.e. into [points]'s result) at which a
     * new polyline starts: the first point, plus every point more than [maxGapMs]
     * after its predecessor. A gap means the app was not running, so the line must
     * break there instead of inventing a straight drain across the hole.
     */
    fun segmentStarts(
        samples: List<BatterySample>,
        range: BatteryChartRange,
        nowMillis: Long,
        maxGapMs: Long = 30L * 60 * 1000,
    ): List<Int> {
        val visible = samples.inWindow(nowMillis - range.windowMillis(), nowMillis)
        if (visible.isEmpty()) return emptyList()
        val starts = mutableListOf(0)
        for (index in 1 until visible.size) {
            if (visible[index].timeMillis - visible[index - 1].timeMillis > maxGapMs) starts += index
        }
        return starts
    }

    private fun List<BatterySample>.inWindow(startMillis: Long, nowMillis: Long) =
        filter { it.timeMillis in startMillis..nowMillis }
}
