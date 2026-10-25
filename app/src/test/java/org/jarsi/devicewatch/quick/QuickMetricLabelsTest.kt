package org.jarsi.devicewatch.quick

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.data.QuickMetric
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class QuickMetricLabelsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every figure has its own title and tile label`() {
        val names = quickMetricLabels(context).names
        assertThat(names.keys).containsExactlyElementsIn(QuickMetric.entries)
        assertThat(names.values.map { it.title }.toSet()).hasSize(QuickMetric.entries.size)
        assertThat(names.values.map { it.tileLabel }.toSet()).hasSize(QuickMetric.entries.size)
        assertThat(names.getValue(QuickMetric.CPU_TEMP).title).isEqualTo("Processor temperature")
    }

    @Test
    @Config(qualifiers = "fi")
    fun `finnish names and units`() {
        val labels = quickMetricLabels(context)
        assertThat(labels.names.getValue(QuickMetric.UPTIME).shortName).isEqualTo("Päällä")
        assertThat(labels.hourUnit).isEqualTo("t")
        assertThat(labels.needsUsageAccess).isEqualTo("vaatii käyttötietojen luvan")
    }
}
