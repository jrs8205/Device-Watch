package org.jarsi.devicewatch

import android.service.quicksettings.TileService

/** The parts of the Settings tab that one of Android's own pages leads straight to. */
enum class SettingsSection { Screensaver, QuickTiles, FloatingFigure }

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

/**
 * The part of the Settings tab a start of the app asks for, or null when it asks for none.
 * [componentClass] is the class the start names and [tileClass] the tile a long press was on.
 * A task brought back from the recents list repeats the start that created it, long after
 * that request was answered, so it asks for nothing.
 */
internal fun requestedSettingsSection(
    action: String?,
    componentClass: String?,
    tileClass: String?,
    fromRecents: Boolean,
): SettingsSection? = when {
    fromRecents -> null
    componentClass == SCREENSAVER_SETTINGS_ALIAS -> SettingsSection.Screensaver
    action == TileService.ACTION_QS_TILE_PREFERENCES ->
        if (tileClass == FLOATING_FIGURE_TILE) SettingsSection.FloatingFigure else SettingsSection.QuickTiles
    else -> null
}
