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
import org.jarsi.devicewatch.data.PrivacyLog
import org.jarsi.devicewatch.data.PrivacyLogRead
import org.jarsi.devicewatch.data.PrivacyLogSource
import org.jarsi.devicewatch.data.PrivacySensor
import org.jarsi.devicewatch.data.SensorUse
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyViewModelTest {

    private class FakeSource(var answer: PrivacyLogRead?) : PrivacyLogSource {
        var reads = 0
        override fun read(): PrivacyLogRead? {
            reads++
            return answer
        }
    }

    private val emptyLog = PrivacyLog(null, null, PrivacySensor.entries.map { SensorUse(it, emptyList(), emptyList()) })
    private val read = PrivacyLogRead(log = emptyLog, unsupported = false)
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
    private val source = FakeSource(read)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = PrivacyViewModel(source, dispatcher)

    @Test
    fun `the record is read only while something watches, once a minute`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val watching = backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertThat(source.reads).isEqualTo(1)
        assertThat(viewModel.state.value.read).isSameInstanceAs(read)
        assertThat(viewModel.state.value.readFailed).isFalse()
        advanceTimeBy(PrivacyViewModel.POLL_MILLIS * 2 + 1)
        assertThat(source.reads).isEqualTo(3)

        watching.cancel()
        advanceTimeBy(60_000)
        val afterStop = source.reads
        advanceTimeBy(5 * 60_000)
        assertThat(source.reads).isEqualTo(afterStop)
    }

    @Test
    fun `a read that gets nothing keeps the last good one and says so`() = runTest(dispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        source.answer = null
        advanceTimeBy(PrivacyViewModel.POLL_MILLIS + 1)
        assertThat(viewModel.state.value.read).isSameInstanceAs(read)
        assertThat(viewModel.state.value.readFailed).isTrue()
        source.answer = read
        advanceTimeBy(PrivacyViewModel.POLL_MILLIS)
        assertThat(viewModel.state.value.readFailed).isFalse()
    }

    @Test
    fun `before any answer there is nothing, and a failure says failed`() = runTest(dispatcher) {
        source.answer = null
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertThat(viewModel.state.value.read).isNull()
        assertThat(viewModel.state.value.readFailed).isTrue()
    }

    @Test
    fun `refresh reads again at once`() = runTest(dispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertThat(source.reads).isEqualTo(1)
        advanceTimeBy(10_000)
        viewModel.refresh()
        runCurrent()
        assertThat(source.reads).isEqualTo(2)
        // The poll starts over from the refresh, not from the earlier tick.
        advanceTimeBy(PrivacyViewModel.POLL_MILLIS - 10_000)
        assertThat(source.reads).isEqualTo(2)
        advanceTimeBy(10_001)
        assertThat(source.reads).isEqualTo(3)
    }
}
