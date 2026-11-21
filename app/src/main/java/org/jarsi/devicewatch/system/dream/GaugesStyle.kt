package org.jarsi.devicewatch.system.dream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.presentation.ui.valueTextStyle

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

/** The room between a row's label and its value. */
private val GAUGES_LABEL_GAP = 10.dp

/** How small a line in the phone's language gets before it takes a second line. */
private const val GAUGES_SMALL_FLOOR_SP = 10f

/** A row's label, and how many whole steps a calendar row's time may shrink before it wraps. */
private const val GAUGES_LABEL_SP = GAUGES_VALUE_SP * GAUGES_LABEL_RATIO
private const val GAUGES_LABEL_STEPS = 3f

/**
 * What a text that may be cut short keeps of its line, however long the text beside it is:
 * an event's title beside its time, and the phone's name beside the date.
 */
private const val GAUGES_TITLE_SHARE = 0.4f
private const val GAUGES_NAME_SHARE = 0.4f

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

    val locale = LocalConfiguration.current.locales[0]
    val name = state.deviceName?.takeIf { options.shows(DreamField.DEVICE_NAME) }?.uppercase(locale)
    val date = state.compactDateText.takeIf { options.shows(DreamField.DATE) }?.uppercase(locale)

    if (landscape) {
        val columnWidth = (innerWidth - insets.centerGap) / 2
        val clockSize = rememberGaugesClockSize(state, options, columnWidth)
        val headerStacked = rememberGaugesHeaderStacked(name, date, columnWidth)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = insets.horizontal, vertical = insets.vertical),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                GaugesHeader(name, date, headerStacked)
                GaugesClock(state, options, clockSize)
            }
            Spacer(modifier = Modifier.width(insets.centerGap))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                GaugesRows(fitGaugeRows(rows, ((innerHeight - 1.dp) / rowHeight).toInt()), state, rowHeight)
            }
        }
    } else {
        val clockSize = rememberGaugesClockSize(state, options, innerWidth)
        val headerStacked = rememberGaugesHeaderStacked(name, date, innerWidth)
        // What the header and the clock take, estimated a little high so the rows that
        // are kept always fit under them.
        val headHeight = with(density) {
            (GAUGES_HEADER_SP * 1.5f).sp.toDp() * (if (headerStacked) 2 else 1) + (clockSize * 1.35f).sp.toDp()
        } + GAUGES_CLOCK_GAP
        val rowsByEstimate = ((innerHeight - headHeight - 1.dp) / rowHeight).toInt()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = insets.horizontal, vertical = insets.vertical),
            verticalArrangement = Arrangement.Center
        ) {
            GaugesHeader(name, date, headerStacked)
            GaugesClock(state, options, clockSize)
            Spacer(modifier = Modifier.height(GAUGES_CLOCK_GAP))
            // The estimate holds for a header of the lines counted above. A date too long
            // for its line takes more of them, and then the height that is really left says
            // how many rows there is room for.
            BoxWithConstraints(modifier = Modifier.weight(1f, fill = false)) {
                val rowsByHeight = ((this.maxHeight - 1.dp) / rowHeight).toInt()
                GaugesRows(fitGaugeRows(rows, minOf(rowsByEstimate, rowsByHeight)), state, rowHeight)
            }
        }
    }
}

/**
 * Whether the header's name and date take a line each. Side by side the date is whole and the
 * name gives way with an ellipsis; when that would leave the name less than its share of a
 * line [width] wide ([GAUGES_NAME_SHARE]), and less than it needs, the date goes under it.
 */
@Composable
private fun rememberGaugesHeaderStacked(name: String?, date: String?, width: Dp): Boolean {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val resolver = LocalFontFamilyResolver.current
    return remember(name, date, width, density, resolver) {
        if (name == null || date == null) return@remember false
        val style = TextStyle(fontSize = GAUGES_HEADER_SP.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.2.em)
        fun widthPx(text: String): Int = measurer.measure(
            text = text,
            style = style,
            softWrap = false,
            maxLines = 1,
            density = density,
            fontFamilyResolver = resolver,
        ).size.width
        val linePx = with(density) { width.roundToPx() }
        gaugesHeaderStacks(linePx, nameWidth = widthPx(name), dateWidth = widthPx(gaugesHeaderDate(date)))
    }
}

/** The date as it follows the name on the header's one line. */
private fun gaugesHeaderDate(date: String): String = " · $date"

/**
 * The choice apart from the measuring: on a line [lineWidth] wide the name has what the date
 * leaves. It stays beside the date when that is all it needs, or at least its share of the line.
 */
internal fun gaugesHeaderStacks(lineWidth: Int, nameWidth: Int, dateWidth: Int): Boolean {
    val left = lineWidth - dateWidth
    return left < nameWidth && left < lineWidth * GAUGES_NAME_SHARE
}

/**
 * The device's name and the date: on one line, `PIXEL 8A · FRI NOV 13`, or with [stacked] the
 * date under the name (see [rememberGaugesHeaderStacked]).
 */
@Composable
private fun GaugesHeader(name: String?, date: String?, stacked: Boolean) {
    if (name == null && date == null) return
    // The name is the user's own text and can be any length: it ends in an ellipsis.
    val nameText: @Composable (Modifier) -> Unit = { modifier ->
        Text(
            text = name.orEmpty(),
            style = valueTextStyle(),
            modifier = modifier.testTag(DREAM_TRUNCATES_TAG),
            fontSize = GAUGES_HEADER_SP.sp,
            fontFamily = FontFamily.Monospace,
            color = GAUGES_LABEL,
            letterSpacing = 0.2.em,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
    // The date is written in the phone's language and can be as long as that language's
    // words are: it gets smaller and then takes a second line rather than lose its end.
    val dateText: @Composable (String) -> Unit = { text ->
        Text(
            text = text,
            autoSize = fitUpTo(GAUGES_HEADER_SP.sp, floorSp = GAUGES_SMALL_FLOOR_SP),
            fontFamily = FontFamily.Monospace,
            color = GAUGES_LABEL,
            letterSpacing = 0.2.em,
            maxLines = DREAM_LINE_MAX_LINES,
            softWrap = true,
            overflow = TextOverflow.Ellipsis
        )
    }
    if (stacked && name != null && date != null) {
        Column {
            nameText(Modifier)
            dateText(date)
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (name != null) nameText(Modifier.weight(1f, fill = false))
        if (date != null) dateText(if (name != null) gaugesHeaderDate(date) else date)
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
        minClockSp = 14,
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
        // A marker after the time stands over the seconds; a long one sets the column's width.
        trailingText = state.amPmText.takeIf { !state.amPmLeads }.orEmpty(),
        trailingSp = { clockSize -> (clockSize * GAUGES_AM_PM_RATIO).coerceAtLeast(GAUGES_AM_PM_MIN_SP) },
        trailingWeight = FontWeight.Normal,
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
                    // The clock has given way to a long marker as far as it can (see
                    // rememberGaugesClockSize); one still too wide goes on a second line.
                    val markerSp = (clockSize * GAUGES_AM_PM_RATIO).coerceAtLeast(GAUGES_AM_PM_MIN_SP)
                    FitAnew(fitUpTo(markerSp.sp, floorSp = markerSp)) { fit ->
                        Text(
                            text = trailingMarker,
                            autoSize = fit,
                            fontFamily = FontFamily.Monospace,
                            color = GAUGES_LABEL,
                            maxLines = DREAM_LINE_MAX_LINES,
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
            if (row.kind == GaugeRowKind.EVENT) {
                GaugesEventRow(row, modifier = Modifier.fillMaxWidth().height(rowHeight - 1.dp))
                continue
            }
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
                Spacer(modifier = Modifier.width(GAUGES_LABEL_GAP))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    when (row.kind) {
                        GaugeRowKind.NOTIFICATIONS -> NotificationIconRow(
                            groups = state.notificationGroups,
                            pulseToken = state.notificationPulseToken,
                            iconSize = 20.dp,
                            alignment = Alignment.CenterEnd,
                        )
                        else -> {
                            // The alarm and the estimate are written in the phone's language:
                            // a long one gets smaller, then takes the lines the row has room
                            // for. A figure stays on one line with its unit.
                            val phoneLanguage = row.kind == GaugeRowKind.ALARM || row.kind == GaugeRowKind.FULL
                            Text(
                                text = row.value,
                                autoSize = fitUpTo(GAUGES_VALUE_SP.sp, floorSp = GAUGES_SMALL_FLOOR_SP),
                                fontFamily = FontFamily.Monospace,
                                color = if (row.kind == GaugeRowKind.BATTERY) batteryColor else GAUGES_VALUE,
                                maxLines = if (phoneLanguage) DREAM_LINE_MAX_LINES else 1,
                                softWrap = phoneLanguage,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }
        GaugesHairline()
    }
}

/**
 * A calendar event: when it is where the other rows have their label, its title where they
 * have their value.
 *
 * The time is written in the phone's language and must be read whole; in Bulgarian or Latvian
 * it is twice as long as in English ("CETURTD., 12:59 PĒCPUSDIENĀ"). As a plain row's first
 * text it took all the width it wanted: on a narrow column its own end was cut off and the
 * title was left no room at all. Here the title keeps its share of the row
 * ([wholePartMaxWidth]) and the time fits into the rest, smaller first and then on the lines
 * the row's height has room for.
 */
@Composable
private fun GaugesEventRow(row: GaugeRow, modifier: Modifier) {
    WholeBesideCut(
        gap = GAUGES_LABEL_GAP,
        cutShare = GAUGES_TITLE_SHARE,
        modifier = modifier,
        whole = {
            Text(
                text = row.label,
                // Whole steps down from the label's own size, 13.2 sp, so that a time with room is drawn at it.
                autoSize = fitUpTo(GAUGES_LABEL_SP.sp, floorSp = GAUGES_LABEL_SP - GAUGES_LABEL_STEPS),
                fontWeight = FontWeight.Medium,
                color = GAUGES_LABEL,
                letterSpacing = 0.18.em,
                maxLines = DREAM_LINE_MAX_LINES,
                softWrap = true,
                overflow = TextOverflow.Ellipsis
            )
        },
        cut = {
            // The title is the user's own text: one line, cut with an ellipsis, in the plain face.
            Text(
                text = row.value,
                style = valueTextStyle(),
                modifier = Modifier.testTag(DREAM_TRUNCATES_TAG),
                fontSize = (GAUGES_VALUE_SP * 0.85f).sp,
                color = GAUGES_VALUE,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        },
    )
}

@Composable
private fun GaugesHairline() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GAUGES_HAIRLINE))
}
