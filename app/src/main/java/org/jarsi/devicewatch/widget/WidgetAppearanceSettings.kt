package org.jarsi.devicewatch.widget

import android.content.Context
import androidx.core.content.edit
import org.jarsi.devicewatch.data.AppSettingsRepositoryImpl

/** Shared default survives widget removal and is included in the existing settings backup. */
internal object WidgetAppearanceSettings {
    private const val BLACK_BACKGROUND = "widget_black_background"

    private fun prefs(context: Context) =
        context.getSharedPreferences(AppSettingsRepositoryImpl.PREFS_NAME, Context.MODE_PRIVATE)

    fun blackBackground(context: Context): Boolean? = prefs(context).let {
        if (it.contains(BLACK_BACKGROUND)) it.getBoolean(BLACK_BACKGROUND, false) else null
    }

    @Synchronized
    fun setBlackBackground(context: Context, black: Boolean) {
        prefs(context).edit { putBoolean(BLACK_BACKGROUND, black) }
    }

    /** A suspended legacy read must never overwrite a setting changed in the meantime. */
    @Synchronized
    fun migrateBlackBackground(context: Context, legacy: Boolean): Boolean =
        blackBackground(context) ?: legacy.also { setBlackBackground(context, it) }
}
