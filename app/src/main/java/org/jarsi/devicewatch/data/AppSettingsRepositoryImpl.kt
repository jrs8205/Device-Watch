package org.jarsi.devicewatch.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SharedPreferences-backed settings store. Deliberately synchronous: the stats
 * repository reads these inside its synchronous compute path (under the stats
 * mutex on the default dispatcher), and SharedPreferences values are memory-cached
 * after the first load — the same pattern the screensaver already uses with
 * [org.jarsi.devicewatch.system.DreamPreferences].
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

    override fun chargeLimitPercent(): Int =
        coerceChargeLimit(prefs.getInt(KEY_CHARGE_LIMIT_PERCENT, 0))

    override fun setChargeLimitPercent(value: Int) {
        prefs.edit().putInt(KEY_CHARGE_LIMIT_PERCENT, coerceChargeLimit(value)).apply()
    }

    /** 0 means off; anything else lands on a slider stop between 50 % and 95 %. */
    private fun coerceChargeLimit(value: Int): Int {
        if (value <= 0) return 0
        val bounded = value.coerceIn(CHARGE_LIMIT_MIN, CHARGE_LIMIT_MAX)
        // Nearest slider stop; both bounds are multiples of 5, so this stays in range.
        return ((bounded + 2) / 5) * 5
    }

    // Stored as a Float: SharedPreferences has no Double, and a plan size in GB needs
    // nothing finer than Float can represent.
    override fun dataQuotaGb(): Double =
        coerceDataQuota(prefs.getFloat(KEY_DATA_QUOTA_GB, 0f).toDouble())

    override fun setDataQuotaGb(value: Double) {
        val coerced = coerceDataQuota(value)
        val editor = prefs.edit().putFloat(KEY_DATA_QUOTA_GB, coerced.toFloat())
        if (coerced != dataQuotaGb()) {
            // A changed quota makes the 80 %/100 % crossings new events — re-arm
            // both latches instead of staying silent for the rest of the period.
            prefs.all.keys
                .filter { it.startsWith("$KEY_DATA_QUOTA_NOTIFIED_PREFIX:") }
                .forEach(editor::remove)
        }
        editor.apply()
    }

    /** 0 means no quota; anything else is a plan size bounded to 1..500 GB. */
    private fun coerceDataQuota(value: Double): Double =
        if (value <= 0.0) 0.0 else value.coerceIn(DATA_QUOTA_MIN_GB, DATA_QUOTA_MAX_GB)

    override fun dataQuotaNotified(periodStartEpochDay: Long, threshold: Int): Boolean =
        prefs.getBoolean(quotaNotifiedKey(periodStartEpochDay, threshold), false)

    override fun setDataQuotaNotified(periodStartEpochDay: Long, threshold: Int) {
        // The keys are period-scoped, so every period would otherwise leave two
        // booleans behind forever; older periods are pruned as the latch is written.
        val currentPeriodPrefix = "$KEY_DATA_QUOTA_NOTIFIED_PREFIX:$periodStartEpochDay:"
        val stale = prefs.all.keys.filter {
            it.startsWith("$KEY_DATA_QUOTA_NOTIFIED_PREFIX:") && !it.startsWith(currentPeriodPrefix)
        }
        val editor = prefs.edit()
        stale.forEach { editor.remove(it) }
        editor.putBoolean(quotaNotifiedKey(periodStartEpochDay, threshold), true).apply()
    }

    private fun quotaNotifiedKey(periodStartEpochDay: Long, threshold: Int): String =
        "$KEY_DATA_QUOTA_NOTIFIED_PREFIX:$periodStartEpochDay:$threshold"

    override fun appsOldestFirst(): Boolean =
        prefs.getBoolean(KEY_APPS_OLDEST_FIRST, true)

    override fun setAppsOldestFirst(oldestFirst: Boolean) {
        prefs.edit().putBoolean(KEY_APPS_OLDEST_FIRST, oldestFirst).apply()
    }

    override fun onboardingShown(): Boolean =
        prefs.getBoolean(KEY_ONBOARDING_SHOWN, false)

    override fun setOnboardingShown() {
        prefs.edit().putBoolean(KEY_ONBOARDING_SHOWN, true).apply()
    }

    companion object {
        const val PREFS_NAME = "app_settings"
        const val KEY_DATA_COUNTER_MODE = "data_counter_mode"
        const val KEY_CYCLE_START_DAY = "cycle_start_day"
        const val KEY_APPS_OLDEST_FIRST = "apps_oldest_first"
        const val KEY_CHARGE_LIMIT_PERCENT = "charge_limit_percent"
        const val KEY_DATA_QUOTA_GB = "data_quota_gb"
        /** Full key: "data_quota_notified:&lt;periodStartEpochDay&gt;:&lt;80|100&gt;". */
        const val KEY_DATA_QUOTA_NOTIFIED_PREFIX = "data_quota_notified"
        const val KEY_ONBOARDING_SHOWN = "onboarding_shown"
        /** Written from the UI helpers in OnboardingPage.kt (permanent-denial detection). */
        const val KEY_RUNTIME_PERMISSIONS_REQUESTED = "runtime_permissions_requested"
        const val DEFAULT_CYCLE_START_DAY = 1
    }
}
