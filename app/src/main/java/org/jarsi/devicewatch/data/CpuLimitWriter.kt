package org.jarsi.devicewatch.data

import javax.inject.Inject

/** The limits in force right after a write, and the clusters heat was holding down then. */
data class WriteReadback(val effective: Map<CpuPolicyId, Long>, val throttled: Set<CpuPolicyId>)

/** Writes cluster speed limits through the root-only gate. Blocking: never on the main thread. */
class CpuLimitWriter @Inject constructor(private val shell: RootCommandShell) {

    /** Null when root did not answer: the writes may or may not have run. */
    fun write(targets: Map<CpuPolicyId, Long>): WriteReadback? {
        val output = shell.run(KernelCommands.writeMaxFreqs(targets), WRITE_TIMEOUT_MILLIS) ?: return null
        return WriteReadback(
            effective = KernelSysfsParser.maxFreqs(output),
            throttled = KernelSysfsParser.parse(output).throttledPolicies,
        )
    }

    private companion object {
        const val WRITE_TIMEOUT_MILLIS = 5_000L
    }
}
