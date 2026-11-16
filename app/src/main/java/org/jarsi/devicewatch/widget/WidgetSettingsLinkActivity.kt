package org.jarsi.devicewatch.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import org.jarsi.devicewatch.MainActivity
import org.jarsi.devicewatch.SettingsSection

/**
 * What the launcher's settings button on the Dashboard and Compact widgets opens
 * (Android 12+). Their settings are in the app and are shared by every widget, so this
 * screen shows nothing: it hands over to the Settings tab and leaves. The widgets need no
 * set-up, so the launcher does not open this when one is added.
 */
class WidgetSettingsLinkActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The launcher waits for a result; an OK naming the widget says it stays as it is.
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        startActivity(MainActivity.openSettingsSectionIntent(this, SettingsSection.Widgets))
        finish()
    }
}
