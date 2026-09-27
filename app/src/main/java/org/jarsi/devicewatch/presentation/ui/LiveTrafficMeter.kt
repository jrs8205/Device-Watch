package org.jarsi.devicewatch.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.TrafficMeter
import org.jarsi.devicewatch.data.TrafficRate
import org.jarsi.devicewatch.presentation.LiveTrafficViewModel

/**
 * Download and upload right now, with the last minute drawn under them. Reads
 * the counters only while this is on screen; absent where Android has none.
 */
@Composable
internal fun LiveTrafficSection(viewModel: LiveTrafficViewModel = hiltViewModel()) {
    val traffic by viewModel.traffic.collectAsStateWithLifecycle()
    val current = traffic ?: return
    val latest = current.latest

    SettingsSectionCard(titleRes = R.string.traffic_section) {
        Row(modifier = Modifier.fillMaxWidth()) {
            StackedMetricRow(
                label = stringResource(R.string.traffic_down),
                value = latest?.let { TrafficMeter.rateText(it.downBytesPerSecond) } ?: "…",
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            StackedMetricRow(
                label = stringResource(R.string.traffic_up),
                value = latest?.let { TrafficMeter.rateText(it.upBytesPerSecond) } ?: "…",
                modifier = Modifier.weight(1f),
            )
        }
        val peakDown = TrafficMeter.rateText(current.peakDownBytesPerSecond)
        val peakUp = TrafficMeter.rateText(current.peakUpBytesPerSecond)
        val peakText = stringResource(R.string.traffic_peak, peakDown, peakUp)
        TrafficSparkline(
            history = current.history,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .semantics { contentDescription = peakText },
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = peakText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Download as a filled area, upload as a line; the newest second at the right edge. */
@Composable
private fun TrafficSparkline(history: List<TrafficRate>, modifier: Modifier = Modifier) {
    val downColor = meterColor()
    val downFill = downColor.copy(alpha = 0.22f)
    val upColor = MaterialTheme.colorScheme.onSurfaceVariant
    val baseColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier) {
        val bottom = size.height
        drawLine(baseColor, Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = 1.dp.toPx())
        if (history.size < 2) return@Canvas

        // 100 kb/s as the floor keeps an idle line flat instead of magnifying background noise.
        val top = maxOf(
            history.maxOf { maxOf(it.downBytesPerSecond, it.upBytesPerSecond) },
            SPARKLINE_FLOOR_BYTES_PER_SECOND,
        ).toFloat()
        val step = size.width / (TrafficMeter.HISTORY_SIZE - 1)
        val firstSlot = TrafficMeter.HISTORY_SIZE - history.size
        fun x(index: Int) = (firstSlot + index) * step
        fun y(bytesPerSecond: Long) = bottom - bytesPerSecond / top * bottom

        val downLine = Path()
        history.forEachIndexed { index, rate ->
            if (index == 0) downLine.moveTo(x(index), y(rate.downBytesPerSecond))
            else downLine.lineTo(x(index), y(rate.downBytesPerSecond))
        }
        val downArea = Path().apply {
            addPath(downLine)
            lineTo(x(history.lastIndex), bottom)
            lineTo(x(0), bottom)
            close()
        }
        drawPath(downArea, downFill)
        drawPath(downLine, downColor, style = Stroke(width = 2.dp.toPx()))

        val upLine = Path()
        history.forEachIndexed { index, rate ->
            if (index == 0) upLine.moveTo(x(index), y(rate.upBytesPerSecond))
            else upLine.lineTo(x(index), y(rate.upBytesPerSecond))
        }
        drawPath(upLine, upColor, style = Stroke(width = 1.5.dp.toPx()))
    }
}

private const val SPARKLINE_FLOOR_BYTES_PER_SECOND = 12_500L
