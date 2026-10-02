package org.jarsi.devicewatch.data

import android.content.Context
import android.os.Process
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jarsi.devicewatch.R
import javax.inject.Inject
import javax.inject.Singleton

/** Android's battery statistics as `dumpsys batterystats` prints them to a [PrivilegedShell]. */
@Singleton
class ShellBatteryUsageSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AppSettingsRepository,
    private val shell: PrivilegedShell,
) : BatteryUsageSource {

    private var cached: BatteryUsageReport? = null
    private var attemptedAtMillis = 0L
    private var accessSeen = PrivilegedAccess.OFF

    @Synchronized
    override fun sinceCharge(): BatteryUsageReport? {
        val access = settings.privilegedAccess()
        if (access != accessSeen) {
            // Switched: what the other shell did or could not do no longer counts.
            accessSeen = access
            cached = null
            attemptedAtMillis = 0L
        }
        if (access == PrivilegedAccess.OFF) return null
        val now = SystemClock.elapsedRealtime()
        // The dump holds the statistics lock in the system for most of a second:
        // an open page polling every 15 s is answered from the last one. A failed
        // attempt is remembered as long, so a shell that cannot deliver is not
        // asked for the whole dump again at every refresh.
        if (attemptedAtMillis != 0L && now - attemptedAtMillis < MAX_AGE_MILLIS) return cached
        attemptedAtMillis = now
        // --charged: since the last charge only, without the megabytes of history.
        val dump = shell.run("dumpsys batterystats --charged", DUMP_TIMEOUT_MILLIS)
        cached = dump?.let(BatteryStatsDumpParser::parse)?.let { BatteryUsageReport.from(it, ::label) }
        return cached
    }

    private fun label(uid: Int): String {
        when (uid) {
            Process.ROOT_UID -> return context.getString(R.string.battery_usage_uid_root)
            Process.SYSTEM_UID -> return context.getString(R.string.battery_usage_uid_system)
        }
        val packageManager = context.packageManager
        val packageName = try {
            packageManager.getPackagesForUid(uid)?.firstOrNull()
        } catch (_: Exception) {
            null
        }
        if (packageName != null) {
            return try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            } catch (_: Exception) {
                packageName
            }
        }
        // A system daemon without a package: "audioserver", "artd" and the like.
        val daemon = SystemUids.name(uid) ?: try {
            packageManager.getNameForUid(uid)?.substringBefore(':')
        } catch (_: Exception) {
            null
        }
        return if (daemon.isNullOrBlank()) {
            context.getString(R.string.battery_usage_uid_other, uid)
        } else {
            context.getString(R.string.battery_usage_uid_daemon, daemon)
        }
    }

    private companion object {
        const val MAX_AGE_MILLIS = 60_000L
        const val DUMP_TIMEOUT_MILLIS = 10_000L
    }
}
