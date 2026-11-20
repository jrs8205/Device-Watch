package org.jarsi.devicewatch.system.dream

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * How a line of the screensaver is sized, and how a row is shared between a text that must be
 * read whole and one that may be cut short: the choices apart from the measuring.
 */
class DreamLineFitTest {

    /** A line as wide as its size times [perSp], in a box [box] wide. */
    private fun oneLineFits(perSp: Float, box: Float): (Float) -> Boolean = { size -> size * perSp <= box }

    private fun sized(maxSp: Float, floorSp: Float, perSp: Float, box: Float, wrappedWhole: (Float) -> Boolean = { true }): Float =
        fittedLineSp(maxSp, floorSp, leastSp = 6f, onOneLine = oneLineFits(perSp, box), wrappedWhole = wrappedWhole)

    @Test
    fun `a line that fits is drawn at its full size`() {
        assertThat(sized(maxSp = 24f, floorSp = 12f, perSp = 10f, box = 300f)).isEqualTo(24f)
    }

    @Test
    fun `a longer line shrinks by whole steps from its floor up, as it always has`() {
        // 18 sp is the largest of 12, 13, 14 ... that fits 185 px at 10 px a size.
        assertThat(sized(maxSp = 24f, floorSp = 12f, perSp = 10f, box = 185f)).isEqualTo(18f)
        // The steps are counted from the floor: 12, 13, 14, 15, never the 15.84 the line starts from.
        assertThat(sized(maxSp = 15.84f, floorSp = 12f, perSp = 10f, box = 300f)).isEqualTo(15f)
        assertThat(sized(maxSp = 24f, floorSp = 12f, perSp = 10f, box = 120f)).isEqualTo(12f)
    }

    @Test
    fun `a line too long for its floor is wrapped at the floor`() {
        val asked = mutableListOf<Float>()
        val size = sized(maxSp = 24f, floorSp = 12f, perSp = 10f, box = 100f, wrappedWhole = { asked += it; true })

        assertThat(size).isEqualTo(12f)
        assertThat(asked).containsExactly(12f)
    }

    @Test
    fun `a line that is not whole on its lines at the floor gets smaller until it is`() {
        val size = sized(maxSp = 24f, floorSp = 12f, perSp = 10f, box = 100f, wrappedWhole = { it <= 9f })

        assertThat(size).isEqualTo(9f)
    }

    @Test
    fun `nothing is drawn below the least size`() {
        assertThat(sized(maxSp = 24f, floorSp = 12f, perSp = 10f, box = 10f, wrappedWhole = { false })).isEqualTo(6f)
        // A floor already under the least size is kept: the line was allowed that small before.
        assertThat(sized(maxSp = 16f, floorSp = 5f, perSp = 10f, box = 10f, wrappedWhole = { false })).isEqualTo(5f)
    }

    @Test
    fun `a size above the line's largest is never tried`() {
        val asked = mutableListOf<Float>()
        fittedLineSp(13f, 10f, 6f, onOneLine = { asked += it; true }, wrappedWhole = { true })

        assertThat(asked.max()).isEqualTo(13f)
    }

    // External review round 8: a calendar row's time took all the width it wanted, and on a
    // 163 px column the Bulgarian "СБ, 12:59 Ч. СЛ.ОБ." (185 px) left the title nothing.
    @Test
    fun `a title keeps its share of the row beside a long time`() {
        val forTime = wholePartMaxWidth(rowWidth = 163, gap = 10, cutWanted = 400, cutShare = 0.4f)

        assertThat(forTime).isEqualTo(163 - 10 - 65)
        assertThat(163 - 10 - forTime).isAtLeast(65)
    }

    @Test
    fun `a short title takes only what it needs`() {
        assertThat(wholePartMaxWidth(rowWidth = 163, gap = 10, cutWanted = 30, cutShare = 0.4f)).isEqualTo(123)
    }

    @Test
    fun `a row too narrow for anything gives nothing below zero`() {
        assertThat(wholePartMaxWidth(rowWidth = 8, gap = 10, cutWanted = 30, cutShare = 0.4f)).isEqualTo(0)
    }

    @Test
    fun `the header's name and date share a line while the name has what it needs or its share`() {
        // Both fit.
        assertThat(gaugesHeaderStacks(lineWidth = 400, nameWidth = 96, dateWidth = 155)).isFalse()
        // The name is cut short but keeps 125 of 280 px.
        assertThat(gaugesHeaderStacks(lineWidth = 280, nameWidth = 287, dateWidth = 155)).isFalse()
    }

    @Test
    fun `the header's date goes under the name when the name would be left a few letters`() {
        assertThat(gaugesHeaderStacks(lineWidth = 203, nameWidth = 96, dateWidth = 155)).isTrue()
        // A date wider than the line by itself.
        assertThat(gaugesHeaderStacks(lineWidth = 163, nameWidth = 96, dateWidth = 237)).isTrue()
    }
}
