package org.jarsi.devicewatch.presentation.ui

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.data.BatterySample
import org.junit.Test

class BatteryChartLogicTest {

    private val now = 1_800_000_000_000L
    private val minute = 60L * 1000
    private val hour = 60 * minute

    /** Sample [agoMillis] before the fixed "now" of these tests. */
    private fun sample(agoMillis: Long, level: Int, charging: Boolean = false) =
        BatterySample(timeMillis = now - agoMillis, level = level, charging = charging)

    /** Where a reading [agoMillis] before now sits on the day chart's x axis. */
    private fun dayX(agoMillis: Long): Float =
        (24 * hour - agoMillis).toFloat() / (24 * hour).toFloat()

    /** Spans the chart would shade, wired together exactly the way the canvas does it. */
    private fun spansOf(
        samples: List<BatterySample>,
        range: BatteryChartRange = BatteryChartRange.Day,
    ): List<ClosedFloatingPointRange<Float>> = BatteryChartLogic.chargingSpans(
        BatteryChartLogic.points(samples, range, now),
        BatteryChartLogic.segmentStarts(samples, range, now),
    )

    @Test
    fun `the window start maps to the left edge and now to the right edge`() {
        val points = BatteryChartLogic.points(
            listOf(sample(24 * hour, 50), sample(12 * hour, 40), sample(0, 30)),
            BatteryChartRange.Day,
            now,
        )

        assertThat(points).hasSize(3)
        assertThat(points.first().xFraction).isWithin(TOLERANCE).of(0f)
        assertThat(points[1].xFraction).isWithin(TOLERANCE).of(0.5f)
        assertThat(points.last().xFraction).isWithin(TOLERANCE).of(1f)
    }

    @Test
    fun `level maps to a raw fraction where full battery is one`() {
        val points = BatteryChartLogic.points(
            listOf(sample(3 * hour, 0), sample(2 * hour, 50), sample(hour, 100)),
            BatteryChartRange.Day,
            now,
        )

        assertThat(points.map { it.yFraction }).containsExactly(0f, 0.5f, 1f).inOrder()
    }

    @Test
    fun `samples older than the range or in the future are dropped`() {
        val samples = listOf(sample(25 * hour, 80), sample(2 * hour, 60), sample(-hour, 55))

        val day = BatteryChartLogic.points(samples, BatteryChartRange.Day, now)

        assertThat(day.map { it.yFraction }).containsExactly(0.6f)
    }

    @Test
    fun `the week range keeps samples the day range drops`() {
        val samples = listOf(sample(3 * 24 * hour, 80), sample(2 * hour, 60))

        val week = BatteryChartLogic.points(samples, BatteryChartRange.Week, now)

        assertThat(week).hasSize(2)
        assertThat(week.first().xFraction).isWithin(TOLERANCE).of(4f / 7f)
    }

    @Test
    fun `charging state travels with the point`() {
        val points = BatteryChartLogic.points(
            listOf(sample(2 * hour, 60, charging = true), sample(hour, 70)),
            BatteryChartRange.Day,
            now,
        )

        assertThat(points.map { it.charging }).containsExactly(true, false).inOrder()
    }

    @Test
    fun `consecutive charging samples merge into a single span`() {
        val spans = spansOf(
            listOf(
                sample(60 * minute, 40, charging = true),
                sample(55 * minute, 60, charging = true),
                sample(50 * minute, 80, charging = true),
                sample(45 * minute, 78),
                sample(40 * minute, 76),
            )
        )

        assertThat(spans).hasSize(1)
        assertThat(spans.single().start).isWithin(TOLERANCE).of(dayX(60 * minute))
        assertThat(spans.single().endInclusive).isWithin(TOLERANCE).of(dayX(45 * minute))
    }

    @Test
    fun `separate charging sessions stay separate spans`() {
        val spans = spansOf(
            listOf(
                sample(90 * minute, 40, charging = true),
                sample(85 * minute, 60),
                sample(60 * minute, 50, charging = true),
                sample(55 * minute, 70),
            )
        )

        assertThat(spans).hasSize(2)
        assertThat(spans.first().start).isWithin(TOLERANCE).of(dayX(90 * minute))
        assertThat(spans.last().endInclusive).isWithin(TOLERANCE).of(dayX(55 * minute))
    }

    @Test
    fun `a lone charging sample is shaded up to the next reading`() {
        val span = spansOf(
            listOf(sample(60 * minute, 40, charging = true), sample(55 * minute, 60))
        ).single()

        assertThat(span.start).isWithin(TOLERANCE).of(dayX(60 * minute))
        assertThat(span.endInclusive).isWithin(TOLERANCE).of(dayX(55 * minute))
    }

    @Test
    fun `a charging run stops at a break in the line instead of shading the gap`() {
        val spans = spansOf(
            listOf(
                sample(10 * hour, 40, charging = true), // last reading before the gap
                sample(60 * minute, 80, charging = true),
                sample(55 * minute, 78),
            )
        )

        assertThat(spans).hasSize(2)
        // The pre-gap run ends where it started: it must not reach across the hole.
        assertThat(spans.first().start).isWithin(TOLERANCE).of(dayX(10 * hour))
        assertThat(spans.first().endInclusive).isWithin(TOLERANCE).of(dayX(10 * hour))
        assertThat(spans.last().start).isWithin(TOLERANCE).of(dayX(60 * minute))
        assertThat(spans.last().endInclusive).isWithin(TOLERANCE).of(dayX(55 * minute))
    }

    @Test
    fun `a window that is charging throughout is shaded end to end`() {
        val span = spansOf(
            listOf(
                sample(10 * minute, 40, charging = true),
                sample(5 * minute, 80, charging = true),
                sample(0, 90, charging = true),
            )
        ).single()

        assertThat(span.start).isWithin(TOLERANCE).of(dayX(10 * minute))
        assertThat(span.endInclusive).isWithin(TOLERANCE).of(1f)
    }

    @Test
    fun `a window without charging has nothing to shade`() {
        assertThat(spansOf(listOf(sample(60 * minute, 60), sample(55 * minute, 55)))).isEmpty()
    }

    @Test
    fun `a gap longer than the limit starts a new segment`() {
        val samples = listOf(
            sample(3 * hour, 60),
            sample(3 * hour - 10 * minute, 58),
            sample(3 * hour - 55 * minute, 50), // 45 min after the previous one
            sample(3 * hour - 65 * minute, 45),
        )

        val starts = BatteryChartLogic.segmentStarts(samples, BatteryChartRange.Day, now)

        assertThat(starts).containsExactly(0, 2).inOrder()
    }

    @Test
    fun `samples inside the gap limit stay on one polyline`() {
        val samples = listOf(sample(2 * hour, 60), sample(2 * hour - 30 * minute, 55), sample(hour, 50))

        val starts = BatteryChartLogic.segmentStarts(samples, BatteryChartRange.Day, now)

        assertThat(starts).containsExactly(0)
    }

    @Test
    fun `segment starts index the in-window samples only`() {
        val samples = listOf(
            sample(30 * hour, 90), // dropped
            sample(26 * hour, 85), // dropped
            sample(2 * hour, 60),
            sample(hour, 55), // an hour later: its own segment, at filtered index 1
        )

        val starts = BatteryChartLogic.segmentStarts(samples, BatteryChartRange.Day, now)

        assertThat(starts).containsExactly(0, 1).inOrder()
    }

    @Test
    fun `no samples means nothing to draw`() {
        val points = BatteryChartLogic.points(emptyList(), BatteryChartRange.Day, now)

        assertThat(points).isEmpty()
        assertThat(spansOf(emptyList())).isEmpty()
        assertThat(BatteryChartLogic.segmentStarts(emptyList(), BatteryChartRange.Day, now)).isEmpty()
    }

    private companion object {
        const val TOLERANCE = 1e-4f
    }
}
