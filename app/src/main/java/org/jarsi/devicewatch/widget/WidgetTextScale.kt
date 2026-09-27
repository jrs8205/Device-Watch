package org.jarsi.devicewatch.widget

import android.graphics.Paint
import android.graphics.Typeface
import android.util.DisplayMetrics
import androidx.compose.runtime.Composable
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
 * [widgetSp] or [widgetDp], which apply the same font cap as the app's screens
 * and undo a larger display size.
 */
const val MAX_WIDGET_FONT_SCALE = MAX_FONT_SCALE

/** Below this the labels stop being readable, so a shrunk system font stops here. */
const val MIN_WIDGET_FONT_SCALE = 0.85f

fun clampedWidgetFontScale(systemScale: Float): Float {
    if (!systemScale.isFinite() || systemScale <= 0f) return 1f
    return systemScale.coerceIn(MIN_WIDGET_FONT_SCALE, MAX_WIDGET_FONT_SCALE)
}

/**
 * Factor that turns a dp size into the dp size it would have at the device's
 * default density. The launcher keeps a cell's pixel size when the display size
 * grows, so the widget's content keeps its default pixel size too. A smaller
 * display size passes through, like it does in the app.
 */
fun widgetDensityRatio(systemDensity: Float, defaultDensity: Float): Float {
    val usable = systemDensity.isFinite() && systemDensity > 0f &&
        defaultDensity.isFinite() && defaultDensity > 0f
    if (!usable || systemDensity <= defaultDensity) return 1f
    return defaultDensity / systemDensity
}

/**
 * Proportionally smaller size for text that measures [widthAtSizeDp] at
 * [sizeDp] and must fit [availableDp]; text that already fits keeps its size.
 */
fun shrinkToFitDp(sizeDp: Float, widthAtSizeDp: Float, availableDp: Float): Float {
    val measurable = widthAtSizeDp.isFinite() && widthAtSizeDp > 0f &&
        availableDp.isFinite() && availableDp > 0f
    if (!measurable || widthAtSizeDp <= availableDp) return sizeDp
    return sizeDp * availableDp / widthAtSizeDp
}

@Composable
private fun densityRatio(): Float {
    val metrics = LocalContext.current.resources.displayMetrics
    val defaultDensity = DisplayMetrics.DENSITY_DEVICE_STABLE / DisplayMetrics.DENSITY_DEFAULT.toFloat()
    return widgetDensityRatio(metrics.density, defaultDensity)
}

/** A layout size in the widgets: [baseDp] with a larger display size undone. */
@Composable
fun widgetDp(baseDp: Float): Dp = (baseDp * densityRatio()).dp

/** A text size in the widgets, in dp, with the font cap and the display-size bound applied. */
@Composable
fun widgetTextDp(baseDp: Float): Float {
    val scale = clampedWidgetFontScale(LocalContext.current.resources.configuration.fontScale)
    return baseDp * scale * densityRatio()
}

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
fun textWidthDp(text: String, sizeDp: Float, bold: Boolean = true): Float {
    val density = LocalContext.current.resources.displayMetrics.density
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textSize = sizeDp * density
    }
    return paint.measureText(text) / density
}

/**
 * [sizeDp] shrunk until [text] fits [availableDp]. The width is measured with
 * the platform's default typeface, which is what the launcher draws widget text
 * in; the margin absorbs launchers that substitute a slightly wider face.
 */
@Composable
fun fittedTextDp(text: String, sizeDp: Float, availableDp: Float, bold: Boolean = true): Float =
    shrinkToFitDp(sizeDp, textWidthDp(text, sizeDp, bold), availableDp * FIT_MARGIN)

private const val FIT_MARGIN = 0.92f
