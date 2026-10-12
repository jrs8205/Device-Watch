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

/**
 * An apply written ahead of its writes, so a process that dies with them in
 * flight, or a shell that stops answering, leaves the next pass enough to settle
 * it: done if the kernel shows it, undone (or, toward Stock, done again) if not.
 */
data class PendingApply(
    val profile: CpuProfile,
    val writes: Map<CpuPolicyId, Long>,
    /** The limits held before, to go back to. */
    val previousHeld: Map<CpuPolicyId, Long>,
    /** The limits to hold once it is done, including ones Android keeps lower for now and so were not written. */
    val held: Map<CpuPolicyId, Long>,
    val custom: Map<CpuPolicyId, Long>,
)

/** A cluster's limit as the phone had it before the app wrote anything this boot. */
data class CapturedOriginal(val kHz: Long, val assumed: Boolean)

/** Everything the processor profile keeps between passes and process starts. */
data class CpuProfileState(
    val active: CpuProfile = CpuProfile.STOCK,
    /** The limits the app holds now, by cluster; empty on Stock. */
    val held: Map<CpuPolicyId, Long> = emptyMap(),
    /**
     * Limits of the app's own that should have gone back to the phone's but have
     * not shown it yet (the kernel refused, the cluster's cores were off, root did
     * not answer), by cluster, at the value written: let go of again on each pass,
     * never held.
     */
    val releasing: Map<CpuPolicyId, Long> = emptyMap(),
    /** The user's Custom choices, kept across restarts. */
    val custom: Map<CpuPolicyId, Long> = emptyMap(),
    /** This boot's originals; Stock writes these back. */
    val originals: Map<CpuPolicyId, Long> = emptyMap(),
    /** Originals taken to be the maximum because heat or battery saver lowered the reading. */
    val originalsAssumed: Set<CpuPolicyId> = emptySet(),
    val pending: PendingApply? = null,
    val applyAfterRestart: Boolean = false,
    /**
     * The profile to set again after a restart, at the next screen-on pass. The
     * profile shows as Stock until then: nothing is held before it is written.
     */
    val bootProfile: CpuProfile? = null,
    /** How many times Android raised a held limit and the app set it again, this boot. */
    val overrideCount: Int = 0,
    val reapplyFailures: Int = 0,
    /** The kernel boot this state belongs to ([BootIdentity]); null before the first look. */
    val bootId: String? = null,
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
     * A write back to the phone's own limit raises the cluster, which the kernel
     * never refuses. It took unless the app's own limit [previousCap] still reads
     * back with nothing else to explain it; anything else lower than the target is
     * someone else's limit, not the app's.
     */
    fun releaseAcceptance(target: Long, readback: Long?, previousCap: Long?, lowered: Boolean): Acceptance = when {
        readback == null -> Acceptance.REJECTED
        readback >= target -> Acceptance.EXACT
        // Heat's limit comes from the same steps as the app's and can match it exactly.
        lowered -> Acceptance.HELD_LOWER
        previousCap != null && readback == previousCap -> Acceptance.REJECTED
        else -> Acceptance.HELD_LOWER
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
     * wrote over it (when one of its power hints ended, say). One that reads lower is heat or
     * Android's own lower limit and is left alone: the app never raises a cluster.
     */
    fun toReapply(held: Map<CpuPolicyId, Long>, effective: Map<CpuPolicyId, Long>): Set<CpuPolicyId> =
        held.filter { (id, cap) -> effective[id]?.let { it > cap } == true }.keys

    /**
     * A restart (a new BOOT_COUNT) wipes every limit in the kernel, so whatever
     * this boot held or saved is gone with it. Android sends BOOT_COMPLETED after
     * an app update too, which is why the count, not that broadcast, decides.
     */
    fun onBoot(state: CpuProfileState, current: String?): CpuProfileState {
        if (current == null || current == state.bootId) return state
        val stored = state.bootId ?: return state.copy(bootId = current)
        when (BootIdentity.sameBoot(stored, current)) {
            // The same boot: what was read now joins what was known, so either part can tell the next restart.
            true -> return state.copy(bootId = BootIdentity.merge(stored, current))
            // Neither the id nor the count on both sides: nothing to tell a restart by, so none is assumed.
            null -> return state.copy(bootId = current)
            false -> Unit
        }
        val fresh = state.copy(
            bootId = current,
            held = emptyMap(),
            releasing = emptyMap(),
            originals = emptyMap(),
            originalsAssumed = emptySet(),
            pending = null,
            active = CpuProfile.STOCK,
            bootProfile = null,
            overrideCount = 0,
            reapplyFailures = 0,
            notice = null,
        )
        // A profile still waiting from the boot before counts as the one chosen.
        val chosen = state.bootProfile ?: state.active.takeUnless { it == CpuProfile.STOCK } ?: return fresh
        return if (state.applyAfterRestart) {
            fresh.copy(bootProfile = chosen)
        } else {
            fresh.copy(notice = CpuProfileNotice.RESET_BY_RESTART)
        }
    }
}

/**
 * Which kernel boot this is, as `id=<boot_id>;count=<BOOT_COUNT>`. Only a kernel
 * restart clears the limits, so the kernel's own `boot_id` names the boot:
 * Android's BOOT_COUNT may also move when just the framework restarts, while the
 * limits stay. The count tells restarts apart only where the id could not be
 * read on one side, so a read that fails once never hides a restart, and one
 * that fails once on the same boot never invents one.
 */
object BootIdentity {
    private const val ID = "id="
    private const val COUNT = "count="

    /** How builds before 1.8.0 saved a count that stood in for the id. */
    private const val LEGACY_COUNT = "count:"

    fun of(bootId: String?, bootCount: Int?): String? {
        val id = bootId?.trim()?.takeIf { it.isNotEmpty() }
        val parts = listOfNotNull(id?.let { "$ID$it" }, bootCount?.let { "$COUNT$it" })
        return parts.joinToString(";").ifEmpty { null }
    }

    /** True or false where both name the id, else where both name the count; null where neither side can tell. */
    fun sameBoot(stored: String, current: String): Boolean? {
        val a = parse(stored)
        val b = parse(current)
        if (a.id != null && b.id != null) return a.id == b.id
        if (a.count != null && b.count != null) return a.count == b.count
        return null
    }

    /** The current identity, with whatever the stored one still knows that could not be read now. */
    fun merge(stored: String, current: String): String {
        val a = parse(stored)
        val b = parse(current)
        return of(b.id ?: a.id, (b.count ?: a.count)?.toIntOrNull()) ?: current
    }

    private class Parts(val id: String?, val count: String?)

    private fun parse(identity: String): Parts {
        var id: String? = null
        var count: String? = null
        for (part in identity.split(';')) {
            when {
                part.startsWith(ID) -> id = part.removePrefix(ID)
                part.startsWith(COUNT) -> count = part.removePrefix(COUNT)
                part.startsWith(LEGACY_COUNT) -> count = part.removePrefix(LEGACY_COUNT)
                // A bare id, as earlier builds saved it.
                part.isNotEmpty() -> id = part
            }
        }
        return Parts(id, count)
    }
}
