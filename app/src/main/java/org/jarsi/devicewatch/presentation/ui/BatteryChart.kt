package org.jarsi.devicewatch.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.BatterySample
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Tall enough for the drain shape to be readable without crowding the page. */
private val ChartHeight = 160.dp

/** Battery-level gridlines, as fractions of a full battery. */
private val GridFractions = listOf(0f, 0.5f, 1f)

/**
 * Battery level over the selected [range], drawn straight onto a [Canvas] — no
 * chart library, nothing but the samples the app already keeps on the device.
 * Gaps in the history break the line instead of being bridged with an invented
 * straight drain, and charging stretches are shaded behind it.
 */
@Composable
internal fun BatteryChart(
    samples: List<BatterySample>,
    range: BatteryChartRange,
    modifier: Modifier = Modifier,
) {
    // "Now" is sampled per data set, not per recomposition: the x axis then stays
    // pinned to the poll that produced these samples instead of creeping.
    val nowMillis = remember(samples, range) { System.currentTimeMillis() }
    val points = remember(samples, range, nowMillis) {
        BatteryChartLogic.points(samples, range, nowMillis)
    }
    val segmentStarts = remember(samples, range, nowMillis) {
        BatteryChartLogic.segmentStarts(samples, range, nowMillis)
    }
    val chargingSpans = remember(points, segmentStarts) {
        BatteryChartLogic.chargingSpans(points, segmentStarts)
    }

    val lineColor = MaterialTheme.colorScheme.primary
    val chargingColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (points.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ChartHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.history_battery_empty),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ChartHeight)
                ) {
                    val strokeWidth = 2.dp.toPx()
                    // Inset by half a stroke on both axes so the 0 % and 100 % lines,
                    // the round caps and the lone-reading dots stay whole at the edges.
                    val inset = strokeWidth / 2f
                    val plotWidth = size.width - strokeWidth
                    val plotHeight = size.height - strokeWidth
                    fun xFor(fraction: Float) = inset + fraction * plotWidth
                    fun yFor(fraction: Float) = inset + (1f - fraction) * plotHeight

                    chargingSpans.forEach { span ->
                        val left = xFor(span.start)
                        val right = xFor(span.endInclusive)
                        // A momentary charge would otherwise be zero pixels wide. Widen
                        // it around its own middle, so one at either edge stays on screen.
                        val width = (right - left).coerceAtLeast(strokeWidth)
                        val start = ((left + right - width) / 2f)
                            .coerceIn(0f, (size.width - width).coerceAtLeast(0f))
                        drawRect(
                            color = chargingColor,
                            topLeft = Offset(start, 0f),
                            size = Size(width, size.height),
                        )
                    }

                    GridFractions.forEach { fraction ->
                        val y = yFor(fraction)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }

                    segmentStarts.forEachIndexed { index, start ->
                        val end = segmentStarts.getOrNull(index + 1) ?: points.size
                        val segment = points.subList(start, end)
                        if (segment.size == 1) {
                            // A lone reading has no line to draw — mark it as a dot.
                            val point = segment.single()
                            drawCircle(
                                color = lineColor,
                                radius = strokeWidth,
                                center = Offset(xFor(point.xFraction), yFor(point.yFraction)),
                            )
                        } else {
                            val path = Path()
                            segment.forEachIndexed { pointIndex, point ->
                                val x = xFor(point.xFraction)
                                val y = yFor(point.yFraction)
                                if (pointIndex == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(
                                path = path,
                                color = lineColor,
                                style = Stroke(
                                    width = strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                ),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                TimeAxisLabels(range = range, nowMillis = nowMillis)
            }
        }
    }
}

/** Window start, midpoint and now under the chart: clock times for a day, weekdays for a week. */
@Composable
private fun TimeAxisLabels(range: BatteryChartRange, nowMillis: Long) {
    val locale = LocalLocale.current.platformLocale
    val formatter = remember(locale, range) {
        val pattern = when (range) {
            BatteryChartRange.Day -> android.text.format.DateFormat.getBestDateTimePattern(locale, "Hm")
            BatteryChartRange.Week -> "EEE"
        }
        DateTimeFormatter.ofPattern(pattern, locale).withZone(ZoneId.systemDefault())
    }
    val windowMillis = range.windowMillis()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        listOf(nowMillis - windowMillis, nowMillis - windowMillis / 2, nowMillis).forEach { millis ->
            Text(
                text = formatter.format(Instant.ofEpochMilli(millis)),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
