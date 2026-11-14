package org.jarsi.devicewatch.system.dream

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// The Edge style's arithmetic: a line that runs round the screen's edge as far as the
// battery is full. It starts at the viewer's bottom centre and goes clockwise: left along
// the bottom, up the left side, across the top, down the right side and back.

/** The corner used when the device does not report its own. */
private const val EDGE_DEFAULT_CORNER_DP = 28f

/** The smallest corner a lane is drawn with. */
private const val EDGE_MIN_CORNER_DP = 6f

/** Width of the line's bright core, in dp. */
internal const val EDGE_CORE_DP = 3f

/**
 * The four places the line takes turns in, as its centre line's distance from the screen's
 * edge in dp. They are as far apart as the core is wide, so the core lights any one pixel
 * for a quarter of the time at most.
 */
internal val EDGE_LANE_INSETS_DP = floatArrayOf(4f, 7f, 10f, 13f)

/** The lane for a minute of wall-clock time: one lane a minute, each in turn. */
internal fun edgeLane(epochMinute: Long): Int = Math.floorMod(epochMinute, EDGE_LANE_INSETS_DP.size.toLong()).toInt()

/**
 * The corner radius of a lane, in dp. The lanes are concentric with the screen's own
 * corner ([deviceRadius], in dp; null or zero when the device does not tell, and then a
 * 28 dp corner stands in: a screen's rounding never cuts a line that curves that much, and
 * on a square screen the curve looks meant). A canvas too small for the corner, like the
 * settings thumbnail, gets the largest corner that fits it.
 */
internal fun edgeCornerRadius(deviceRadius: Float?, inset: Float, width: Float, height: Float): Float {
    val screenCorner = if (deviceRadius == null || deviceRadius <= 0f) EDGE_DEFAULT_CORNER_DP else deviceRadius
    val wanted = (screenCorner - inset).coerceAtLeast(EDGE_MIN_CORNER_DP)
    val largest = (min(width, height) / 2f - inset).coerceAtLeast(0f)
    return min(wanted, largest)
}

/** The length of the whole route: the perimeter of the rounded rectangle [inset] in from the edge. */
internal fun edgeLength(width: Float, height: Float, inset: Float, radius: Float): Float {
    val w = width - 2 * inset
    val h = height - 2 * inset
    return 2 * (w - 2 * radius) + 2 * (h - 2 * radius) + (2 * PI * radius).toFloat()
}

/**
 * The point [fraction] of the way round the route: 0 is the bottom centre, 1 is the bottom
 * centre again after the whole loop. A fraction outside 0..1 (or not a number) is taken as
 * the nearer end. All arguments and the result are in one unit, whichever the caller uses.
 */
internal fun edgePoint(fraction: Float, width: Float, height: Float, inset: Float, radius: Float): Pair<Float, Float> {
    val left = inset
    val top = inset
    val right = width - inset
    val bottom = height - inset
    val halfBottom = (right - left) / 2f - radius
    val side = (bottom - top) - 2 * radius
    val across = (right - left) - 2 * radius
    val arc = (PI * radius / 2).toFloat()
    val clamped = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
    var along = clamped * edgeLength(width, height, inset, radius)

    fun onArc(centerX: Float, centerY: Float, startDegrees: Float, distance: Float): Pair<Float, Float> {
        val share = if (arc > 0f) distance / arc else 0f
        val angle = Math.toRadians((startDegrees + 90f * share).toDouble())
        return (centerX + radius * cos(angle).toFloat()) to (centerY + radius * sin(angle).toFloat())
    }

    // Left along the bottom from its centre.
    if (along <= halfBottom) return (width / 2f - along) to bottom
    along -= halfBottom
    // Round the bottom-left corner: from pointing down to pointing left.
    if (along <= arc) return onArc(left + radius, bottom - radius, 90f, along)
    along -= arc
    // Up the left side.
    if (along <= side) return left to (bottom - radius - along)
    along -= side
    // Round the top-left corner: from pointing left to pointing up.
    if (along <= arc) return onArc(left + radius, top + radius, 180f, along)
    along -= arc
    // Across the top.
    if (along <= across) return (left + radius + along) to top
    along -= across
    // Round the top-right corner: from pointing up to pointing right.
    if (along <= arc) return onArc(right - radius, top + radius, 270f, along)
    along -= arc
    // Down the right side.
    if (along <= side) return right to (top + radius + along)
    along -= side
    // Round the bottom-right corner: from pointing right to pointing down.
    if (along <= arc) return onArc(right - radius, bottom - radius, 0f, along)
    along -= arc
    // Back along the bottom to its centre.
    return (right - radius - along.coerceAtMost(halfBottom)) to bottom
}

/**
 * The same route as a path, for cutting the lit part out with a path measure. It follows
 * [edgePoint] exactly: the line is drawn from this and its head is placed by that.
 */
internal fun edgePath(width: Float, height: Float, inset: Float, radius: Float): Path {
    val left = inset
    val top = inset
    val right = width - inset
    val bottom = height - inset
    val diameter = 2 * radius
    return Path().apply {
        moveTo(width / 2f, bottom)
        lineTo(left + radius, bottom)
        if (radius > 0f) arcTo(Rect(left, bottom - diameter, left + diameter, bottom), 90f, 90f, false)
        lineTo(left, top + radius)
        if (radius > 0f) arcTo(Rect(left, top, left + diameter, top + diameter), 180f, 90f, false)
        lineTo(right - radius, top)
        if (radius > 0f) arcTo(Rect(right - diameter, top, right, top + diameter), 270f, 90f, false)
        lineTo(right, bottom - radius)
        if (radius > 0f) arcTo(Rect(right - diameter, bottom - diameter, right, bottom), 0f, 90f, false)
        lineTo(width / 2f, bottom)
    }
}

/** How long the level and the charger's state must stand still before the line rests. */
internal const val EDGE_REST_MS = 600_000L

/** The line's brightness while it rests. */
internal const val EDGE_REST_ALPHA = 0.35f

/** The level and state the line last changed to, and when. */
internal data class EdgeRest(val level: Int, val state: DreamChargeState, val sinceMs: Long)

/**
 * Carries the rest forward: while the level and the state stay as they were, the moment of
 * the last change stays too; any change starts again from [nowMs].
 */
internal fun edgeRest(previous: EdgeRest?, level: Int, state: DreamChargeState, nowMs: Long): EdgeRest =
    if (previous != null && previous.level == level && previous.state == state) {
        previous
    } else {
        EdgeRest(level, state, nowMs)
    }

/**
 * The line's brightness. A bright line that does not move would burn in over a night on a
 * full battery or at a charge limit, so after ten minutes without a change it dims, in
 * every state alike, and comes back when the level or the state changes.
 */
internal fun edgeBrightness(rest: EdgeRest, nowMs: Long): Float =
    if (nowMs - rest.sinceMs < EDGE_REST_MS) 1f else EDGE_REST_ALPHA

/**
 * The alpha a layer needs so that, drawn over layers of the alphas in [under], everything
 * together covers [target]. At full brightness the line's core hides the glow under it;
 * while the line rests they show through each other and would add up to more than the
 * rest allows, so the core is drawn this much fainter.
 */
internal fun edgeTopAlpha(target: Float, vararg under: Float): Float {
    val through = under.fold(1f) { remaining, alpha -> remaining * (1f - alpha) }
    if (through <= 0f) return 0f
    return (1f - (1f - target) / through).coerceIn(0f, 1f)
}

private class EdgeStop(val level: Int, val srgb: Color, val displayP3: Color)

private fun p3(red: Float, green: Float, blue: Float) = Color(red, green, blue, 1f, ColorSpaces.DisplayP3)

/** As saturated as the screen allows: the P3 colours go beyond what sRGB can show. */
private val EDGE_STOPS = listOf(
    EdgeStop(10, Color(0xFFFF1F3D), p3(1f, 0.1f, 0.2f)),
    EdgeStop(30, Color(0xFFFF8A00), p3(1f, 0.55f, 0f)),
    EdgeStop(50, Color(0xFFFFD400), p3(1f, 0.84f, 0f)),
    EdgeStop(70, Color(0xFFB6FF00), p3(0.75f, 1f, 0.1f)),
    EdgeStop(100, Color(0xFF00FF6A), p3(0.1f, 1f, 0.4f)),
)

/**
 * The line's colour for a battery level: red at 10 % and under, through orange, yellow and
 * lime to green at 100 %. Between two stops the colour is blended in a perceptual space,
 * so neighbouring hues meet without passing through grey. [wideGamut] picks the Display P3
 * colours for a window that can show them.
 */
internal fun edgeColor(level: Int, wideGamut: Boolean): Color {
    fun EdgeStop.color() = if (wideGamut) displayP3 else srgb
    if (level <= EDGE_STOPS.first().level) return EDGE_STOPS.first().color()
    for (index in 1 until EDGE_STOPS.size) {
        val upper = EDGE_STOPS[index]
        if (level == upper.level) return upper.color()
        if (level < upper.level) {
            val lower = EDGE_STOPS[index - 1]
            val share = (level - lower.level).toFloat() / (upper.level - lower.level)
            return lerp(lower.color(), upper.color(), share)
        }
    }
    return EDGE_STOPS.last().color()
}

/** The colour of the Edge style's state word and estimate: a lighter shade of the line's. */
internal fun edgeTextColor(level: Int): Color = lerp(edgeColor(level, wideGamut = false), Color.White, 0.27f)
