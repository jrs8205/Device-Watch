package org.jarsi.devicewatch.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import org.jarsi.devicewatch.data.PrivacyLogRead
import org.jarsi.devicewatch.data.PrivacyLogSource
import org.jarsi.devicewatch.di.IoDispatcher
import javax.inject.Inject

/**
 * The privacy log's band and page. One poll, running only while something on
 * screen watches it; the source behind it answers from its last dump for half a
 * minute, so a quick refresh costs the system nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val source: PrivacyLogSource,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @Volatile
    private var lastGood: PrivacyLogRead? = null

    val state: StateFlow<PrivacyState> = refresh
        .onStart { emit(Unit) }
        .flatMapLatest {
            flow {
                while (true) {
                    val read = source.read()
                    if (read != null) lastGood = read
                    emit(PrivacyState(read = lastGood, readFailed = read == null))
                    delay(POLL_MILLIS)
                }
            }
        }
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PrivacyState())

    /** Reads again now (within the source's own spacing) and starts the poll over. */
    fun refresh() {
        refresh.tryEmit(Unit)
    }

    companion object {
        const val POLL_MILLIS = 60_000L
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
