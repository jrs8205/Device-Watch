package org.jarsi.devicewatch.data

/**
 * A cpufreq policy's directory name, `policy0` and the like. The constructor is
 * private: a name reaches a shell command only after [of] has checked it, so no
 * text read from the phone, or anywhere else, can become part of a path.
 */
@JvmInline
value class CpuPolicyId private constructor(val name: String) {

    /** The file whose value is this cluster's speed limit. */
    val maxFreqPath: String get() = "$CPUFREQ_DIR/$name/scaling_max_freq"

    companion object {
        const val CPUFREQ_DIR = "/sys/devices/system/cpu/cpufreq"
        private val NAME = Regex("policy\\d{1,4}")

        fun of(name: String): CpuPolicyId? = if (NAME.matches(name)) CpuPolicyId(name) else null
    }
}

/** Where a cluster's speed steps came from. */
enum class StepsSource { AVAILABLE, TIME_IN_STATE, NONE }

/**
 * One cpufreq policy: a cluster of cores that run at one speed. All speeds in kHz,
 * as the kernel gives them. A null is a file that could not be read.
 */
data class CpuPolicySnapshot(
    val id: CpuPolicyId,
    val relatedCpus: List<Int>,
    val cpuinfoMinKHz: Long?,
    val cpuinfoMaxKHz: Long?,
    val scalingMinKHz: Long?,
    /** The limit in force now: the lowest of every request, Android's, heat's and the app's. */
    val scalingMaxKHz: Long?,
    val curKHz: Long?,
    val governor: String?,
    val availableGovernors: List<String>,
    val driver: String?,
    /** The speeds the cluster can be held to, ascending, within the hardware range. */
    val steps: List<Long>,
    val stepsSource: StepsSource,
) {
    /** A cluster whose cores are all switched off answers nothing, and is never written to. */
    val online: Boolean get() = cpuinfoMaxKHz != null && scalingMaxKHz != null
}

/** The graphics processor, in kHz whatever unit its driver uses. */
data class GpuSnapshot(
    val curKHz: Long?,
    val maxKHz: Long?,
    val minKHz: Long?,
    val steps: List<Long>,
    /** The active one of the driver's power policies, where it has them. */
    val powerPolicy: String?,
)

/** A kernel cooling device: something heat can slow down, from 0 (not at all) to [max]. */
data class CoolingDevice(val index: Int, val type: String, val cur: Int, val max: Int)

/** A temperature sensor that reads an actual temperature. */
data class ThermalZone(val index: Int, val type: String, val celsius: Double)

data class ZramSnapshot(
    val disksizeBytes: Long?,
    val algorithm: String?,
    val origBytes: Long?,
    val comprBytes: Long?,
    val memUsedBytes: Long?,
) {
    /** How many times smaller the compressed memory is; null while nothing is compressed. */
    val ratio: Double?
        get() {
            val orig = origBytes ?: return null
            val compr = comprBytes ?: return null
            return if (compr > 0L && orig > 0L) orig.toDouble() / compr else null
        }
}

enum class SelinuxMode { ENFORCING, PERMISSIVE }

/** Everything the Kernel page shows, from one read. */
data class KernelSnapshot(
    /** Ascending by top speed: the little cluster first, the biggest last. */
    val policies: List<CpuPolicySnapshot>,
    val gpu: GpuSnapshot?,
    val coolingDevices: List<CoolingDevice>,
    val thermalZones: List<ThermalZone>,
    val zram: ZramSnapshot?,
    val kernelRelease: String?,
    val selinux: SelinuxMode?,
    /** Block device name to its active I/O scheduler. */
    val ioSchedulers: Map<String, String>,
) {
    /** Heat is holding a processor cluster below its limit right now. */
    val cpuThrottled: Boolean
        get() = coolingDevices.any { isCpuCooling(it.type) && it.cur > 0 }

    /**
     * The clusters heat is holding down right now, as far as the cooling devices
     * say which: `cpufreq-cpuN` names a cluster by its first core, `thermal-cpufreq-N`
     * counts the clusters in core order. A device that names no cluster of this
     * phone counts for none, so heat elsewhere never passes for heat on a cluster.
     */
    val throttledPolicies: Set<CpuPolicyId>
        get() {
            val hot = coolingDevices.filter { isCpuCooling(it.type) && it.cur > 0 }
            if (hot.isEmpty()) return emptySet()
            val byCore = policies.sortedBy { it.relatedCpus.minOrNull() ?: Int.MAX_VALUE }
            return hot.mapNotNullTo(LinkedHashSet()) { device ->
                MAINLINE_COOLING.matchEntire(device.type)?.let { m ->
                    val core = m.groupValues[1].toInt()
                    return@mapNotNullTo policies.firstOrNull { core in it.relatedCpus }?.id
                }
                PIXEL_COOLING.matchEntire(device.type)?.let { m -> byCore.getOrNull(m.groupValues[1].toInt())?.id }
            }
        }

    companion object {
        /**
         * `thermal-cpufreq-N` as Pixel's driver and older kernels name it,
         * `cpufreq-cpuN` as mainline Linux has since 5.13.
         */
        private val PIXEL_COOLING = Regex("thermal-cpufreq-(\\d{1,3})")
        private val MAINLINE_COOLING = Regex("cpufreq-cpu(\\d{1,4})")

        fun isCpuCooling(type: String): Boolean = PIXEL_COOLING.matches(type) || MAINLINE_COOLING.matches(type)
    }
}
