package com.example.modernwidget.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SharedPreferences-backed settings store. Deliberately synchronous: the stats
 * repository reads these inside its synchronous compute path (under the stats
 * mutex on the default dispatcher), and SharedPreferences values are memory-cached
 * after the first load — the same pattern the screensaver already uses with
 * [com.example.modernwidget.system.DreamPreferences].
 */
@Singleton
class AppSettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : AppSettingsRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun dataCounterMode(): DataCounterMode {
        val stored = prefs.getString(KEY_DATA_COUNTER_MODE, null) ?: return DataCounterMode.DAY
        return try {
            DataCounterMode.valueOf(stored)
        } catch (_: IllegalArgumentException) {
            DataCounterMode.DAY
        }
    }

    override fun setDataCounterMode(mode: DataCounterMode) {
        prefs.edit().putString(KEY_DATA_COUNTER_MODE, mode.name).apply()
    }

    override fun cycleStartDay(): Int =
        prefs.getInt(KEY_CYCLE_START_DAY, DEFAULT_CYCLE_START_DAY).coerceIn(1, 31)

    override fun setCycleStartDay(day: Int) {
        prefs.edit().putInt(KEY_CYCLE_START_DAY, day.coerceIn(1, 31)).apply()
    }

    companion object {
        const val PREFS_NAME = "app_settings"
        const val KEY_DATA_COUNTER_MODE = "data_counter_mode"
        const val KEY_CYCLE_START_DAY = "cycle_start_day"
        const val DEFAULT_CYCLE_START_DAY = 1
    }
}
