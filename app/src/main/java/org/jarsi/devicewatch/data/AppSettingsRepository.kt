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
     * [periodStartEpochDay] so a new period re-arms both thresholds (80 and 100),
     * and to the quota the alert was judged against, so a latch the service writes
     * just after the user changed the quota cannot silence the new quota's alerts.
     */
    fun dataQuotaNotified(periodStartEpochDay: Long, quotaGb: Double, threshold: Int): Boolean

    fun setDataQuotaNotified(periodStartEpochDay: Long, quotaGb: Double, threshold: Int)

    /**
     * Moves on every change to the data quota, the counter mode or the cycle start
     * day. A stats reading carries the value it was taken under, so the monitor can
     * tell a reading that predates a change from a current one.
     */
    fun dataSettingsGeneration(): Long

    /** Apps-tab "last opened" order; true = oldest (and never-used) first. */
    fun appsOldestFirst(): Boolean

    fun setAppsOldestFirst(oldestFirst: Boolean)

    /** True once the first-run intro has been completed or skipped. */
    fun onboardingShown(): Boolean

    fun setOnboardingShown()

    /** True for the pre-1.6 look: wallpaper-based colours and the regular number font. */
    fun classicLook(): Boolean

    fun setClassicLook(enabled: Boolean)

    /**
     * Through which shell, if any, readings Android denies an ordinary app are
     * fetched ([PrivilegedShell]). Set only after that route granted access.
     */
    fun privilegedAccess(): PrivilegedAccess

    fun setPrivilegedAccess(access: PrivilegedAccess)

    /** Whether the user switched [alert] on; every alert is off by default. */
    fun alertEnabled(alert: HealthAlert): Boolean

    /** Switching an alert off also clears its latch, so switched on again it can alert at once. */
    fun setAlertEnabled(alert: HealthAlert, enabled: Boolean)

    /** True once [alert] has been posted and the condition has not yet cleared. */
    fun alertLatched(alert: HealthAlert): Boolean

    /** Moves on every switch of [alert], so a decision taken before a switch can tell. */
    fun alertGeneration(alert: HealthAlert): Long

    /**
     * Latches [alert] if it is still on and has not been switched since [generation];
     * returns whether it did. One step with the switch, so neither can slip between.
     */
    fun latchAlert(alert: HealthAlert, generation: Long): Boolean

    fun unlatchAlert(alert: HealthAlert)
}
