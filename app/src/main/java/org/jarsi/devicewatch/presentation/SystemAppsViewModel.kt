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
) {
    val visibleApps: List<SystemApp> get() = SystemAppRules.matching(apps, query)
    val visibleRemoved: List<SystemApp> get() = SystemAppRules.matching(removed, query)

    /** The question's remove button: a warned-about app waits for the acknowledgement. */
    val canConfirm: Boolean
        get() = pending != null && (pending.verdict.risk != SystemAppRisk.WARNING || acknowledged)
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

    /** Reads the lists; called when the page opens and whenever it comes back on screen. */
    fun load() {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val apps = repository.systemApps().sortedBy { it.label.lowercase() }
        _uiState.update { state ->
            state.copy(loading = false, apps = apps.filter { it.installed }, removed = apps.filterNot { it.installed })
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

    /** The question's remove button: runs the removal the question was about. */
    fun onConfirmRemove() {
        val state = _uiState.value
        val app = state.pending ?: return
        if (!state.canConfirm || state.busyPackage != null) return
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
