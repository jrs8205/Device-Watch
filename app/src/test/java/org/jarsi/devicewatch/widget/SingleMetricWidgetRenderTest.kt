package org.jarsi.devicewatch.widget

import android.app.Application
import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.QuickMetric
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Renders the single-figure widget (GitHub #7) from the state the updater writes. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SingleMetricWidgetRenderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun state(metric: QuickMetric, value: String, detail: String? = null, reason: String? = null): Preferences =
        mutablePreferencesOf().apply {
            this[RefreshStatsAction.SINGLE_METRIC] = metric.name
            this[RefreshStatsAction.SINGLE_VALUE] = value
            detail?.let { this[RefreshStatsAction.SINGLE_DETAIL] = it }
            reason?.let { this[RefreshStatsAction.SINGLE_REASON] = it }
        }

    private fun render(prefs: Preferences, size: DpSize = DpSize(110.dp, 110.dp), checks: GlanceAppWidgetUnitTest.() -> Unit) =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            setAppWidgetSize(size)
            setState(prefs)
            provideComposable { SingleMetricWidgetContent() }
            checks()
        }

    @Test
    fun `shows the figure's name and value`() =
        render(state(QuickMetric.MEMORY, "5,1 / 7,6 GB")) {
            onNode(hasText(context.getString(R.string.quick_memory_tile).uppercase())).assertExists()
            onNode(hasText("5,1 / 7,6 GB")).assertExists()
        }

    @Test
    fun `shows the detail line under the value`() =
        render(state(QuickMetric.DATA_TODAY, "1,4 GB", detail = "mob 307 MB · wifi 1,1 GB"), DpSize(57.dp, 57.dp)) {
            onNode(hasText("1,4 GB")).assertExists()
            onNode(hasText("mob 307 MB · wifi 1,1 GB")).assertExists()
        }

    @Test
    fun `a missing figure shows a dash and why`() =
        render(state(QuickMetric.CPU_TEMP, "—", reason = "vaatii laajennetun käytön")) {
            onNode(hasText("—")).assertExists()
            onNode(hasText("vaatii laajennetun käytön")).assertExists()
        }

    @Test
    fun `before its first reading it names the battery`() =
        render(mutablePreferencesOf()) {
            onNode(hasText(context.getString(R.string.quick_battery_tile).uppercase())).assertExists()
            onNode(hasText("—")).assertExists()
        }
}
