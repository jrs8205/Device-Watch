package org.jarsi.devicewatch.data

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Commands that only root may run: the Kernel page's reads and the processor
 * limits it writes. Deliberately not a [PrivilegedShell], so neither can stand in
 * for the other where either is injected: the stats poll never gets this one,
 * and the kernel code never gets the one that may route to Shizuku.
 */
interface RootCommandShell {
    /** As [PrivilegedShell.run]: null when root is not selected, the screen is off, or root did not answer. */
    fun run(command: String, timeoutMillis: Long = PrivilegedShell.DEFAULT_TIMEOUT_MILLIS): String?
}

/**
 * The gate: a command passes only while root is the selected access and someone
 * is looking at the phone. It shares the one [RootShell] with
 * [SelectedPrivilegedShell], so there is still a single `su` and a single
 * generation guard, and has no way to reach Shizuku at all.
 */
@Singleton
class RootOnlyShell internal constructor(
    private val settings: AppSettingsRepository,
    private val root: RootShell,
    private val screenOn: () -> Boolean,
) : RootCommandShell {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        settings: AppSettingsRepository,
        root: RootShell,
    ) : this(settings, root, { context.getSystemService(PowerManager::class.java)?.isInteractive != false })

    override fun run(command: String, timeoutMillis: Long): String? {
        // Read before the setting, as SelectedPrivilegedShell does: a switch going
        // off persists OFF first and closes the shell after.
        val wanted = root.generation
        if (settings.privilegedAccess() != PrivilegedAccess.ROOT) return null
        if (!screenOn()) return null
        return root.run(command, timeoutMillis, wanted).also { giveUpRootIfLost(settings, root) }
    }
}
