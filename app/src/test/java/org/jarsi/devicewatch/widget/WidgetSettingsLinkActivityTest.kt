package org.jarsi.devicewatch.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.MainActivity
import org.jarsi.devicewatch.SettingsSection
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The settings button a launcher shows on the Dashboard and Compact widgets. Their settings
 * are in the app, so the button's screen only hands over to them and leaves.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetSettingsLinkActivityTest {

    private fun openedFor(widgetId: Int?): WidgetSettingsLinkActivity {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
        if (widgetId != null) intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        return Robolectric.buildActivity(WidgetSettingsLinkActivity::class.java, intent).create().get()
    }

    @Test
    fun `the button opens the app on the widgets' settings and leaves`() {
        val link = openedFor(widgetId = 7)

        val next = shadowOf(link).nextStartedActivity
        assertThat(next.component?.className).isEqualTo(MainActivity::class.java.name)
        assertThat(next.getStringExtra(MainActivity.EXTRA_SETTINGS_SECTION)).isEqualTo(SettingsSection.Widgets.name)
        assertThat(link.isFinishing).isTrue()
    }

    @Test
    fun `the launcher is told the widget stays as it is`() {
        val link = shadowOf(openedFor(widgetId = 7))

        assertThat(link.resultCode).isEqualTo(Activity.RESULT_OK)
        assertThat(link.resultIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)).isEqualTo(7)
    }

    @Test
    fun `opened without a widget it still hands over`() {
        val link = openedFor(widgetId = null)

        assertThat(shadowOf(link).nextStartedActivity.getStringExtra(MainActivity.EXTRA_SETTINGS_SECTION))
            .isEqualTo(SettingsSection.Widgets.name)
        assertThat(link.isFinishing).isTrue()
    }
}
