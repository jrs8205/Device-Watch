package org.jarsi.devicewatch.data

/** How a written limit read back. */
enum class Acceptance {
    /** Exactly as written. */
    EXACT,

    /** Lower, while heat or battery saver hold the cluster below it: the limit is in place. */
    HELD_LOWER,

    /** Higher, missing, or lower with nothing to explain it: the kernel did not take it. */
    REJECTED,
}

/** Something the Kernel page should tell about the profile beyond its name. */
enum class CpuProfileNotice {
    /** The phone restarted, its own limits came back, and the profile was not set to return. */
    RESET_BY_RESTART,

    /** Root went away while limits were held: they stay in force until the phone restarts. */
    KEPT_UNTIL_RESTART,

    /** Android raised the limit again faster than it could be held: the profile was let go. */
    ANDROID_KEEPS_CHANGING,
}

/** An apply whose writes may have run but whose answer never came: settled on the next pass. */
data class PendingApply(
    val profile: CpuProfile,
    val writes: Map<CpuPolicyId, Long>,
    val previousHeld: Map<CpuPolicyId, Long>,
)

/** A cluster's limit as the phone had it before the app wrote anything this boot. */
data class CapturedOriginal(val kHz: Long, val assumed: Boolean)

/** Everything the processor profile keeps between passes and process starts. */
data class CpuProfileState(
    val active: CpuProfile = CpuProfile.STOCK,
    /** The limits the app holds now, by cluster; empty on Stock. */
    val held: Map<CpuPolicyId, Long> = emptyMap(),
    /** The user's Custom choices, kept across restarts. */
    val custom: Map<CpuPolicyId, Long> = emptyMap(),
    /** This boot's originals; Stock writes these back. */
    val originals: Map<CpuPolicyId, Long> = emptyMap(),
    /** Originals taken to be the maximum because heat or battery saver lowered the reading. */
    val originalsAssumed: Set<CpuPolicyId> = emptySet(),
    val pending: PendingApply? = null,
    val applyAfterRestart: Boolean = false,
    /** A restart came and the profile is to return at the next screen-on pass. */
    val bootApplyDue: Boolean = false,
    /** How many times Android raised a held limit and the app set it again, this boot. */
    val overrideCount: Int = 0,
    val reapplyFailures: Int = 0,
    /** Settings.Global.BOOT_COUNT when last seen; -1 before the first look. */
    val bootCount: Int = -1,
    val notice: CpuProfileNotice? = null,
)

/** The profile's decisions that need no shell, kept apart so each can be tested alone. */
object CpuProfileRules {

    /** Failed re-applies in a row before the app stops fighting Android over the limit. */
    const val MAX_REAPPLY_FAILURES = 3

    /**
     * [lowered] is heat (a processor cooling device in use) or battery saver: both
     * hold a cluster below any limit, and the kernel then reads back their speed,
     * not the one written. Anything else lower is not explained, so not trusted.
     */
    fun acceptance(target: Long, readback: Long?, lowered: Boolean): Acceptance = when {
        readback == null -> Acceptance.REJECTED
        readback == target -> Acceptance.EXACT
        readback < target && lowered -> Acceptance.HELD_LOWER
        else -> Acceptance.REJECTED
    }

    /**
     * The limit to return to on Stock. What `scaling_max_freq` reads is already
     * the lowest of every limit, heat's included: read while heat or battery
     * saver hold the cluster down, it would make their limit permanent once
     * written back, so the hardware maximum is taken instead and marked assumed.
     */
    fun captureOriginal(readMax: Long?, cpuinfoMax: Long?, lowered: Boolean): CapturedOriginal? {
        if (readMax == null || cpuinfoMax == null) return null
        return if (readMax < cpuinfoMax && lowered) {
            CapturedOriginal(cpuinfoMax, assumed = true)
        } else {
            CapturedOriginal(minOf(readMax, cpuinfoMax), assumed = false)
        }
    }

    /**
     * Held clusters whose limit now reads higher than held: Android's power HAL
     * wrote over it (after the camera, say). One that reads lower is heat or
     * Android's own lower limit and is left alone: the app never raises a cluster.
     */
    fun toReapply(held: Map<CpuPolicyId, Long>, effective: Map<CpuPolicyId, Long>): Set<CpuPolicyId> =
        held.filter { (id, cap) -> effective[id]?.let { it > cap } == true }.keys

    /**
     * A restart (a new BOOT_COUNT) wipes every limit in the kernel, so whatever
     * this boot held or saved is gone with it. Android sends BOOT_COMPLETED after
     * an app update too, which is why the count, not that broadcast, decides.
     */
    fun onBootCount(state: CpuProfileState, current: Int?): CpuProfileState {
        if (current == null || current == state.bootCount) return state
        if (state.bootCount < 0) return state.copy(bootCount = current)
        val fresh = state.copy(
            bootCount = current,
            held = emptyMap(),
            originals = emptyMap(),
            originalsAssumed = emptySet(),
            pending = null,
            bootApplyDue = false,
            overrideCount = 0,
            reapplyFailures = 0,
            notice = null,
        )
        return when {
            state.active == CpuProfile.STOCK -> fresh
            !state.applyAfterRestart -> fresh.copy(active = CpuProfile.STOCK, notice = CpuProfileNotice.RESET_BY_RESTART)
            else -> fresh.copy(bootApplyDue = true)
        }
    }
}
