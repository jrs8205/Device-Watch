package org.jarsi.devicewatch.system.dream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jarsi.devicewatch.R

// Minimal: pure black, a thin clock, the date in spaced capitals, one hairline for the
// battery and one line of figures. The battery's colour is the only colour.

private val MINIMAL_CLOCK = Color(0xFFF2F2F2)
private val MINIMAL_DIM = Color(0xFF7B8189)
private val MINIMAL_TRACK = Color(0xFF1F2123)

/** The notification row rests at 0.6; Minimal keeps it quieter still, at 0.45. */
private const val MINIMAL_ICON_ROW_ALPHA = 0.75f

/**
 * The parts of Minimal's one line: the level, the charger's state in lower case, and the
 * charging power when there is one and the battery details are switched on.
 */
internal fun minimalStatusParts(
    state: DreamUiState,
    percent: String,
    word: String,
    showWatts: Boolean,
): List<String> = listOfNotNull(percent, word.lowercase(), state.wattsText.takeIf { showWatts })

@Composable
internal fun BoxWithConstraintsScope.MinimalContent(state: DreamUiState, options: DreamOptions, landscape: Boolean) {
    val insets = dreamInsets(maxWidth, maxHeight)
    // The clock is the time alone, so its width budget has no seconds in it. On a 12-hour
    // clock there is no AM/PM either, like on Android's own lock screen clock.
    val clockAvailableWidth = if (landscape) {
        (maxWidth - insets.horizontal * 2 - insets.centerGap) * 0.58f
    } else {
        maxWidth - insets.horizontal * 2
    }
    val clockSize = rememberFittedClockSize(
        availableWidth = clockAvailableWidth,
        // Larger than Glow's caps: the time stands alone, and the approved mockup draws it
        // at about a third of the screen's width.
        maxClockSp = if (landscape) 96 else 140,
        minClockSp = if (landscape) 48 else 56,
        secondsRatio = 0f,
        secondsMinSp = 0,
        gap = 0.dp,
        timeWeight = FontWeight.ExtraLight,
    )

    if (landscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = insets.horizontal, vertical = insets.vertical),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MinimalClock(state, options, clockSize, modifier = Modifier.weight(0.58f))
            Spacer(modifier = Modifier.width(insets.centerGap))
            MinimalBattery(state, options, modifier = Modifier.weight(0.42f))
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = insets.horizontal, vertical = insets.vertical),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            MinimalClock(state, options, clockSize)
            Spacer(modifier = Modifier.weight(1f))
            MinimalBattery(state, options, modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp))
            Spacer(modifier = Modifier.height(this@MinimalContent.maxHeight * 0.05f))
        }
    }
}

@Composable
private fun MinimalClock(state: DreamUiState, options: DreamOptions, clockSize: Int, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val groups = if (options.shows(DreamField.NOTIFICATIONS)) state.notificationGroups else emptyList()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = state.timeText,
            fontSize = clockSize.sp,
            fontWeight = FontWeight.ExtraLight,
            color = MINIMAL_CLOCK,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(fontFeatureSettings = "tnum"),
            letterSpacing = 0.sp
        )
        if (options.shows(DreamField.DATE)) {
            Spacer(modifier = Modifier.height(8.dp))
            val dateAlign = rememberWrapAlign(TextAlign.Center)
            Text(
                text = state.shortDateText.uppercase(locale),
                autoSize = fitUpTo(16.sp),
                maxLines = DREAM_LINE_MAX_LINES,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
                textAlign = dateAlign.textAlign,
                onTextLayout = dateAlign.onTextLayout,
                color = MINIMAL_DIM,
                letterSpacing = 0.26.em
            )
        }
        if (groups.isNotEmpty()) {
            Spacer(modifier = Modifier.height(22.dp))
            NotificationIconRow(
                groups = groups,
                pulseToken = state.notificationPulseToken,
                iconSize = 18.dp,
                modifier = Modifier.graphicsLayer { alpha = MINIMAL_ICON_ROW_ALPHA },
            )
        }
    }
}

@Composable
private fun MinimalBattery(state: DreamUiState, options: DreamOptions, modifier: Modifier = Modifier) {
    val batteryColor = getBatteryGradientColors(state.batteryLevel).first()
    val parts = minimalStatusParts(
        state = state,
        percent = stringResource(R.string.dream_percent_spaced, state.batteryLevel),
        word = stringResource(state.chargeState.label),
        showWatts = options.shows(DreamField.BATTERY_DETAILS),
    )
    val line = buildAnnotatedString {
        withStyle(SpanStyle(color = batteryColor, fontWeight = FontWeight.SemiBold)) { append(parts.first()) }
        for (part in parts.drop(1)) {
            append("  ·  ")
            append(part)
        }
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(MINIMAL_TRACK)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(state.batteryLevel.coerceIn(0, 100) / 100f)
                    .fillMaxHeight()
                    .background(batteryColor)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = line,
            autoSize = fitUpTo(18.sp, floorSp = 9f),
            maxLines = 1,
            softWrap = false,
            color = MINIMAL_DIM,
            letterSpacing = 0.04.em,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}
