package com.example.modernwidget.data

/** Per-app foreground usage for the current day, aggregated from usage events. */
data class AppScreenTime(
    val packageName: String,
    val label: String,
    val foregroundMillis: Long,
    val launchCount: Int,
    val lastUsedMillis: Long,
)

/** Per-UID network usage for the current day (Wi-Fi plus metered mobile combined). */
data class AppDataUsage(
    val uid: Int,
    val packageName: String?,
    val label: String,
    val bytes: Long,
)

/** A launchable app with the last time the user opened it (null = never in the query range). */
data class LaunchableApp(
    val packageName: String,
    val label: String,
    val lastUsedEpochMillis: Long?,
    val isSystemApp: Boolean,
)

/** Precise usage totals since local midnight, computed in one usage-events pass. */
data class UsageTotals(
    val screenTimeMillis: Long,
    val unlockCount: Int,
)

/** Detail-sheet content for one app, assembled from the already-loaded tab data. */
data class AppUsageDetail(
    val packageName: String,
    val label: String,
    val foregroundMillisToday: Long,
    val lastOpenedEpochMillis: Long?,
    val launchCountToday: Int,
    val dataBytesToday: Long,
    /** [UNAVAILABLE_INT] when the notification listener is not enabled. */
    val notificationsToday: Int,
)
