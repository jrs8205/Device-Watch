package org.jarsi.devicewatch.presentation

import org.jarsi.devicewatch.data.AppSettingsRepository
import org.jarsi.devicewatch.data.AppUsageRepository
import org.jarsi.devicewatch.data.DATA_QUOTA_MAX_GB
import org.jarsi.devicewatch.data.DataCounterMode
import org.jarsi.devicewatch.data.DataUsageSince
import org.jarsi.devicewatch.data.DeviceInfo
import org.jarsi.devicewatch.data.MonthlyDataUsage
import org.jarsi.devicewatch.data.NotificationStats
import org.jarsi.devicewatch.data.SystemStats
import org.jarsi.devicewatch.data.SystemStatsRepository
import org.jarsi.devicewatch.data.DataBreakdown
import org.jarsi.devicewatch.data.TrafficSplit
import org.jarsi.devicewatch.data.StorageBreakdown
import org.jarsi.devicewatch.data.DeviceState
import org.jarsi.devicewatch.data.UNAVAILABLE_INT
import org.jarsi.devicewatch.data.UsageHistory
import org.jarsi.devicewatch.data.UsageTotals
import org.jarsi.devicewatch.widget.WidgetController
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        repository: SystemStatsRepository = FakeSystemStatsRepository(sampleStats()),
        widget: WidgetController = FakeWidgetController(installed = true),
        settings: AppSettingsRepository = FakeAppSettingsRepository(),
        appUsage: AppUsageRepository = FakeAppUsageRepository(),
        notifications: NotificationStats = FakeNotificationStats(),
        history: UsageHistory = FakeUsageHistory(),
        relay: FakeMonitorServiceRelay = FakeMonitorServiceRelay(),
    ) = DashboardViewModel(repository, widget, settings, appUsage, notifications, history, relay)

    @Test
    fun `given fresh stats, when refreshing, then state reflects repository and widget flag`() =
        runTest(dispatcher) {
            // Given
            val stats = sampleStats(batteryLevel = 77)
            val repository = FakeSystemStatsRepository(stats)
            val widget = FakeWidgetController(installed = true)
            val viewModel = buildViewModel(repository = repository, widget = widget)

            // When
            viewModel.refresh()
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertThat(state.stats).isEqualTo(stats)
            assertThat(state.isWidgetInstalled).isTrue()
            assertThat(state.lastUpdated).isNotEqualTo("--:--")
            assertThat(repository.callCount).isEqualTo(1)
            assertThat(widget.pushedStats).containsExactly(stats)
        }

    @Test
    fun `given day mode, when refreshing, then the previous-period comparison is filled`() =
        runTest(dispatcher) {
            // Given
            val today = LocalDate.now()
            val history = FakeUsageHistory().apply {
                recordScreenTime(today.minusDays(1), 90_000L)
                recordUnlocks(today.minusDays(1), 12)
            }
            val notifications = FakeNotificationStats(
                enabled = true,
                totalsByDay = mapOf(today.minusDays(1) to 7, today to 3),
            )
            val viewModel = buildViewModel(history = history, notifications = notifications)

            // When
            viewModel.refresh()
            advanceUntilIdle()

            // Then
            val comparison = viewModel.uiState.value.periodComparison
            assertThat(comparison).isNotNull()
            comparison!!
            assertThat(comparison.daysCompared).isEqualTo(1)
            assertThat(comparison.screenTimePrevMillis).isEqualTo(90_000L)
            assertThat(comparison.unlocksPrev).isEqualTo(12)
            assertThat(comparison.notificationsPrev).isEqualTo(7)
            assertThat(comparison.notificationsNow).isEqualTo(3)
        }

    @Test
    fun `given nothing recorded before the previous window, when refreshing, then the previous values are unknown`() =
        runTest(dispatcher) {
            // Given: the stores only have today, so yesterday was never observed —
            // it must not be reported as a zero to compare against.
            val today = LocalDate.now()
            val history = FakeUsageHistory().apply {
                recordScreenTime(today, 120_000L)
                recordUnlocks(today, 4)
            }
            val notifications = FakeNotificationStats(enabled = true, totalsByDay = mapOf(today to 3))
            val viewModel = buildViewModel(history = history, notifications = notifications)

            // When
            viewModel.refresh()
            advanceUntilIdle()

            // Then
            val comparison = viewModel.uiState.value.periodComparison
            assertThat(comparison).isNotNull()
            comparison!!
            assertThat(comparison.screenTimeNowMillis).isEqualTo(120_000L)
            assertThat(comparison.screenTimePrevMillis).isNull()
            assertThat(comparison.unlocksPrev).isNull()
            assertThat(comparison.notificationsPrev).isNull()
        }

    @Test
    fun `given data older than the previous window, when refreshing, then a quiet previous day is a real zero`() =
        runTest(dispatcher) {
            // Given: recording started three days ago, so yesterday's empty tally
            // is an observed zero rather than a gap.
            val today = LocalDate.now()
            val history = FakeUsageHistory().apply {
                recordScreenTime(today.minusDays(3), 60_000L)
                recordUnlocks(today.minusDays(3), 2)
            }
            val notifications = FakeNotificationStats(
                enabled = true,
                totalsByDay = mapOf(today.minusDays(3) to 5),
            )
            val viewModel = buildViewModel(history = history, notifications = notifications)

            // When
            viewModel.refresh()
            advanceUntilIdle()

            // Then
            val comparison = viewModel.uiState.value.periodComparison
            assertThat(comparison).isNotNull()
            comparison!!
            assertThat(comparison.screenTimePrevMillis).isEqualTo(0L)
            assertThat(comparison.unlocksPrev).isEqualTo(0)
            assertThat(comparison.notificationsPrev).isEqualTo(0)
        }

    @Test
    fun `given a refresh, then the period's data breakdown reaches the screen`() =
        runTest(dispatcher) {
            // Given
            val breakdown = DataBreakdown(
                wifi = TrafficSplit(foregroundPercent = 70, backgroundPercent = 30),
                mobile = null,
                mobileRoamingBytes = 5L,
            )
            val repository = FakeSystemStatsRepository(sampleStats(), breakdown)
            val viewModel = buildViewModel(repository = repository)

            // When
            viewModel.refresh()
            advanceUntilIdle()

            // Then: asked for the current counting period (a day by default).
            assertThat(viewModel.uiState.value.dataBreakdown).isEqualTo(breakdown)
            val startOfToday = java.time.LocalDate.now()
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            assertThat(repository.breakdownStartMillis).isEqualTo(startOfToday)
        }

    @Test
    fun `given a refresh, then the storage breakdown reaches the screen`() =
        runTest(dispatcher) {
            val storage = StorageBreakdown(
                appsBytes = 1L, cacheBytes = 2L, imageBytes = 3L,
                videoBytes = 4L, audioBytes = 5L, otherBytes = 6L,
            )
            val repository = FakeSystemStatsRepository(sampleStats(), storage = storage)
            val viewModel = buildViewModel(repository = repository)

            viewModel.refresh()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.storageBreakdown).isEqualTo(storage)
        }

    @Test
    fun `given a refresh, then the device state reaches the screen`() =
        runTest(dispatcher) {
            val state = DeviceState(powerSaveMode = "Yes", deviceIdle = "No")
            val repository = FakeSystemStatsRepository(sampleStats(), state = state)
            val viewModel = buildViewModel(repository = repository)

            viewModel.refresh()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.deviceState).isEqualTo(state)
        }

    @Test
    fun `given usage access, then screen-on time is backfilled and shown for the period`() =
        runTest(dispatcher) {
            val today = java.time.LocalDate.now()
            val appUsage = FakeAppUsageRepository(
                screenOn = mapOf(today to 5_400_000L, today.minusDays(1) to 3_600_000L),
            )
            val history = FakeUsageHistory()
            val viewModel = buildViewModel(appUsage = appUsage, history = history)

            viewModel.refresh()
            advanceUntilIdle()

            assertThat(history.screenOn[today.minusDays(1)]).isEqualTo(3_600_000L)
            // The counting period is a day by default: only today's figure shows.
            assertThat(viewModel.uiState.value.screenOnMillis).isEqualTo(5_400_000L)
        }

    @Test
    fun `given a committed charge limit, then the monitor is told to apply it at once`() =
        runTest(dispatcher) {
            // Given
            val relay = FakeMonitorServiceRelay()
            val viewModel = buildViewModel(relay = relay)

            // When
            viewModel.onChargeLimitChange(80)
            viewModel.onCommitChargeLimit()
            advanceUntilIdle()

            // Then
            assertThat(relay.chargeLimitChangedCount).isEqualTo(1)
        }

    @Test
    fun `given a committed data quota, then the monitor is told to re-check it at once`() =
        runTest(dispatcher) {
            // Given: without this the usage watch keeps the old quota's threshold
            // until the next screen-on check, which may be hours away.
            val relay = FakeMonitorServiceRelay()
            val viewModel = buildViewModel(relay = relay)

            // When
            viewModel.onDataQuotaChange(20.0)
            viewModel.onCommitDataQuota()
            advanceUntilIdle()

            // Then
            assertThat(relay.dataQuotaChangedCount).isEqualTo(1)
        }

    @Test
    fun `given a new counting mode, then the monitor is told to re-check the quota at once`() =
        runTest(dispatcher) {
            // Given: the mode decides the period the quota counts against, and a
            // period switched with the screen off would otherwise go unchecked.
            val relay = FakeMonitorServiceRelay()
            val viewModel = buildViewModel(relay = relay)

            // When
            viewModel.onDataCounterModeSelected(DataCounterMode.BILLING_CYCLE)
            advanceUntilIdle()

            // Then
            assertThat(relay.dataQuotaChangedCount).isEqualTo(1)
        }

    @Test
    fun `given a new cycle start day, then the monitor is told to re-check the quota at once`() =
        runTest(dispatcher) {
            val relay = FakeMonitorServiceRelay()
            val viewModel = buildViewModel(relay = relay)

            viewModel.onCycleStartDayChange(15)
            viewModel.commitCycleStartDay()
            advanceUntilIdle()

            assertThat(relay.dataQuotaChangedCount).isEqualTo(1)
        }

    @Test
    fun `given the classic look stored, when created, then the first state already carries it`() =
        runTest(dispatcher) {
            // Given: read synchronously in init, so the first frame is themed right
            // and the app never flashes the new look before switching.
            val settings = FakeAppSettingsRepository(classic = true)

            // When
            val viewModel = buildViewModel(settings = settings)

            // Then
            assertThat(viewModel.uiState.value.classicLook).isTrue()
        }

    @Test
    fun `given the look switched, then it is persisted and the state follows`() =
        runTest(dispatcher) {
            // Given
            val settings = FakeAppSettingsRepository()
            val viewModel = buildViewModel(settings = settings)

            // When
            viewModel.onClassicLookChange(true)

            // Then
            assertThat(settings.classic).isTrue()
            assertThat(viewModel.uiState.value.classicLook).isTrue()
        }

    @Test
    fun `given a saved opacity, when loading, then state adopts it`() = runTest(dispatcher) {
        // Given
        val widget = FakeWidgetController(installed = true, savedOpacity = 0.42f)
        val viewModel = buildViewModel(widget = widget)

        // When
        viewModel.loadWidgetOpacity()
        advanceUntilIdle()

        // Then
        assertThat(viewModel.uiState.value.widgetOpacity).isEqualTo(0.42f)
    }

    @Test
    fun `given no widget, when loading opacity, then default is kept`() = runTest(dispatcher) {
        // Given
        val widget = FakeWidgetController(installed = false, savedOpacity = null)
        val viewModel = buildViewModel(widget = widget)

        // When
        viewModel.loadWidgetOpacity()
        advanceUntilIdle()

        // Then
        assertThat(viewModel.uiState.value.widgetOpacity).isEqualTo(DEFAULT_WIDGET_OPACITY)
    }

    @Test
    fun `given a dragged opacity, when committing, then it is persisted`() = runTest(dispatcher) {
        // Given
        val widget = FakeWidgetController(installed = true)
        val viewModel = buildViewModel(widget = widget)

        // When
        viewModel.onWidgetOpacityChange(0.5f)
        viewModel.commitWidgetOpacity()
        advanceUntilIdle()

        // Then
        assertThat(viewModel.uiState.value.widgetOpacity).isEqualTo(0.5f)
        assertThat(widget.committedOpacity).isEqualTo(0.5f)
    }

    @Test
    fun `given saved counter settings, when loading, then state adopts them`() = runTest(dispatcher) {
        // Given
        val settings = FakeAppSettingsRepository(
            mode = DataCounterMode.BILLING_CYCLE,
            cycleDay = 15,
        )
        val viewModel = buildViewModel(settings = settings)

        // When
        viewModel.loadDataCounterSettings()
        advanceUntilIdle()

        // Then
        assertThat(viewModel.uiState.value.dataCounterMode).isEqualTo(DataCounterMode.BILLING_CYCLE)
        assertThat(viewModel.uiState.value.cycleStartDay).isEqualTo(15)
    }

    @Test
    fun `given mode selection, when selected, then persisted and stats repushed to widget`() =
        runTest(dispatcher) {
            // Given
            val settings = FakeAppSettingsRepository()
            val repository = FakeSystemStatsRepository(sampleStats())
            val widget = FakeWidgetController(installed = true)
            val viewModel = buildViewModel(repository = repository, widget = widget, settings = settings)

            // When
            viewModel.onDataCounterModeSelected(DataCounterMode.BILLING_CYCLE)
            advanceUntilIdle()

            // Then
            assertThat(settings.mode).isEqualTo(DataCounterMode.BILLING_CYCLE)
            assertThat(viewModel.uiState.value.dataCounterMode).isEqualTo(DataCounterMode.BILLING_CYCLE)
            assertThat(repository.callCount).isEqualTo(1)
            assertThat(widget.pushedStats).hasSize(1)
        }

    @Test
    fun `given dragged cycle day, when committing, then persisted and stats repushed`() =
        runTest(dispatcher) {
            // Given
            val settings = FakeAppSettingsRepository()
            val repository = FakeSystemStatsRepository(sampleStats())
            val widget = FakeWidgetController(installed = true)
            val viewModel = buildViewModel(repository = repository, widget = widget, settings = settings)

            // When
            viewModel.onCycleStartDayChange(21)
            viewModel.commitCycleStartDay()
            advanceUntilIdle()

            // Then
            assertThat(settings.cycleDay).isEqualTo(21)
            assertThat(viewModel.uiState.value.cycleStartDay).isEqualTo(21)
            assertThat(repository.callCount).isEqualTo(1)
            assertThat(widget.pushedStats).hasSize(1)
        }

    @Test
    fun `given a saved charge limit, when loading, then state adopts it`() = runTest(dispatcher) {
        // Given
        val settings = FakeAppSettingsRepository(chargeLimit = 85)
        val viewModel = buildViewModel(settings = settings)

        // When
        viewModel.loadDataCounterSettings()
        advanceUntilIdle()

        // Then
        assertThat(viewModel.uiState.value.chargeLimitPercent).isEqualTo(85)
    }

    @Test
    fun `given a dragged charge limit, when committing, then it is persisted`() =
        runTest(dispatcher) {
            // Given
            val settings = FakeAppSettingsRepository()
            val viewModel = buildViewModel(settings = settings)

            // When
            viewModel.onChargeLimitChange(80)
            viewModel.onCommitChargeLimit()
            advanceUntilIdle()

            // Then
            assertThat(viewModel.uiState.value.chargeLimitPercent).isEqualTo(80)
            assertThat(settings.chargeLimit).isEqualTo(80)
        }

    @Test
    fun `given an out-of-range charge limit, when committing, then it is coerced into 50 to 95`() =
        runTest(dispatcher) {
            // Given
            val settings = FakeAppSettingsRepository()
            val viewModel = buildViewModel(settings = settings)

            // When
            viewModel.onChargeLimitChange(140)
            viewModel.onCommitChargeLimit()
            advanceUntilIdle()

            // Then
            assertThat(viewModel.uiState.value.chargeLimitPercent).isEqualTo(95)
            assertThat(settings.chargeLimit).isEqualTo(95)
        }

    @Test
    fun `given the reminder switched off, when committing, then zero is persisted`() =
        runTest(dispatcher) {
            // Given
            val settings = FakeAppSettingsRepository(chargeLimit = 80)
            val viewModel = buildViewModel(settings = settings)

            // When
            viewModel.onChargeLimitChange(0)
            viewModel.onCommitChargeLimit()
            advanceUntilIdle()

            // Then
            assertThat(viewModel.uiState.value.chargeLimitPercent).isEqualTo(0)
            assertThat(settings.chargeLimit).isEqualTo(0)
        }

    @Test
    fun `given a saved data quota, when loading, then state adopts it`() = runTest(dispatcher) {
        // Given
        val settings = FakeAppSettingsRepository(quotaGb = 25.0)
        val viewModel = buildViewModel(settings = settings)

        // When
        viewModel.loadDataCounterSettings()
        advanceUntilIdle()

        // Then
        assertThat(viewModel.uiState.value.dataQuotaGb).isEqualTo(25.0)
    }

    @Test
    fun `given a dragged data quota, when committing, then persisted and stats repushed`() =
        runTest(dispatcher) {
            // Given
            val settings = FakeAppSettingsRepository()
            val repository = FakeSystemStatsRepository(sampleStats())
            val widget = FakeWidgetController(installed = true)
            val viewModel = buildViewModel(repository = repository, widget = widget, settings = settings)

            // When
            viewModel.onDataQuotaChange(50.0)
            viewModel.onCommitDataQuota()
            advanceUntilIdle()

            // Then
            assertThat(viewModel.uiState.value.dataQuotaGb).isEqualTo(50.0)
            assertThat(settings.quotaGb).isEqualTo(50.0)
            assertThat(repository.callCount).isEqualTo(1)
            assertThat(widget.pushedStats).hasSize(1)
        }

    @Test
    fun `given an out-of-range data quota, when committing, then it is coerced to the maximum`() =
        runTest(dispatcher) {
            // Given
            val settings = FakeAppSettingsRepository()
            val viewModel = buildViewModel(settings = settings)

            // When
            viewModel.onDataQuotaChange(9000.0)
            viewModel.onCommitDataQuota()
            advanceUntilIdle()

            // Then
            assertThat(viewModel.uiState.value.dataQuotaGb).isEqualTo(DATA_QUOTA_MAX_GB)
            assertThat(settings.quotaGb).isEqualTo(DATA_QUOTA_MAX_GB)
        }

    @Test
    fun `given the quota switched off, when committing, then zero is persisted`() =
        runTest(dispatcher) {
            // Given
            val settings = FakeAppSettingsRepository(quotaGb = 30.0)
            val viewModel = buildViewModel(settings = settings)

            // When
            viewModel.onDataQuotaChange(0.0)
            viewModel.onCommitDataQuota()
            advanceUntilIdle()

            // Then
            assertThat(viewModel.uiState.value.dataQuotaGb).isEqualTo(0.0)
            assertThat(settings.quotaGb).isEqualTo(0.0)
        }

    @Test
    fun `given day mode, when refreshing, then today's totals are recorded and shown`() =
        runTest(dispatcher) {
            // Given
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)
            val appUsage = FakeAppUsageRepository(
                unlocksByDay = mapOf(yesterday to 5, today to 6),
                screenByDay = mapOf(yesterday to 1_000_000L),
                totalsToday = UsageTotals(screenTimeMillis = 3_600_000L, unlockCount = 7),
            )
            val notifications = FakeNotificationStats(
                enabled = true,
                totalsByDay = mapOf(yesterday to 9, today to 4),
            )
            val history = FakeUsageHistory().apply {
                boots[today] = 2
                boots[yesterday] = 1 // outside the day period
                incrementCharge(today)
            }
            val viewModel = buildViewModel(
                appUsage = appUsage, notifications = notifications, history = history
            )

            // When
            viewModel.refresh()
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertThat(state.usageAccessEnabled).isTrue()
            assertThat(state.screenTimeMillis).isEqualTo(3_600_000L)
            assertThat(state.unlockCount).isEqualTo(7)
            assertThat(state.notificationCount).isEqualTo(4)
            assertThat(state.bootCount).isEqualTo(2)
            assertThat(state.chargeCount).isEqualTo(1)
            // Backfill was written into the history store, and old days purged.
            assertThat(history.unlocks[yesterday]).isEqualTo(5)
            assertThat(history.screen[yesterday]).isEqualTo(1_000_000L)
            assertThat(history.purgedWith).isEqualTo(today)
        }

    @Test
    fun `given cycle mode, when refreshing, then sums cover the whole period`() =
        runTest(dispatcher) {
            // Given a cycle that started yesterday regardless of the calendar date
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)
            val settings = FakeAppSettingsRepository(
                mode = DataCounterMode.BILLING_CYCLE,
                cycleDay = yesterday.dayOfMonth,
            )
            val appUsage = FakeAppUsageRepository(
                totalsToday = UsageTotals(screenTimeMillis = 2_000L, unlockCount = 7),
            )
            val notifications = FakeNotificationStats(
                enabled = true,
                totalsByDay = mapOf(yesterday to 3, today to 4),
            )
            val history = FakeUsageHistory().apply {
                recordUnlocks(yesterday, 10)
                recordScreenTime(yesterday, 1_000L)
                boots[yesterday] = 1
                boots[today] = 1
                incrementCharge(yesterday)
            }
            val viewModel = buildViewModel(
                settings = settings, appUsage = appUsage,
                notifications = notifications, history = history
            )

            // When
            viewModel.refresh()
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertThat(state.unlockCount).isEqualTo(17)
            assertThat(state.screenTimeMillis).isEqualTo(3_000L)
            assertThat(state.bootCount).isEqualTo(2)
            assertThat(state.chargeCount).isEqualTo(1)
            assertThat(state.notificationCount).isEqualTo(7)
        }

    @Test
    fun `given no usage access, when refreshing, then usage values unavailable but boots remain`() =
        runTest(dispatcher) {
            // Given
            val today = LocalDate.now()
            val history = FakeUsageHistory().apply { boots[today] = 1 }
            val viewModel = buildViewModel(
                appUsage = FakeAppUsageRepository(hasAccess = false),
                history = history,
            )

            // When
            viewModel.refresh()
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertThat(state.usageAccessEnabled).isFalse()
            assertThat(state.unlockCount).isEqualTo(UNAVAILABLE_INT)
            assertThat(state.screenTimeMillis).isEqualTo(-1L)
            assertThat(state.bootCount).isEqualTo(1)
            assertThat(state.notificationCount).isEqualTo(UNAVAILABLE_INT)
            assertThat(state.notificationAccessEnabled).isFalse()
        }
}

private class FakeSystemStatsRepository(
    private val stats: SystemStats,
    private val breakdown: DataBreakdown = DataBreakdown.NONE,
    private val storage: StorageBreakdown? = null,
    private val state: DeviceState = DeviceState(),
) : SystemStatsRepository {
    override suspend fun deviceState(): DeviceState = state

    override suspend fun storageBreakdown(): StorageBreakdown? = storage

    var callCount = 0
        private set

    /** The period start of the last breakdown asked for. */
    var breakdownStartMillis: Long? = null
        private set

    override suspend fun dataBreakdown(startMillis: Long): DataBreakdown {
        breakdownStartMillis = startMillis
        return breakdown
    }

    override suspend fun getStats(): SystemStats {
        callCount++
        return stats
    }

    override suspend fun getDeviceInfo(): DeviceInfo = sampleDeviceInfo()

    override suspend fun dataUsedSince(startMillis: Long): DataUsageSince =
        DataUsageSince(wifiGb = 0.0, mobileGb = 0.0)

    override suspend fun monthlyDataUsage(monthsBack: Int): List<MonthlyDataUsage> = emptyList()
}

private fun sampleDeviceInfo(): DeviceInfo = DeviceInfo(
    manufacturer = "Google",
    model = "Pixel 8a",
    codename = "akita",
    androidVersion = "15 (API 35)",
    securityPatch = "2026-06-01",
    buildNumber = "TEST.123",
    bootloader = "bl-1.0",
    radioVersion = "g5300",
    soc = "Google Tensor G3",
    supportedAbis = "arm64-v8a",
    kernelVersion = "6.1.0",
    gpuRenderer = "Mali-G715",
    glVersion = "OpenGL ES 3.2",
    screenResolution = "1080 × 2400 px",
    screenDensity = "420 dpi · xxhdpi",
    physicalSize = "6.1\"",
    refreshRate = "60 Hz",
    hdr = "Yes",
    totalRam = "8.0 GB",
    totalStorage = "128 GB",
    batteryTechnology = "Li-ion",
    batteryCapacityMah = "4492 mAh",
    cameraCount = "2",
    rearCamera = "64 MP",
    frontCamera = "13 MP",
    cameraFlash = "Yes",
    sensorCount = "30",
    sensors = "Accelerometer, Gyroscope",
    locale = "fi-FI",
    timezone = "Europe/Helsinki",
    webViewVersion = "120.0",
    playServicesVersion = "24.0",
    deviceFeatures = "NFC, Fingerprint",
    bootCountTotal = "42",
    vpnActive = "No",
    dnsServers = "8.8.8.8",
)

private class FakeWidgetController(
    private val installed: Boolean,
    private val savedOpacity: Float? = null,
) : WidgetController {
    val pushedStats = mutableListOf<SystemStats>()
    var committedOpacity: Float? = null
        private set

    override suspend fun pushStats(stats: SystemStats): Boolean {
        pushedStats += stats
        return installed
    }

    override suspend fun currentOpacity(): Float? = savedOpacity

    override suspend fun setOpacity(opacity: Float) {
        committedOpacity = opacity
    }
}

private fun sampleStats(batteryLevel: Int = 50): SystemStats = SystemStats(
    batteryLevel = batteryLevel,
    batteryStatus = "Charging",
    batteryHealth = "Good",
    batteryTemp = 25.0,
    batteryVoltage = 4.0,
    timeRemainingText = "—",
    batteryCycleCount = -1,
    batteryCapacityPercent = -1,
    totalRamGb = 8.0,
    usedRamGb = 4.0,
    ramPercent = 50,
    cpuCores = 8,
    cpuAbi = "arm64-v8a",
    cpuFreqGhz = 2.0,
    cpuLoadPercent = 20,
    cpuLoadLabel = "load",
    cpuTemp = 30.0,
    totalStorageGb = 128.0,
    usedStorageGb = 64.0,
    storagePercent = 50,
    wifiSsid = "Wi-Fi",
    wifiSsidName = "HomeNet",
    wifiBand = "5 GHz",
    wifiSpeedDown = 100,
    wifiSpeedUp = 50,
    wifiBytesTodayGb = 1.0,
    wifiDataLabel = "DATA TODAY",
    operatorName = "Op",
    mobileNetworkType = "5G",
    mobileSignalDbm = -80,
    mobileDataUsedGb = 0.5,
    mobileDataTotalGb = -1.0,
    mobileDataLabel = "DATA",
    simOperator = "Carrier",
    simState = "Ready",
    simSlots = 2,
    dataSimName = "—",
    networkCountry = "FI",
    wifiRssiDbm = -55,
    wifiLinkSpeedMbps = 433,
    wifiStandard = "Wi-Fi 6",
    ipAddress = "192.168.1.50",
    uptimeText = "1h 0m",
)
