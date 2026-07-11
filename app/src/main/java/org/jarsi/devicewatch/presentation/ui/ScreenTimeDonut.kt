package org.jarsi.devicewatch.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jarsi.devicewatch.data.DonutSegment

/**
 * Categorical donut colors. Slots follow the validated reference-palette order
 * (fixed assignment, never cycled); the "others" bucket is deliberately gray —
 * it is a remainder, not an identity. Light set validated against an M3 light
 * surface (#F3EDF7), dark set against #211F26 with the palette validator;
 * sub-3:1 light slots are relieved by the visible legend labels, and the dark
 * CVD floor-band pair by the 2dp arc gaps + labels (secondary encoding).
 */
private val DonutColorsLight = listOf(
    Color(0xFF2A78D6), // blue
    Color(0xFF1BAF7A), // aqua
    Color(0xFFEDA100), // yellow
    Color(0xFF008300), // green
    Color(0xFF4A3AA7), // violet
    Color(0xFFE34948), // red
)

private val DonutColorsDark = listOf(
    Color(0xFF3987E5),
    Color(0xFF199E70),
    Color(0xFFC98500),
    Color(0xFF008300),
    Color(0xFF9085E9),
    Color(0xFFE66767),
)

internal val DonutOthersColor = Color(0xFF898781)

/** Stable color per segment: identity slots in fixed order, gray for "others". */
@Composable
internal fun donutSegmentColors(segments: List<DonutSegment>): List<Color> {
    val palette = if (isSystemInDarkTheme()) DonutColorsDark else DonutColorsLight
    var slot = 0
    return segments.map { segment ->
        if (segment.packageName == null) DonutOthersColor else palette[slot++ % palette.size]
    }
}

/**
 * Screen-time donut: thin 12dp arcs with a 2° surface gap between segments,
 * total time as plain text ink in the center (never a series color). Shares the
 * ring language of the Overview battery indicator.
 */
@Composable
internal fun ScreenTimeDonut(
    segments: List<DonutSegment>,
    colors: List<Color>,
    totalText: String,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(180.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)

            if (segments.isEmpty()) {
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth),
                )
            } else {
                val gapAngle = 2f
                var startAngle = -90f
                segments.forEachIndexed { index, segment ->
                    val sweep = segment.fraction * 360f
                    val drawnSweep = (sweep - gapAngle).coerceAtLeast(0f)
                    if (drawnSweep > 0.1f) {
                        drawArc(
                            color = colors[index],
                            startAngle = startAngle + gapAngle / 2f,
                            sweepAngle = drawnSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                        )
                    }
                    startAngle += sweep
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = totalText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
