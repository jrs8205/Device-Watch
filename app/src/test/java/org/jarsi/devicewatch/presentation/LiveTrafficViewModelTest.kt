package org.jarsi.devicewatch.presentation

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jarsi.devicewatch.data.TrafficCounterSource
import org.jarsi.devicewatch.data.TrafficCounters
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTrafficViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** 125 000 bytes more on every read: one megabit per second at one read a second. */
    private class SteadySource(private val scope: TestScope) : TrafficCounterSource {
        var reads = 0
        override fun read(): TrafficCounters {
            reads++
            return TrafficCounters(scope.testScheduler.currentTime, rxBytes = reads * 125_000L, txBytes = 0L)
        }
    }

    @Test
    fun `while watched, the meter adds a rate every second`() = runTest(dispatcher) {
        val source = SteadySource(this)
        val viewModel = LiveTrafficViewModel(source, dispatcher)
        backgroundScope.launch { viewModel.traffic.collect {} }

        runCurrent()
        advanceTimeBy(3_000L)
        runCurrent()

        val traffic = viewModel.traffic.value!!
        assertThat(traffic.history).hasSize(3)
        assertThat(traffic.history.map { it.downBytesPerSecond }).containsExactly(125_000L, 125_000L, 125_000L)
    }

    @Test
    fun `nobody watching means no reading at all`() = runTest(dispatcher) {
        val source = SteadySource(this)
        LiveTrafficViewModel(source, dispatcher)

        advanceTimeBy(10_000L)
        runCurrent()

        assertThat(source.reads).isEqualTo(0)
    }

    @Test
    fun `a device without counters has no meter`() = runTest(dispatcher) {
        val viewModel = LiveTrafficViewModel({ null }, dispatcher)
        backgroundScope.launch { viewModel.traffic.collect {} }

        runCurrent()

        assertThat(viewModel.traffic.value).isNull()
    }
}
