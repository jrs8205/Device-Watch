package com.example.modernwidget.data

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

    /** Per-UID Wi-Fi + (metered) mobile bytes since local midnight, largest first. */
    suspend fun dataConsumersToday(): List<AppDataUsage>

    /** All launcher-visible apps with their last-used time over a ~2 year range. */
    suspend fun launchableAppsByLastUse(): List<LaunchableApp>

    /** Keyguard-hidden count since midnight; [UNAVAILABLE_INT] below API 28 or without access. */
    suspend fun unlockCountToday(): Int
}
