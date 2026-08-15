package org.jarsi.devicewatch.presentation.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The readout scale. Nothing here goes below 12 sp, and every step is a job
 * rather than a size: a row label is 13 because it labels a row, not because 13
 * looked right next to 22.
 */
internal val ROW_LABEL_SP = 13.sp
internal val SECONDARY_VALUE_SP = 18.sp
internal val PRIMARY_VALUE_SP = 22.sp
internal val HERO_VALUE_SP = 44.sp

/** Meter heights, in dp — a meter is a drawing and must not grow with the font. */
internal val BATTERY_METER_HEIGHT = 12.dp
internal val METER_HEIGHT = 8.dp
internal val METER_RADIUS = 2.dp

/**
 * Every number in the app.
 *
 * Monospaced because these values refresh every few seconds: with proportional
 * digits a 1 replacing a 4 shifts everything after it, and a column of figures
 * never lines up. [FontFamily.Monospace] resolves to whatever mono the device
 * ships rather than bundling Roboto Mono — the widths are what matter here, and
 * a bundled face would cost more than it buys in an app this size.
 */
@Composable
internal fun MetricValue(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = PRIMARY_VALUE_SP,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight = FontWeight.Medium,
) {
    Text(
        text = text,
        modifier = modifier,
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
    )
}

/** The muted caption above a value. */
@Composable
internal fun MetricLabel(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = ROW_LABEL_SP,
) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = fontSize,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Caption over value, each on its own line.
 *
 * Used where the value is a reading rather than a fact — meters and tallies that
 * change while you watch. Stacking costs a line, and buys a layout that cannot
 * break at any font size, because the label and the value never compete for the
 * same width. Rows of settled facts keep [LabelValueRow], which only stacks when
 * it has to; a whole page of stacked pairs is a long scroll for no gain.
 */
@Composable
internal fun StackedMetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueSize: TextUnit = PRIMARY_VALUE_SP,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier = modifier.padding(vertical = 6.dp)) {
        MetricLabel(label)
        MetricValue(value, fontSize = valueSize, color = valueColor)
    }
}
