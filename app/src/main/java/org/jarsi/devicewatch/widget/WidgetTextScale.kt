package org.jarsi.devicewatch.widget

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.glance.LocalContext
import org.jarsi.devicewatch.presentation.ui.MAX_FONT_SCALE

/**
 * A home-screen widget gets a fixed grid cell: when the user raises the system
 * font size or display size, the content grows but the cell does not, so rows
 * clip and figures lose their digits. RemoteViews has no autosizing text and no
 * way to opt out of either setting, so every size in the widgets goes through
 * [widgetSp] or [widgetDp]: the font follows the same cap as the app's screens,
 * and the whole content is scaled down when the height the launcher gives the
 * widget is less than the content needs ([widgetSpaceScale]).
 *
 * Nothing here asks for the device's default density. Android's figure for it
 * (DENSITY_DEVICE_STABLE) can be a 160 dpi fallback where an app cannot read the
 * real one, which once drew a Fairphone 6's widget at a third of its size; the
 * space the widget actually has is the one measure that cannot be wrong.
 */
const val MAX_WIDGET_FONT_SCALE = MAX_FONT_SCALE

/** Below this the labels stop being readable, so a shrunk system font stops here. */
const val MIN_WIDGET_FONT_SCALE = 0.85f

/** The fit to a short widget stops here; below it the content stays readable and the bottom clips. */
const val MIN_WIDGET_SPACE_SCALE = 0.7f

/** A name or label shrinks to fit its width down to this share of its size, then it is cut short. */
const val NAME_MIN_SIZE_RATIO = 0.75f

/**
 * Height of one line of widget text per dp of its size. RemoteViews text keeps the
 * font's padding: the battery tile's 12 dp heading measured 1.42 times its size on a
 * Fairphone 6 and 1.49 times on a Pixel 8a.
 */
const val WIDGET_LINE_HEIGHT = 1.45f

fun clampedWidgetFontScale(systemScale: Float): Float {
    if (!systemScale.isFinite() || systemScale <= 0f) return 1f
    return systemScale.coerceIn(MIN_WIDGET_FONT_SCALE, MAX_WIDGET_FONT_SCALE)
}

/**
 * The share of their full size the widget's contents are drawn at: 1 when the
 * launcher gives the widget at least [neededDp] of height, less when it gives
 * [availableDp] only, but never below [MIN_WIDGET_SPACE_SCALE].
 */
fun widgetSpaceScale(availableDp: Float, neededDp: Float): Float {
    val usable = availableDp.isFinite() && availableDp > 0f && neededDp.isFinite() && neededDp > 0f
    if (!usable) return 1f
    return (availableDp / neededDp).coerceIn(MIN_WIDGET_SPACE_SCALE, 1f)
}

/** A layout size in the widgets, in dp: [baseDp] fitted to the widget's height. */
fun widgetSizeDp(baseDp: Float, spaceScale: Float): Float = baseDp * spaceScale

/** A text size in the widgets, in dp: [baseDp] under the font cap, fitted to the widget's height. */
fun widgetTextSizeDp(baseDp: Float, fontScale: Float, spaceScale: Float): Float =
    baseDp * clampedWidgetFontScale(fontScale) * spaceScale

/** Height of one line of text of [baseDp] at [fontScale], before any fit, for the widgets' height sums. */
fun widgetLineDp(baseDp: Float, fontScale: Float): Float =
    baseDp * clampedWidgetFontScale(fontScale) * WIDGET_LINE_HEIGHT

/**
 * Proportionally smaller size for text that measures [widthAtSizeDp] at
 * [sizeDp] and must fit [availableDp]; text that already fits keeps its size,
 * and no text goes below [minDp].
 */
fun shrinkToFitDp(sizeDp: Float, widthAtSizeDp: Float, availableDp: Float, minDp: Float = 0f): Float {
    val measurable = widthAtSizeDp.isFinite() && widthAtSizeDp > 0f &&
        availableDp.isFinite() && availableDp > 0f
    if (!measurable || widthAtSizeDp <= availableDp) return sizeDp
    return maxOf(sizeDp * availableDp / widthAtSizeDp, minOf(minDp, sizeDp))
}

/**
 * [text] as it fits [availableDp] by [widthOf]: whole when it fits, otherwise
 * the longest start that fits with "…" after it, never cut inside an emoji or
 * with a space before the ellipsis. Nothing but "…" when not even one character fits.
 */
fun ellipsizeToFit(text: String, availableDp: Float, widthOf: (String) -> Float): String {
    if (widthOf(text) <= availableDp) return text
    for (end in text.length - 1 downTo 1) {
        if (Character.isHighSurrogate(text[end - 1])) continue
        val candidate = text.substring(0, end).trimEnd() + ELLIPSIS
        if (candidate.length > ELLIPSIS.length && widthOf(candidate) <= availableDp) return candidate
    }
    return ELLIPSIS
}

private const val ELLIPSIS = "…"

/** The share [widgetSpaceScale] gave this widget, provided once at the root of its content. */
val LocalWidgetSpaceScale = compositionLocalOf { 1f }

/** A layout size in the widgets: [baseDp] fitted to the widget's height. */
@Composable
fun widgetDp(baseDp: Float): Dp = widgetSizeDp(baseDp, LocalWidgetSpaceScale.current).dp

/** A text size in the widgets, in dp, with the font cap and the fit to the widget's height applied. */
@Composable
fun widgetTextDp(baseDp: Float): Float =
    widgetTextSizeDp(baseDp, LocalContext.current.resources.configuration.fontScale, LocalWidgetSpaceScale.current)

/** The system font scale the widgets size their text with, for their height sums. */
@Composable
fun widgetFontScale(): Float = LocalContext.current.resources.configuration.fontScale

/**
 * [sizeDp] converted to the sp the platform expects. [Density] carries the
 * device's font scale, so the conversion also covers Android 14+'s non-linear
 * scaling curve, where a plain division by the scale factor would be wrong.
 */
@Composable
fun spForDp(sizeDp: Float): TextUnit = with(Density(LocalContext.current)) { sizeDp.dp.toSp() }

/** Text size for widget content: [widgetTextDp] as sp. */
@Composable
fun widgetSp(baseDp: Float): TextUnit = spForDp(widgetTextDp(baseDp))

/** Width of [text] at [sizeDp], in dp, in the platform's default typeface. */
@Composable
fun textWidthDp(text: String, sizeDp: Float, bold: Boolean = true): Float =
    measureTextWidthDp(text, sizeDp, bold, LocalContext.current.resources.displayMetrics.density)

private fun measureTextWidthDp(text: String, sizeDp: Float, bold: Boolean, density: Float): Float {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textSize = sizeDp * density
    }
    return paint.measureText(text) / density
}

/**
 * [sizeDp] shrunk until [text] fits [availableDp]. For figures, which must stay
 * whole: they have no floor. The width is measured with the platform's default
 * typeface, which is what the launcher draws widget text in; the margin absorbs
 * launchers that substitute a slightly wider face.
 */
@Composable
fun fittedTextDp(text: String, sizeDp: Float, availableDp: Float, bold: Boolean = true): Float =
    shrinkToFitDp(sizeDp, textWidthDp(text, sizeDp, bold), availableDp * FIT_MARGIN)

/** A name or label as it is drawn: possibly cut short, at its fitted size. */
class FittedText(val text: String, val sizeDp: Float)

/**
 * A name or label (a network's name, a heading) fitted to [availableDp]: it
 * shrinks down to [NAME_MIN_SIZE_RATIO] of [sizeDp] and is cut with "…" past
 * that, so it never becomes too small to read.
 */
@Composable
fun fittedName(text: String, sizeDp: Float, availableDp: Float, bold: Boolean = true): FittedText {
    val density = LocalContext.current.resources.displayMetrics.density
    return fitName(text, sizeDp, availableDp * FIT_MARGIN, sizeDp * NAME_MIN_SIZE_RATIO) { shown, size ->
        measureTextWidthDp(shown, size, bold, density)
    }
}

/**
 * [fittedName]'s fit, with [widthAt] measuring text at a size. Glyph widths are
 * rounded, so a size shrunk in proportion can still measure a hair too wide; the
 * size is measured again and stepped down until the whole text fits, and only a
 * text that does not fit at [minDp] is cut.
 */
fun fitName(
    text: String,
    sizeDp: Float,
    availableDp: Float,
    minDp: Float,
    widthAt: (String, Float) -> Float,
): FittedText {
    val floor = minOf(minDp, sizeDp)
    var size = shrinkToFitDp(sizeDp, widthAt(text, sizeDp), availableDp, floor)
    var refits = 0
    while (size > floor && refits++ < MAX_REFITS) {
        val width = widthAt(text, size)
        if (!width.isFinite() || width <= availableDp) break
        size = maxOf(floor, minOf(size - REFIT_STEP_DP, size * availableDp / width))
    }
    return FittedText(ellipsizeToFit(text, availableDp) { widthAt(it, size) }, size)
}

/** Enough steps to cross from any size to its floor; each one measures once. */
private const val MAX_REFITS = 40
private const val REFIT_STEP_DP = 0.1f

private const val FIT_MARGIN = 0.92f
