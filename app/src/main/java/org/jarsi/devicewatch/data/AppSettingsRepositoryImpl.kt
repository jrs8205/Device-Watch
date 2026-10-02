package org.jarsi.devicewatch.data

import android.content.Context
import android.content.SharedPreferences
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
    @ApplicationContext private val context: Context,
) : AppSettingsRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    // Delivery on this phone is not a user setting. Neither backup rule file
    // includes this file, so a new phone can deliver its own first alerts.
    private val alertState = context.getSharedPreferences(ALERT_STATE_PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // Older 1.6.0 candidates stored latches in the backed-up settings. We
        // cannot tell an in-place update from a restore of that file, so discard
        // those latches instead of migrating them. This may re-alert once after
        // updating a candidate, but cannot silence a new phone's first alert.
        val legacyLatches = prefs.all.keys.filter {
            it.startsWith("$KEY_ALERT_LATCHED_PREFIX:") ||
                it.startsWith("$KEY_DATA_QUOTA_NOTIFIED_PREFIX:")
        }
        if (legacyLatches.isNotEmpty()) {
            prefs.edit().apply { legacyLatches.forEach(::remove) }.apply()
        }
    }

    override fun dataCounterMode(): DataCounterMode {
        val stored = prefs.getString(KEY_DATA_COUNTER_MODE, null) ?: return DataCounterMode.DAY
        return try {
            DataCounterMode.valueOf(stored)
        } catch (_: IllegalArgumentException) {
            DataCounterMode.DAY
        }
    }

    override fun setDataCounterMode(mode: DataCounterMode) {
        prefs.edit().putString(KEY_DATA_COUNTER_MODE, mode.name).nextDataSettingsGeneration().apply()
    }

    override fun cycleStartDay(): Int =
        prefs.getInt(KEY_CYCLE_START_DAY, DEFAULT_CYCLE_START_DAY).coerceIn(1, 31)

    override fun setCycleStartDay(day: Int) {
        prefs.edit().putInt(KEY_CYCLE_START_DAY, day.coerceIn(1, 31)).nextDataSettingsGeneration().apply()
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

    @Synchronized
    override fun setDataQuotaGb(value: Double) {
        val coerced = coerceDataQuota(value)
        val editor = prefs.edit().putFloat(KEY_DATA_QUOTA_GB, coerced.toFloat()).nextDataSettingsGeneration()
        if (coerced != dataQuotaGb()) {
            // A changed quota makes the 80 %/100 % crossings new events — re-arm
            // both latches instead of staying silent for the rest of the period.
            val latchEditor = alertState.edit()
            alertState.all.keys
                .filter { it.startsWith("$KEY_DATA_QUOTA_NOTIFIED_PREFIX:") }
                .forEach(latchEditor::remove)
            latchEditor.apply()
        }
        editor.apply()
    }

    /** 0 means no quota; anything else is a plan size bounded to 1..500 GB. */
    private fun coerceDataQuota(value: Double): Double =
        if (value <= 0.0) 0.0 else value.coerceIn(DATA_QUOTA_MIN_GB, DATA_QUOTA_MAX_GB)

    @Synchronized
    override fun dataQuotaNotified(periodStartEpochDay: Long, quotaGb: Double, threshold: Int): Boolean =
        alertState.getBoolean(quotaNotifiedKey(periodStartEpochDay, quotaGb, threshold), false)

    @Synchronized
    override fun setDataQuotaNotified(periodStartEpochDay: Long, quotaGb: Double, threshold: Int) {
        // The keys are scoped to a period and a quota, so every period would
        // otherwise leave booleans behind forever; latches for any other period or
        // quota are pruned as this one is written.
        val currentPrefix = quotaNotifiedPrefix(periodStartEpochDay, quotaGb)
        val stale = alertState.all.keys.filter {
            it.startsWith("$KEY_DATA_QUOTA_NOTIFIED_PREFIX:") && !it.startsWith(currentPrefix)
        }
        val editor = alertState.edit()
        stale.forEach { editor.remove(it) }
        editor.putBoolean(quotaNotifiedKey(periodStartEpochDay, quotaGb, threshold), true).apply()
    }

    private fun quotaNotifiedPrefix(periodStartEpochDay: Long, quotaGb: Double): String =
        "$KEY_DATA_QUOTA_NOTIFIED_PREFIX:$periodStartEpochDay:$quotaGb:"

    private fun quotaNotifiedKey(periodStartEpochDay: Long, quotaGb: Double, threshold: Int): String =
        quotaNotifiedPrefix(periodStartEpochDay, quotaGb) + threshold

    override fun dataSettingsGeneration(): Long = prefs.getLong(KEY_DATA_SETTINGS_GENERATION, 0L)

    /** Written in the same edit as the setting it versions, so the two can never disagree. */
    private fun SharedPreferences.Editor.nextDataSettingsGeneration(): SharedPreferences.Editor =
        putLong(KEY_DATA_SETTINGS_GENERATION, dataSettingsGeneration() + 1)

    override fun appsOldestFirst(): Boolean =
        prefs.getBoolean(KEY_APPS_OLDEST_FIRST, true)

    override fun setAppsOldestFirst(oldestFirst: Boolean) {
        prefs.edit().putBoolean(KEY_APPS_OLDEST_FIRST, oldestFirst).apply()
    }

    /** A flag about this phone, kept out of the backed-up settings ([DeviceLocalFlags]). */
    override fun onboardingShown(): Boolean =
        DeviceLocalFlags.prefs(context).getBoolean(KEY_ONBOARDING_SHOWN, false)

    override fun setOnboardingShown() {
        DeviceLocalFlags.prefs(context).edit().putBoolean(KEY_ONBOARDING_SHOWN, true).apply()
    }

    override fun classicLook(): Boolean =
        prefs.getBoolean(KEY_CLASSIC_LOOK, false)

    override fun setClassicLook(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CLASSIC_LOOK, enabled).apply()
    }

    /**
     * About this phone ([DeviceLocalFlags]): a restore onto another phone must not
     * start asking it for root or Shizuku.
     */
    override fun privilegedAccess(): PrivilegedAccess {
        val stored = DeviceLocalFlags.prefs(context).getString(KEY_PRIVILEGED_ACCESS, null)
        return PrivilegedAccess.entries.firstOrNull { it.name == stored } ?: PrivilegedAccess.OFF
    }

    override fun setPrivilegedAccess(access: PrivilegedAccess) {
        DeviceLocalFlags.prefs(context).edit().putString(KEY_PRIVILEGED_ACCESS, access.name).apply()
    }

    override fun alertEnabled(alert: HealthAlert): Boolean =
        prefs.getBoolean(alertKey(KEY_ALERT_ENABLED_PREFIX, alert), false)

    // The switch and the latch share this object's lock: the switch comes from the
    // UI, the latch from the monitor service, and a latch must never land after
    // the switch that cleared it. apply() updates the in-memory map at once, so
    // reads inside the lock always see the other side's write.
    @Synchronized
    override fun setAlertEnabled(alert: HealthAlert, enabled: Boolean) {
        val editor = prefs.edit()
            .putBoolean(alertKey(KEY_ALERT_ENABLED_PREFIX, alert), enabled)
            .putLong(alertKey(KEY_ALERT_GENERATION_PREFIX, alert), alertGeneration(alert) + 1)
        if (!enabled) alertState.edit().remove(alertKey(KEY_ALERT_LATCHED_PREFIX, alert)).apply()
        editor.apply()
    }

    override fun alertLatched(alert: HealthAlert): Boolean =
        alertState.getBoolean(alertKey(KEY_ALERT_LATCHED_PREFIX, alert), false)

    override fun alertGeneration(alert: HealthAlert): Long =
        prefs.getLong(alertKey(KEY_ALERT_GENERATION_PREFIX, alert), 0L)

    @Synchronized
    override fun latchAlert(alert: HealthAlert, generation: Long): Boolean {
        if (!alertEnabled(alert) || alertGeneration(alert) != generation) return false
        alertState.edit().putBoolean(alertKey(KEY_ALERT_LATCHED_PREFIX, alert), true).apply()
        return true
    }

    @Synchronized
    override fun unlatchAlert(alert: HealthAlert) {
        alertState.edit().remove(alertKey(KEY_ALERT_LATCHED_PREFIX, alert)).apply()
    }

    private fun alertKey(prefix: String, alert: HealthAlert): String = "$prefix:${alert.name.lowercase()}"

    companion object {
        const val PREFS_NAME = "app_settings"
        private const val ALERT_STATE_PREFS_NAME = "alert_delivery"
        const val KEY_DATA_COUNTER_MODE = "data_counter_mode"
        const val KEY_CYCLE_START_DAY = "cycle_start_day"
        const val KEY_APPS_OLDEST_FIRST = "apps_oldest_first"
        const val KEY_CHARGE_LIMIT_PERCENT = "charge_limit_percent"
        const val KEY_DATA_QUOTA_GB = "data_quota_gb"
        /** Full key: "data_quota_notified:&lt;periodStartEpochDay&gt;:&lt;quotaGb&gt;:&lt;80|100&gt;". */
        const val KEY_DATA_QUOTA_NOTIFIED_PREFIX = "data_quota_notified"
        const val KEY_DATA_SETTINGS_GENERATION = "data_settings_generation"
        const val KEY_ONBOARDING_SHOWN = "onboarding_shown"
        /** Written from the UI helpers in OnboardingPage.kt (permanent-denial detection). */
        const val KEY_RUNTIME_PERMISSIONS_REQUESTED = "runtime_permissions_requested"
        const val KEY_CLASSIC_LOOK = "classic_look"
        const val KEY_PRIVILEGED_ACCESS = "privileged_access"
        /** Full keys: "alert_enabled:hot_battery", "alert_latched:low_storage" and so on. */
        const val KEY_ALERT_ENABLED_PREFIX = "alert_enabled"
        const val KEY_ALERT_LATCHED_PREFIX = "alert_latched"
        const val KEY_ALERT_GENERATION_PREFIX = "alert_generation"
        const val DEFAULT_CYCLE_START_DAY = 1
    }
}
