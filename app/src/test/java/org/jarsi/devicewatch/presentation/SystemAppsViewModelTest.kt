package org.jarsi.devicewatch.presentation

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jarsi.devicewatch.data.SystemApp
import org.jarsi.devicewatch.data.SystemAppActionResult
import org.jarsi.devicewatch.data.SystemAppRisk
import org.jarsi.devicewatch.data.SystemAppRole
import org.jarsi.devicewatch.data.SystemAppVerdict
import org.jarsi.devicewatch.data.SystemAppsRepository
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SystemAppsViewModelTest {

    private class FakeRepository(apps: List<SystemApp>) : SystemAppsRepository {
        val apps = apps.toMutableList()
        val removed = mutableListOf<String>()
        val restored = mutableListOf<String>()
        var reads = 0
        var result: SystemAppActionResult = SystemAppActionResult.Done
        /** Holds the next removal until completed, so a test can act while one runs. */
        var removeGate: CompletableDeferred<Unit>? = null

        override suspend fun systemApps(): List<SystemApp> {
            reads++
            return apps.toList()
        }

        override suspend fun remove(packageName: String): SystemAppActionResult {
            removed += packageName
            removeGate?.await()
            if (result == SystemAppActionResult.Done) replace(packageName, installed = false)
            return result
        }

        override suspend fun restore(packageName: String): SystemAppActionResult {
            restored += packageName
            if (result == SystemAppActionResult.Done) replace(packageName, installed = true)
            return result
        }

        private fun replace(packageName: String, installed: Boolean) {
            val index = apps.indexOfFirst { it.packageName == packageName }
            if (index >= 0) apps[index] = apps[index].copy(installed = installed)
        }
    }

    private val plain = SystemAppVerdict(SystemAppRisk.NORMAL, null)
    private val youtube = SystemApp("com.google.android.youtube", "YouTube", installed = true, updated = true, verdict = plain)
    private val facebook = SystemApp("com.facebook.services", "Facebook Services", installed = true, updated = false, verdict = plain)
    private val gms = SystemApp("com.google.android.gms", "Google Play services", installed = true, updated = true, verdict = SystemAppVerdict(SystemAppRisk.WARNING, SystemAppRole.PLAY_SERVICES))
    private val systemUi = SystemApp("com.android.systemui", "System UI", installed = true, updated = false, verdict = SystemAppVerdict(SystemAppRisk.BLOCKED, SystemAppRole.CORE, aospComponent = true))
    private val gone = SystemApp("com.example.bloat", "Bloat", installed = false, updated = false, verdict = plain)

    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun repository() = FakeRepository(listOf(youtube, systemUi, gone, gms, facebook))

    @Test
    fun `a load lists the installed and the removed apart, by name`() = runTest(dispatcher) {
        val repository = repository()
        val viewModel = SystemAppsViewModel(repository)
        assertThat(viewModel.uiState.value.loading).isTrue()

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.loading).isFalse()
        assertThat(state.apps.map { it.label }).containsExactly("Facebook Services", "Google Play services", "System UI", "YouTube").inOrder()
        assertThat(state.removed.map { it.label }).containsExactly("Bloat")
        assertThat(state.visibleApps).isEqualTo(state.apps)
        assertThat(state.visibleRemoved).isEqualTo(state.removed)
    }

    @Test
    fun `the search narrows both lists`() = runTest(dispatcher) {
        val viewModel = SystemAppsViewModel(repository())
        viewModel.load()
        advanceUntilIdle()

        viewModel.onQueryChange("goo")
        val state = viewModel.uiState.value
        assertThat(state.query).isEqualTo("goo")
        assertThat(state.visibleApps.map { it.packageName }).containsExactly("com.google.android.gms", "com.google.android.youtube")
        assertThat(state.visibleRemoved).isEmpty()
    }

    @Test
    fun `a removal is asked about first, then done, and the lists read again`() = runTest(dispatcher) {
        val repository = repository()
        val viewModel = SystemAppsViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.onRemoveRequested("com.google.android.youtube")
        assertThat(viewModel.uiState.value.pending).isEqualTo(youtube)
        assertThat(repository.removed).isEmpty()

        viewModel.onConfirmRemove()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(repository.removed).containsExactly("com.google.android.youtube")
        assertThat(state.pending).isNull()
        assertThat(state.busyPackage).isNull()
        assertThat(state.lastAction).isEqualTo(SystemAppAction(youtube, restore = false, result = SystemAppActionResult.Done))
        assertThat(state.apps.map { it.packageName }).doesNotContain("com.google.android.youtube")
        assertThat(state.removed.map { it.packageName }).containsExactly("com.example.bloat", "com.google.android.youtube")
        assertThat(repository.reads).isEqualTo(2)
    }

    @Test
    fun `cancelling the question removes nothing`() = runTest(dispatcher) {
        val repository = repository()
        val viewModel = SystemAppsViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.onRemoveRequested("com.google.android.youtube")
        viewModel.onDismissRemove()
        viewModel.onConfirmRemove()
        advanceUntilIdle()

        assertThat(repository.removed).isEmpty()
        assertThat(viewModel.uiState.value.pending).isNull()
    }

    @Test
    fun `a blocked app is never asked about`() = runTest(dispatcher) {
        val repository = repository()
        val viewModel = SystemAppsViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.onRemoveRequested("com.android.systemui")
        viewModel.onConfirmRemove()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.pending).isNull()
        assertThat(repository.removed).isEmpty()
    }

    @Test
    fun `a warned-about app goes only once the warning is acknowledged`() = runTest(dispatcher) {
        val repository = repository()
        val viewModel = SystemAppsViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.onRemoveRequested("com.google.android.gms")
        assertThat(viewModel.uiState.value.pending).isEqualTo(gms)
        assertThat(viewModel.uiState.value.acknowledged).isFalse()
        assertThat(viewModel.uiState.value.canConfirm).isFalse()

        // Confirmed without the acknowledgement: nothing happens, the question stays up.
        viewModel.onConfirmRemove()
        advanceUntilIdle()
        assertThat(repository.removed).isEmpty()
        assertThat(viewModel.uiState.value.pending).isEqualTo(gms)

        viewModel.onAcknowledgeChange(true)
        assertThat(viewModel.uiState.value.canConfirm).isTrue()
        viewModel.onConfirmRemove()
        advanceUntilIdle()
        assertThat(repository.removed).containsExactly("com.google.android.gms")

        // The next question starts unacknowledged.
        viewModel.onRemoveRequested("com.facebook.services")
        assertThat(viewModel.uiState.value.acknowledged).isFalse()
        assertThat(viewModel.uiState.value.canConfirm).isTrue()
    }

    @Test
    fun `a plain app needs no acknowledgement`() = runTest(dispatcher) {
        val viewModel = SystemAppsViewModel(repository())
        viewModel.load()
        advanceUntilIdle()

        viewModel.onRemoveRequested("com.facebook.services")
        assertThat(viewModel.uiState.value.canConfirm).isTrue()
    }

    @Test
    fun `a failed removal keeps the package manager's words, and the lists are read again`() = runTest(dispatcher) {
        val repository = repository().apply { result = SystemAppActionResult.Failed("Failure [DELETE_FAILED_INTERNAL_ERROR]") }
        val viewModel = SystemAppsViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.onRemoveRequested("com.google.android.youtube")
        viewModel.onConfirmRemove()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.lastAction?.result).isEqualTo(SystemAppActionResult.Failed("Failure [DELETE_FAILED_INTERNAL_ERROR]"))
        assertThat(state.apps.map { it.packageName }).contains("com.google.android.youtube")
        assertThat(repository.reads).isEqualTo(2)
    }

    @Test
    fun `a restore needs no question and reads the lists again`() = runTest(dispatcher) {
        val repository = repository()
        val viewModel = SystemAppsViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.onRestore("com.example.bloat")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(repository.restored).containsExactly("com.example.bloat")
        assertThat(state.removed).isEmpty()
        assertThat(state.apps.map { it.packageName }).contains("com.example.bloat")
        assertThat(state.lastAction).isEqualTo(SystemAppAction(gone, restore = true, result = SystemAppActionResult.Done))
    }

    @Test
    fun `one action at a time, and a notice can be put away`() = runTest(dispatcher) {
        val repository = repository().apply { removeGate = CompletableDeferred() }
        val viewModel = SystemAppsViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.onRemoveRequested("com.google.android.youtube")
        viewModel.onConfirmRemove()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.busyPackage).isEqualTo("com.google.android.youtube")

        // A second request while the first runs is ignored.
        viewModel.onRemoveRequested("com.facebook.services")
        assertThat(viewModel.uiState.value.pending).isNull()
        viewModel.onRestore("com.example.bloat")
        advanceUntilIdle()
        assertThat(repository.restored).isEmpty()

        repository.removeGate!!.complete(Unit)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.busyPackage).isNull()
        assertThat(viewModel.uiState.value.lastAction).isNotNull()

        viewModel.onDismissAction()
        assertThat(viewModel.uiState.value.lastAction).isNull()
    }
}
