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
    val holdsCaps: Boolean get() = held.isNotEmpty() || pending
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
        val restored = applyLocked(CpuProfile.STOCK, emptyMap()).settled &&
            state.held.isEmpty() && state.pending == null
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
        val plan = CpuProfilePlanner.plan(snapshot.policies, profile, custom, before.originals, before.held)
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
        // A held cluster with every core off can be neither written nor forgotten:
        // its limit comes back with the cores.
        val offlineHeld = before.held.filterKeys { id -> snapshot.policies.none { it.id == id && it.online } }
        val held = plan.caps + offlineHeld
        val done = before.copy(
            active = profile,
            held = held,
            custom = chosenCustom,
            pending = null,
            bootProfile = null,
            overrideCount = 0,
            reapplyFailures = 0,
            notice = null,
        )
        if (writes.isEmpty()) {
            commit(done)
            return if (deferred) ApplyOutcome.HeldLower else ApplyOutcome.NothingToChange
        }

        // Write-ahead: if the process dies with the writes in flight, the next pass knows to look.
        commit(before.copy(pending = PendingApply(profile, writes, before.held, held, chosenCustom)))
        val readback = writer.write(writes) ?: return ApplyOutcome.Unconfirmed
        val lowered = readback.cpuThrottled || powerSave()
        val verdicts = writes.mapValues { (id, target) ->
            if (id in plan.restores) {
                CpuProfileRules.releaseAcceptance(target, readback.effective[id], before.held[id], lowered)
            } else {
                CpuProfileRules.acceptance(target, readback.effective[id], lowered)
            }
        }
        val refused = verdicts.filterValues { it == Acceptance.REJECTED }.keys
        val refusedCaps = refused.filterTo(LinkedHashSet()) { it in plan.caps }
        val stillHeld = before.held.filterKeys { it in refused && it in plan.restores }
        val released = plan.restores.keys - stillHeld.keys
        if (refusedCaps.isNotEmpty()) {
            // The limits go back to where they were, the refused ones included:
            // one that reads back lower for no known reason may well hold the
            // app's value all the same. A release is never undone: toward the
            // phone's own limits is always the safe way.
            val rollback = writes.keys.filter { it in plan.caps }.associateWith { id -> previousLimit(before, id, snapshot) }
            // Unanswered: the pending apply is still there for the next pass to settle.
            if (writer.write(rollback) == null) return ApplyOutcome.Unconfirmed
            commit(before.copy(pending = null, held = before.held - released))
            return ApplyOutcome.NotSupported(refusedCaps)
        }
        // A release that did not show stays on record, so Stock or the next pass can try again.
        commit(done.copy(held = done.held + stillHeld))
        return when {
            stillHeld.isNotEmpty() -> ApplyOutcome.Unconfirmed
            deferred || Acceptance.HELD_LOWER in verdicts.values -> ApplyOutcome.HeldLower
            else -> ApplyOutcome.Applied
        }
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
        if (state.held.isEmpty()) return
        if (state.active == CpuProfile.STOCK) {
            // Left from a Stock that found a cluster's cores off: released once they are back.
            applyLocked(CpuProfile.STOCK, emptyMap())
            return
        }
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
     * why. Written ahead like any apply, so nothing is forgotten before the kernel
     * shows it.
     */
    private fun Session.giveUp() {
        val restore = state.held.keys.mapNotNull { id -> state.originals[id]?.let { id to it } }.toMap()
        val stock = state.copy(active = CpuProfile.STOCK, held = emptyMap(), pending = null, reapplyFailures = 0)
        if (restore.isEmpty()) {
            commit(stock.copy(notice = CpuProfileNotice.ANDROID_KEEPS_CHANGING))
            return
        }
        commit(
            state.copy(
                pending = PendingApply(CpuProfile.STOCK, restore, state.held, emptyMap(), state.custom),
                reapplyFailures = 0,
                notice = CpuProfileNotice.ANDROID_KEEPS_CHANGING,
            )
        )
        writer.write(restore) ?: return
        commit(stock.copy(notice = CpuProfileNotice.ANDROID_KEEPS_CHANGING))
    }

    /** An apply whose answer never came: done if the kernel shows it, undone otherwise. */
    private fun Session.reconcilePending() {
        val pending = state.pending ?: return
        val snapshot = source.read() ?: return
        val effective = snapshot.policies.mapNotNull { p -> p.scalingMaxKHz?.let { p.id to it } }.toMap()
        val lowered = snapshot.cpuThrottled || powerSave()
        val verdicts = pending.writes.mapValues { (id, target) ->
            if (id in pending.held) {
                CpuProfileRules.acceptance(target, effective[id], lowered)
            } else {
                CpuProfileRules.releaseAcceptance(target, effective[id], pending.previousHeld[id], lowered)
            }
        }
        if (verdicts.values.none { it == Acceptance.REJECTED }) {
            settle(pending)
            return
        }
        if (pending.profile == CpuProfile.STOCK) {
            // Toward the phone's own limits is the safe way: written again, not undone.
            writer.write(pending.writes) ?: return
            settle(pending)
            return
        }
        // Limits go back; releases that showed stay released.
        val rollback = pending.writes.keys.filter { it in pending.held }.associateWith { id -> previousLimit(state, id, snapshot) }
        if (rollback.isNotEmpty()) writer.write(rollback) ?: return
        val released = verdicts.filter { (id, verdict) -> id !in pending.held && verdict != Acceptance.REJECTED }.keys
        commit(state.copy(pending = null, held = state.held - released))
    }

    private fun Session.settle(pending: PendingApply) {
        commit(
            state.copy(
                active = pending.profile,
                held = pending.held,
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

    /** Each online cluster's limit as the phone had it, saved once per boot before the first write. */
    private fun Session.captureOriginals(snapshot: KernelSnapshot) {
        val lowered = snapshot.cpuThrottled || powerSave()
        val originals = LinkedHashMap(state.originals)
        val assumed = LinkedHashSet(state.originalsAssumed)
        for (policy in snapshot.policies) {
            if (!policy.online || policy.id in originals) continue
            if (policy.id in state.held) {
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
