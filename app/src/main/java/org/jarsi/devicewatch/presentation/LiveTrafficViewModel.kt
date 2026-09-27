package org.jarsi.devicewatch.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import org.jarsi.devicewatch.data.LiveTraffic
import org.jarsi.devicewatch.data.TrafficCounterSource
import org.jarsi.devicewatch.data.TrafficMeter
import org.jarsi.devicewatch.di.DefaultDispatcher
import javax.inject.Inject

/**
 * The live network meter: the device's download and upload rate once a second.
 * It reads only while the page shows it, and starts a fresh minute each time.
 */
@HiltViewModel
class LiveTrafficViewModel @Inject constructor(
    private val source: TrafficCounterSource,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    /** Null where the device reports no traffic counters. */
    val traffic: StateFlow<LiveTraffic?> = flow {
        var previous = source.read() ?: run {
            emit(null)
            return@flow
        }
        var traffic = LiveTraffic()
        emit(traffic)
        while (true) {
            delay(SAMPLE_MILLIS)
            val current = source.read() ?: continue
            TrafficMeter.rate(previous, current)?.let { rate ->
                traffic = TrafficMeter.next(traffic, rate)
                emit(traffic)
            }
            previous = current
        }
    }
        .flowOn(dispatcher)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L, replayExpirationMillis = 0L),
            LiveTraffic(),
        )

    private companion object {
        const val SAMPLE_MILLIS = 1_000L
    }
}
