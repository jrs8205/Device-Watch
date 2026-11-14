package org.jarsi.devicewatch.system.dream

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import kotlin.math.PI

/**
 * The Edge style's arithmetic: the line's route around the screen, its colours, the lanes
 * it rotates through against burn-in, and its rest when nothing changes.
 */
class EdgeGeometryTest {

    // A 400 x 800 screen, the line 4 from the edge, corners of radius 20.
    private val w = 400f
    private val h = 800f
    private val inset = 4f
    private val r = 20f

    private fun point(fraction: Float) = edgePoint(fraction, w, h, inset, r)

    @Test
    fun `the line starts at the bottom centre and runs clockwise`() {
        val start = point(0f)
        assertThat(start.first).isWithin(0.01f).of(200f)
        assertThat(start.second).isWithin(0.01f).of(796f)

        // First to the left along the bottom...
        val justAfter = point(0.02f)
        assertThat(justAfter.first).isLessThan(200f)
        assertThat(justAfter.second).isWithin(0.01f).of(796f)

        // ...then up the left side...
        val quarter = point(0.25f)
        assertThat(quarter.first).isWithin(0.01f).of(4f)
        assertThat(quarter.second).isWithin(0.5f).of(400f)

        // ...across the top...
        val half = point(0.5f)
        assertThat(half.first).isWithin(0.5f).of(200f)
        assertThat(half.second).isWithin(0.01f).of(4f)

        // ...down the right side...
        val threeQuarters = point(0.75f)
        assertThat(threeQuarters.first).isWithin(0.01f).of(396f)
        assertThat(threeQuarters.second).isWithin(0.5f).of(400f)

        // ...and back to where it started.
        val end = point(1f)
        assertThat(end.first).isWithin(0.01f).of(200f)
        assertThat(end.second).isWithin(0.01f).of(796f)
    }

    @Test
    fun `a corner is rounded`() {
        // Halfway round the bottom-left corner the line is on the corner's diagonal.
        val straight = 196f - r
        val arc = (PI * r / 2).toFloat()
        val fraction = (straight + arc / 2) / edgeLength(w, h, inset, r)
        val middle = point(fraction)
        val centreX = inset + r
        val centreY = h - inset - r
        val offset = r * 0.70710677f

        assertThat(middle.first).isWithin(0.05f).of(centreX - offset)
        assertThat(middle.second).isWithin(0.05f).of(centreY + offset)
    }

    @Test
    fun `the length is the rounded rectangle's perimeter`() {
        val expected = 2 * (392f - 40f) + 2 * (792f - 40f) + (2 * PI * 20).toFloat()

        assertThat(edgeLength(w, h, inset, r)).isWithin(0.5f).of(expected)
    }

    @Test
    fun `nothing breaks at the ends`() {
        for (fraction in listOf(-1f, 0f, 1f, 2f, Float.NaN)) {
            val (x, y) = point(fraction)
            assertWithMessage("fraction $fraction").that(x.isFinite() && y.isFinite()).isTrue()
            assertWithMessage("fraction $fraction x").that(x).isIn(com.google.common.collect.Range.closed(0f, w))
            assertWithMessage("fraction $fraction y").that(y).isIn(com.google.common.collect.Range.closed(0f, h))
        }
        assertThat(point(-1f)).isEqualTo(point(0f))
        assertThat(point(2f)).isEqualTo(point(1f))
    }

    @Test
    fun `a canvas smaller than the corner still works`() {
        // The settings thumbnail: far smaller than a phone's 47 dp corner at the widest lane.
        val radius = edgeCornerRadius(deviceRadius = 47f, inset = 13f, width = 112f, height = 60f)
        assertThat(radius).isAtMost(17f)
        assertThat(radius).isAtLeast(0f)

        for (step in 0..100) {
            val (x, y) = edgePoint(step / 100f, 112f, 60f, 13f, radius)
            assertWithMessage("step $step").that(x.isFinite() && y.isFinite()).isTrue()
            assertWithMessage("step $step x").that(x).isIn(com.google.common.collect.Range.closed(13f - 0.01f, 99f + 0.01f))
            assertWithMessage("step $step y").that(y).isIn(com.google.common.collect.Range.closed(13f - 0.01f, 47f + 0.01f))
        }
        assertThat(edgeLength(112f, 60f, 13f, radius).isFinite()).isTrue()
    }

    @Test
    fun `a canvas too small for any lane gives a radius of zero, not a negative one`() {
        assertThat(edgeCornerRadius(deviceRadius = 47f, inset = 13f, width = 20f, height = 20f)).isEqualTo(0f)
    }

    @Test
    fun `without a device radius the corner is 28 minus the inset`() {
        assertThat(edgeCornerRadius(deviceRadius = null, inset = 4f, width = 400f, height = 800f)).isEqualTo(24f)
        assertThat(edgeCornerRadius(deviceRadius = 0f, inset = 4f, width = 400f, height = 800f)).isEqualTo(24f)
    }

    @Test
    fun `the device's corner radius is followed, lane by lane`() {
        assertThat(edgeCornerRadius(deviceRadius = 47f, inset = 4f, width = 400f, height = 800f)).isEqualTo(43f)
        assertThat(edgeCornerRadius(deviceRadius = 47f, inset = 13f, width = 400f, height = 800f)).isEqualTo(34f)
        // A nearly square corner still gets a small curve.
        assertThat(edgeCornerRadius(deviceRadius = 8f, inset = 13f, width = 400f, height = 800f)).isEqualTo(6f)
    }

    @Test
    fun `four lanes, a quarter of the time each`() {
        assertThat((0L..7L).map(::edgeLane)).containsExactly(0, 1, 2, 3, 0, 1, 2, 3).inOrder()
        for (minute in -9L..-1L) {
            assertWithMessage("minute $minute").that(edgeLane(minute)).isIn(0..3)
        }
        // Over a day every lane is used for exactly a quarter of the minutes.
        val perLane = (0L until 1440L).groupingBy(::edgeLane).eachCount()
        assertThat(perLane).containsExactly(0, 360, 1, 360, 2, 360, 3, 360)
    }

    @Test
    fun `the lane centre lines are 3 apart from 4 to 13`() {
        assertThat(EDGE_LANE_INSETS_DP.toList()).containsExactly(4f, 7f, 10f, 13f).inOrder()
        // A 3 dp core on centre lines 3 dp apart: neighbouring lanes never share a pixel row.
        assertThat(EDGE_CORE_DP).isEqualTo(3f)
    }

    @Test
    fun `the line rests after ten minutes without a change`() {
        val rest = EdgeRest(level = 80, state = DreamChargeState.HELD, sinceMs = 1_000L)

        assertThat(edgeBrightness(rest, nowMs = 1_000L)).isEqualTo(1f)
        assertThat(edgeBrightness(rest, nowMs = 1_000L + 599_999L)).isEqualTo(1f)
        assertThat(edgeBrightness(rest, nowMs = 1_000L + 600_000L)).isEqualTo(0.35f)
        assertThat(edgeBrightness(rest, nowMs = 1_000L + 6_000_000L)).isEqualTo(0.35f)
    }

    @Test
    fun `a new level or a new state makes the line bright again`() {
        val start = edgeRest(previous = null, level = 80, state = DreamChargeState.HELD, nowMs = 0L)
        val later = 700_000L
        assertThat(edgeBrightness(start, later)).isEqualTo(0.35f)

        // Nothing changed: the rest goes on from the same moment.
        val same = edgeRest(start, level = 80, state = DreamChargeState.HELD, nowMs = later)
        assertThat(same.sinceMs).isEqualTo(0L)
        assertThat(edgeBrightness(same, later)).isEqualTo(0.35f)

        // The level moved.
        val newLevel = edgeRest(start, level = 81, state = DreamChargeState.HELD, nowMs = later)
        assertThat(newLevel.sinceMs).isEqualTo(later)
        assertThat(edgeBrightness(newLevel, later)).isEqualTo(1f)

        // The charger's state changed.
        val newState = edgeRest(start, level = 80, state = DreamChargeState.CHARGING, nowMs = later)
        assertThat(newState.sinceMs).isEqualTo(later)
        assertThat(edgeBrightness(newState, later)).isEqualTo(1f)
    }

    @Test
    fun `the rest is the same in every state`() {
        for (state in DreamChargeState.entries) {
            val rest = edgeRest(previous = null, level = 100, state = state, nowMs = 0L)
            assertWithMessage("$state before").that(edgeBrightness(rest, 599_999L)).isEqualTo(1f)
            assertWithMessage("$state after").that(edgeBrightness(rest, 600_000L)).isEqualTo(0.35f)
        }
    }

    @Test
    fun `a clock that jumps backwards does not dim the line`() {
        val rest = EdgeRest(level = 50, state = DreamChargeState.CHARGING, sinceMs = 1_000_000L)

        assertThat(edgeBrightness(rest, nowMs = 0L)).isEqualTo(1f)
    }

    @Test
    fun `the core over its glow is as bright as the line should be`() {
        // The glow's two strokes lie under the core. At full brightness the core hides
        // them; resting, all three show through each other, and the core is drawn fainter
        // so that together they come to the rest's 35 %, not more.
        fun together(top: Float, vararg under: Float): Float =
            1f - under.fold(1f - top) { remaining, alpha -> remaining * (1f - alpha) }

        assertThat(edgeTopAlpha(1f, 0.22f, 0.10f)).isEqualTo(1f)

        val rested = EDGE_REST_ALPHA
        val innerGlow = 0.22f * rested
        val outerGlow = 0.10f * rested
        val core = edgeTopAlpha(rested, innerGlow, outerGlow)
        assertThat(core).isLessThan(rested)
        assertThat(together(core, innerGlow, outerGlow)).isWithin(0.001f).of(rested)

        // Nothing under it: the top layer is simply the target.
        assertThat(edgeTopAlpha(0.35f)).isWithin(0.0001f).of(0.35f)
        // Never outside 0..1, whatever lies under it.
        assertThat(edgeTopAlpha(0.1f, 0.5f)).isEqualTo(0f)
        assertThat(edgeTopAlpha(0f)).isEqualTo(0f)
    }

    @Test
    fun `the colour stops`() {
        assertThat(edgeColor(5, wideGamut = false)).isEqualTo(Color(0xFFFF1F3D))
        assertThat(edgeColor(10, wideGamut = false)).isEqualTo(Color(0xFFFF1F3D))
        assertThat(edgeColor(30, wideGamut = false)).isEqualTo(Color(0xFFFF8A00))
        assertThat(edgeColor(50, wideGamut = false)).isEqualTo(Color(0xFFFFD400))
        assertThat(edgeColor(70, wideGamut = false)).isEqualTo(Color(0xFFB6FF00))
        assertThat(edgeColor(100, wideGamut = false)).isEqualTo(Color(0xFF00FF6A))
        assertThat(edgeColor(140, wideGamut = false)).isEqualTo(Color(0xFF00FF6A))
        assertThat(edgeColor(-3, wideGamut = false)).isEqualTo(Color(0xFFFF1F3D))
    }

    @Test
    fun `between two stops the colour lies between them`() {
        val middle = edgeColor(20, wideGamut = false)

        // Red to orange: red stays full, green climbs from 0x1F toward 0x8A, blue falls.
        assertThat(middle.red).isWithin(0.03f).of(1f)
        assertThat(middle.green).isGreaterThan(0x1F / 255f)
        assertThat(middle.green).isLessThan(0x8A / 255f)
        assertThat(middle.blue).isLessThan(0x3D / 255f)
    }

    @Test
    fun `no grey between the stops`() {
        for (level in 0..100) {
            val color = edgeColor(level, wideGamut = false)
            val channels = listOf(color.red, color.green, color.blue)
            assertWithMessage("level $level: $color")
                .that(channels.max() - channels.min())
                .isAtLeast(0.5f)
        }
    }

    @Test
    fun `the wide-gamut colours are Display P3 and the others sRGB`() {
        for (level in listOf(10, 20, 30, 64, 100)) {
            assertWithMessage("level $level").that(edgeColor(level, wideGamut = true).colorSpace)
                .isEqualTo(ColorSpaces.DisplayP3)
            assertWithMessage("level $level").that(edgeColor(level, wideGamut = false).colorSpace)
                .isEqualTo(ColorSpaces.Srgb)
        }
        val red = edgeColor(10, wideGamut = true)
        assertThat(red.red).isEqualTo(1f)
        assertThat(red.green).isWithin(0.001f).of(0.1f)
        assertThat(red.blue).isWithin(0.001f).of(0.2f)
    }

    @Test
    fun `the text colour is a lighter shade of the line's`() {
        for (level in listOf(10, 64, 100)) {
            val line = edgeColor(level, wideGamut = false)
            val text = edgeTextColor(level)
            assertWithMessage("level $level red").that(text.red).isAtLeast(line.red - 0.001f)
            assertWithMessage("level $level green").that(text.green).isAtLeast(line.green - 0.001f)
            assertWithMessage("level $level blue").that(text.blue).isGreaterThan(line.blue)
        }
    }
}
