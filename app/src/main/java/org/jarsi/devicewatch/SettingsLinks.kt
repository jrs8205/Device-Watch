package org.jarsi.devicewatch

import android.service.quicksettings.TileService
import org.jarsi.devicewatch.system.ChargeLimitNotifier
import org.jarsi.devicewatch.system.DataQuotaNotifier
import org.jarsi.devicewatch.system.MonitorNotifications

/** The parts of the Settings tab that a page outside the app leads straight to, in the tab's order. */
enum class SettingsSection {
    DataCounter, Battery, Alerts, NotificationFigures, QuickTiles, FloatingFigure, Widgets, Screensaver
}

/** The section of this name, or null: how a request is written down and read back. */
internal fun settingsSectionNamed(name: String?): SettingsSection? =
    SettingsSection.entries.firstOrNull { it.name == name }

/**
 * The name the gear beside the screensaver in Android's settings starts the app by: an alias
 * of [MainActivity] in the manifest. Android starts a screensaver's settings with no action
 * and no extras, so the name is all that tells this start from any other.
 */
const val SCREENSAVER_SETTINGS_ALIAS = "org.jarsi.devicewatch.ScreensaverSettingsActivity"

/** The tile that shows and hides the floating card; its settings are a section of their own. */
const val FLOATING_FIGURE_TILE = "org.jarsi.devicewatch.overlay.FloatingFigureTileService"

/** What a start of the app carries, as far as the links into its settings read it. */
internal data class AppStart(
    val action: String? = null,
    /** The class the start names; an alias keeps its own name. */
    val componentClass: String? = null,
    /** The task was brought back from the recents list, which repeats the start that created it. */
    val fromRecents: Boolean = false,
    /** The tile a long press was on. */
    val tileClass: String? = null,
    /** The start is the link on one of Android's notification pages for this app. */
    val fromNotificationSettings: Boolean = false,
    /** The channel whose page that link was on, when it was on one. */
    val notificationChannel: String? = null,
    /** The section one of the app's own shortcuts names (the widgets' settings button). */
    val sectionName: String? = null,
)

/**
 * The part of the Settings tab a start of the app asks for, or null when it asks for none.
 * A start repeated from the recents list was answered long ago and asks for nothing.
 */
internal fun requestedSettingsSection(start: AppStart): SettingsSection? = when {
    start.fromRecents -> null
    start.componentClass == SCREENSAVER_SETTINGS_ALIAS -> SettingsSection.Screensaver
    start.action == TileService.ACTION_QS_TILE_PREFERENCES ->
        if (start.tileClass == FLOATING_FIGURE_TILE) SettingsSection.FloatingFigure else SettingsSection.QuickTiles
    start.fromNotificationSettings -> notificationSettingsSection(start.notificationChannel)
    else -> settingsSectionNamed(start.sectionName)
}

/**
 * The section whose settings decide what a notification channel carries. The page for the
 * app as a whole, and a channel the app no longer has, lead to the alerts.
 */
private fun notificationSettingsSection(channel: String?): SettingsSection = when (channel) {
    ChargeLimitNotifier.CHANNEL_ID -> SettingsSection.Battery
    DataQuotaNotifier.CHANNEL_ID -> SettingsSection.DataCounter
    MonitorNotifications.QUIET_CHANNEL_ID, MonitorNotifications.LIVE_CHANNEL_ID -> SettingsSection.NotificationFigures
    else -> SettingsSection.Alerts
}
