package com.example.modernwidget.presentation

import com.example.modernwidget.data.AppSettingsRepository
import com.example.modernwidget.data.DataCounterMode
import com.example.modernwidget.data.DeviceInfo
import com.example.modernwidget.data.SystemStats
import com.example.modernwidget.data.SystemStatsRepository
import com.example.modernwidget.widget.WidgetController
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

    @Test
    fun `given fresh stats, when refreshing, then state reflects repository and widget flag`() =
        runTest(dispatcher) {
            // Given
            val stats = sampleStats(batteryLevel = 77)
            val repository = FakeSystemStatsRepository(stats)
            val widget = FakeWidgetController(installed = true)
            val viewModel = DashboardViewModel(repository, widget, FakeAppSettingsRepository())

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
    fun `given a saved opacity, when loading, then state adopts it`() = runTest(dispatcher) {
        // Given
        val widget = FakeWidgetController(installed = true, savedOpacity = 0.42f)
        val viewModel = DashboardViewModel(
            FakeSystemStatsRepository(sampleStats()), widget, FakeAppSettingsRepository()
        )

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
        val viewModel = DashboardViewModel(
            FakeSystemStatsRepository(sampleStats()), widget, FakeAppSettingsRepository()
        )

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
        val viewModel = DashboardViewModel(
            FakeSystemStatsRepository(sampleStats()), widget, FakeAppSettingsRepository()
        )

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
        val viewModel = DashboardViewModel(
            FakeSystemStatsRepository(sampleStats()), FakeWidgetController(installed = true), settings
        )

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
            val viewModel = DashboardViewModel(repository, widget, settings)

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
            val viewModel = DashboardViewModel(repository, widget, settings)

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
}

private class FakeSystemStatsRepository(private val stats: SystemStats) : SystemStatsRepository {
    var callCount = 0
        private set

    override suspend fun getStats(): SystemStats {
        callCount++
        return stats
    }

    override suspend fun getDeviceInfo(): DeviceInfo = sampleDeviceInfo()
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
    vpnActive = "No",
    dnsServers = "8.8.8.8",
)

private class FakeAppSettingsRepository(
    var mode: DataCounterMode = DataCounterMode.DAY,
    var cycleDay: Int = 1,
) : AppSettingsRepository {
    override fun dataCounterMode(): DataCounterMode = mode

    override fun setDataCounterMode(mode: DataCounterMode) {
        this.mode = mode
    }

    override fun cycleStartDay(): Int = cycleDay

    override fun setCycleStartDay(day: Int) {
        cycleDay = day
    }
}

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
    networkCountry = "FI",
    wifiRssiDbm = -55,
    wifiLinkSpeedMbps = 433,
    wifiStandard = "Wi-Fi 6",
    ipAddress = "192.168.1.50",
    uptimeText = "1h 0m",
)
