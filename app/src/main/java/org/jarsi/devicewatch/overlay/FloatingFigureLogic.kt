package org.jarsi.devicewatch.overlay

import org.jarsi.devicewatch.data.QuickMetric
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The floating card's decisions in plain Kotlin, so each is tested without a window:
 * how many figures it takes, where it sits, what a touch meant, what the tile shows,
 * and whether the monitor should show it, hide it or turn it off.
 */

/** The card holds one to three figures: more would make it a panel, not a glance. */
const val OVERLAY_MAX_FIGURES = 3

/** How long the hide and open buttons stay after a tap. */
const val OVERLAY_BUTTONS_VISIBLE_MS = 4_000L

/** How long a still touch must last to hide the card. */
const val OVERLAY_LONG_PRESS_MS = 500L

/** Where the card goes until it has been dragged: the top-right corner. */
val OVERLAY_DEFAULT_POSITION = OverlayPosition(1f, 0f)

/** What the card shows when nothing has been chosen: the figures a gamer asked for (#2). */
fun overlayDefaultMetrics(): Set<QuickMetric> = setOf(QuickMetric.BATTERY_TEMP, QuickMetric.BATTERY_POWER)

/**
 * A selection after one switch is flipped: a fourth figure is refused, and so is
 * taking the last one away, so the card always has something to show and the rows
 * always agree with it.
 */
fun limitOverlaySelection(current: Set<QuickMetric>, metric: QuickMetric, enabled: Boolean): Set<QuickMetric> = when {
    !enabled -> if (current.size <= 1 && metric in current) current else current - metric
    metric in current -> current
    current.size >= OVERLAY_MAX_FIGURES -> current
    else -> current + metric
}

/** The figures to show: the chosen ones, or the default pair when none is chosen. */
fun overlayMetricsOrDefault(chosen: Set<QuickMetric>): Set<QuickMetric> =
    if (chosen.isEmpty()) overlayDefaultMetrics() else chosen

/**
 * Where to show the card in an orientation: the place it was dragged to there, else
 * the place it was dragged to in the other orientation (the same corner, so it does
 * not jump on the first rotation), else nowhere stored yet.
 */
fun overlayPositionChoice(landscape: Boolean, portrait: OverlayPosition?, landscapePosition: OverlayPosition?): OverlayPosition? =
    if (landscape) landscapePosition ?: portrait else portrait ?: landscapePosition

enum class OverlayLayout { ROW, COLUMN }

/** The card takes at most this share of the room's width: never the whole screen. */
const val OVERLAY_MAX_WIDTH_SHARE = 0.9f

/**
 * Three figures stack in portrait, where long values would not fit side by side; so
 * do two or more whenever the measured row would not fit the width (a large font).
 */
fun overlayLayout(count: Int, landscape: Boolean, rowFits: Boolean = true): OverlayLayout = when {
    count >= OVERLAY_MAX_FIGURES && !landscape -> OverlayLayout.COLUMN
    count > 1 && !rowFits -> OverlayLayout.COLUMN
    else -> OverlayLayout.ROW
}

/** Whether a row this wide fits the card's share of the room. */
fun overlayRowFits(rowWidthPx: Int, roomWidthPx: Int): Boolean = rowWidthPx <= roomWidthPx * OVERLAY_MAX_WIDTH_SHARE

/**
 * The card's top-left corner in pixels for a stored share of the room it has; a card
 * larger than the screen sits at the edge. Null is the default corner.
 */
fun overlayPixels(position: OverlayPosition?, cardW: Int, cardH: Int, screenW: Int, screenH: Int): Pair<Int, Int> {
    val p = position ?: OVERLAY_DEFAULT_POSITION
    val roomX = max(0, screenW - cardW)
    val roomY = max(0, screenH - cardH)
    val x = (p.xFraction * roomX).roundToInt().coerceIn(0, roomX)
    val y = (p.yFraction * roomY).roundToInt().coerceIn(0, roomY)
    return x to y
}

/**
 * Where the card goes after its size changed (the buttons, a longer value): while a
 * drag is under way the dragged place, only clamped into the room, so a reading that
 * lands mid-drag does not snap the card back; otherwise its stored share.
 */
fun overlayPlaceAfterResize(
    dragging: Boolean,
    currentX: Int,
    currentY: Int,
    position: OverlayPosition,
    cardW: Int,
    cardH: Int,
    screenW: Int,
    screenH: Int,
): Pair<Int, Int> =
    if (dragging) {
        currentX.coerceIn(0, max(0, screenW - cardW)) to currentY.coerceIn(0, max(0, screenH - cardH))
    } else {
        overlayPixels(position, cardW, cardH, screenW, screenH)
    }

/** The inverse of [overlayPixels]: the card's corner as a share of the room it has. */
fun overlayFraction(xPx: Int, yPx: Int, cardW: Int, cardH: Int, screenW: Int, screenH: Int): OverlayPosition {
    val roomX = screenW - cardW
    val roomY = screenH - cardH
    val x = if (roomX <= 0) 0f else (xPx.toFloat() / roomX).coerceIn(0f, 1f)
    val y = if (roomY <= 0) 0f else (yPx.toFloat() / roomY).coerceIn(0f, 1f)
    return OverlayPosition(x, y)
}

enum class OverlayAction { SHOW, HIDE, TURN_OFF }

/**
 * What the monitor does with the card on a reading: a card switched on whose
 * permission has gone is turned off for good, so the tile tells the truth; one
 * switched on with the screen off is only hidden until the screen comes back.
 */
fun overlayAction(shown: Boolean, permission: Boolean, screenOn: Boolean, dreaming: Boolean = false): OverlayAction = when {
    !shown -> OverlayAction.HIDE
    !permission -> OverlayAction.TURN_OFF
    // A screensaver keeps the device interactive, so it is a case of its own.
    screenOn && !dreaming -> OverlayAction.SHOW
    else -> OverlayAction.HIDE
}

data class OverlayTileLook(val active: Boolean, val needsPermission: Boolean)

/** The tile is active only while the card is up; without the permission it says so. */
fun overlayTileLook(permission: Boolean, shown: Boolean): OverlayTileLook =
    OverlayTileLook(active = permission && shown, needsPermission = !permission)

enum class OverlayTap { TAP, LONG_PRESS, NONE }

/**
 * Reads one touch on the card: a drag once the finger moves past [slopPx], a long
 * press when it stays put for [longPressMs], otherwise a tap. A drag is never a tap
 * or a long press.
 */
class OverlayGesture(private val slopPx: Float, private val longPressMs: Long) {
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var dragging = false

    /** True from the move that crossed the slop until the touch ends. */
    val isDragging: Boolean get() = dragging

    fun down(x: Float, y: Float, atMs: Long) {
        downX = x
        downY = y
        downAt = atMs
        dragging = false
    }

    /** The movement since the touch began, once it counts as a drag; null while it is still a touch. */
    fun move(x: Float, y: Float, atMs: Long): Pair<Float, Float>? {
        val dx = x - downX
        val dy = y - downY
        if (!dragging && abs(dx) <= slopPx && abs(dy) <= slopPx) return null
        dragging = true
        return dx to dy
    }

    /** True once a still touch has lasted long enough to be a long press. */
    fun longPressDue(atMs: Long): Boolean = !dragging && atMs - downAt >= longPressMs

    fun up(atMs: Long): OverlayTap {
        val result = when {
            dragging -> OverlayTap.NONE
            longPressDue(atMs) -> OverlayTap.LONG_PRESS
            else -> OverlayTap.TAP
        }
        dragging = false
        return result
    }
}
