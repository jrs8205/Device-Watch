package org.jarsi.devicewatch.widget

import android.app.Application
import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.DataSpan
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders the compact widget from its stored state. Codex round 10: its data
 * cells read the period figures but were headed only MOBILE and WI-FI, so a
 * switch from the day to the billing cycle changed what the numbers meant with
 * nothing on the widget to say so.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CompactWidgetRenderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun state(mobileSpan: DataSpan?, wifiSpan: DataSpan?): Preferences = mutablePreferencesOf().apply {
        this[RefreshStatsAction.MOBILE_DATA_USED] = 1.5
        this[RefreshStatsAction.WIFI_BYTES_TODAY] = 4.8
        mobileSpan?.let { this[RefreshStatsAction.MOBILE_DATA_SPAN] = it.name }
        wifiSpan?.let { this[RefreshStatsAction.WIFI_DATA_SPAN] = it.name }
    }

    private fun render(prefs: Preferences, checks: GlanceAppWidgetUnitTest.() -> Unit) = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(180.dp, 180.dp))
        setState(prefs)
        provideComposable { CompactWidgetContent() }
        checks()
    }

    @Test
    fun `the data cells name the billing period their figures cover`() =
        render(state(DataSpan.PERIOD, DataSpan.PERIOD)) {
            onAllNodes(hasText(context.getString(R.string.widget_span_period))).assertCountEquals(2)
        }

    @Test
    fun `the data cells name today when counting by day`() =
        render(state(DataSpan.TODAY, DataSpan.TODAY)) {
            onAllNodes(hasText(context.getString(R.string.widget_span_today))).assertCountEquals(2)
        }

    @Test
    fun `mobile data counted since boot says so, next to Wi-Fi counted today`() =
        render(state(DataSpan.SINCE_BOOT, DataSpan.TODAY)) {
            onNode(hasText(context.getString(R.string.widget_span_since_boot))).assertExists()
            onNode(hasText(context.getString(R.string.widget_span_today))).assertExists()
        }

    @Test
    fun `the network stays named, by its icon, for a screen reader`() =
        render(state(DataSpan.TODAY, DataSpan.TODAY)) {
            onNode(hasContentDescription(context.getString(R.string.widget_tile_mobile))).assertExists()
            onNode(hasContentDescription(context.getString(R.string.widget_tile_wifi))).assertExists()
        }

    @Test
    fun `without a known span the cells keep the network names`() =
        render(state(mobileSpan = null, wifiSpan = null)) {
            onNode(hasText(context.getString(R.string.widget_tile_mobile))).assertExists()
            onNode(hasText(context.getString(R.string.widget_tile_wifi))).assertExists()
        }
}
