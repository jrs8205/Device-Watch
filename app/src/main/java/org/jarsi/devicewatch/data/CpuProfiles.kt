package org.jarsi.devicewatch.data

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import java.io.File
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jarsi.devicewatch.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton

/** How choosing a profile went. */
sealed interface ApplyOutcome {
    data object Applied : ApplyOutcome

    /** In place, but heat or battery saver hold a cluster lower still for now. */
    data object HeldLower : ApplyOutcome

    /** The kernel would not take a limit on these clusters; nothing changed. */
    data class NotSupported(val ids: Set<CpuPolicyId>) : ApplyOutcome

    /** Root did not answer; whether anything changed is settled on the next pass. */
    data object Unconfirmed : ApplyOutcome

    data object NoRoot : ApplyOutcome

    data object NothingToChange : ApplyOutcome
}

/** What the Kernel page shows about the profile. */
data class CpuProfileStatus(
    val active: CpuProfile = CpuProfile.STOCK,
    val held: Map<CpuPolicyId, Long> = emptyMap(),
    /** The app's limits still in the kernel that should be gone ([CpuProfileState.releasing]). */
    val releasing: Map<CpuPolicyId, Long> = emptyMap(),
    val custom: Map<CpuPolicyId, Long> = emptyMap(),
    val originals: Map<CpuPolicyId, Long> = emptyMap(),
    val originalsAssumed: Set<CpuPolicyId> = emptySet(),
    val applyAfterRestart: Boolean = false,
    val overrideCount: Int = 0,
    val notice: CpuProfileNotice? = null,
    val pending: Boolean = false,
    /** A profile is to be set again after the restart, at the next screen-on pass. */
    val waitingAfterRestart: Boolean = false,
) {
    /** The app holds, or may hold, a limit in the kernel right now. */
    val holdsCaps: Boolean get() = held.isNotEmpty() || releasing.isNotEmpty() || pending
}

/** The processor profile: choosing one, holding it, and letting go of it. */
interface CpuProfiles {
    val status: StateFlow<CpuProfileStatus>

    suspend fun apply(profile: CpuProfile, custom: Map<CpuPolicyId, Long> = emptyMap()): ApplyOutcome

    suspend fun setApplyAfterRestart(enabled: Boolean)

    /** One pass of the monitor: settles restarts and unfinished applies, and holds the limits. */
    suspend fun enforce()

    /** Stock, as the last thing root does before the user switches it off. True when nothing is left held. */
    suspend fun restoreStockBeforeRootOff(): Boolean

    /** Root went away by itself: no shell, just a note that the limits stay until a restart. */
    suspend fun onRootGone()
}

/**
 * Every operation runs under one lock, on fresh state from [store], after the
 * restart check: an apply and a monitor pass can never interleave, so a pass
 * never brings back a limit the user has just replaced.
 */
@Singleton
class CpuProfileController internal constructor(
    private val store: CpuProfileStore,
    private val source: KernelSnapshotSource,
    private val writer: CpuLimitWriter,
    private val settings: AppSettingsRepository,
    private val bootId: () -> String?,
    private val powerSave: () -> Boolean,
    private val dispatcher: CoroutineDispatcher,
) : CpuProfiles {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        store: CpuProfileStore,
        source: KernelSnapshotSource,
        writer: CpuLimitWriter,
        settings: AppSettingsRepository,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ) : this(
        store,
        source,
        writer,
        settings,
        bootId = {
            BootIdentity.of(
                bootId = try {
                    File(BOOT_ID_PATH).readText()
                } catch (_: Exception) {
                    null
                },
                bootCount = try {
                    Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
                } catch (_: Settings.SettingNotFoundException) {
                    null
                },
            )
        },
        powerSave = { context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true },
        dispatcher = dispatcher,
    )

    private val mutex = Mutex()
    private val _status = MutableStateFlow(statusOf(store.load()))
    override val status: StateFlow<CpuProfileStatus> = _status.asStateFlow()

    override suspend fun apply(profile: CpuProfile, custom: Map<CpuPolicyId, Long>): ApplyOutcome =
        session { applyLocked(profile, custom) }

    override suspend fun setApplyAfterRestart(enabled: Boolean) = session {
        // Off also drops a profile still waiting from a restart.
        commit(state.copy(applyAfterRestart = enabled, bootProfile = state.bootProfile.takeIf { enabled }))
    }

    override suspend fun enforce() = session { enforceLocked() }

    override suspend fun restoreStockBeforeRootOff(): Boolean = session {
        // Switching root off chooses Stock: a profile waiting from a restart goes too.
        if (state.bootProfile != null) commit(state.copy(bootProfile = null))
        if (!statusOf(state).holdsCaps) return@session true
        val restored = applyLocked(CpuProfile.STOCK, emptyMap()).settled && !statusOf(state).holdsCaps
        if (!restored) commit(state.copy(notice = CpuProfileNotice.KEPT_UNTIL_RESTART))
        restored
    }

    override suspend fun onRootGone() = session {
        if (statusOf(state).holdsCaps) commit(state.copy(notice = CpuProfileNotice.KEPT_UNTIL_RESTART))
    }

    /** The state as it stands inside the lock; [commit] writes it through to disk and the page. */
    private inner class Session(var state: CpuProfileState) {
        fun commit(next: CpuProfileState) {
            if (next == state) return
            state = next
            store.save(next)
            _status.value = statusOf(next)
        }
    }

    private suspend fun <R> session(block: Session.() -> R): R = mutex.withLock {
        withContext(dispatcher) {
            val loaded = store.load()
            val session = Session(loaded)
            session.commit(CpuProfileRules.onBoot(loaded, bootId()))
            _status.value = statusOf(session.state)
            session.block()
        }
    }

    private val ApplyOutcome.settled: Boolean
        get() = this == ApplyOutcome.Applied || this == ApplyOutcome.HeldLower || this == ApplyOutcome.NothingToChange

    private fun Session.applyLocked(profile: CpuProfile, custom: Map<CpuPolicyId, Long>): ApplyOutcome {
        if (settings.privilegedAccess() != PrivilegedAccess.ROOT) return ApplyOutcome.NoRoot
        if (state.pending != null) {
            reconcilePending()
            if (state.pending != null) return ApplyOutcome.Unconfirmed
        }
        val snapshot = source.read() ?: return ApplyOutcome.Unconfirmed
        if (profile != CpuProfile.STOCK) captureOriginals(snapshot)

        val before = state
        // Every limit of the app's in the kernel, wanted or not: each goes back unless the profile caps it.
        val written = before.held + before.releasing
        val plan = CpuProfilePlanner.plan(snapshot.policies, profile, custom, before.originals, written)
        val chosenCustom = if (profile == CpuProfile.CUSTOM) custom else before.custom
        if (profile != CpuProfile.STOCK && plan.caps.isEmpty() && plan.unsupported.isNotEmpty()) {
            return ApplyOutcome.NotSupported(plan.unsupported)
        }

        val effective = snapshot.policies.mapNotNull { p -> p.scalingMaxKHz?.let { p.id to it } }.toMap()
        val throttled = snapshot.throttledPolicies
        val writes = LinkedHashMap<CpuPolicyId, Long>()
        var deferred = false
        for ((id, target) in plan.caps) {
            val now = effective[id]
            // Android holds a lower limit in the same file (a power hint of its
            // own): writing would lift it, so the limit is held unwritten and
            // the monitor sets it once Android lets go. Heat is a limit of its
            // own, which a write does not lift; only heat on this very cluster
            // explains its reading, never heat on another.
            if (now != null && now < target && now != before.held[id] && id !in throttled) {
                deferred = true
            } else {
                writes[id] = target
            }
        }
        writes.putAll(plan.restores)
        // A held cluster with every core off can be neither written nor forgotten.
        // Its limit comes back with the cores: still held where the profile wants
        // one there, let go of once they are back where it does not.
        val offlineHeld = before.held.filterKeys { id ->
            snapshot.policies.none { it.id == id && it.online } && wantsCap(profile, custom, id)
        }
        val pending = PendingApply(profile, writes, previousHeld = written, held = plan.caps + offlineHeld, custom = chosenCustom)
        if (writes.isEmpty()) {
            finish(pending, unreleased = emptyMap())
            return if (deferred) ApplyOutcome.HeldLower else ApplyOutcome.NothingToChange
        }

        // Write-ahead: if the process dies with the writes in flight, the next pass knows to look.
        commit(before.copy(pending = pending))
        val readback = writer.write(writes) ?: return ApplyOutcome.Unconfirmed
        return complete(pending, readback, snapshot, deferred)
    }

    /** The presets limit every cluster but the little one; Custom only those chosen. */
    private fun wantsCap(profile: CpuProfile, custom: Map<CpuPolicyId, Long>, id: CpuPolicyId): Boolean = when (profile) {
        CpuProfile.STOCK -> false
        CpuProfile.CUSTOM -> id in custom
        CpuProfile.POWER_SAVING, CpuProfile.BALANCED -> true
    }

    /**
     * The writes ran and answered: what each one shows decides. A refused limit
     * undoes the apply; a release that did not show is tried again on the next pass.
     */
    private fun Session.complete(pending: PendingApply, readback: WriteReadback, snapshot: KernelSnapshot, deferred: Boolean = false): ApplyOutcome {
        val lowered = readback.cpuThrottled || powerSave()
        val verdicts = pending.writes.mapValues { (id, target) ->
            if (id in pending.held) {
                CpuProfileRules.acceptance(target, readback.effective[id], lowered)
            } else {
                CpuProfileRules.releaseAcceptance(target, readback.effective[id], pending.previousHeld[id], lowered)
            }
        }
        if (verdicts.any { (id, verdict) -> id in pending.held && verdict == Acceptance.REJECTED }) {
            return rollback(pending, verdicts, snapshot)
        }
        val unreleased = LinkedHashMap<CpuPolicyId, Long>()
        for ((id, verdict) in verdicts) {
            if (id in pending.held || verdict != Acceptance.REJECTED) continue
            unreleased[id] = pending.previousHeld[id] ?: readback.effective[id] ?: continue
        }
        finish(pending, unreleased)
        return when {
            unreleased.isNotEmpty() -> ApplyOutcome.Unconfirmed
            deferred || Acceptance.HELD_LOWER in verdicts.values -> ApplyOutcome.HeldLower
            else -> ApplyOutcome.Applied
        }
    }

    /**
     * The limits go back to where they were, the refused ones included: one that
     * reads back lower for no known reason may well hold the app's value all the
     * same. A release is never undone: toward the phone's own limits is always
     * the safe way. A limit the kernel will not let go of stays on record, to be
     * let go of on a later pass.
     */
    private fun Session.rollback(pending: PendingApply, verdicts: Map<CpuPolicyId, Acceptance>, snapshot: KernelSnapshot): ApplyOutcome {
        val refusedCaps = verdicts.filterTo(LinkedHashMap()) { (id, verdict) -> id in pending.held && verdict == Acceptance.REJECTED }.keys
        val released = verdicts.filter { (id, verdict) -> id !in pending.held && verdict != Acceptance.REJECTED }.keys
        val rollback = pending.writes.keys.filter { it in pending.held }.associateWith { id -> previousLimit(state, id, snapshot) }
        // Unanswered: the pending apply is still there for the next pass to settle.
        val readback = writer.write(rollback) ?: return ApplyOutcome.Unconfirmed
        val lowered = readback.cpuThrottled || powerSave()
        val stray = LinkedHashMap<CpuPolicyId, Long>()
        for ((id, target) in rollback) {
            // Back to a limit held before: refused or not, the record of that limit stands.
            if (id in state.held) continue
            val cap = pending.writes.getValue(id)
            if (CpuProfileRules.releaseAcceptance(target, readback.effective[id], cap, lowered) == Acceptance.REJECTED) stray[id] = cap
        }
        commit(state.copy(pending = null, held = state.held - released, releasing = state.releasing - released + stray))
        return ApplyOutcome.NotSupported(refusedCaps)
    }

    /** The apply is over: its limits are held, what it let go of is gone, and what would not go is tried again. */
    private fun Session.finish(pending: PendingApply, unreleased: Map<CpuPolicyId, Long>) {
        val releasing = LinkedHashMap(state.held + state.releasing)
        pending.writes.keys.forEach(releasing::remove)
        pending.held.keys.forEach(releasing::remove)
        releasing.putAll(unreleased)
        commit(
            state.copy(
                active = pending.profile,
                held = pending.held,
                releasing = releasing,
                custom = pending.custom,
                pending = null,
                bootProfile = null,
                overrideCount = 0,
                reapplyFailures = 0,
                // Stock keeps the reason it came about, such as Android winning.
                notice = if (pending.profile == CpuProfile.STOCK) state.notice else null,
            )
        )
    }

    private fun Session.enforceLocked() {
        if (settings.privilegedAccess() != PrivilegedAccess.ROOT) {
            // No root to write with: whatever is held stays in the kernel until a restart.
            if (statusOf(state).holdsCaps) commit(state.copy(notice = CpuProfileNotice.KEPT_UNTIL_RESTART))
            return
        }
        if (state.notice == CpuProfileNotice.KEPT_UNTIL_RESTART) commit(state.copy(notice = null))
        if (state.pending != null) {
            reconcilePending()
            return
        }
        state.bootProfile?.let { profile ->
            // Kept until it settles one way or the other: a first read that fails
            // after a restart is tried again on the next pass.
            when (applyLocked(profile, state.custom)) {
                ApplyOutcome.Applied, ApplyOutcome.HeldLower, ApplyOutcome.NothingToChange, ApplyOutcome.Unconfirmed -> Unit
                is ApplyOutcome.NotSupported, ApplyOutcome.NoRoot ->
                    commit(state.copy(bootProfile = null, notice = CpuProfileNotice.RESET_BY_RESTART))
            }
            return
        }
        if (state.releasing.isNotEmpty()) release()
        if (state.held.isEmpty()) return
        val effective = source.readMaxFreqs(state.held.keys) ?: return
        val raised = CpuProfileRules.toReapply(state.held, effective)
        if (raised.isEmpty()) {
            if (state.reapplyFailures != 0) commit(state.copy(reapplyFailures = 0))
            return
        }
        val targets = state.held.filterKeys { it in raised }
        val readback = writer.write(targets)
        val lowered = readback?.cpuThrottled == true || powerSave()
        val held = readback != null && targets.all { (id, target) ->
            CpuProfileRules.acceptance(target, readback.effective[id], lowered) != Acceptance.REJECTED
        }
        if (held) {
            commit(state.copy(overrideCount = state.overrideCount + 1, reapplyFailures = 0))
            return
        }
        val failures = state.reapplyFailures + 1
        if (failures < CpuProfileRules.MAX_REAPPLY_FAILURES) {
            commit(state.copy(reapplyFailures = failures))
            return
        }
        giveUp()
    }

    /**
     * Not fighting Android for ever: everything held goes back, and the page says
     * why. The limits move to the release list before anything is written, so a
     * process that dies mid-way still knows what to let go of.
     */
    private fun Session.giveUp() {
        commit(
            state.copy(
                active = CpuProfile.STOCK,
                held = emptyMap(),
                releasing = state.releasing + state.held,
                reapplyFailures = 0,
                notice = CpuProfileNotice.ANDROID_KEEPS_CHANGING,
            )
        )
        release()
    }

    /**
     * Limits that would not go when asked: let go of again, cluster by cluster, until
     * the kernel shows them gone. A cluster whose cores are off waits for them.
     */
    private fun Session.release() {
        val snapshot = source.read() ?: return
        val targets = LinkedHashMap<CpuPolicyId, Long>()
        for (policy in snapshot.policies) {
            if (!policy.online || policy.id !in state.releasing) continue
            targets[policy.id] = state.originals[policy.id] ?: policy.cpuinfoMaxKHz!!
        }
        if (targets.isEmpty()) return
        val readback = writer.write(targets) ?: return
        val lowered = readback.cpuThrottled || powerSave()
        val released = targets.filter { (id, target) ->
            CpuProfileRules.releaseAcceptance(target, readback.effective[id], state.releasing[id], lowered) != Acceptance.REJECTED
        }.keys
        if (released.isNotEmpty()) commit(state.copy(releasing = state.releasing - released))
    }

    /**
     * An apply whose answer never came. Done if the kernel shows every write
     * exactly; anything else, and the writes run again, since a reading that heat
     * or Android hold low says nothing about whether the write ever ran. Writing
     * again is safe either way: a limit only ever goes lower, a release only ever
     * toward the phone's own.
     */
    private fun Session.reconcilePending() {
        val pending = state.pending ?: return
        val snapshot = source.read() ?: return
        val effective = snapshot.policies.mapNotNull { p -> p.scalingMaxKHz?.let { p.id to it } }.toMap()
        val shown = pending.writes.all { (id, target) ->
            val reading = effective[id] ?: return@all false
            if (id in pending.held) reading == target else reading >= target
        }
        if (shown) {
            finish(pending, unreleased = emptyMap())
            return
        }
        val readback = writer.write(pending.writes) ?: return
        complete(pending, readback, snapshot)
    }

    /** Each online cluster's limit as the phone had it, saved once per boot before the first write. */
    private fun Session.captureOriginals(snapshot: KernelSnapshot) {
        val lowered = snapshot.cpuThrottled || powerSave()
        val originals = LinkedHashMap(state.originals)
        val assumed = LinkedHashSet(state.originalsAssumed)
        for (policy in snapshot.policies) {
            if (!policy.online || policy.id in originals) continue
            if (policy.id in state.held || policy.id in state.releasing) {
                // What reads now is the app's own limit, so it cannot be the phone's.
                originals[policy.id] = policy.cpuinfoMaxKHz!!
                assumed += policy.id
                continue
            }
            val captured = CpuProfileRules.captureOriginal(policy.scalingMaxKHz, policy.cpuinfoMaxKHz, lowered) ?: continue
            originals[policy.id] = captured.kHz
            if (captured.assumed) assumed += policy.id
        }
        commit(state.copy(originals = originals, originalsAssumed = assumed))
    }

    private fun previousLimit(state: CpuProfileState, id: CpuPolicyId, snapshot: KernelSnapshot): Long =
        state.held[id] ?: state.originals[id] ?: snapshot.policies.firstOrNull { it.id == id }?.cpuinfoMaxKHz
            ?: KernelCommands.MAX_KHZ

    private fun statusOf(state: CpuProfileState) = CpuProfileStatus(
        active = state.active,
        held = state.held,
        releasing = state.releasing,
        custom = state.custom,
        originals = state.originals,
        originalsAssumed = state.originalsAssumed,
        applyAfterRestart = state.applyAfterRestart,
        overrideCount = state.overrideCount,
        notice = state.notice,
        pending = state.pending != null,
        waitingAfterRestart = state.bootProfile != null,
    )

    private companion object {
        /** A new id at every kernel boot; readable by apps. */
        const val BOOT_ID_PATH = "/proc/sys/kernel/random/boot_id"
    }
}
