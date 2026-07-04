package com.example.modernwidget.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modernwidget.data.NotificationLog
import com.example.modernwidget.data.NotificationLogEntry
import com.example.modernwidget.data.NotificationStats
import com.example.modernwidget.data.UsageHistory
import com.example.modernwidget.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

data class HistoryDay(
    val day: LocalDate,
    val screenTimeMillis: Long,
    val unlocks: Int,
    val notifications: Int,
    val boots: Int,
    val charges: Int,
)

data class HistoryUiState(
    val isLoading: Boolean = true,
    val days: List<HistoryDay> = emptyList(),
    val logEntries: List<NotificationLogEntry> = emptyList(),
    val listenerEnabled: Boolean = false,
)

/** State for the Historia page: 62-day tallies + the notification log. */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val usageHistory: UsageHistory,
    private val notificationStats: NotificationStats,
    private val notificationLog: NotificationLog,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val state = withContext(dispatcher) {
                val today = LocalDate.now()
                val start = today.minusDays(61)
                val days = usageHistory.dailyTallies(start, today).map { tally ->
                    HistoryDay(
                        day = tally.day,
                        screenTimeMillis = tally.screenTimeMillis,
                        unlocks = tally.unlocks,
                        notifications = notificationStats.totalForDay(tally.day),
                        boots = tally.boots,
                        charges = tally.charges,
                    )
                }
                HistoryUiState(
                    isLoading = false,
                    days = days,
                    logEntries = notificationLog.entriesNewestFirst(),
                    listenerEnabled = notificationStats.isListenerEnabled(),
                )
            }
            _uiState.value = state
        }
    }
}
