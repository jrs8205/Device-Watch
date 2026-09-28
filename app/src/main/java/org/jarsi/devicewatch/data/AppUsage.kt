package org.jarsi.devicewatch.data

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

/** One installed app's size on internal storage: code plus data (cache and own external files). */
data class AppStorageUsage(
    val packageName: String,
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
    /** Time the display was on today; null before Android 9 or when the pass saw no screen events. */
    val screenOnMillis: Long? = null,
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
    /** Read after the sheet opens; null until then, or when the package is gone. */
    val facts: AppPackageFacts? = null,
    /** When a foreground service of the app last ran after it was last opened; null for none. */
    val lastBackgroundMillis: Long? = null,
)

/**
 * The latest use of one app in Android's usage buckets: when an activity of it
 * was last in front, and when a foreground service of it (music, navigation, a
 * sync) last ran. Null for none on record.
 */
data class AppLastUse(val openedMillis: Long?, val foregroundServiceMillis: Long?)
