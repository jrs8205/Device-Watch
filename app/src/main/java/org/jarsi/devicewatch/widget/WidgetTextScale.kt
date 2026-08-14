package org.jarsi.devicewatch.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.glance.LocalContext

/**
 * A home-screen widget gets a fixed grid cell: when the user raises the system
 * font size, the text grows but the cell does not, so the bottom rows simply
 * disappear. RemoteViews has no autosizing text, and the platform offers no way
 * to opt a widget out of font scaling, so the sizes below are computed from dp
 * with the scale capped — the widget still honours a larger font, just not far
 * enough to clip itself. The in-app screens keep the full system scale; they
 * scroll, so nothing is ever lost there.
 */
const val MAX_WIDGET_FONT_SCALE = 1.3f

/** Below this the labels stop being readable, so a shrunk system font stops here. */
const val MIN_WIDGET_FONT_SCALE = 0.85f

fun clampedWidgetFontScale(systemScale: Float): Float {
    if (!systemScale.isFinite() || systemScale <= 0f) return 1f
    return systemScale.coerceIn(MIN_WIDGET_FONT_SCALE, MAX_WIDGET_FONT_SCALE)
}

/**
 * Text size for widget content, expressed in dp and converted back to the sp the
 * platform expects. [Density] carries the device's font scale, so the conversion
 * also covers Android 14+'s non-linear scaling curve, where a plain division by
 * the scale factor would be wrong.
 */
@Composable
fun widgetSp(baseDp: Float): TextUnit {
    val context = LocalContext.current
    val scale = clampedWidgetFontScale(context.resources.configuration.fontScale)
    return with(Density(context)) { (baseDp * scale).dp.toSp() }
}
