package org.jarsi.devicewatch.data

/** The processor profiles the Kernel page offers. */
enum class CpuProfile { POWER_SAVING, BALANCED, STOCK, CUSTOM }

/** What one apply writes: limits to hold from now on, and clusters going back to their own. */
data class CpuPlan(
    val caps: Map<CpuPolicyId, Long>,
    val restores: Map<CpuPolicyId, Long>,
    /** Clusters the profile would limit but whose kernel lists no speeds to limit them to. */
    val unsupported: Set<CpuPolicyId>,
    /** Clusters with every core switched off: nothing can be read or written there now. */
    val skipped: Set<CpuPolicyId>,
) {
    val writes: Map<CpuPolicyId, Long> get() = restores + caps
}

/**
 * Turns a profile into speed limits, from nothing but what this phone's kernel
 * reports: no model is assumed. A limit is always one of the speeds the kernel
 * lists for that cluster, never above its hardware maximum, and never above what
 * the phone itself held before the app changed anything.
 */
object CpuProfilePlanner {

    const val POWER_SAVING_PERCENT = 60
    const val BALANCED_PERCENT = 80
    const val CUSTOM_MIN_PERCENT = 50

    /**
     * The cluster(s) with the lowest top speed. The presets leave them alone: they
     * run the phone's background work at little cost, and holding them back makes
     * everything feel slow for next to no saving.
     */
    fun littleIds(policies: List<CpuPolicySnapshot>): Set<CpuPolicyId> {
        val online = policies.filter { it.online }
        val lowest = online.minOfOrNull { it.cpuinfoMaxKHz!! } ?: return emptySet()
        return online.filter { it.cpuinfoMaxKHz == lowest }.map { it.id }.toSet()
    }

    /** Presets need a cluster faster than the little one to hold back. */
    fun presetsAvailable(policies: List<CpuPolicySnapshot>): Boolean {
        val online = policies.filter { it.online }
        val lowest = online.minOfOrNull { it.cpuinfoMaxKHz!! } ?: return false
        return online.any { it.cpuinfoMaxKHz!! > lowest }
    }

    /** The highest listed speed at or below [percent] of the maximum, and at or below [original]. */
    fun presetCap(policy: CpuPolicySnapshot, percent: Int, original: Long?): Long? {
        val max = policy.cpuinfoMaxKHz ?: return null
        val ceiling = minOf(max * percent / 100, original ?: max)
        return policy.steps.lastOrNull { it <= ceiling }
    }

    /** The speeds Custom offers: listed steps from half the maximum up to the original. */
    fun customSteps(policy: CpuPolicySnapshot, original: Long?): List<Long> {
        val max = policy.cpuinfoMaxKHz ?: return emptyList()
        val low = (max * CUSTOM_MIN_PERCENT + 99) / 100
        val high = minOf(max, original ?: max)
        return policy.steps.filter { it in low..high }
    }

    /** [wanted] snapped down to a step Custom offers, or up to the lowest one. */
    fun snapCustom(policy: CpuPolicySnapshot, wanted: Long, original: Long?): Long? {
        val steps = customSteps(policy, original)
        return steps.lastOrNull { it <= wanted } ?: steps.firstOrNull()
    }

    fun plan(
        policies: List<CpuPolicySnapshot>,
        profile: CpuProfile,
        custom: Map<CpuPolicyId, Long>,
        originals: Map<CpuPolicyId, Long>,
        held: Map<CpuPolicyId, Long>,
    ): CpuPlan {
        val online = policies.filter { it.online }
        val caps = LinkedHashMap<CpuPolicyId, Long>()
        val restores = LinkedHashMap<CpuPolicyId, Long>()
        val unsupported = LinkedHashSet<CpuPolicyId>()

        fun originalOf(policy: CpuPolicySnapshot): Long = originals[policy.id] ?: policy.cpuinfoMaxKHz!!

        // A cluster the new profile does not limit goes back to where the phone had it.
        fun release(policy: CpuPolicySnapshot) {
            if (policy.id in held) restores[policy.id] = originalOf(policy)
        }

        when (profile) {
            CpuProfile.STOCK -> online.forEach(::release)

            CpuProfile.POWER_SAVING, CpuProfile.BALANCED -> {
                val percent = if (profile == CpuProfile.POWER_SAVING) POWER_SAVING_PERCENT else BALANCED_PERCENT
                val little = littleIds(policies)
                val presets = presetsAvailable(policies)
                for (policy in online) {
                    val cap = if (presets && policy.id !in little) presetCap(policy, percent, originals[policy.id]) else null
                    when {
                        cap != null -> caps[policy.id] = cap
                        !presets || policy.id !in little -> {
                            unsupported += policy.id
                            release(policy)
                        }
                        else -> release(policy)
                    }
                }
            }

            CpuProfile.CUSTOM -> for (policy in online) {
                val wanted = custom[policy.id]
                val steps = customSteps(policy, originals[policy.id])
                when {
                    wanted == null -> release(policy)
                    steps.isEmpty() -> {
                        unsupported += policy.id
                        release(policy)
                    }
                    else -> {
                        val snapped = steps.lastOrNull { it <= wanted } ?: steps.first()
                        if (snapped >= originalOf(policy)) release(policy) else caps[policy.id] = snapped
                    }
                }
            }
        }

        return CpuPlan(
            caps = caps,
            restores = restores,
            unsupported = unsupported,
            skipped = policies.filterNot { it.online }.map { it.id }.toSet(),
        )
    }
}
