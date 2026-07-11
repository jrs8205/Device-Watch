package org.jarsi.devicewatch.data

import java.time.LocalDate

/**
 * Per-app usage insights for the Apps tab: screen time, data usage, last-opened
 * dates and the daily unlock count. All queries are on-demand and potentially
 * slow (hundreds of ms) — they must never run inside the widget's 5-second
 * service loop, which only talks to [SystemStatsRepository].
 *
 * Every method returns an empty list / [UNAVAILABLE_INT] when Usage Access is
 * missing — never fabricated data.
 */
interface AppUsageRepository {
    fun hasUsageAccess(): Boolean

    /** Per-app foreground time, launch count and last use since local midnight. */
    suspend fun screenTimeToday(): List<AppScreenTime>

    /** Per-app foreground time, launch count and last use since [startMillis]. */
    suspend fun screenTimeSince(startMillis: Long): List<AppScreenTime>

    /** Per-UID Wi-Fi + (metered) mobile bytes since local midnight, largest first. */
    suspend fun dataConsumersToday(): List<AppDataUsage>

    /** Per-UID Wi-Fi + (metered) mobile bytes since [startMillis], largest first. */
    suspend fun dataConsumersSince(startMillis: Long): List<AppDataUsage>

    /** Unlock (keyguard-hidden) count since [startMillis]; null without support or access. */
    suspend fun unlockCountSince(startMillis: Long): Int?

    /** All launcher-visible apps with their last-used time over a ~2 year range. */
    suspend fun launchableAppsByLastUse(): List<LaunchableApp>

    /** Whether the platform can count unlocks at all (KEYGUARD_HIDDEN needs API 28). */
    fun supportsUnlockCounting(): Boolean

    /**
     * Unlock (keyguard-hidden) counts bucketed by local date over the last [days]
     * days including today. Empty without support or Usage Access. Android keeps
     * detailed events only ~7 days, hence the backfill window.
     */
    suspend fun unlockCountsByDay(days: Int): Map<LocalDate, Int>

    /**
     * Per-day total foreground time from Android's daily usage buckets over the
     * last [days] days. Bucket edges are approximate; today's value should be
     * overwritten with the precise [usageTotalsToday] result.
     */
    suspend fun screenTimeByDay(days: Int): Map<LocalDate, Long>

    /** Precise totals since local midnight in one event pass; null without access. */
    suspend fun usageTotalsToday(): UsageTotals?

    /** Packages that handle the HOME intent; used to keep launchers out of usage rankings. */
    fun launcherPackages(): Set<String>
}
