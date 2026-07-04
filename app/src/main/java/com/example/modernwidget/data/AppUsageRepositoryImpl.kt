package com.example.modernwidget.data

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import com.example.modernwidget.di.DefaultDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUsageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : AppUsageRepository {

    override fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override suspend fun screenTimeToday(): List<AppScreenTime> = withContext(dispatcher) {
        if (!hasUsageAccess()) return@withContext emptyList()
        val usageStatsManager = usageStatsManager() ?: return@withContext emptyList()
        val end = System.currentTimeMillis()

        val samples = mutableListOf<UsageEventSample>()
        try {
            val events = usageStatsManager.queryEvents(startOfTodayMillis(), end)
                ?: return@withContext emptyList()
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                // ACTIVITY_RESUMED/PAUSED share the numeric values of the legacy
                // MOVE_TO_FOREGROUND/BACKGROUND constants, so one branch covers both eras.
                val kind = when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventKind.RESUME
                    UsageEvents.Event.ACTIVITY_PAUSED -> UsageEventKind.PAUSE
                    else -> null
                }
                if (kind != null) {
                    samples += UsageEventSample(event.packageName, kind, event.timeStamp)
                }
            }
        } catch (_: Exception) {
            return@withContext emptyList()
        }

        UsageEventAggregator.aggregateForegroundTime(samples, end)
            .map { (packageName, usage) ->
                AppScreenTime(
                    packageName = packageName,
                    label = appLabel(packageName),
                    foregroundMillis = usage.foregroundMillis,
                    launchCount = usage.launchCount,
                    lastUsedMillis = usage.lastUsedMillis,
                )
            }
            .sortedByDescending { it.foregroundMillis }
    }

    @Suppress("DEPRECATION") // querySummary(networkType, ...) is the public per-UID API
    override suspend fun dataConsumersToday(): List<AppDataUsage> = withContext(dispatcher) {
        if (!hasUsageAccess()) return@withContext emptyList()
        val statsManager = context.getSystemService(NetworkStatsManager::class.java)
            ?: return@withContext emptyList()
        val start = startOfTodayMillis()
        val end = System.currentTimeMillis()

        val bytesByUid = mutableMapOf<Int, Long>()
        for (networkType in intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            try {
                statsManager.querySummary(networkType, null, start, end)?.use { stats ->
                    val bucket = NetworkStats.Bucket()
                    while (stats.hasNextBucket()) {
                        stats.getNextBucket(bucket)
                        val bytes = bucket.rxBytes + bucket.txBytes
                        if (bytes > 0) bytesByUid.merge(bucket.uid, bytes, Long::plus)
                    }
                }
            } catch (_: Exception) {
                // One network type failing should not hide the other.
            }
        }

        bytesByUid.entries
            .map { (uid, bytes) ->
                val packageName = packageForUid(uid)
                AppDataUsage(
                    uid = uid,
                    packageName = packageName,
                    label = packageName?.let { appLabel(it) } ?: fallbackUidLabel(uid),
                    bytes = bytes,
                )
            }
            .sortedByDescending { it.bytes }
    }

    override suspend fun launchableAppsByLastUse(): List<LaunchableApp> = withContext(dispatcher) {
        if (!hasUsageAccess()) return@withContext emptyList()
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = try {
            packageManager.queryIntentActivities(launcherIntent, 0)
        } catch (_: Exception) {
            emptyList()
        }

        val lastUsedByPackage = queryLastUsedByPackage()

        activities
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .map { appInfo ->
                LaunchableApp(
                    packageName = appInfo.packageName,
                    label = packageManager.getApplicationLabel(appInfo).toString(),
                    lastUsedEpochMillis = lastUsedByPackage[appInfo.packageName],
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }
    }

    /**
     * Max lastTimeUsed per package, merged across every stats interval. A single
     * queryAndAggregateUsageStats over the 2-year range picks the yearly interval,
     * which can be completely empty on some devices/versions — merging yearly,
     * monthly, weekly and daily buckets returns whatever the platform actually has.
     */
    private fun queryLastUsedByPackage(): Map<String, Long> {
        val usageStatsManager = usageStatsManager() ?: return emptyMap()
        val end = System.currentTimeMillis()
        val start = end - LAST_USE_RANGE_MS
        val intervals = intArrayOf(
            UsageStatsManager.INTERVAL_YEARLY,
            UsageStatsManager.INTERVAL_MONTHLY,
            UsageStatsManager.INTERVAL_WEEKLY,
            UsageStatsManager.INTERVAL_DAILY,
        )

        val lastUsedByPackage = mutableMapOf<String, Long>()
        for (interval in intervals) {
            try {
                usageStatsManager.queryUsageStats(interval, start, end)?.forEach { bucket ->
                    val lastUsed = bucket.lastTimeUsed
                    if (lastUsed in 1..end) {
                        lastUsedByPackage.merge(bucket.packageName, lastUsed, ::maxOf)
                    }
                }
            } catch (_: Exception) {
                // One interval failing shouldn't hide the others.
            }
        }
        return lastUsedByPackage
    }

    override fun supportsUnlockCounting(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    override suspend fun unlockCountsByDay(days: Int): Map<LocalDate, Int> = withContext(dispatcher) {
        if (!supportsUnlockCounting() || !hasUsageAccess()) return@withContext emptyMap()
        val usageStatsManager = usageStatsManager() ?: return@withContext emptyMap()
        val zone = ZoneId.systemDefault()
        val startMillis = LocalDate.now()
            .minusDays((days - 1).coerceAtLeast(0).toLong())
            .atStartOfDay(zone).toInstant().toEpochMilli()

        val counts = mutableMapOf<LocalDate, Int>()
        try {
            val events = usageStatsManager.queryEvents(startMillis, System.currentTimeMillis())
                ?: return@withContext emptyMap()
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) {
                    val day = Instant.ofEpochMilli(event.timeStamp).atZone(zone).toLocalDate()
                    counts.merge(day, 1, Int::plus)
                }
            }
        } catch (_: Exception) {
            return@withContext emptyMap()
        }
        counts
    }

    override suspend fun screenTimeByDay(days: Int): Map<LocalDate, Long> = withContext(dispatcher) {
        if (!hasUsageAccess()) return@withContext emptyMap()
        val usageStatsManager = usageStatsManager() ?: return@withContext emptyMap()
        val zone = ZoneId.systemDefault()
        val startMillis = LocalDate.now()
            .minusDays((days - 1).coerceAtLeast(0).toLong())
            .atStartOfDay(zone).toInstant().toEpochMilli()

        val totals = mutableMapOf<LocalDate, Long>()
        try {
            val buckets = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startMillis, System.currentTimeMillis()
            ) ?: return@withContext emptyMap()
            for (bucket in buckets) {
                val foreground = bucket.totalTimeInForeground
                if (foreground <= 0L) continue
                val day = Instant.ofEpochMilli(bucket.firstTimeStamp).atZone(zone).toLocalDate()
                totals.merge(day, foreground, Long::plus)
            }
        } catch (_: Exception) {
            return@withContext emptyMap()
        }
        totals
    }

    override suspend fun usageTotalsToday(): UsageTotals? = withContext(dispatcher) {
        if (!hasUsageAccess()) return@withContext null
        val usageStatsManager = usageStatsManager() ?: return@withContext null
        val end = System.currentTimeMillis()

        val samples = mutableListOf<UsageEventSample>()
        var unlockCount = 0
        try {
            val events = usageStatsManager.queryEvents(startOfTodayMillis(), end)
                ?: return@withContext null
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED ->
                        samples += UsageEventSample(event.packageName, UsageEventKind.RESUME, event.timeStamp)

                    UsageEvents.Event.ACTIVITY_PAUSED ->
                        samples += UsageEventSample(event.packageName, UsageEventKind.PAUSE, event.timeStamp)

                    UsageEvents.Event.KEYGUARD_HIDDEN -> unlockCount++
                }
            }
        } catch (_: Exception) {
            return@withContext null
        }

        val screenTimeMillis = UsageEventAggregator.aggregateForegroundTime(samples, end)
            .values.sumOf { it.foregroundMillis }
        UsageTotals(screenTimeMillis, unlockCount)
    }

    private fun usageStatsManager(): UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    private fun startOfTodayMillis(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun appLabel(packageName: String): String = try {
        val packageManager = context.packageManager
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (_: Exception) {
        packageName
    }

    private fun packageForUid(uid: Int): String? = try {
        context.packageManager.getPackagesForUid(uid)?.firstOrNull()
    } catch (_: Exception) {
        null
    }

    private fun fallbackUidLabel(uid: Int): String =
        if (uid == Process.SYSTEM_UID) "Android" else "UID $uid"

    private companion object {
        /** How far back the "last opened" list looks. */
        private const val LAST_USE_RANGE_MS = 2L * 365 * 24 * 60 * 60 * 1000
    }
}
