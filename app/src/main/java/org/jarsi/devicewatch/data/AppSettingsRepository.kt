package org.jarsi.devicewatch.data

/** Charge-reminder slider bounds; 0 (off) is the only value outside this range. */
const val CHARGE_LIMIT_MIN = 50
const val CHARGE_LIMIT_MAX = 95

/** The level the charge reminder starts at when it is switched on. */
const val DEFAULT_CHARGE_LIMIT_PERCENT = 80

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

    /** Apps-tab "last opened" order; true = oldest (and never-used) first. */
    fun appsOldestFirst(): Boolean

    fun setAppsOldestFirst(oldestFirst: Boolean)

    /** True once the first-run intro has been completed or skipped. */
    fun onboardingShown(): Boolean

    fun setOnboardingShown()
}
