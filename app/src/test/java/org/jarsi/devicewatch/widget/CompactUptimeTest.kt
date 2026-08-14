package org.jarsi.devicewatch.widget

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.data.UNAVAILABLE_TEXT
import org.junit.Test

class CompactUptimeTest {

    private fun text(millis: Long) = compactUptimeText(millis, "pv", "t", "min")

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test
    fun `under an hour reads in minutes`() {
        assertThat(text(0L)).isEqualTo("0 min")
        assertThat(text(45 * minute)).isEqualTo("45 min")
        assertThat(text(59 * minute + 59_000L)).isEqualTo("59 min")
    }

    @Test
    fun `under a day reads in whole hours`() {
        assertThat(text(hour)).isEqualTo("1 t")
        // Minutes are dropped on purpose: the cell is two characters wide at worst,
        // and the full figure stays on the large widget.
        assertThat(text(5 * hour + 30 * minute)).isEqualTo("5 t")
        assertThat(text(23 * hour + 59 * minute)).isEqualTo("23 t")
    }

    @Test
    fun `a day or more reads in whole days`() {
        assertThat(text(day)).isEqualTo("1 pv")
        assertThat(text(22 * day + 3 * hour)).isEqualTo("22 pv")
    }

    @Test
    fun `a missing reading stays a dash`() {
        assertThat(text(-1L)).isEqualTo(UNAVAILABLE_TEXT)
    }
}
