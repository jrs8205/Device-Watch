package org.jarsi.devicewatch.system.dream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jarsi.devicewatch.R

// Gauges: the app's own look. Monospaced figures on named rows with hairlines between
// them, under a header line and a clock with its seconds.

private val GAUGES_TIME = Color(0xFFF2F2F2)
private val GAUGES_VALUE = Color(0xFFE6E8EA)
private val GAUGES_LABEL = Color(0xFF7D848E)
private val GAUGES_HAIRLINE = Color(0xFF26282B)

/** The header line's and the rows' text sizes; a row's label is drawn at 0.6 of its value. */
private const val GAUGES_HEADER_SP = 13f
private const val GAUGES_VALUE_SP = 22f
private const val GAUGES_LABEL_RATIO = 0.6f

/**
 * Space above and below a row's text: a little less on a phone on its side, so the six
 * everyday rows fit a 360 dp high screen. And the space under the clock.
 */
private val GAUGES_ROW_PADDING = 12.dp
private val GAUGES_ROW_PADDING_LANDSCAPE = 9.dp
private val GAUGES_CLOCK_GAP = 22.dp

/** The seconds, and AM/PM when the clock has one, as shares of the time's size. */
private const val GAUGES_SECONDS_RATIO = 0.42f
private const val GAUGES_AM_PM_RATIO = 0.22f

@Composable
internal fun BoxWithConstraintsScope.GaugesContent(state: DreamUiState, options: DreamOptions, landscape: Boolean) {
    val insets = dreamInsets(maxWidth, maxHeight)
    val innerWidth = maxWidth - insets.horizontal * 2
    val innerHeight = maxHeight - insets.vertical * 2
    val density = LocalDensity.current
    // A row is as tall as its value's line and its paddings, whatever it holds, so the
    // number of rows that fit is known before anything is laid out.
    val rowPadding = if (landscape) GAUGES_ROW_PADDING_LANDSCAPE else GAUGES_ROW_PADDING
    val rowHeight = with(density) { (GAUGES_VALUE_SP * 1.35f).sp.toDp() } + rowPadding * 2
    val rows = gaugeRows(
        state = state,
        options = options,
        labels = GaugeLabels(
            battery = stringResource(R.string.dream_row_battery),
            power = stringResource(R.string.dream_row_power),
            temperature = stringResource(R.string.dream_row_temperature),
            full = stringResource(R.string.dream_row_full),
            alarm = stringResource(R.string.dream_row_alarm),
            notifications = stringResource(R.string.dream_row_notifications),
        ),
        percent = stringResource(R.string.dream_percent_spaced, state.batteryLevel),
    )

    if (landscape) {
        val columnWidth = (innerWidth - insets.centerGap) / 2
        val clockSize = rememberGaugesClockSize(state, options, columnWidth)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = insets.horizontal, vertical = insets.vertical),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                GaugesHeader(state, options)
                GaugesClock(state, options, clockSize)
            }
            Spacer(modifier = Modifier.width(insets.centerGap))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                GaugesRows(fitGaugeRows(rows, ((innerHeight - 1.dp) / rowHeight).toInt()), state, rowHeight)
            }
        }
    } else {
        val clockSize = rememberGaugesClockSize(state, options, innerWidth)
        // What the header and the clock take, estimated a little high so the rows that
        // are kept always fit under them.
        val headHeight = with(density) {
            (GAUGES_HEADER_SP * 1.5f).sp.toDp() + (clockSize * 1.35f).sp.toDp()
        } + GAUGES_CLOCK_GAP
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = insets.horizontal, vertical = insets.vertical),
            verticalArrangement = Arrangement.Center
        ) {
            GaugesHeader(state, options)
            GaugesClock(state, options, clockSize)
            Spacer(modifier = Modifier.height(GAUGES_CLOCK_GAP))
            GaugesRows(fitGaugeRows(rows, ((innerHeight - headHeight - 1.dp) / rowHeight).toInt()), state, rowHeight)
        }
    }
}

/** The device's name and the date on one line: `PIXEL 8A · FRI NOV 13`. */
@Composable
private fun GaugesHeader(state: DreamUiState, options: DreamOptions) {
    val locale = LocalConfiguration.current.locales[0]
    val name = state.deviceName?.takeIf { options.shows(DreamField.DEVICE_NAME) }?.uppercase(locale)
    val date = state.compactDateText.takeIf { options.shows(DreamField.DATE) }?.uppercase(locale)
    if (name == null && date == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (name != null) {
            // The name is the user's own text and can be any length: it gives way to the
            // date with an ellipsis instead of pushing the date off the line.
            Text(
                text = name,
                modifier = Modifier.weight(1f, fill = false).testTag(DREAM_TRUNCATES_TAG),
                fontSize = GAUGES_HEADER_SP.sp,
                fontFamily = FontFamily.Monospace,
                color = GAUGES_LABEL,
                letterSpacing = 0.2.em,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (date != null) {
            Text(
                text = if (name != null) " · $date" else date,
                fontSize = GAUGES_HEADER_SP.sp,
                fontFamily = FontFamily.Monospace,
                color = GAUGES_LABEL,
                letterSpacing = 0.2.em,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/** AM or PM when it stands on the time's left (see [amPmLeads]); empty otherwise. */
private fun gaugesLeadingMarker(state: DreamUiState): String = state.amPmText.takeIf { state.amPmLeads }.orEmpty()

/** What stands on the time's right: the seconds, or AM/PM alone when it follows the time, or nothing. */
private fun gaugesSideRatio(state: DreamUiState, options: DreamOptions): Float = when {
    options.shows(DreamField.SECONDS) -> GAUGES_SECONDS_RATIO
    state.amPmText.isNotEmpty() && !state.amPmLeads -> GAUGES_AM_PM_RATIO
    else -> 0f
}

/** The room between a marker on the time's left and the time. */
private val GAUGES_LEAD_GAP = 6.dp

/** The smallest AM or PM is drawn. */
private const val GAUGES_AM_PM_MIN_SP = 10f

@Composable
private fun rememberGaugesClockSize(state: DreamUiState, options: DreamOptions, availableWidth: Dp): Int {
    val sideRatio = gaugesSideRatio(state, options)
    return rememberFittedClockSize(
        availableWidth = availableWidth,
        maxClockSp = 96,
        minClockSp = 28,
        secondsRatio = sideRatio,
        secondsMinSp = if (sideRatio > 0f) 12 else 0,
        gap = 0.dp,
        timeWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
        leadingText = gaugesLeadingMarker(state),
        leadingRatio = GAUGES_AM_PM_RATIO,
        leadingMinSp = GAUGES_AM_PM_MIN_SP,
        leadingGap = GAUGES_LEAD_GAP,
        leadingWeight = FontWeight.Normal,
    )
}

/** The time with its seconds on the same baseline; AM/PM, when there is one, above them. */
@Composable
private fun GaugesClock(state: DreamUiState, options: DreamOptions, clockSize: Int) {
    LeftToRight { GaugesClockRow(state, options, clockSize) }
}

@Composable
private fun GaugesClockRow(state: DreamUiState, options: DreamOptions, clockSize: Int) {
    val showSeconds = options.shows(DreamField.SECONDS)
    val leadingMarker = gaugesLeadingMarker(state)
    val trailingMarker = state.amPmText.takeIf { !state.amPmLeads }.orEmpty()
    Row {
        if (leadingMarker.isNotEmpty()) {
            Text(
                text = leadingMarker,
                modifier = Modifier.alignBy(LastBaseline),
                fontSize = (clockSize * GAUGES_AM_PM_RATIO).coerceAtLeast(GAUGES_AM_PM_MIN_SP).sp,
                fontFamily = FontFamily.Monospace,
                color = GAUGES_LABEL,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.width(GAUGES_LEAD_GAP))
        }
        Text(
            text = state.timeText,
            modifier = Modifier.alignBy(LastBaseline),
            fontSize = clockSize.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = GAUGES_TIME,
            maxLines = 1,
            softWrap = false,
            // A monospaced colon takes a whole cell; the mockup pulls the digits together.
            letterSpacing = (-0.04).em
        )
        if (showSeconds || trailingMarker.isNotEmpty()) {
            Column(modifier = Modifier.alignBy(LastBaseline)) {
                if (trailingMarker.isNotEmpty()) {
                    Text(
                        text = trailingMarker,
                        fontSize = (clockSize * GAUGES_AM_PM_RATIO).coerceAtLeast(GAUGES_AM_PM_MIN_SP).sp,
                        fontFamily = FontFamily.Monospace,
                        color = GAUGES_LABEL,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                if (showSeconds) {
                    Text(
                        text = ":${state.secondsText}",
                        fontSize = (clockSize * GAUGES_SECONDS_RATIO).coerceAtLeast(12f).sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = options.accent.color,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun GaugesRows(rows: List<GaugeRow>, state: DreamUiState, rowHeight: Dp) {
    val batteryColor = getBatteryGradientColors(state.batteryLevel).first()
    Column(modifier = Modifier.fillMaxWidth()) {
        for (row in rows) {
            GaugesHairline()
            Row(
                modifier = Modifier.fillMaxWidth().height(rowHeight - 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row.label,
                    fontSize = (GAUGES_VALUE_SP * GAUGES_LABEL_RATIO).sp,
                    fontWeight = FontWeight.Medium,
                    color = GAUGES_LABEL,
                    letterSpacing = 0.18.em,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    when (row.kind) {
                        GaugeRowKind.NOTIFICATIONS -> NotificationIconRow(
                            groups = state.notificationGroups,
                            pulseToken = state.notificationPulseToken,
                            iconSize = 20.dp,
                            alignment = Alignment.CenterEnd,
                        )
                        // A calendar event's title is the user's own text: one line, cut
                        // with an ellipsis, in the plain face.
                        GaugeRowKind.EVENT -> Text(
                            text = row.value,
                            modifier = Modifier.testTag(DREAM_TRUNCATES_TAG),
                            fontSize = (GAUGES_VALUE_SP * 0.85f).sp,
                            color = GAUGES_VALUE,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        else -> Text(
                            text = row.value,
                            autoSize = fitUpTo(GAUGES_VALUE_SP.sp, floorSp = 10f),
                            fontFamily = FontFamily.Monospace,
                            color = if (row.kind == GaugeRowKind.BATTERY) batteryColor else GAUGES_VALUE,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
        GaugesHairline()
    }
}

@Composable
private fun GaugesHairline() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GAUGES_HAIRLINE))
}
