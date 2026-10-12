package org.jarsi.devicewatch.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jarsi.devicewatch.data.ApplyOutcome
import org.jarsi.devicewatch.data.CpuPolicyId
import org.jarsi.devicewatch.data.CpuProfile
import org.jarsi.devicewatch.data.CpuProfilePlanner
import org.jarsi.devicewatch.data.CpuProfileStatus
import org.jarsi.devicewatch.data.CpuProfiles
import org.jarsi.devicewatch.data.KernelSnapshot
import org.jarsi.devicewatch.data.KernelSnapshotSource
import org.jarsi.devicewatch.di.IoDispatcher
import javax.inject.Inject

data class KernelPageState(
    /** The last good read; null until one arrives. */
    val snapshot: KernelSnapshot? = null,
    /** The latest read got no answer from root. */
    val readFailed: Boolean = false,
    val status: CpuProfileStatus = CpuProfileStatus(),
    val editingCustom: Boolean = false,
    /** Custom's slider values, by cluster, in kHz. */
    val draft: Map<CpuPolicyId, Long> = emptyMap(),
    val busy: Boolean = false,
    val lastOutcome: ApplyOutcome? = null,
)

/**
 * The Kernel page and the Device tab's KERNEL band. Both read from one poll,
 * which runs only while something on screen watches it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class KernelViewModel @Inject constructor(
    private val source: KernelSnapshotSource,
    private val profiles: CpuProfiles,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    private data class Reading(val snapshot: KernelSnapshot? = null, val failed: Boolean = false)

    private data class Local(
        val editingCustom: Boolean = false,
        val draft: Map<CpuPolicyId, Long> = emptyMap(),
        val busy: Boolean = false,
        val lastOutcome: ApplyOutcome? = null,
    )

    private val refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @Volatile
    private var lastGood: KernelSnapshot? = null

    private val reading: StateFlow<Reading> = refresh
        .onStart { emit(Unit) }
        .flatMapLatest {
            flow {
                while (true) {
                    val read = source.read()
                    if (read != null) lastGood = read
                    emit(Reading(lastGood, failed = read == null))
                    delay(POLL_MILLIS)
                }
            }
        }
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), Reading())

    private val local = MutableStateFlow(Local())

    val page: StateFlow<KernelPageState> = combine(reading, profiles.status, local) { reading, status, local ->
        KernelPageState(
            snapshot = reading.snapshot,
            readFailed = reading.failed,
            status = status,
            editingCustom = local.editingCustom,
            draft = local.draft,
            busy = local.busy,
            lastOutcome = local.lastOutcome,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), KernelPageState())

    val summary: StateFlow<KernelSummary?> = combine(reading, profiles.status) { reading, status ->
        reading.snapshot?.let { KernelSummary.from(it, status) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** A preset applies at once; Custom opens its sliders first. */
    fun select(profile: CpuProfile) {
        if (profile == CpuProfile.CUSTOM) {
            local.update { it.copy(editingCustom = true, draft = initialDraft(), lastOutcome = null) }
            return
        }
        local.update { it.copy(editingCustom = false) }
        run { profiles.apply(profile) }
    }

    fun onCustomStep(id: CpuPolicyId, kHz: Long) {
        local.update { it.copy(draft = it.draft + (id to kHz)) }
    }

    fun applyCustom() {
        val draft = local.value.draft
        run { profiles.apply(CpuProfile.CUSTOM, draft) }
    }

    fun cancelCustom() {
        local.update { it.copy(editingCustom = false) }
    }

    fun setApplyAfterRestart(enabled: Boolean) {
        viewModelScope.launch { profiles.setApplyAfterRestart(enabled) }
    }

    private fun run(action: suspend () -> ApplyOutcome) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, lastOutcome = null) }
        viewModelScope.launch {
            val outcome = action()
            val done = outcome == ApplyOutcome.Applied || outcome == ApplyOutcome.HeldLower ||
                outcome == ApplyOutcome.NothingToChange
            local.update {
                it.copy(busy = false, lastOutcome = outcome, editingCustom = it.editingCustom && !done)
            }
            // The new limits show at once, not at the next poll.
            refresh.tryEmit(Unit)
        }
    }

    /** Each cluster Custom can limit, at the user's last Custom choice, the held limit, or no limit. */
    private fun initialDraft(): Map<CpuPolicyId, Long> {
        val snapshot = reading.value.snapshot ?: return emptyMap()
        val status = profiles.status.value
        return snapshot.policies.filter { it.online }.mapNotNull { policy ->
            val original = status.originals[policy.id]
            val steps = CpuProfilePlanner.customSteps(policy, original)
            if (steps.isEmpty()) return@mapNotNull null
            val wanted = status.custom[policy.id] ?: status.held[policy.id] ?: steps.last()
            CpuProfilePlanner.snapCustom(policy, wanted, original)?.let { policy.id to it }
        }.toMap()
    }

    companion object {
        const val POLL_MILLIS = 3_000L
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
