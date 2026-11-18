package org.jarsi.devicewatch.system.dream

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Glow: the screensaver's look since 1.0 and the default style. A heavy clock, a glow at
// the bottom tinted by the battery level, a bar and every figure.

@Composable
internal fun BoxScope.GlowBackground(level: Int, swapped: Boolean) {
    Box(
        modifier = Modifier
            .matchParentSize()
            // Dark vertical gradient tinted by the battery level: the viewer's top stays pure
            // black (clock legibility, burn-in) and the battery-colored tone builds up toward
            // the viewer's bottom. The background does not rotate with the 180° layout flip,
            // so when the layout is swapped (phone docked upside down) the gradient is
            // mirrored to keep the glow at the bottom of what the user actually sees.
            .background(
                Brush.verticalGradient(
                    colorStops = if (swapped) {
                        arrayOf(
                            0.0f to batteryTintDeep(level),
                            0.6f to Color(0xFF000000),
                            1.0f to Color(0xFF000000)
                        )
                    } else {
                        arrayOf(
                            0.0f to Color(0xFF000000),
                            0.4f to Color(0xFF000000),
                            1.0f to batteryTintDeep(level)
                        )
                    }
                )
            )
    )
}

@Composable
internal fun BoxWithConstraintsScope.GlowContent(state: DreamUiState, options: DreamOptions, landscape: Boolean) {
    val chargeState = state.chargeState
    val batteryLevel = state.batteryLevel
    val detailsStr = glowDetailsText(state)
    val showDetails = options.shows(DreamField.BATTERY_DETAILS)
    val fullTimeStr = if (options.shows(DreamField.ESTIMATE)) state.fullTimeText else ""
    val showSeconds = options.shows(DreamField.SECONDS)
    val showDate = options.shows(DreamField.DATE)
    // The column beside the time: the seconds, or AM/PM alone on a 12-hour clock.
    val hasClockSide = showSeconds || state.amPmText.isNotEmpty()
    val batteryGradientColors = getBatteryGradientColors(batteryLevel)
    val deviceName = state.deviceName.takeIf { options.shows(DreamField.DEVICE_NAME) }
    val notificationGroups =
        if (options.shows(DreamField.NOTIFICATIONS)) state.notificationGroups else emptyList()
    val alarmText = if (options.shows(DreamField.ALARM) && state.alarmText.isNotEmpty()) {
        "⏰ " + state.alarmText
    } else {
        ""
    }

    // Soft pulse on the charging bolt; composed only while the battery is filling, so a
    // full or held battery shows a steady icon and produces no animation frames.
    val boltAlpha = if (chargeState.pulses && LocalDreamMoving.current) {
        val boltTransition = rememberInfiniteTransition(label = "boltPulse")
        val pulsedAlpha by boltTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "boltAlpha"
        )
        pulsedAlpha
    } else {
        1f
    }

    val insets = dreamInsets(maxWidth, maxHeight)
    val horizontalPadding = insets.horizontal
    val verticalPadding = insets.vertical
    val centerGap = insets.centerGap

    // The clock auto-sizes to the largest font that still fits the available width, so the
    // seconds never wrap or overlap on narrow phones (e.g. Pixel 9a portrait). The width
    // budget differs per orientation: portrait gets the full inner width; landscape gets
    // the clock column's weighted (0.58) share of the row after padding/divider/gap.
    val clockAvailableWidth = if (landscape) {
        (maxWidth - horizontalPadding * 2 - 1.5.dp - centerGap) * 0.58f
    } else {
        maxWidth - horizontalPadding * 2
    }
    val clockSize = rememberFittedClockSize(
        availableWidth = clockAvailableWidth,
        maxClockSp = if (landscape) 82 else 120,
        minClockSp = if (landscape) 48 else 56,
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

            Spacer(modifier = Modifier.width(centerGap))

            BatteryBlockLandscape(
                modifier = Modifier.weight(0.42f),
                chargeState = chargeState,
                batteryLevel = batteryLevel,
                batteryGradientColors = batteryGradientColors,
                detailsStr = detailsStr,
                fullTimeStr = fullTimeStr,
                boltAlpha = boltAlpha,
                showDetails = showDetails
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

            BatteryBlockPortrait(
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
                chargeState = chargeState,
                batteryLevel = batteryLevel,
                batteryGradientColors = batteryGradientColors,
                detailsStr = detailsStr,
                fullTimeStr = fullTimeStr,
                boltAlpha = boltAlpha,
                showDetails = showDetails
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * How small the landscape block's estimate line may get. The sentence with a date in it is the
 * longest text on the screensaver: on a 480 dp wide phone on its side, at the largest font
 * scale, it needs a third more room than the usual 12 sp floor leaves it.
 */
private const val NARROW_ESTIMATE_FLOOR_SP = 8f

/**
 * The bar's colours in the order they are painted from the left. The bar fills from the side
 * where reading starts, so in a language written from the right they turn round with it.
 */
internal fun batteryBarColors(colors: List<Color>, direction: LayoutDirection): List<Color> =
    if (direction == LayoutDirection.Rtl) colors.reversed() else colors

@Composable
internal fun BatteryBlockLandscape(
    modifier: Modifier = Modifier,
    chargeState: DreamChargeState,
    batteryLevel: Int,
    batteryGradientColors: List<Color>,
    detailsStr: String,
    fullTimeStr: String,
    boltAlpha: Float = 1f,
    showDetails: Boolean = true
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = chargeState.icon,
                fontSize = 30.sp,
                color = if (chargeState == DreamChargeState.ON_BATTERY) Color(0xFF34D399) else Color(0xFFFBBF24),
                modifier = Modifier.graphicsLayer { alpha = boltAlpha }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(chargeState.label),
                autoSize = fitUpTo(22.sp),
                maxLines = 1,
                softWrap = false,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF34D399)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "$batteryLevel%",
            fontSize = 58.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFEDEDED),
            style = TextStyle(fontFeatureSettings = "tnum")
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(11.dp)
                .clip(RoundedCornerShape(5.5.dp))
                .background(Color(0x33FFFFFF))
                .border(
                    1.dp,
                    Color(0x59FFFFFF),
                    RoundedCornerShape(5.5.dp)
                )
        ) {
            val activeFraction = batteryLevel / 100f
            Box(
                modifier = Modifier
                    .fillMaxWidth(activeFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.5.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = batteryBarColors(batteryGradientColors, LocalLayoutDirection.current)
                        )
                    )
            )
        }

        if (showDetails) {
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = detailsStr,
                autoSize = fitUpTo(24.sp),
                maxLines = 1,
                softWrap = false,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF9AA0A8),
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }

        if (fullTimeStr.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = fullTimeStr,
                autoSize = fitUpTo(24.sp, floorSp = NARROW_ESTIMATE_FLOOR_SP),
                maxLines = 1,
                softWrap = false,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF34D399),
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }
    }
}

@Composable
internal fun BatteryBlockPortrait(
    modifier: Modifier = Modifier,
    chargeState: DreamChargeState,
    batteryLevel: Int,
    batteryGradientColors: List<Color>,
    detailsStr: String,
    fullTimeStr: String,
    boltAlpha: Float = 1f,
    showDetails: Boolean = true
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = chargeState.icon,
                fontSize = 32.sp,
                color = if (chargeState == DreamChargeState.ON_BATTERY) Color(0xFF34D399) else Color(0xFFFBBF24),
                modifier = Modifier.graphicsLayer { alpha = boltAlpha }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(chargeState.label),
                autoSize = fitUpTo(26.sp),
                maxLines = 1,
                softWrap = false,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF34D399)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "$batteryLevel%",
            fontSize = 64.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFEDEDED),
            style = TextStyle(fontFeatureSettings = "tnum")
        )

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0x33FFFFFF))
                .border(
                    1.dp,
                    Color(0x59FFFFFF),
                    RoundedCornerShape(5.dp)
                )
        ) {
            val activeFraction = batteryLevel / 100f
            Box(
                modifier = Modifier
                    .fillMaxWidth(activeFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = batteryBarColors(batteryGradientColors, LocalLayoutDirection.current)
                        )
                    )
            )
        }

        if (showDetails) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = detailsStr,
                autoSize = fitUpTo(24.sp),
                maxLines = 1,
                softWrap = false,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF9AA0A8),
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }

        if (fullTimeStr.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = fullTimeStr,
                autoSize = fitUpTo(24.sp),
                maxLines = 1,
                softWrap = false,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF34D399),
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }
    }
}
