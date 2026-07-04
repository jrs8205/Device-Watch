package com.example.modernwidget.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modernwidget.data.AppDataUsage
import com.example.modernwidget.data.AppScreenTime
import com.example.modernwidget.data.AppSettingsRepository
import com.example.modernwidget.data.AppUsageDetail
import com.example.modernwidget.data.AppUsageRepository
import com.example.modernwidget.data.DonutSegment
import com.example.modernwidget.data.LaunchableApp
import com.example.modernwidget.data.NotificationStats
import com.example.modernwidget.data.UNAVAILABLE_INT
import com.example.modernwidget.data.UsageEventAggregator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class AppsUiState(
    val isLoading: Boolean = false,
    val hasUsageAccess: Boolean = true,
    val screenTimes: List<AppScreenTime> = emptyList(),
    val screenTimeSegments: List<DonutSegment> = emptyList(),
    val totalScreenTimeMillis: Long = 0L,
    val dataConsumers: List<AppDataUsage> = emptyList(),
    val apps: List<LaunchableApp> = emptyList(),
    val oldestFirst: Boolean = true,
    val notificationAccessEnabled: Boolean = false,
    val selectedDetail: AppUsageDetail? = null,
)

/**
 * State holder for the Apps tab. All queries are on-demand: the tab refreshes on
 * every resume (first open, returning from Settings or an uninstall dialog) —
 * never from the widget's background service loop.
 */
@HiltViewModel
class AppsViewModel @Inject constructor(
    private val appUsageRepository: AppUsageRepository,
    private val settings: AppSettingsRepository,
    private val notificationStats: NotificationStats,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()

    /** Reloads everything the tab shows. Called from the UI on every resume. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            if (!appUsageRepository.hasUsageAccess()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasUsageAccess = false,
                        screenTimes = emptyList(),
                        screenTimeSegments = emptyList(),
                        totalScreenTimeMillis = 0L,
                        dataConsumers = emptyList(),
                        apps = emptyList(),
                    )
                }
                return@launch
            }

            val oldestFirst = settings.appsOldestFirst()
            val launchers = appUsageRepository.launcherPackages()
            val screenTimes = UsageEventAggregator.excludeLaunchers(
                appUsageRepository.screenTimeToday(), launchers
            )
            val dataConsumers = appUsageRepository.dataConsumersToday()
            val apps = UsageEventAggregator.sortByLastUse(
                appUsageRepository.launchableAppsByLastUse(), oldestFirst
            )
            _uiState.update {
                it.copy(
                    isLoading = false,
                    hasUsageAccess = true,
                    screenTimes = screenTimes,
                    screenTimeSegments = UsageEventAggregator.donutSegments(screenTimes),
                    totalScreenTimeMillis = screenTimes.sumOf { time -> time.foregroundMillis },
                    dataConsumers = dataConsumers,
                    apps = apps,
                    oldestFirst = oldestFirst,
                    notificationAccessEnabled = notificationStats.isListenerEnabled(),
                )
            }
        }
    }

    /** Assembles the detail sheet for [packageName] from the already-loaded lists. */
    fun onAppSelected(packageName: String) {
        val state = _uiState.value
        val screenTime = state.screenTimes.firstOrNull { it.packageName == packageName }
        val app = state.apps.firstOrNull { it.packageName == packageName }
        val label = screenTime?.label
            ?: app?.label
            ?: state.dataConsumers.firstOrNull { it.packageName == packageName }?.label
            ?: packageName
        val notifications = if (state.notificationAccessEnabled) {
            notificationStats.countForPackage(packageName, LocalDate.now())
        } else {
            UNAVAILABLE_INT
        }
        val detail = AppUsageDetail(
            packageName = packageName,
            label = label,
            foregroundMillisToday = screenTime?.foregroundMillis ?: 0L,
            lastOpenedEpochMillis = screenTime?.lastUsedMillis ?: app?.lastUsedEpochMillis,
            launchCountToday = screenTime?.launchCount ?: 0,
            dataBytesToday = state.dataConsumers
                .filter { it.packageName == packageName }
                .sumOf { it.bytes },
            notificationsToday = notifications,
        )
        _uiState.update { it.copy(selectedDetail = detail) }
    }

    fun onDetailDismiss() {
        _uiState.update { it.copy(selectedDetail = null) }
    }

    /** Flips the last-opened sort order and persists the choice. */
    fun onSortToggle() {
        val newOldestFirst = !_uiState.value.oldestFirst
        settings.setAppsOldestFirst(newOldestFirst)
        _uiState.update {
            it.copy(
                oldestFirst = newOldestFirst,
                apps = UsageEventAggregator.sortByLastUse(it.apps, newOldestFirst),
            )
        }
    }
}
