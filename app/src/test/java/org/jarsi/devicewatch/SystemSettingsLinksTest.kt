package org.jarsi.devicewatch

import android.app.Notification
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.service.dreams.DreamService
import android.service.quicksettings.TileService
import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.SourceXml.actions
import org.jarsi.devicewatch.SourceXml.android
import org.jarsi.devicewatch.SourceXml.androidAttributes
import org.jarsi.devicewatch.SourceXml.application
import org.jarsi.devicewatch.SourceXml.categories
import org.jarsi.devicewatch.SourceXml.children
import org.jarsi.devicewatch.system.ChargeLimitNotifier
import org.jarsi.devicewatch.system.DataQuotaNotifier
import org.jarsi.devicewatch.system.HealthAlertNotifier
import org.jarsi.devicewatch.system.MonitorNotifications
import org.junit.Test
import org.w3c.dom.Element

/**
 * The ways from Android's own pages into this app's settings. Each is declared under a name
 * Android looks up, and a misspelt name fails without a word: from the first screensaver on
 * its extra information sat under `android.service.dreams`, which Android never reads, so
 * Android's screensaver page showed neither a settings gear nor the preview picture.
 */
class SystemSettingsLinksTest {

    private val mainActivity: Element = SourceXml.component("activity", ".MainActivity")

    private fun dreamService(): Element =
        application.children("service").single { DreamService.SERVICE_INTERFACE in it.actions() }

    // What the manifest and the resources declare

    @Test
    fun `the screensaver's extra information is under the name Android reads`() {
        val names = dreamService().children("meta-data").map { it.android("name") }

        assertThat(names).containsExactly(DreamService.DREAM_META_DATA)
    }

    @Test
    fun `the screensaver's gear starts a screen of this app that Android's settings may start`() {
        // DreamService takes "package/.Class" and turns down a class in any other package.
        val (packageName, shortClass) = SourceXml.parse("res/xml/dream_info.xml").android("settingsActivity").split("/")
        assertThat(packageName).isEqualTo(APPLICATION_ID)
        assertThat(packageName + shortClass).isEqualTo(SCREENSAVER_SETTINGS_ALIAS)

        val alias = SourceXml.component("activity-alias", shortClass)
        assertThat(alias.android("exported")).isEqualTo("true")
        assertThat(alias.android("targetActivity")).isEqualTo(".MainActivity")
    }

    @Test
    fun `a long press on a tile has a screen of this app to open`() {
        val answering = application.children("activity").filter { activity ->
            activity.children("intent-filter").any { TileService.ACTION_QS_TILE_PREFERENCES in it.actions() }
        }

        assertThat(answering.map { it.android("name") }).containsExactly(".MainActivity")
        assertThat(answering.single().android("exported")).isEqualTo("true")
    }

    @Test
    fun `the floating card's tile is one the manifest declares`() {
        val tiles = application.children("service")
            .filter { TileService.ACTION_QS_TILE in it.actions() }
            .map { APPLICATION_ID + it.android("name") }

        assertThat(tiles).hasSize(10)
        assertThat(tiles).contains(FLOATING_FIGURE_TILE)
    }

    @Test
    fun `Android's notification settings have a screen of this app to link to`() {
        // Android looks for MAIN with this category, and shows no link without both.
        val linked = mainActivity.children("intent-filter").filter {
            Notification.INTENT_CATEGORY_NOTIFICATION_PREFERENCES in it.categories()
        }

        assertThat(linked).hasSize(1)
        assertThat(linked.single().actions()).containsExactly(Intent.ACTION_MAIN)
    }

    @Test
    fun `the widgets' settings button has a screen of this app to open`() {
        val link = SourceXml.component("activity", ".widget.WidgetSettingsLinkActivity")

        assertThat(link.android("exported")).isEqualTo("true")
        assertThat(link.children("intent-filter").single().actions())
            .containsExactly(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
    }

    @Test
    fun `from Android 12 the Dashboard and Compact widgets carry the settings button and are still added without a stop`() {
        for (widget in listOf("dashboard_widget_info", "compact_widget_info")) {
            val before12 = SourceXml.parse("res/xml/$widget.xml").androidAttributes()
            val from12 = SourceXml.parse("res/xml-v31/$widget.xml").androidAttributes()

            // Before Android 12 a widget that names a settings screen is stopped at it when added.
            assertThat(before12).doesNotContainKey("configure")
            assertThat(from12["configure"]).isEqualTo("$APPLICATION_ID.widget.WidgetSettingsLinkActivity")
            assertThat(from12.getValue("widgetFeatures").split("|"))
                .containsExactly("reconfigurable", "configuration_optional")
            // Nothing else may drift between the two copies.
            assertThat(from12 - "configure" - "widgetFeatures").isEqualTo(before12)
        }
    }

    // What a start of the app asks for

    private fun section(start: AppStart) = requestedSettingsSection(start)

    private val longPress = AppStart(action = TileService.ACTION_QS_TILE_PREFERENCES, componentClass = MAIN_ACTIVITY)

    private val fromNotificationSettings =
        AppStart(action = Intent.ACTION_MAIN, componentClass = MAIN_ACTIVITY, fromNotificationSettings = true)

    @Test
    fun `a start through the screensaver's gear asks for the screensaver's settings`() {
        assertThat(section(AppStart(componentClass = SCREENSAVER_SETTINGS_ALIAS))).isEqualTo(SettingsSection.Screensaver)
    }

    @Test
    fun `a long press on a figure's tile asks for the tiles' settings`() {
        assertThat(section(longPress.copy(tileClass = "$APPLICATION_ID.quick.BatteryTileService")))
            .isEqualTo(SettingsSection.QuickTiles)
        // An Android that does not name the tile still gets the tiles' settings.
        assertThat(section(longPress)).isEqualTo(SettingsSection.QuickTiles)
    }

    @Test
    fun `a long press on the floating card's tile asks for the floating card's settings`() {
        assertThat(section(longPress.copy(tileClass = FLOATING_FIGURE_TILE))).isEqualTo(SettingsSection.FloatingFigure)
    }

    @Test
    fun `the link on Android's notification page for the app asks for the alerts`() {
        assertThat(section(fromNotificationSettings)).isEqualTo(SettingsSection.Alerts)
    }

    @Test
    fun `the link on a notification channel's page asks for the settings behind that channel`() {
        fun onChannel(id: String) = section(fromNotificationSettings.copy(notificationChannel = id))

        assertThat(onChannel(HealthAlertNotifier.CHANNEL_ID)).isEqualTo(SettingsSection.Alerts)
        assertThat(onChannel(ChargeLimitNotifier.CHANNEL_ID)).isEqualTo(SettingsSection.Battery)
        assertThat(onChannel(DataQuotaNotifier.CHANNEL_ID)).isEqualTo(SettingsSection.DataCounter)
        assertThat(onChannel(MonitorNotifications.LIVE_CHANNEL_ID)).isEqualTo(SettingsSection.NotificationFigures)
        assertThat(onChannel(MonitorNotifications.QUIET_CHANNEL_ID)).isEqualTo(SettingsSection.NotificationFigures)
        // A channel from an older version of the app, or none the app knows.
        assertThat(onChannel("system_monitor_channel")).isEqualTo(SettingsSection.Alerts)
    }

    @Test
    fun `a channel's name alone, without Android's notification page, asks for nothing`() {
        val start = AppStart(componentClass = MAIN_ACTIVITY, notificationChannel = DataQuotaNotifier.CHANNEL_ID)

        assertThat(section(start)).isNull()
    }

    @Test
    fun `the app's own shortcut names its section`() {
        for (wanted in SettingsSection.entries) {
            assertThat(section(AppStart(componentClass = MAIN_ACTIVITY, sectionName = wanted.name))).isEqualTo(wanted)
        }
        assertThat(section(AppStart(componentClass = MAIN_ACTIVITY, sectionName = "NoSuchSection"))).isNull()
    }

    @Test
    fun `an ordinary start asks for no part of the settings`() {
        assertThat(section(AppStart(action = Intent.ACTION_MAIN, componentClass = MAIN_ACTIVITY))).isNull()
        assertThat(section(AppStart())).isNull()
        // The tile's name alone is not a long press.
        assertThat(section(AppStart(action = Intent.ACTION_MAIN, tileClass = FLOATING_FIGURE_TILE))).isNull()
    }

    @Test
    fun `a task brought back from the recents list repeats no request`() {
        val starts = listOf(
            AppStart(componentClass = SCREENSAVER_SETTINGS_ALIAS),
            longPress,
            fromNotificationSettings,
            AppStart(sectionName = SettingsSection.Widgets.name),
        )

        for (start in starts) {
            assertThat(section(start)).isNotNull()
            assertThat(section(start.copy(fromRecents = true))).isNull()
        }
    }

    private companion object {
        const val APPLICATION_ID = "org.jarsi.devicewatch"
        const val MAIN_ACTIVITY = "$APPLICATION_ID.MainActivity"
    }
}
