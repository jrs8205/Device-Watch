package org.jarsi.devicewatch.data

/**
 * Every command the Kernel page sends to root. Built only from constants,
 * [CpuPolicyId] paths and numbers, never from text read anywhere: nothing a file
 * on the phone says can change what a command does.
 *
 * Reads are one `grep -s -H '^'` over many files: one process instead of a `cat`
 * per file, each line labelled with its file, and files that are missing or
 * unreadable on this phone skipped without a word (stderr is dropped anyway).
 */
internal object KernelCommands {

    /** Far above any phone's processor (10 GHz), so only a garbled value is refused. */
    const val MAX_KHZ = 10_000_000L

    private const val GREP = "grep -s -H '^'"
    private const val POLICIES = "${CpuPolicyId.CPUFREQ_DIR}/policy*"
    private const val THERMAL = "/sys/class/thermal"
    private const val MALI = "/sys/class/misc/mali0/device"
    private const val KGSL = "/sys/class/kgsl/kgsl-3d0"

    private val CPU_FILES = listOf(
        "related_cpus", "cpuinfo_min_freq", "cpuinfo_max_freq", "scaling_min_freq", "scaling_max_freq",
        "scaling_cur_freq", "scaling_governor", "scaling_available_governors", "scaling_driver",
        "scaling_available_frequencies", "stats/time_in_state",
    )

    /** The counters every wakeup source keeps since boot; `active_time_ms` is how long it is being held right now. */
    val WAKEUP_FILES: List<String> = listOf(
        "name", "active_count", "event_count", "wakeup_count", "expire_count", "active_time_ms", "total_time_ms",
        "max_time_ms", "last_change_ms",
    )

    /** The kernel's tally of its sleep attempts, and what stopped the last failed one. */
    val SUSPEND_FILES: List<String> = listOf(
        "success", "fail", "failed_freeze", "failed_prepare", "failed_suspend", "failed_suspend_late", "failed_suspend_noirq",
        "failed_resume", "failed_resume_early", "failed_resume_noirq", "last_failed_dev", "last_failed_errno", "last_failed_step",
    )

    /** The sleep section's read: every wakeup source's counters, then the suspend statistics. */
    val WAKE_READ: String = (
        WAKEUP_FILES.map { "/sys/class/wakeup/wakeup*/$it" } + SUSPEND_FILES.map { "/sys/power/suspend_stats/$it" }
        ).joinToString(" ", prefix = "$GREP ")

    /** Written out glob by glob: `sh` on Android has no brace expansion to lean on. */
    val FULL_READ: String = (
        listOf("/proc/sys/kernel/osrelease", "/sys/fs/selinux/enforce") +
            CPU_FILES.map { "$POLICIES/$it" } +
            listOf("type", "temp").map { "$THERMAL/thermal_zone*/$it" } +
            COOLING_FILES +
            listOf("cur_freq", "max_freq", "min_freq", "available_frequencies", "power_policy").map { "$MALI/$it" } +
            listOf("gpuclk", "max_gpuclk", "gpu_available_frequencies").map { "$KGSL/$it" } +
            listOf("disksize", "comp_algorithm", "mm_stat").map { "/sys/block/zram0/$it" } +
            listOf("/sys/block/sd?/queue/scheduler", "/sys/block/mmcblk?/queue/scheduler")
        ).joinToString(" ", prefix = "$GREP ")

    /**
     * What the profile decides on: the clusters and the cooling devices, none of
     * the sensors that can take seconds to answer on some phones.
     */
    val CPU_READ: String = (CPU_FILES.map { "$POLICIES/$it" } + COOLING_FILES).joinToString(" ", prefix = "$GREP ")

    /** Reads back [ids]' speed limits. */
    fun readMaxFreqs(ids: Collection<CpuPolicyId>): String {
        require(ids.isNotEmpty()) { "nothing to read" }
        return "$GREP " + ordered(ids).joinToString(" ") { ShellProtocol.quote(it.maxFreqPath) }
    }

    /**
     * Sets each cluster's limit and reads every one of them back, with the cooling
     * devices and every cluster's cores (so heat can be told cluster by cluster),
     * in the same command: a write the kernel refused says nothing on stdout, so
     * the value read back is the only answer there is. `;` keeps one refused
     * write from stopping the rest.
     */
    fun writeMaxFreqs(targets: Map<CpuPolicyId, Long>): String {
        require(targets.isNotEmpty()) { "nothing to write" }
        targets.values.forEach { require(it in 1..MAX_KHZ) { "not a processor speed: $it" } }
        val ids = ordered(targets.keys)
        val writes = ids.joinToString("; ") { "echo ${targets.getValue(it)} > ${ShellProtocol.quote(it.maxFreqPath)}" }
        return "$writes; ${readMaxFreqs(ids)} $POLICIES/related_cpus ${COOLING_FILES.joinToString(" ")}"
    }

    /** All three: the parser keeps a cooling device only with its type and both states. */
    private val COOLING_FILES: List<String>
        get() = listOf("type", "cur_state", "max_state").map { "$THERMAL/cooling_device*/$it" }

    private fun ordered(ids: Collection<CpuPolicyId>): List<CpuPolicyId> =
        ids.distinct().sortedBy { it.name.removePrefix("policy").toInt() }
}
