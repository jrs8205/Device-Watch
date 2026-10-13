package org.jarsi.devicewatch.data

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jarsi.devicewatch.R
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** What a read of the record came to. */
data class PrivacyLogRead(
    /** The record, built for the page; null when this Android keeps none. */
    val log: PrivacyLog?,
    /** This Android (11 or older) keeps no record of discrete accesses to show. */
    val unsupported: Boolean,
)

/** Android's record of private things used, as the privileged shell reads it. Blocking: never call on the main thread. */
interface PrivacyLogSource {
    /** The latest read; null when extended access is off, or the shell did not answer, or the dump could not be read. */
    fun read(): PrivacyLogRead?
}

/**
 * Reads `dumpsys appops --history --include-discrete 0` through whichever
 * [PrivilegedShell] is selected, Shizuku or root, on the [ShellBatteryUsageSource]
 * pattern: nothing while access is off, nothing new with the screen off, and one
 * dump at most every [MIN_INTERVAL_MILLIS], since a page that refreshes often
 * would otherwise have the system print its whole record each time. Android 11 and
 * older keep no such record, so there nothing is asked at all.
 */
@Singleton
class ShellPrivacyLogSource internal constructor(
    private val settings: AppSettingsRepository,
    private val shell: PrivilegedShell,
    private val screenOn: () -> Boolean,
    private val now: () -> Long,
    private val zone: () -> ZoneId,
    private val label: (Int, String) -> String,
    private val sdk: Int,
) : PrivacyLogSource {

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
        zone = ZoneId::systemDefault,
        label = PackageLabels(context)::label,
        sdk = Build.VERSION.SDK_INT,
    )

    private var cached: PrivacyLogRead? = null
    private var attemptedAtMillis = 0L
    private var accessSeen = PrivilegedAccess.OFF

    @Synchronized
    override fun read(): PrivacyLogRead? {
        val access = settings.privilegedAccess()
        if (access != accessSeen) {
            // Switched: what the other shell did or could not do no longer counts.
            accessSeen = access
            cached = null
            attemptedAtMillis = 0L
        }
        if (access == PrivilegedAccess.OFF) return null
        if (sdk < FIRST_SDK_WITH_RECORD) return UNSUPPORTED
        // Nobody sees the page under a locked screen; the last read stands and the shell is left alone.
        if (!screenOn()) return cached
        val time = now()
        if (attemptedAtMillis != 0L && time - attemptedAtMillis < MIN_INTERVAL_MILLIS) return cached
        attemptedAtMillis = time
        val dump = shell.run(COMMAND, DUMP_TIMEOUT_MILLIS)
        // A dump this Android should have but the parser could not read (a layout not
        // met before, a dump cut short) is a failed read, not an empty record.
        cached = dump?.let(::parse)?.let { history -> PrivacyLogRead(log = PrivacyLogBuilder.build(history, label), unsupported = false) }
        return cached
    }

    private fun parse(output: String): AppOpsHistory? = AppOpsHistoryParser.parse(output, zone())

    companion object {
        internal const val COMMAND = "dumpsys appops --history --include-discrete 0"
        internal const val MIN_INTERVAL_MILLIS = 30_000L
        private const val DUMP_TIMEOUT_MILLIS = 10_000L

        /** Android 12: the first with a record of discrete accesses (and a Privacy dashboard). */
        internal const val FIRST_SDK_WITH_RECORD = Build.VERSION_CODES.S

        private val UNSUPPORTED = PrivacyLogRead(log = null, unsupported = true)
    }
}

/** Names the package a record line belongs to: an installed app by its label, the system by what it is. */
internal class PackageLabels(private val context: Context) {

    fun label(uid: Int, packageName: String): String {
        when {
            uid == Process.ROOT_UID -> return context.getString(R.string.battery_usage_uid_root)
            uid == Process.SYSTEM_UID && packageName == SYSTEM_PACKAGE -> return context.getString(R.string.battery_usage_uid_system)
        }
        val packageManager = context.packageManager
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString().ifBlank { packageName }
        } catch (_: Exception) {
            packageName
        }
    }

    private companion object {
        const val SYSTEM_PACKAGE = "android"
    }
}
