package org.jarsi.devicewatch

/** The parts of the Settings tab that one of Android's own pages leads straight to. */
enum class SettingsSection { Screensaver }

/**
 * The name the gear beside the screensaver in Android's settings starts the app by: an alias
 * of [MainActivity] in the manifest. Android starts a screensaver's settings with no action
 * and no extras, so the name is all that tells this start from any other.
 */
const val SCREENSAVER_SETTINGS_ALIAS = "org.jarsi.devicewatch.ScreensaverSettingsActivity"

/**
 * The part of the Settings tab a start of the app asks for, or null when it asks for none.
 * [componentClass] is the class the start names. A task brought back from the recents list
 * repeats the start that created it, long after that request was answered, so it asks for
 * nothing.
 */
internal fun requestedSettingsSection(componentClass: String?, fromRecents: Boolean): SettingsSection? = when {
    fromRecents -> null
    componentClass == SCREENSAVER_SETTINGS_ALIAS -> SettingsSection.Screensaver
    else -> null
}
