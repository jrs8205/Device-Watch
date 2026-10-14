package org.jarsi.devicewatch.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jarsi.devicewatch.data.SystemApp
import org.jarsi.devicewatch.data.SystemAppActionResult
import org.jarsi.devicewatch.data.SystemAppRisk
import org.jarsi.devicewatch.data.SystemAppRules
import org.jarsi.devicewatch.data.SystemAppsRepository
import javax.inject.Inject

/** The last removal or restore and how it went, for the notice at the top of the page. */
data class SystemAppAction(val app: SystemApp, val restore: Boolean, val result: SystemAppActionResult)

data class SystemAppsUiState(
    val loading: Boolean = true,
    /** Installed for this user, by name. */
    val apps: List<SystemApp> = emptyList(),
    /** Removed for this user, by name; each can come back. */
    val removed: List<SystemApp> = emptyList(),
    val query: String = "",
    /** The app the removal question is up for. */
    val pending: SystemApp? = null,
    /** The warning under the question has been read and accepted. */
    val acknowledged: Boolean = false,
    /** The app a removal or restore is running for; one at a time. */
    val busyPackage: String? = null,
    val lastAction: SystemAppAction? = null,
    /** A read of the lists is under way; what it brings may change a verdict. */
    val reloading: Boolean = false,
) {
    val visibleApps: List<SystemApp> get() = SystemAppRules.matching(apps, query)
    val visibleRemoved: List<SystemApp> get() = SystemAppRules.matching(removed, query)

    /**
     * The question's remove button: a warned-about app waits for the acknowledgement,
     * and any app for a read under way, since the verdict it brings is the one that counts.
     */
    val canConfirm: Boolean
        get() = pending != null && !reloading && (pending.verdict.risk != SystemAppRisk.WARNING || acknowledged)
}

/**
 * The system-apps page: the list, the search, and the removal question that every
 * removal goes through. A blocked app is never asked about, a warned-about one
 * only with the warning acknowledged, and one action runs at a time.
 */
@HiltViewModel
class SystemAppsViewModel @Inject constructor(
    private val repository: SystemAppsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SystemAppsUiState())
    val uiState: StateFlow<SystemAppsUiState> = _uiState.asStateFlow()

    /** Counts the reads, so a read overtaken by a newer one (an action's re-read, say) is not shown. */
    private var loadGeneration = 0

    /** How many reads are under way; the question cannot be confirmed while any is. */
    private var readsInFlight = 0

    /** Reads the lists; called when the page opens and whenever it comes back on screen. */
    fun load() {
        viewModelScope.launch { reload() }
    }

    /**
     * Only the latest read reaches the screen: a read started on the return to the
     * page and answered after a removal's own re-read would otherwise put the removed
     * app back among the installed. A question left up follows the app as it stands
     * now: gone, or blocked, and the question is gone; warned about anew, and the
     * acknowledgement is asked again. While a read is under way the question waits
     * for it (`reloading`): the verdict it brings is the one that counts.
     */
    private suspend fun reload() {
        val generation = ++loadGeneration
        readsInFlight++
        _uiState.update { it.copy(reloading = true) }
        try {
            publish(generation, repository.systemApps().sortedBy { it.label.lowercase() })
        } finally {
            readsInFlight--
            if (readsInFlight == 0) _uiState.update { it.copy(reloading = false) }
        }
    }

    private fun publish(generation: Int, apps: List<SystemApp>) {
        if (generation != loadGeneration) return
        _uiState.update { state ->
            val installed = apps.filter { it.installed }
            val pending = state.pending?.let { asked ->
                installed.firstOrNull { it.packageName == asked.packageName }?.takeIf { it.verdict.risk != SystemAppRisk.BLOCKED }
            }
            state.copy(
                loading = false,
                apps = installed,
                removed = apps.filterNot { it.installed },
                pending = pending,
                acknowledged = state.acknowledged && pending != null && pending.verdict == state.pending?.verdict,
            )
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    /** Puts the question up for [packageName], unless it is blocked or something is already running. */
    fun onRemoveRequested(packageName: String) {
        val state = _uiState.value
        if (state.busyPackage != null) return
        val app = state.apps.firstOrNull { it.packageName == packageName } ?: return
        if (app.verdict.risk == SystemAppRisk.BLOCKED) return
        _uiState.update { it.copy(pending = app, acknowledged = false) }
    }

    fun onAcknowledgeChange(acknowledged: Boolean) {
        _uiState.update { it.copy(acknowledged = acknowledged) }
    }

    fun onDismissRemove() {
        _uiState.update { it.copy(pending = null, acknowledged = false) }
    }

    /**
     * The question's remove button: runs the removal the question was about, by the
     * app's verdict as it stands in the list now, never by the copy the question was
     * opened with.
     */
    fun onConfirmRemove() {
        val state = _uiState.value
        val asked = state.pending ?: return
        if (!state.canConfirm || state.busyPackage != null) return
        val app = state.apps.firstOrNull { it.packageName == asked.packageName }
        if (app == null || app.verdict.risk == SystemAppRisk.BLOCKED) {
            onDismissRemove()
            return
        }
        _uiState.update { it.copy(pending = null, acknowledged = false, busyPackage = app.packageName, lastAction = null) }
        viewModelScope.launch {
            val result = repository.remove(app.packageName)
            finish(SystemAppAction(app, restore = false, result = result))
        }
    }

    /** Brings a removed app back; the safe direction, so no question. */
    fun onRestore(packageName: String) {
        val state = _uiState.value
        if (state.busyPackage != null) return
        val app = state.removed.firstOrNull { it.packageName == packageName } ?: return
        _uiState.update { it.copy(busyPackage = app.packageName, lastAction = null) }
        viewModelScope.launch {
            val result = repository.restore(app.packageName)
            finish(SystemAppAction(app, restore = true, result = result))
        }
    }

    /** The lists are read again either way: the package manager's state is the truth, not the answer. */
    private suspend fun finish(action: SystemAppAction) {
        reload()
        _uiState.update { it.copy(busyPackage = null, lastAction = action) }
    }

    fun onDismissAction() {
        _uiState.update { it.copy(lastAction = null) }
    }
}
