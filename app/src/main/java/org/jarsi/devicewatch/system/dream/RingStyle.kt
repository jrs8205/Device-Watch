package org.jarsi.devicewatch.system.dream

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import org.jarsi.devicewatch.R
import kotlin.math.hypot
import kotlin.math.max
import org.jarsi.devicewatch.presentation.ui.valueTextStyle

// Ring: the clock inside a ring that fills clockwise with the battery, like a watch face,
// on a very dark glow of the battery's colour.

private val RING_TRACK = Color(0xFF1B1D1F)
private val RING_TEXT = Color(0xFFEDEDED)
private val RING_DIM = Color(0xFF8B929C)
private val RING_DATE = Color(0xFF7B8189)

/** The ring's stroke as a share of its diameter. */
private const val RING_STROKE = 0.035f

/** The time's width as a share of the ring's diameter. */
private const val RING_CLOCK_WIDTH = 0.7f

/** How much of a full turn the ring is filled, in degrees from 12 o'clock clockwise. */
internal fun ringSweepDegrees(level: Int): Float = level.coerceIn(0, 100) * 3.6f

/** The background glow's colour: the battery's three bands, very dark. */
internal fun ringTint(level: Int): Color = when {
    level <= 20 -> Color(0xFF200908)
    level <= 60 -> Color(0xFF1F1805)
    else -> Color(0xFF0A1F18)
}

/**
 * Where the glow's middle is, as shares of the screen's width and height. Upright the ring
 * sits a little above the middle. On its side it sits in the half where reading starts: the
 * left one, or the right one in a language written from the right. A flipped layout mirrors
 * both.
 */
internal fun ringGlowCenter(landscape: Boolean, swapped: Boolean, rtl: Boolean): Offset =
    if (landscape) {
        Offset(if (swapped != rtl) 0.73f else 0.27f, 0.5f)
    } else {
        Offset(0.5f, if (swapped) 0.54f else 0.46f)
    }

/**
 * The parts of the line under the level: the charger's state with its icon, then the
 * charging power and the temperature when the battery details are switched on.
 */
internal fun ringStatusParts(
    state: DreamUiState,
    icon: String,
    word: String,
    showDetails: Boolean,
): List<String> = listOfNotNull(
    "$icon ${word.lowercase()}",
    state.wattsText.takeIf { showDetails },
    state.tempText.takeIf { showDetails },
)

/**
 * A radial glow behind the ring. The background does not turn with the 180° flip and does
 * not know the layout, so it finds the ring itself (see [ringGlowCenter]).
 */
@Composable
internal fun BoxScope.RingBackground(level: Int, swapped: Boolean) {
    val tint = ringTint(level)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        modifier = Modifier
            .matchParentSize()
            .drawBehind {
                drawRect(Color.Black)
                val landscape = size.width > size.height
                val share = ringGlowCenter(landscape, swapped, rtl)
                val center = Offset(size.width * share.x, size.height * share.y)
                val farthestCorner = hypot(
                    max(center.x, size.width - center.x),
                    max(center.y, size.height - center.y),
                )
                drawRect(
                    Brush.radialGradient(
                        0f to tint,
                        1f to Color.Black,
                        center = center,
                        radius = farthestCorner * 0.62f,
                    )
                )
            }
    )
}

@Composable
internal fun BoxWithConstraintsScope.RingContent(state: DreamUiState, options: DreamOptions, landscape: Boolean) {
    val insets = dreamInsets(maxWidth, maxHeight)
    val innerWidth = maxWidth - insets.horizontal * 2
    val innerHeight = maxHeight - insets.vertical * 2
    if (landscape) {
        val columnWidth = (innerWidth - insets.centerGap) / 2
        val diameter = min(min(columnWidth, innerHeight), maxHeight * 0.8f)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = insets.horizontal, vertical = insets.vertical),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                RingClock(state, options, diameter)
            }
            Spacer(modifier = Modifier.width(insets.centerGap))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                RingHeader(state, options, gapBelow = 14.dp)
                RingBattery(state, options)
            }
        }
    } else {
        val diameter = min(innerWidth, maxHeight * 0.5f)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = insets.horizontal, vertical = insets.vertical),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            RingHeader(state, options, gapBelow = 26.dp)
            // The lines above and below take the room they need first. On a short screen
            // with everything switched on, the ring is what gives way.
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .sizeIn(maxWidth = diameter, maxHeight = diameter)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                RingClock(state, options, min(maxWidth, maxHeight))
            }
            Spacer(modifier = Modifier.height(28.dp))
            RingBattery(state, options)
        }
    }
}

/**
 * The lines over the ring (beside it on a phone on its side): the phone's name, the date and
 * the next alarm, each as far as it is switched on.
 */
@Composable
private fun RingHeader(state: DreamUiState, options: DreamOptions, gapBelow: Dp) {
    val deviceName = state.deviceName.takeIf { options.shows(DreamField.DEVICE_NAME) }
    val showDate = options.shows(DreamField.DATE)
    val alarmText = state.alarmText.takeIf { options.shows(DreamField.ALARM) && it.isNotEmpty() }
    if (deviceName == null && !showDate && alarmText == null) return
    val locale = LocalConfiguration.current.locales[0]
    if (deviceName != null) {
        val wrapAlign1 = rememberWrapAlign(TextAlign.Center)
        Text(
            text = deviceName,
            // The user's own text: by its first letter, never by the screen's direction.
            style = valueTextStyle(),
            autoSize = fitUpTo(13.sp),
            maxLines = DREAM_LINE_MAX_LINES,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
            textAlign = wrapAlign1.textAlign,
            onTextLayout = wrapAlign1.onTextLayout,
            fontWeight = FontWeight.Medium,
            color = RING_DIM,
            letterSpacing = 1.5.sp
        )
        if (showDate || alarmText != null) Spacer(modifier = Modifier.height(6.dp))
    }
    if (showDate) {
        val wrapAlign2 = rememberWrapAlign(TextAlign.Center)
        Text(
            text = state.shortDateText.uppercase(locale),
            autoSize = fitUpTo(16.sp),
            maxLines = DREAM_LINE_MAX_LINES,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
            textAlign = wrapAlign2.textAlign,
            onTextLayout = wrapAlign2.onTextLayout,
            color = RING_DATE,
            letterSpacing = 0.22.em
        )
        if (alarmText != null) Spacer(modifier = Modifier.height(6.dp))
    }
    if (alarmText != null) {
        val wrapAlign3 = rememberWrapAlign(TextAlign.Center)
        Text(
            text = "⏰ $alarmText",
            autoSize = fitUpTo(13.sp),
            maxLines = DREAM_LINE_MAX_LINES,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
            textAlign = wrapAlign3.textAlign,
            onTextLayout = wrapAlign3.onTextLayout,
            fontWeight = FontWeight.Medium,
            color = RING_DATE,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
    Spacer(modifier = Modifier.height(gapBelow))
}

/** The ring with the time inside it and, under the time, the seconds. */
@Composable
private fun RingClock(state: DreamUiState, options: DreamOptions, diameter: Dp) {
    val batteryColor = getBatteryGradientColors(state.batteryLevel).first()
    val sweep = ringSweepDegrees(state.batteryLevel)
    Box(modifier = Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = size.minDimension * RING_STROKE
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = RING_TRACK,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            if (sweep > 0f) {
                drawArc(
                    color = batteryColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        val clockSize = rememberFittedClockSize(
            availableWidth = diameter * RING_CLOCK_WIDTH,
            maxClockSp = 96,
            minClockSp = 16,
            secondsRatio = 0f,
            secondsMinSp = 0,
            gap = 0.dp,
            timeWeight = FontWeight.SemiBold,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = state.timeText,
                fontSize = clockSize.sp,
                fontWeight = FontWeight.SemiBold,
                color = RING_TEXT,
                maxLines = 1,
                softWrap = false,
                style = TextStyle(fontFeatureSettings = "tnum"),
                letterSpacing = 0.sp
            )
            val showSeconds = options.shows(DreamField.SECONDS)
            if (showSeconds || state.amPmText.isNotEmpty()) {
                LeftToRight {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val seconds: @Composable () -> Unit = {
                            Text(
                                text = state.secondsText,
                                fontSize = (clockSize * 0.26f).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = options.accent.color,
                                maxLines = 1,
                                softWrap = false,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                        }
                        val marker: @Composable () -> Unit = {
                            Text(
                                text = state.amPmText,
                                fontSize = (clockSize * 0.2f).sp,
                                fontWeight = FontWeight.Medium,
                                color = RING_DIM,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        // AM or PM on the side its language reads it (see amPmLeads).
                        val parts = listOfNotNull(
                            seconds.takeIf { showSeconds },
                            marker.takeIf { state.amPmText.isNotEmpty() },
                        ).let { if (state.amPmLeads) it.reversed() else it }
                        parts.forEachIndexed { index, part ->
                            if (index > 0) Spacer(modifier = Modifier.width(6.dp))
                            part()
                        }
                    }
                }
            }
        }
    }
}

/** The level, the line of figures under it and the notification icons. */
@Composable
private fun RingBattery(state: DreamUiState, options: DreamOptions) {
    val batteryColor = getBatteryGradientColors(state.batteryLevel).first()
    val groups = if (options.shows(DreamField.NOTIFICATIONS)) state.notificationGroups else emptyList()
    val parts = ringStatusParts(
        state = state,
        icon = state.chargeState.icon,
        word = stringResource(state.chargeState.label),
        showDetails = options.shows(DreamField.BATTERY_DETAILS),
    )
    val line = buildAnnotatedString {
        withStyle(SpanStyle(color = batteryColor)) { append(parts.first()) }
        for (part in parts.drop(1)) {
            append("  ·  ")
            append(part)
        }
    }
    Text(
        text = stringResource(R.string.dream_percent_spaced, state.batteryLevel),
        autoSize = fitUpTo(34.sp),
        maxLines = 1,
        softWrap = false,
        fontWeight = FontWeight.Bold,
        color = RING_TEXT,
        style = TextStyle(fontFeatureSettings = "tnum")
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = line,
        autoSize = fitUpTo(18.sp, floorSp = 9f),
        maxLines = 1,
        softWrap = false,
        color = RING_DIM,
        style = TextStyle(fontFeatureSettings = "tnum")
    )
    val estimate = state.fullTimeText.takeIf { options.shows(DreamField.ESTIMATE) && it.isNotEmpty() }
    if (estimate != null) {
        Spacer(modifier = Modifier.height(6.dp))
        val wrapAlign4 = rememberWrapAlign(TextAlign.Center)
        Text(
            text = estimate,
            // With a date in it the sentence is the longest text there is; beside the ring
            // on a small phone it has to get very small to stay whole.
            autoSize = fitUpTo(16.sp, floorSp = 6f),
            maxLines = DREAM_LINE_MAX_LINES,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
            textAlign = wrapAlign4.textAlign,
            onTextLayout = wrapAlign4.onTextLayout,
            color = RING_DIM,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
    if (groups.isNotEmpty()) {
        Spacer(modifier = Modifier.height(22.dp))
        NotificationIconRow(
            groups = groups,
            pulseToken = state.notificationPulseToken,
            iconSize = 20.dp,
        )
    }
}
