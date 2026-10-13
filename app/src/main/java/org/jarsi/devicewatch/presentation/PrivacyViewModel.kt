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
import org.jarsi.devicewatch.data.PrivacyLogRead
import org.jarsi.devicewatch.data.PrivacyLogSource
import org.jarsi.devicewatch.di.IoDispatcher
import javax.inject.Inject

/**
 * The privacy log's band and page. One poll, running only while something on
 * screen watches it; the source behind it answers from its last dump for half a
 * minute, so opening the page right after the band costs the system nothing.
 */
@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val source: PrivacyLogSource,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    @Volatile
    private var lastGood: PrivacyLogRead? = null

    val state: StateFlow<PrivacyState> = flow {
        while (true) {
            val read = source.read()
            if (read != null) lastGood = read
            emit(PrivacyState(read = lastGood, readFailed = read == null))
            delay(POLL_MILLIS)
        }
    }
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PrivacyState())

    companion object {
        const val POLL_MILLIS = 60_000L
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
