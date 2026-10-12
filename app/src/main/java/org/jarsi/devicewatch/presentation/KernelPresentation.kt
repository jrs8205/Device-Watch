package org.jarsi.devicewatch.presentation

import androidx.annotation.StringRes
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.CpuPolicyId
import org.jarsi.devicewatch.data.CpuPolicySnapshot
import org.jarsi.devicewatch.data.CpuProfile
import org.jarsi.devicewatch.data.CpuProfileStatus
import org.jarsi.devicewatch.data.KernelSnapshot
import java.util.Locale

/** A speed in kHz as people read it: gigahertz from one up, megahertz below. */
fun freqText(kHz: Long, locale: Locale): String =
    if (kHz >= KHZ_PER_GHZ) {
        String.format(locale, "%.2f GHz", kHz.toDouble() / KHZ_PER_GHZ)
    } else {
        String.format(locale, "%d MHz", kHz / KHZ_PER_MHZ)
    }

/** "2,29 / 2,91 GHz": a limit over the maximum, the unit said once when both share it. */
fun capOverMaxText(capKHz: Long, maxKHz: Long, locale: Locale): String =
    if (capKHz >= KHZ_PER_GHZ && maxKHz >= KHZ_PER_GHZ) {
        String.format(locale, "%.2f / %.2f GHz", capKHz.toDouble() / KHZ_PER_GHZ, maxKHz.toDouble() / KHZ_PER_GHZ)
    } else {
        "${freqText(capKHz, locale)} / ${freqText(maxKHz, locale)}"
    }

/** A cluster's place among the phone's clusters, by top speed. */
enum class ClusterRole(@StringRes val labelRes: Int) {
    ONLY(R.string.cluster_only),
    LITTLE(R.string.cluster_little),
    MIDDLE(R.string.cluster_middle),
    BIG(R.string.cluster_big),
}

fun clusterRoles(policies: List<CpuPolicySnapshot>): Map<CpuPolicyId, ClusterRole> {
    val maxima = policies.mapNotNull { it.cpuinfoMaxKHz }.distinct().sorted()
    return policies.associate { policy ->
        policy.id to when {
            maxima.size <= 1 -> ClusterRole.ONLY
            policy.cpuinfoMaxKHz == maxima.first() -> ClusterRole.LITTLE
            policy.cpuinfoMaxKHz == maxima.last() -> ClusterRole.BIG
            else -> ClusterRole.MIDDLE
        }
    }
}

/** "0–3" for consecutive cores, "0, 2, 5" otherwise. */
fun cpuRangeText(cpus: List<Int>): String {
    if (cpus.isEmpty()) return ""
    val sorted = cpus.sorted()
    if (sorted.size == 1) return sorted.single().toString()
    val consecutive = sorted.zipWithNext().all { (a, b) -> b == a + 1 }
    return if (consecutive) "${sorted.first()}–${sorted.last()}" else sorted.joinToString(", ")
}

/** What a kernel cooling device slows down, from its type name. */
sealed interface CoolingKind {
    /** A processor cluster, by its place in the kernel's cpufreq order. */
    data class CpuCluster(val index: Int) : CoolingKind
    data object Gpu : CoolingKind
    data object ChargingCurrent : CoolingKind
    data object Other : CoolingKind

    companion object {
        private val CPU = Regex("thermal-cpufreq-(\\d{1,3})")
        private val GPU = Regex("thermal-(gpufreq|devfreq)-\\d{1,3}")
        private val CHARGING = setOf("fcc", "dc_icl", "battery", "charge-limit")

        fun of(type: String): CoolingKind {
            CPU.matchEntire(type)?.let { return CpuCluster(it.groupValues[1].toInt()) }
            if (GPU.matches(type)) return Gpu
            if (type in CHARGING) return ChargingCurrent
            return Other
        }
    }
}

/** A plain name for the temperature sensors phones commonly name; null keeps the sensor's own. */
@StringRes
fun thermalZoneLabelRes(type: String): Int? = when (type.lowercase(Locale.ROOT)) {
    "big", "cpu-big", "cpu2" -> R.string.zone_big
    "mid", "cpu-mid", "cpu1" -> R.string.zone_mid
    "little", "cpu-little", "cpu0" -> R.string.zone_little
    "g3d", "gpu", "gpu-thermal", "gpuss-0" -> R.string.zone_gpu
    "tpu" -> R.string.zone_tpu
    "battery", "battery-thermal" -> R.string.zone_battery
    "charger_therm", "chg_therm" -> R.string.zone_charger
    "display_therm", "disp_therm" -> R.string.zone_display
    "usb_conn_therm", "usb_therm" -> R.string.zone_usb
    "modem_therm" -> R.string.zone_modem
    "quiet_therm", "skin_therm", "skin", "sdm-therm" -> R.string.zone_skin
    "soc_therm" -> R.string.zone_soc
    else -> null
}

/** The Device tab's KERNEL band: the profile, the fastest cluster's limit, and heat. */
data class KernelSummary(
    val profile: CpuProfile,
    val bigCapKHz: Long?,
    val bigMaxKHz: Long?,
    val throttled: Boolean,
    /** The kernel lists speed steps for at least one cluster, so profiles can work. */
    val supported: Boolean,
) {
    companion object {
        fun from(snapshot: KernelSnapshot, status: CpuProfileStatus): KernelSummary {
            val big = snapshot.policies.lastOrNull { it.online }
            return KernelSummary(
                profile = status.active,
                bigCapKHz = big?.scalingMaxKHz,
                bigMaxKHz = big?.cpuinfoMaxKHz,
                throttled = snapshot.cpuThrottled,
                supported = snapshot.policies.any { it.online && it.steps.isNotEmpty() },
            )
        }
    }
}

@StringRes
fun profileLabelRes(profile: CpuProfile): Int = when (profile) {
    CpuProfile.POWER_SAVING -> R.string.cpu_profile_power_saving
    CpuProfile.BALANCED -> R.string.cpu_profile_balanced
    CpuProfile.STOCK -> R.string.cpu_profile_stock
    CpuProfile.CUSTOM -> R.string.cpu_profile_custom
}

private const val KHZ_PER_GHZ = 1_000_000L
private const val KHZ_PER_MHZ = 1_000L
