package org.jarsi.devicewatch.presentation.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LabelValueRowTest {

    @Test
    fun `a label and value with room to spare stay on one line`() {
        assertThat(
            labelAndValueFitOnOneLine(
                labelWidth = 200,
                valueWidth = 300,
                gapWidth = 30,
                availableWidth = 1000
            )
        ).isTrue()
    }

    @Test
    fun `the gap counts towards the fit`() {
        // Exactly filling the width is still a fit; one pixel more is not, so the
        // two never touch.
        assertThat(labelAndValueFitOnOneLine(400, 570, 30, 1000)).isTrue()
        assertThat(labelAndValueFitOnOneLine(401, 570, 30, 1000)).isFalse()
    }

    @Test
    fun `a label grown by the system font pushes the value onto its own line`() {
        // What a plain Row gets wrong: the label claims nearly the whole width and
        // the value is left with a sliver to wrap inside.
        assertThat(labelAndValueFitOnOneLine(950, 300, 30, 1000)).isFalse()
    }

    @Test
    fun `a value wider than the whole row also stacks`() {
        assertThat(labelAndValueFitOnOneLine(100, 1200, 30, 1000)).isFalse()
    }
}
