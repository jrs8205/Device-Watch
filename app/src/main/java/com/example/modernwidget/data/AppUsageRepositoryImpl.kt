package com.example.modernwidget.data

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
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

        val end = System.currentTimeMillis()
        val usageByPackage: Map<String, UsageStats> = try {
            usageStatsManager()?.queryAndAggregateUsageStats(end - LAST_USE_RANGE_MS, end)
                ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }

        activities
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .map { appInfo ->
                LaunchableApp(
                    packageName = appInfo.packageName,
                    label = packageManager.getApplicationLabel(appInfo).toString(),
                    lastUsedEpochMillis = usageByPackage[appInfo.packageName]
                        ?.lastTimeUsed
                        ?.takeIf { it > 0L },
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }
    }

    override suspend fun unlockCountToday(): Int = withContext(dispatcher) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@withContext UNAVAILABLE_INT
        if (!hasUsageAccess()) return@withContext UNAVAILABLE_INT
        val usageStatsManager = usageStatsManager() ?: return@withContext UNAVAILABLE_INT

        var count = 0
        try {
            val events = usageStatsManager.queryEvents(startOfTodayMillis(), System.currentTimeMillis())
                ?: return@withContext UNAVAILABLE_INT
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) count++
            }
        } catch (_: Exception) {
            return@withContext UNAVAILABLE_INT
        }
        count
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
