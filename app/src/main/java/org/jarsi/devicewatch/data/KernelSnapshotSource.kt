package org.jarsi.devicewatch.data

import javax.inject.Inject
import javax.inject.Singleton

/** The kernel's state as root reads it. Blocking: never call on the main thread. */
interface KernelSnapshotSource {
    /** A fresh read; null without root, with the screen off, or when root did not answer. */
    fun read(): KernelSnapshot?

    /** Just these clusters' limits in force now, by the same rules as [read]. */
    fun readMaxFreqs(ids: Collection<CpuPolicyId>): Map<CpuPolicyId, Long>?
}

/**
 * Reads through [RootCommandShell], whose gate already says no without root or
 * with the screen off. Nothing is cached: whoever shows the page keeps the last
 * good snapshot, and a limit is never planned from an old one.
 */
@Singleton
class ShellKernelSnapshotSource @Inject constructor(
    private val shell: RootCommandShell,
) : KernelSnapshotSource {

    override fun read(): KernelSnapshot? =
        shell.run(KernelCommands.FULL_READ, FULL_READ_TIMEOUT_MILLIS)?.let(KernelSysfsParser::parse)

    override fun readMaxFreqs(ids: Collection<CpuPolicyId>): Map<CpuPolicyId, Long>? {
        if (ids.isEmpty()) return emptyMap()
        return shell.run(KernelCommands.readMaxFreqs(ids))?.let(KernelSysfsParser::maxFreqs)
    }

    private companion object {
        /** About two hundred small files; slow thermal sensors on some phones take their time. */
        const val FULL_READ_TIMEOUT_MILLIS = 5_000L
    }
}
