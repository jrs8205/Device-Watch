package com.example.modernwidget.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modernwidget.data.AppSettingsRepository
import com.example.modernwidget.data.DataCounterMode
import com.example.modernwidget.data.DeviceInfo
import com.example.modernwidget.data.SystemStats
import com.example.modernwidget.data.SystemStatsRepository
import com.example.modernwidget.widget.WidgetController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

const val DEFAULT_WIDGET_OPACITY = 0.86f

data class DashboardUiState(
    val stats: SystemStats? = null,
    val deviceInfo: DeviceInfo? = null,
    val isWidgetInstalled: Boolean = false,
    val lastUpdated: String = "--:--",
    val widgetOpacity: Float = DEFAULT_WIDGET_OPACITY,
    val dataCounterMode: DataCounterMode = DataCounterMode.DAY,
    val cycleStartDay: Int = 1,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: SystemStatsRepository,
    private val widgetController: WidgetController,
    private val settings: AppSettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /** Reads fresh stats, pushes them to every installed widget, and updates the screen. */
    fun refresh() {
        viewModelScope.launch {
            val stats = repository.getStats()
            val widgetInstalled = widgetController.pushStats(stats)
            _uiState.update {
                it.copy(
                    stats = stats,
                    isWidgetInstalled = widgetInstalled,
                    lastUpdated = currentTime(),
                )
            }
        }
    }

    /** Loads the static, root-free device facts once (build, SoC, display, memory). */
    fun loadDeviceInfo() {
        viewModelScope.launch {
            val info = repository.getDeviceInfo()
            _uiState.update { it.copy(deviceInfo = info) }
        }
    }

    /** Loads the saved widget opacity from the first installed widget, if any. */
    fun loadWidgetOpacity() {
        viewModelScope.launch {
            widgetController.currentOpacity()?.let { saved ->
                _uiState.update { it.copy(widgetOpacity = saved) }
            }
        }
    }

    /** Live-updates the slider value without persisting (called on every drag tick). */
    fun onWidgetOpacityChange(value: Float) {
        _uiState.update { it.copy(widgetOpacity = value) }
    }

    /** Persists the current opacity to every installed widget and re-renders them. */
    fun commitWidgetOpacity() {
        val opacity = _uiState.value.widgetOpacity
        viewModelScope.launch {
            widgetController.setOpacity(opacity)
        }
    }

    /** Loads the saved data-counter mode and billing-cycle start day into the state. */
    fun loadDataCounterSettings() {
        _uiState.update {
            it.copy(
                dataCounterMode = settings.dataCounterMode(),
                cycleStartDay = settings.cycleStartDay(),
            )
        }
    }

    /** Persists the counter mode and re-queries stats so the widget shows the new period. */
    fun onDataCounterModeSelected(mode: DataCounterMode) {
        settings.setDataCounterMode(mode)
        _uiState.update { it.copy(dataCounterMode = mode) }
        refresh()
    }

    /** Live-updates the cycle-day slider value without persisting (called on every drag tick). */
    fun onCycleStartDayChange(day: Int) {
        _uiState.update { it.copy(cycleStartDay = day.coerceIn(1, 31)) }
    }

    /** Persists the dragged cycle start day and re-queries stats for the new period. */
    fun commitCycleStartDay() {
        settings.setCycleStartDay(_uiState.value.cycleStartDay)
        refresh()
    }

    private fun currentTime(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
}
