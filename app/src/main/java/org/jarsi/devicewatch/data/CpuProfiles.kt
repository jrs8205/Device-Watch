package org.jarsi.devicewatch.data

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
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
    private val bootCount: () -> Int?,
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
        bootCount = {
            try {
                Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
            } catch (_: Settings.SettingNotFoundException) {
                null
            }
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
        commit(state.copy(applyAfterRestart = enabled))
    }

    override suspend fun enforce() = session { enforceLocked() }

    override suspend fun restoreStockBeforeRootOff(): Boolean = session {
        if (!statusOf(state).holdsCaps) return@session true
        val outcome = applyLocked(CpuProfile.STOCK, emptyMap())
        val restored = outcome == ApplyOutcome.Applied || outcome == ApplyOutcome.HeldLower ||
            outcome == ApplyOutcome.NothingToChange
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
            session.commit(CpuProfileRules.onBootCount(loaded, bootCount()))
            _status.value = statusOf(session.state)
            session.block()
        }
    }

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
        val writes = plan.writes
        if (writes.isEmpty()) {
            commit(before.copy(active = profile, custom = chosenCustom, held = emptyMap(), notice = null))
            return ApplyOutcome.NothingToChange
        }

        // Write-ahead: if the process dies with the writes in flight, the next pass knows to look.
        commit(before.copy(pending = PendingApply(profile, writes, before.held)))
        val readback = writer.write(writes) ?: return ApplyOutcome.Unconfirmed
        val lowered = readback.cpuThrottled || powerSave()
        val verdicts = writes.mapValues { (id, target) -> CpuProfileRules.acceptance(target, readback.effective[id], lowered) }
        val refused = verdicts.filterValues { it == Acceptance.REJECTED }.keys
        if (refused.isNotEmpty()) {
            // All or nothing: the clusters that did take go back to where they were.
            val rollback = (writes.keys - refused).associateWith { id -> previousLimit(before, id, snapshot) }
            if (rollback.isNotEmpty()) writer.write(rollback)
            commit(before.copy(pending = null))
            return ApplyOutcome.NotSupported(refused)
        }
        commit(
            before.copy(
                active = profile,
                held = plan.caps,
                custom = chosenCustom,
                pending = null,
                overrideCount = 0,
                reapplyFailures = 0,
                notice = null,
            )
        )
        return if (Acceptance.HELD_LOWER in verdicts.values) ApplyOutcome.HeldLower else ApplyOutcome.Applied
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
        if (state.bootApplyDue) {
            commit(state.copy(bootApplyDue = false))
            when (applyLocked(state.active, state.custom)) {
                ApplyOutcome.Applied, ApplyOutcome.HeldLower, ApplyOutcome.NothingToChange, ApplyOutcome.Unconfirmed -> Unit
                is ApplyOutcome.NotSupported, ApplyOutcome.NoRoot ->
                    commit(state.copy(active = CpuProfile.STOCK, notice = CpuProfileNotice.RESET_BY_RESTART))
            }
            return
        }
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
        // Not fighting Android for ever: everything held goes back, and the page says why.
        val restore = state.held.keys.mapNotNull { id -> state.originals[id]?.let { id to it } }.toMap()
        if (restore.isNotEmpty()) writer.write(restore)
        commit(
            state.copy(
                active = CpuProfile.STOCK,
                held = emptyMap(),
                reapplyFailures = 0,
                notice = CpuProfileNotice.ANDROID_KEEPS_CHANGING,
            )
        )
    }

    /** An apply whose answer never came: done if the kernel shows it, undone otherwise. */
    private fun Session.reconcilePending() {
        val pending = state.pending ?: return
        val snapshot = source.read() ?: return
        val effective = snapshot.policies.mapNotNull { p -> p.scalingMaxKHz?.let { p.id to it } }.toMap()
        val lowered = snapshot.cpuThrottled || powerSave()
        val took = pending.writes.all { (id, target) ->
            CpuProfileRules.acceptance(target, effective[id], lowered) != Acceptance.REJECTED
        }
        if (took) {
            // A write at a cluster's original was a release, not a limit to hold.
            val caps = pending.writes.filter { (id, value) -> value < (state.originals[id] ?: Long.MAX_VALUE) }
            commit(
                state.copy(
                    active = pending.profile,
                    held = caps,
                    pending = null,
                    overrideCount = 0,
                    reapplyFailures = 0,
                    notice = null,
                )
            )
            return
        }
        val rollback = pending.writes.keys.associateWith { id -> previousLimit(state, id, snapshot) }
        if (writer.write(rollback) == null) return
        commit(state.copy(pending = null))
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
    )
}
