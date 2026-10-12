package org.jarsi.devicewatch.presentation

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jarsi.devicewatch.data.ApplyOutcome
import org.jarsi.devicewatch.data.CpuPolicyId
import org.jarsi.devicewatch.data.CpuProfile
import org.jarsi.devicewatch.data.CpuProfileStatus
import org.jarsi.devicewatch.data.KernelFixtures
import org.jarsi.devicewatch.data.KernelSnapshot
import org.jarsi.devicewatch.data.KernelSnapshotSource
import org.jarsi.devicewatch.data.KernelSysfsParser
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KernelViewModelTest {

    private class FakeSource(var snapshot: KernelSnapshot? = KernelSysfsParser.parse(KernelFixtures.PIXEL_8A_FULL)) : KernelSnapshotSource {
        var reads = 0
        override fun read(): KernelSnapshot? {
            reads++
            return snapshot
        }
        override fun readMaxFreqs(ids: Collection<CpuPolicyId>): Map<CpuPolicyId, Long>? = null
    }

    private val dispatcher: TestDispatcher = StandardTestDispatcher()
    private val source = FakeSource()
    private val profiles = FakeCpuProfiles(CpuProfileStatus(originals = mapOf(id("policy8") to 2_914_000L)))

    private fun id(name: String) = CpuPolicyId.of(name)!!

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = KernelViewModel(source, profiles, dispatcher)

    @Test
    fun `the kernel is read only while the page is watched`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val watching = backgroundScope.launch { viewModel.page.collect {} }
        runCurrent()
        assertThat(source.reads).isEqualTo(1)
        advanceTimeBy(KernelViewModel.POLL_MILLIS * 2 + 1)
        assertThat(source.reads).isEqualTo(3)
        assertThat(viewModel.page.value.snapshot).isNotNull()

        watching.cancel()
        advanceTimeBy(60_000)
        val afterStop = source.reads
        advanceTimeBy(60_000)
        assertThat(source.reads).isEqualTo(afterStop)
    }

    @Test
    fun `a failed read keeps the last snapshot and says so`() = runTest(dispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.page.collect {} }
        runCurrent()
        source.snapshot = null
        advanceTimeBy(KernelViewModel.POLL_MILLIS + 1)
        assertThat(viewModel.page.value.snapshot).isNotNull()
        assertThat(viewModel.page.value.readFailed).isTrue()
    }

    @Test
    fun `nothing read yet is loading, not failed`() = runTest(dispatcher) {
        val viewModel = viewModel()
        assertThat(viewModel.page.value.snapshot).isNull()
        assertThat(viewModel.page.value.readFailed).isFalse()
    }

    @Test
    fun `a preset applies at once and the page reads again right after`() = runTest(dispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.page.collect {} }
        runCurrent()
        val reads = source.reads

        viewModel.select(CpuProfile.BALANCED)
        runCurrent()

        assertThat(profiles.calls).containsExactly("apply BALANCED")
        assertThat(viewModel.page.value.lastOutcome).isEqualTo(ApplyOutcome.Applied)
        assertThat(viewModel.page.value.busy).isFalse()
        assertThat(source.reads).isGreaterThan(reads)
    }

    @Test
    fun `custom opens the sliders at each cluster's top step without applying`() = runTest(dispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.page.collect {} }
        runCurrent()

        viewModel.select(CpuProfile.CUSTOM)
        runCurrent()

        val page = viewModel.page.value
        assertThat(page.editingCustom).isTrue()
        assertThat(page.draft).containsExactly(id("policy0"), 1_704_000L, id("policy4"), 2_367_000L, id("policy8"), 2_914_000L)
        assertThat(profiles.calls).isEmpty()
    }

    @Test
    fun `the sliders start from the saved custom choice`() = runTest(dispatcher) {
        profiles.status.value = profiles.status.value.copy(custom = mapOf(id("policy8") to 2_049_000L))
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.page.collect {} }
        runCurrent()

        viewModel.select(CpuProfile.CUSTOM)
        runCurrent()

        assertThat(viewModel.page.value.draft[id("policy8")]).isEqualTo(2_049_000L)
    }

    @Test
    fun `applying custom sends the sliders' values`() = runTest(dispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.page.collect {} }
        runCurrent()
        viewModel.select(CpuProfile.CUSTOM)
        runCurrent()

        viewModel.onCustomStep(id("policy8"), 1_745_000L)
        viewModel.applyCustom()
        runCurrent()

        assertThat(profiles.calls).containsExactly("apply CUSTOM")
        assertThat(profiles.lastCustom[id("policy8")]).isEqualTo(1_745_000L)
        assertThat(viewModel.page.value.editingCustom).isFalse()
    }

    @Test
    fun `the apply-after-restart switch reaches the profile`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.setApplyAfterRestart(true)
        advanceUntilIdle()
        assertThat(profiles.calls).containsExactly("applyAfterRestart true")
    }

    @Test
    fun `the summary follows the snapshot and the profile`() = runTest(dispatcher) {
        profiles.status.value = profiles.status.value.copy(active = CpuProfile.POWER_SAVING)
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.summary.collect {} }
        runCurrent()
        assertThat(viewModel.summary.value?.profile).isEqualTo(CpuProfile.POWER_SAVING)
        assertThat(viewModel.summary.value?.bigMaxKHz).isEqualTo(2_914_000L)
    }
}
