package org.jarsi.devicewatch.data

import android.content.Context
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jarsi.devicewatch.R
import javax.inject.Inject
import javax.inject.Singleton

/** Android's battery statistics as `dumpsys batterystats` prints them to a [PrivilegedShell]. */
@Singleton
class ShellBatteryUsageSource internal constructor(
    private val settings: AppSettingsRepository,
    private val shell: PrivilegedShell,
    /** Whether anyone is looking: with the screen off the dump is left unasked and the phone asleep. */
    private val screenOn: () -> Boolean,
    private val now: () -> Long,
    private val label: (Int) -> String,
) : BatteryUsageSource {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        settings: AppSettingsRepository,
        shell: PrivilegedShell,
    ) : this(
        settings,
        shell,
        screenOn = { context.getSystemService(PowerManager::class.java)?.isInteractive != false },
        now = SystemClock::elapsedRealtime,
        label = UidLabels(context)::label,
    )

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
        // The since-charge page keeps refreshing under a locked screen. Nobody
        // sees what the dump would say, so the last one stands, as the poll
        // leaves its shell alone with the screen off.
        if (!screenOn()) return cached
        val time = now()
        // The dump holds the statistics lock in the system for most of a second:
        // an open page polling every 15 s is answered from the last one. A failed
        // attempt is remembered as long, so a shell that cannot deliver is not
        // asked for the whole dump again at every refresh.
        if (attemptedAtMillis != 0L && time - attemptedAtMillis < MAX_AGE_MILLIS) return cached
        attemptedAtMillis = time
        // --charged: since the last charge only, without the megabytes of history.
        val dump = shell.run("dumpsys batterystats --charged", DUMP_TIMEOUT_MILLIS)
        cached = dump?.let(BatteryStatsDumpParser::parse)?.let { BatteryUsageReport.from(it, label) }
        return cached
    }

    private companion object {
        const val MAX_AGE_MILLIS = 60_000L
        const val DUMP_TIMEOUT_MILLIS = 10_000L
    }
}

/** Names the uids a battery statistics dump charges: apps by their label, system users by what they are. */
internal class UidLabels(private val context: Context) {

    fun label(uid: Int): String {
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
}
