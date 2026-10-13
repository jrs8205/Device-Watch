package org.jarsi.devicewatch.presentation

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jarsi.devicewatch.data.CpuPolicyId
import org.jarsi.devicewatch.data.CpuProfileStatus
import org.jarsi.devicewatch.data.KernelFixtures
import org.jarsi.devicewatch.data.KernelSnapshot
import org.jarsi.devicewatch.data.KernelSnapshotSource
import org.jarsi.devicewatch.data.KernelSysfsParser
import org.jarsi.devicewatch.data.WakeFixtures
import org.jarsi.devicewatch.data.WakeSnapshot
import org.junit.After
import org.junit.Before
import org.junit.Test

/** The sleep section's poll: the page's alone, slower than the kernel poll, and keeping its last good read. */
@OptIn(ExperimentalCoroutinesApi::class)
class KernelViewModelWakeTest {

    private class FakeSource : KernelSnapshotSource {
        var reads = 0
        var wakeReads = 0
        var wake: WakeSnapshot? = KernelSysfsParser.parseWake(WakeFixtures.PIXEL_8A_WAKE)
        override fun read(): KernelSnapshot? {
            reads++
            return KernelSysfsParser.parse(KernelFixtures.PIXEL_8A_FULL)
        }
        override fun readCpu(): KernelSnapshot? = read()
        override fun readMaxFreqs(ids: Collection<CpuPolicyId>): Map<CpuPolicyId, Long>? = null
        override fun readWake(): WakeSnapshot? {
            wakeReads++
            return wake
        }
    }

    private val dispatcher: TestDispatcher = StandardTestDispatcher()
    private val source = FakeSource()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = KernelViewModel(source, FakeCpuProfiles(CpuProfileStatus()), dispatcher)

    @Test
    fun `the page reads the wakeup sources, at its own slower pace`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val watching = backgroundScope.launch { viewModel.page.collect {} }
        runCurrent()
        assertThat(source.wakeReads).isEqualTo(1)
        assertThat(viewModel.page.value.wake!!.sources).hasSize(22)
        assertThat(viewModel.page.value.wakeReadFailed).isFalse()
        advanceTimeBy(KernelViewModel.WAKE_POLL_MILLIS * 2 + 1)
        assertThat(source.wakeReads).isEqualTo(3)
        // Meanwhile the kernel poll ran at its own three-second pace.
        assertThat(source.reads).isGreaterThan(3)

        watching.cancel()
        advanceTimeBy(60_000)
        val afterStop = source.wakeReads
        advanceTimeBy(60_000)
        assertThat(source.wakeReads).isEqualTo(afterStop)
    }

    @Test
    fun `the band alone never reads the wakeup sources`() = runTest(dispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.band.collect {} }
        runCurrent()
        advanceTimeBy(KernelViewModel.WAKE_POLL_MILLIS * 3)
        assertThat(source.reads).isGreaterThan(0)
        assertThat(source.wakeReads).isEqualTo(0)
    }

    @Test
    fun `a wake read that gets nothing keeps the last good one and says so`() = runTest(dispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.page.collect {} }
        runCurrent()
        val first = viewModel.page.value.wake
        source.wake = null
        advanceTimeBy(KernelViewModel.WAKE_POLL_MILLIS + 1)
        assertThat(viewModel.page.value.wake).isSameInstanceAs(first)
        assertThat(viewModel.page.value.wakeReadFailed).isTrue()
        source.wake = first
        advanceTimeBy(KernelViewModel.WAKE_POLL_MILLIS)
        assertThat(viewModel.page.value.wakeReadFailed).isFalse()
    }
}
