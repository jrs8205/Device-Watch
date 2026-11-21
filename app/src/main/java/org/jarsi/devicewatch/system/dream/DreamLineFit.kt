package org.jarsi.devicewatch.system.dream

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlin.math.roundToInt

// How the screensaver's lines fit the room they have. The dates and the times of day are
// written in the phone's language, which need not be one the app is written in, and the
// phone's name is whatever its owner typed: any of them can be several times as long as the
// English the styles were drawn for.

/**
 * A line shrinks from [max] toward a floor to stay on one line, as a long date always has.
 * When it is too wide even at the floor it is no longer cut off at its box's edge: it goes on
 * a second and a third line ([DREAM_LINE_MAX_LINES]), at the floor's size or the largest one
 * under it that keeps every word whole, never below [DREAM_LINE_LEAST_SP]. A text set to stay
 * on one line (`softWrap = false`) only gets smaller, down to the same least size: the lines
 * of figures are, so that a second line never parts a figure from its unit.
 *
 * A line that fits at or above its floor is sized exactly as before.
 */
internal fun fitUpTo(max: TextUnit, floorSp: Float = 12f): TextAutoSize =
    FitThenWrap(maxSp = max.value, floorSp = minOf(floorSp, max.value))

/**
 * A text sized by [fit], made anew whenever [fit] is another: for a text whose largest size
 * is worked out from the clock's or the screen's, and so can change while the text is shown.
 *
 * The library compares a text's parameters without its auto-size (foundation 1.11.3, and
 * 1.12.1 the same: `TextAnnotatedStringElement.equals`): a text drawn again with a new one
 * and nothing else changed is never handed it, and goes on fitting itself up to the old size. A screen that
 * turns under a running screensaver is drawn first in its new layout at the old width, where
 * the clock is small, and then at the real one. The lines that follow the clock's size kept
 * the first: Gauges on its side drew "AM" at 10 sp beside a 96 sp clock. A text that fits up
 * to a size that never changes needs none of this.
 */
@Composable
internal inline fun FitAnew(fit: TextAutoSize, content: @Composable (TextAutoSize) -> Unit) {
    key(fit) { content(fit) }
}

/**
 * The side the lines of a centred text stand on once it has had to take a second line. Until
 * then no side is asked for and the line is laid out as it always was: asked to centre, a
 * single line is drawn up to a pixel to the side of where it stood, because its box is a
 * fraction of a pixel wider than its letters.
 */
internal class WrapAlign(private val whenWrapped: TextAlign) {
    private var wrapped by mutableStateOf(false)

    val textAlign: TextAlign? get() = if (wrapped) whenWrapped else null

    val onTextLayout: (TextLayoutResult) -> Unit = { wrapped = it.lineCount > 1 }
}

@Composable
internal fun rememberWrapAlign(whenWrapped: TextAlign): WrapAlign = remember(whenWrapped) { WrapAlign(whenWrapped) }

/** How many lines a line that does not fit at its floor may take. */
internal const val DREAM_LINE_MAX_LINES = 3

/** The smallest a line is drawn when even its floor leaves it too wide for its lines. */
internal const val DREAM_LINE_LEAST_SP = 6f

internal data class FitThenWrap(val maxSp: Float, val floorSp: Float) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit =
        fittedLineSp(
            maxSp = maxSp,
            floorSp = floorSp,
            leastSp = DREAM_LINE_LEAST_SP,
            onOneLine = { size -> performLayout(constraints, text, size.sp).isOneWholeLine(constraints) },
            wrappedWhole = { size -> performLayout(constraints, text, size.sp).isWholeWithin(constraints) },
        ).sp
}

/**
 * The size for a line, the choice apart from the measuring. The sizes tried are the floor and
 * whole steps above it, as the library's own step-based sizing tries them. [onOneLine] says
 * whether the line fits on one line at a size, [wrappedWhole] whether it is whole on the lines
 * it may take.
 */
internal fun fittedLineSp(
    maxSp: Float,
    floorSp: Float,
    leastSp: Float,
    onOneLine: (Float) -> Boolean,
    wrappedWhole: (Float) -> Boolean,
): Float {
    if (onOneLine(floorSp)) {
        // The largest step that still fits: a larger size never fits where a smaller did not.
        var low = 0
        var high = floor(maxSp - floorSp).toInt().coerceAtLeast(0)
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (onOneLine(floorSp + middle)) low = middle else high = middle - 1
        }
        return floorSp + low
    }
    var size = floorSp
    while (size > leastSp) {
        if (wrappedWhole(size)) return size
        size -= 1f
    }
    return minOf(leastSp, floorSp)
}

/**
 * On one line with room for all of it. The width asked of the box is the one the line needs
 * when it may not wrap, the room after its last letter included: a line that may wrap is laid
 * out as fitting a little earlier than that, and would be sized a step larger than a line
 * that may not.
 */
private fun TextLayoutResult.isOneWholeLine(constraints: Constraints): Boolean =
    lineCount == 1 && !didOverflowWidth && !didOverflowHeight && !isLineEllipsized(0) &&
        (!constraints.hasBoundedWidth || multiParagraph.maxIntrinsicWidth <= constraints.maxWidth)

/** Every line shown to its end, within the box, and no word broken in two to get there. */
private fun TextLayoutResult.isWholeWithin(constraints: Constraints): Boolean =
    !didOverflowWidth && !didOverflowHeight &&
        (0 until lineCount).none { isLineEllipsized(it) } &&
        (!constraints.hasBoundedWidth || multiParagraph.minIntrinsicWidth <= constraints.maxWidth + 0.5f)

/**
 * The most a text that must be read whole may take of a row [rowWidth] wide, beside a text
 * that may end in an ellipsis and would like [cutWanted] pixels, [gap] apart. The text that
 * may be cut keeps what it asks for, up to [cutShare] of the row; the whole one has the rest
 * to fit in, smaller or on more lines.
 *
 * Without this the whole text was measured first and took all it wanted: a long time of day
 * on a calendar row left the event's title no room at all.
 */
internal fun wholePartMaxWidth(rowWidth: Int, gap: Int, cutWanted: Int, cutShare: Float): Int {
    val kept = minOf(cutWanted, (rowWidth * cutShare).roundToInt())
    return (rowWidth - gap - kept).coerceAtLeast(0)
}

/**
 * Two texts on one row: [whole] where reading starts, which must be read to its end (a
 * time), and [cut] at the row's far end, which may end in an ellipsis (an event's title) and
 * keeps up to [cutShare] of the row for itself. See [wholePartMaxWidth] for how the row is
 * shared.
 */
@Composable
internal fun WholeBesideCut(
    gap: Dp,
    cutShare: Float,
    modifier: Modifier = Modifier,
    whole: @Composable () -> Unit,
    cut: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            Box { whole() }
            Box { cut() }
        },
    ) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val bounded = constraints.hasBoundedWidth
        val rowWidth = if (bounded) constraints.maxWidth else Constraints.Infinity
        val heightForWidths = if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity
        val wholeMax = if (bounded) {
            wholePartMaxWidth(rowWidth, gapPx, measurables[1].maxIntrinsicWidth(heightForWidths), cutShare)
        } else {
            Constraints.Infinity
        }
        val wholePlaceable = measurables[0].measure(loose.copy(maxWidth = wholeMax))
        val cutMax = if (bounded) (rowWidth - wholePlaceable.width - gapPx).coerceAtLeast(0) else Constraints.Infinity
        val cutPlaceable = measurables[1].measure(loose.copy(maxWidth = cutMax))
        val width = if (bounded) rowWidth else wholePlaceable.width + gapPx + cutPlaceable.width
        val height = maxOf(wholePlaceable.height, cutPlaceable.height).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            // Up and down as a row centres its texts, to the same pixel.
            wholePlaceable.placeRelative(0, Alignment.CenterVertically.align(wholePlaceable.height, height))
            cutPlaceable.placeRelative(width - cutPlaceable.width, Alignment.CenterVertically.align(cutPlaceable.height, height))
        }
    }
}
