package org.jarsi.devicewatch.quick

import android.service.quicksettings.Tile
import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.data.QuickMetric
import org.jarsi.devicewatch.data.UNAVAILABLE_TEXT
import org.junit.Test

class QuickTileAppearanceTest {

    @Test
    fun `api 29 and later show the value as the subtitle`() {
        val a = quickTileAppearance(reading(QuickMetric.MEMORY, "5,1 / 7,6 GB"), names("Muisti"), sdkInt = 29)
        assertThat(a).isEqualTo(QuickTileAppearance("Muisti", "5,1 / 7,6 GB", Tile.STATE_INACTIVE))
    }

    @Test
    fun `before api 29 the value joins the label`() {
        val a = quickTileAppearance(reading(QuickMetric.MEMORY, "5,1 / 7,6 GB"), names("Muisti"), sdkInt = 28)
        assertThat(a).isEqualTo(QuickTileAppearance("Muisti 5,1 / 7,6 GB", null, Tile.STATE_INACTIVE))
    }

    @Test
    fun `a missing figure greys the tile and says why`() {
        val missing = QuickMetricReading(QuickMetric.CPU_TEMP, UNAVAILABLE_TEXT, null, "vaatii laajennetun käytön")
        val a = quickTileAppearance(missing, names("CPU:n lämpö"), sdkInt = 35)
        assertThat(a).isEqualTo(QuickTileAppearance("CPU:n lämpö", "vaatii laajennetun käytön", Tile.STATE_UNAVAILABLE))
    }

    @Test
    fun `before api 29 a missing figure keeps its name`() {
        val missing = QuickMetricReading(QuickMetric.CPU_TEMP, UNAVAILABLE_TEXT, null, "vaatii laajennetun käytön")
        val a = quickTileAppearance(missing, names("CPU:n lämpö"), sdkInt = 28)
        assertThat(a).isEqualTo(QuickTileAppearance("CPU:n lämpö —", null, Tile.STATE_UNAVAILABLE))
    }

    private fun reading(metric: QuickMetric, value: String) = QuickMetricReading(metric, value, null, null)

    private fun names(tile: String) = QuickMetricNames(title = tile, tileLabel = tile, shortName = tile)
}
