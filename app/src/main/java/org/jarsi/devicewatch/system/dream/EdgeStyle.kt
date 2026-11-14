package org.jarsi.devicewatch.system.dream

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Edge: the battery's level as a bright line round the screen's edge, in a colour that
// runs from red to green with the level. The line replaces Glow's glow, bar and charge
// icon; the rest of the content is Glow's.

/**
 * Whether the screensaver's window shows colours beyond sRGB. The service sets the window's
 * colour mode and provides this; the settings preview leaves it false.
 */
internal val LocalDreamWideGamut = staticCompositionLocalOf { false }

/** The nearly black groove the line runs in, so the way still to go can be seen. */
private val EDGE_TRACK = Color(0xFF141516)

/** The lane the line stands in when nothing moves (the settings preview). */
private const val EDGE_STILL_LANE = 1

/** How far the content stays from the screen's edge beyond a style's usual paddings. */
private val EDGE_CLEARANCE = 20.dp

/**
 * The glow round the core: two wider strokes under it. Together they reach 4 dp beyond the
 * core on each side and are, at their brightest, under a third as bright as the core, so
 * the burn-in rule that holds for the core's pixels is not undone by its glow.
 */
private val EDGE_GLOW_OUTER = 4.dp
private val EDGE_GLOW_INNER = 2.dp
private const val EDGE_GLOW_OUTER_ALPHA = 0.10f
private const val EDGE_GLOW_INNER_ALPHA = 0.22f

/** The head's diameter in core widths: steady, and at the two ends of its pulse. */
private const val EDGE_HEAD_STEADY = 2.25f
private const val EDGE_HEAD_PULSE_MIN = 1.75f
private const val EDGE_HEAD_PULSE_MAX = 4.25f
private const val EDGE_HEAD_PULSE_MIN_ALPHA = 0.55f

/** The faint disc round the head, twice the head's size. */
private const val EDGE_HEAD_HALO_ALPHA = 0.25f

/** Remembers when the level or the state last changed, from one tick to the next. */
private class EdgeRestHolder {
    var rest: EdgeRest? = null
}

/**
 * The line. It is drawn into the frame's background, so it neither moves with the content's
 * burn-in shift nor turns with the flip; it has its own protection instead. Its core
 * changes lane once a minute, it rests dimmer when the battery has stood still for ten
 * minutes, and when the layout is flipped the whole drawing is turned so the line still
 * starts at the viewer's bottom centre.
 */
@Composable
internal fun BoxScope.EdgeBackground(state: DreamUiState, swapped: Boolean) {
    val moving = LocalDreamMoving.current
    val wideGamut = LocalDreamWideGamut.current
    val density = LocalDensity.current
    val view = LocalView.current
    val level = state.batteryLevel.coerceIn(0, 100)
    val chargeState = state.chargeState

    // The screen's own corner radius, where Android tells it (API 31+). In the running
    // screensaver a canvas pixel is a screen pixel; in the preview the canvas is a small
    // picture of a phone, and the corner keeps its size in the pictured phone's dp.
    val cornerPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius
    } else {
        null
    }
    val cornerDp = cornerPx?.let { px ->
        px / (if (moving) density.density else view.resources.displayMetrics.density)
    }

    val lane = if (moving) edgeLane(state.clockMillis / 60_000L) else EDGE_STILL_LANE
    val laneInsetDp: State<Float> = animateFloatAsState(
        targetValue = EDGE_LANE_INSETS_DP[lane],
        animationSpec = if (moving) tween(BURN_IN_GLIDE_MS, easing = LinearOutSlowInEasing) else snap(),
        label = "edgeLane",
    )

    // Plain memory between ticks; the brightness that follows from it is what is observed.
    val holder = remember { EdgeRestHolder() }
    val rest = edgeRest(holder.rest, level, chargeState, state.clockMillis)
    holder.rest = rest
    val brightness: State<Float> = animateFloatAsState(
        targetValue = if (moving) edgeBrightness(rest, state.clockMillis) else 1f,
        animationSpec = if (moving) tween(2000) else snap(),
        label = "edgeRest",
    )

    // The head pulses only while the battery is filling; composed only then, so the other
    // states produce no animation frames.
    val pulse: State<Float>? = if (moving && chargeState.pulses) {
        rememberInfiniteTransition(label = "edgeHead").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "edgeHeadPulse",
        )
    } else {
        null
    }

    val lineColor = edgeColor(level, wideGamut)
    val headColor = lerp(lineColor, Color.White, 0.27f)
    val fraction = level / 100f

    Box(
        modifier = Modifier
            .matchParentSize()
            .drawWithCache {
                // Rebuilt when the size or the lane changes; the pulse and the rest are read
                // in the draw block below and only redraw.
                val insetDp = laneInsetDp.value
                val inset = insetDp.dp.toPx()
                val radius = edgeCornerRadius(cornerDp, insetDp, size.width.toDp().value, size.height.toDp().value)
                    .dp.toPx()
                val core = EDGE_CORE_DP.dp.toPx()
                val route = edgePath(size.width, size.height, inset, radius)
                val lit = Path()
                if (fraction > 0f) {
                    val measure = PathMeasure()
                    measure.setPath(route, forceClosed = false)
                    measure.getSegment(0f, fraction * measure.length, lit, startWithMoveTo = true)
                }
                val (headX, headY) = edgePoint(fraction, size.width, size.height, inset, radius)
                val outerGlow = Stroke(width = core + 2 * EDGE_GLOW_OUTER.toPx(), cap = StrokeCap.Round)
                val innerGlow = Stroke(width = core + 2 * EDGE_GLOW_INNER.toPx(), cap = StrokeCap.Round)
                val coreStroke = Stroke(width = core, cap = StrokeCap.Round)
                val restPaint = Paint()

                onDrawBehind {
                    drawRect(Color.Black)
                    rotate(degrees = if (swapped) 180f else 0f) {
                        drawPath(route, EDGE_TRACK, style = Stroke(width = core))
                        // Everything lit is one picture, and resting dims that picture as a
                        // whole. Dimmed one by one, the glow, the core, the halo and the
                        // head would show through each other and add up to more than the
                        // rest allows wherever they overlap.
                        val restAlpha = brightness.value
                        val resting = restAlpha < 1f
                        val canvas = drawContext.canvas
                        if (resting) {
                            restPaint.alpha = restAlpha
                            canvas.saveLayer(size.toRect(), restPaint)
                        }
                        if (fraction > 0f) {
                            drawPath(lit, lineColor.copy(alpha = EDGE_GLOW_OUTER_ALPHA), style = outerGlow)
                            drawPath(lit, lineColor.copy(alpha = EDGE_GLOW_INNER_ALPHA), style = innerGlow)
                            drawPath(lit, lineColor, style = coreStroke)
                        }
                        // A full battery is a closed loop with no head.
                        if (chargeState != DreamChargeState.FULL) {
                            val beat = pulse?.value
                            val diameter = core * if (beat == null) {
                                EDGE_HEAD_STEADY
                            } else {
                                EDGE_HEAD_PULSE_MIN + (EDGE_HEAD_PULSE_MAX - EDGE_HEAD_PULSE_MIN) * beat
                            }
                            val headAlpha = if (beat == null) {
                                1f
                            } else {
                                EDGE_HEAD_PULSE_MIN_ALPHA + (1f - EDGE_HEAD_PULSE_MIN_ALPHA) * beat
                            }
                            val center = Offset(headX, headY)
                            drawCircle(
                                lineColor.copy(alpha = EDGE_HEAD_HALO_ALPHA * headAlpha),
                                radius = diameter,
                                center = center,
                            )
                            drawCircle(headColor.copy(alpha = headAlpha), radius = diameter / 2f, center = center)
                        }
                        if (resting) canvas.restore()
                    }
                }
            }
    )
}

@Composable
internal fun BoxWithConstraintsScope.EdgeContent(state: DreamUiState, options: DreamOptions, landscape: Boolean) {
    val insets = dreamInsets(maxWidth, maxHeight)
    // Further in than the other styles: the content shifts against burn-in and must not
    // slide over the line.
    val horizontalPadding = insets.horizontal + EDGE_CLEARANCE
    val verticalPadding = insets.vertical + EDGE_CLEARANCE
    val deviceName = state.deviceName.takeIf { options.shows(DreamField.DEVICE_NAME) }
    val notificationGroups =
        if (options.shows(DreamField.NOTIFICATIONS)) state.notificationGroups else emptyList()
    val alarmText = if (options.shows(DreamField.ALARM) && state.alarmText.isNotEmpty()) {
        "⏰ " + state.alarmText
    } else {
        ""
    }
    val showSeconds = options.shows(DreamField.SECONDS)
    val showDate = options.shows(DreamField.DATE)
    val hasClockSide = showSeconds || state.amPmText.isNotEmpty()

    val clockAvailableWidth = if (landscape) {
        (maxWidth - horizontalPadding * 2 - 1.5.dp - insets.centerGap) * 0.58f
    } else {
        maxWidth - horizontalPadding * 2
    }
    val clockSize = rememberFittedClockSize(
        availableWidth = clockAvailableWidth,
        maxClockSp = if (landscape) 82 else 120,
        minClockSp = if (landscape) 40 else 48,
        secondsRatio = if (hasClockSide) 0.25f else 0f,
        secondsMinSp = if (hasClockSide) 22 else 0,
        gap = if (hasClockSide) 8.dp else 0.dp,
    )
    val secondsSize = (clockSize * 0.25f).toInt().coerceAtLeast(22)

    if (landscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClockBlock(
                modifier = Modifier.weight(0.58f),
                timeText = state.timeText,
                secondsText = state.secondsText,
                dateText = state.dateText,
                clockSize = clockSize,
                secondsSize = secondsSize,
                dateSize = if (clockSize < 100) 22 else 30,
                amPmText = state.amPmText,
                alarmText = alarmText,
                deviceName = deviceName,
                notificationGroups = notificationGroups,
                notificationPulseToken = state.notificationPulseToken,
                showSeconds = showSeconds,
                showDate = showDate,
                accent = options.accent.color,
            )
            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .fillMaxHeight(0.6f)
                    .background(Color(0x59FFFFFF))
            )
            Spacer(modifier = Modifier.width(insets.centerGap))
            EdgeBattery(
                state = state,
                options = options,
                modifier = Modifier.weight(0.42f),
                alignment = Alignment.Start,
                percentSize = 58,
                wordSize = 22,
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))
            ClockBlock(
                timeText = state.timeText,
                secondsText = state.secondsText,
                dateText = state.dateText,
                clockSize = clockSize,
                secondsSize = secondsSize,
                dateSize = if (clockSize < 100) 22 else 28,
                amPmText = state.amPmText,
                alarmText = alarmText,
                deviceName = deviceName,
                notificationGroups = notificationGroups,
                notificationPulseToken = state.notificationPulseToken,
                showSeconds = showSeconds,
                showDate = showDate,
                accent = options.accent.color,
            )
            Spacer(modifier = Modifier.weight(1f))
            EdgeBattery(
                state = state,
                options = options,
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
                alignment = Alignment.CenterHorizontally,
                percentSize = 64,
                wordSize = 26,
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/** Glow's battery block without the bar and the icon: the word, the level, the figures. */
@Composable
private fun EdgeBattery(
    state: DreamUiState,
    options: DreamOptions,
    modifier: Modifier,
    alignment: Alignment.Horizontal,
    percentSize: Int,
    wordSize: Int,
) {
    val accent = edgeTextColor(state.batteryLevel)
    Column(modifier = modifier, horizontalAlignment = alignment, verticalArrangement = Arrangement.Center) {
        Text(
            text = stringResource(state.chargeState.label),
            autoSize = fitUpTo(wordSize.sp),
            maxLines = 1,
            softWrap = false,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        EdgeGap(6.dp)
        Text(
            text = "${state.batteryLevel}%",
            autoSize = fitUpTo(percentSize.sp),
            maxLines = 1,
            softWrap = false,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFEDEDED),
            style = TextStyle(fontFeatureSettings = "tnum")
        )
        if (options.shows(DreamField.BATTERY_DETAILS)) {
            EdgeGap(10.dp)
            Text(
                text = glowDetailsText(state),
                autoSize = fitUpTo(24.sp, floorSp = 9f),
                maxLines = 1,
                softWrap = false,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF9AA0A8),
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }
        if (options.shows(DreamField.ESTIMATE) && state.fullTimeText.isNotEmpty()) {
            EdgeGap(6.dp)
            Text(
                text = state.fullTimeText,
                // The sentence with a date in it is the longest text there is; in the narrow
                // column of a small phone on its side it has to get very small to stay whole.
                autoSize = fitUpTo(24.sp, floorSp = 6f),
                maxLines = 1,
                softWrap = false,
                fontWeight = FontWeight.Bold,
                color = accent,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }
    }
}

@Composable
private fun EdgeGap(height: Dp) {
    Spacer(modifier = Modifier.height(height))
}
