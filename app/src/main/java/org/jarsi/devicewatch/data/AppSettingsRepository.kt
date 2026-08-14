package org.jarsi.devicewatch.data

/** Charge-reminder slider bounds; 0 (off) is the only value outside this range. */
const val CHARGE_LIMIT_MIN = 50
const val CHARGE_LIMIT_MAX = 95

/** The level the charge reminder starts at when it is switched on. */
const val DEFAULT_CHARGE_LIMIT_PERCENT = 80

/** Data-quota bounds in gigabytes; 0 (off) is the only stored value outside this range. */
const val DATA_QUOTA_MIN_GB = 1.0
const val DATA_QUOTA_MAX_GB = 500.0

/** Where the quota slider stops; the store still accepts up to [DATA_QUOTA_MAX_GB]. */
const val DATA_QUOTA_SLIDER_MAX_GB = 100.0

/** The quota the slider starts at when it is switched on. */
const val DEFAULT_DATA_QUOTA_GB = 20.0

/**
 * Process-wide user settings that both the UI and [SystemStatsRepository] read.
 * Synchronous by design: the stats repository consults these inside its
 * synchronous compute path (see [AppSettingsRepositoryImpl]).
 */
interface AppSettingsRepository {
    fun dataCounterMode(): DataCounterMode

    fun setDataCounterMode(mode: DataCounterMode)

    /** Billing-cycle start day of month, always in 1..31. */
    fun cycleStartDay(): Int

    fun setCycleStartDay(day: Int)

    /** Charge-reminder level in percent; 0 = off, otherwise 50..95 in steps of 5. */
    fun chargeLimitPercent(): Int

    fun setChargeLimitPercent(value: Int)

    /** Mobile-data allowance per counting period in GB; 0 = no quota, otherwise 1..500. */
    fun dataQuotaGb(): Double

    fun setDataQuotaGb(value: Double)

    /**
     * Quota-alert latch, scoped to the counting period that started on
     * [periodStartEpochDay] so a new period re-arms both thresholds (80 and 100).
     */
    fun dataQuotaNotified(periodStartEpochDay: Long, threshold: Int): Boolean

    fun setDataQuotaNotified(periodStartEpochDay: Long, threshold: Int)

    /** Apps-tab "last opened" order; true = oldest (and never-used) first. */
    fun appsOldestFirst(): Boolean

    fun setAppsOldestFirst(oldestFirst: Boolean)

    /** True once the first-run intro has been completed or skipped. */
    fun onboardingShown(): Boolean

    fun setOnboardingShown()
}
