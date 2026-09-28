package org.jarsi.devicewatch.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Flags about this phone rather than the user's choices: whether the intro has
 * run here and whether the permission dialog was ever launched here. They live
 * outside `app_settings`, the file Android's backup carries to a new phone
 * (`res/xml/data_extraction_rules.xml`): restored there, they skipped the intro
 * and made the permission button open the app's settings instead of the dialog.
 * 1.5.0 kept them in `app_settings`; the first read moves them here.
 */
internal object DeviceLocalFlags {

    private const val PREFS_NAME = "device_local"

    private val KEYS = listOf(
        AppSettingsRepositoryImpl.KEY_ONBOARDING_SHOWN,
        AppSettingsRepositoryImpl.KEY_RUNTIME_PERMISSIONS_REQUESTED,
    )

    @Synchronized
    fun prefs(context: Context): SharedPreferences {
        val local = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val settings = context.getSharedPreferences(AppSettingsRepositoryImpl.PREFS_NAME, Context.MODE_PRIVATE)
        val stale = KEYS.filter(settings::contains)
        if (stale.isNotEmpty()) {
            // Copied before it is removed, so an interruption leaves the flag in
            // both files, never in neither.
            local.edit().apply {
                stale.filterNot(local::contains).forEach { putBoolean(it, settings.getBoolean(it, false)) }
            }.commit()
            settings.edit().apply { stale.forEach(::remove) }.commit()
        }
        return local
    }
}
