package com.example.modernwidget.presentation

import com.example.modernwidget.data.AppDataUsage
import com.example.modernwidget.data.AppScreenTime
import com.example.modernwidget.data.AppSettingsRepository
import com.example.modernwidget.data.AppUsageRepository
import com.example.modernwidget.data.DataCounterMode
import com.example.modernwidget.data.LaunchableApp
import com.example.modernwidget.data.NotificationStats
import com.example.modernwidget.data.UNAVAILABLE_INT
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
class AppsViewModelTest {

    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun screenTime(pkg: String, millis: Long, launches: Int = 1, lastUsed: Long = 0L) =
        AppScreenTime(pkg, "label-$pkg", millis, launches, lastUsed)

    private fun app(pkg: String, lastUsed: Long?) =
        LaunchableApp(pkg, "label-$pkg", lastUsed, isSystemApp = false)

    @Test
    fun `given usage data, when refreshing, then lists donut and sort are loaded`() =
        runTest(dispatcher) {
            // Given
            val repository = FakeAppUsageRepository(
                screenTimes = listOf(screenTime("a", 6_000), screenTime("b", 3_000)),
                dataConsumers = listOf(AppDataUsage(101, "a", "label-a", 150L)),
                apps = listOf(app("x", 200L), app("y", null)),
            )
            val viewModel = AppsViewModel(
                repository, FakeAppSettingsRepository(), FakeNotificationStats(enabled = true)
            )

            // When
            viewModel.refresh()
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertThat(state.isLoading).isFalse()
            assertThat(state.hasUsageAccess).isTrue()
            assertThat(state.totalScreenTimeMillis).isEqualTo(9_000)
            assertThat(state.screenTimeSegments.map { it.packageName })
                .containsExactly("a", "b").inOrder()
            assertThat(state.dataConsumers).hasSize(1)
            assertThat(state.apps.map { it.packageName }).containsExactly("y", "x").inOrder()
            assertThat(state.oldestFirst).isTrue()
            assertThat(state.notificationAccessEnabled).isTrue()
        }

    @Test
    fun `given a launcher package, when refreshing, then it is excluded from screen times and donut`() =
        runTest(dispatcher) {
            // Given
            val repository = FakeAppUsageRepository(
                screenTimes = listOf(
                    screenTime("com.sec.android.app.launcher", 9_000, launches = 47),
                    screenTime("com.whatsapp", 3_000, launches = 5),
                ),
                launchers = setOf("com.sec.android.app.launcher"),
            )
            val viewModel = AppsViewModel(
                repository, FakeAppSettingsRepository(), FakeNotificationStats()
            )

            // When
            viewModel.refresh()
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertThat(state.screenTimes.map { it.packageName }).containsExactly("com.whatsapp")
            assertThat(state.totalScreenTimeMillis).isEqualTo(3_000)
            assertThat(state.screenTimeSegments.map { it.packageName }).containsExactly("com.whatsapp")
        }

    @Test
    fun `given no usage access, when refreshing, then empty state is exposed`() =
        runTest(dispatcher) {
            // Given
            val repository = FakeAppUsageRepository(hasAccess = false)
            val viewModel = AppsViewModel(
                repository, FakeAppSettingsRepository(), FakeNotificationStats()
            )

            // When
            viewModel.refresh()
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertThat(state.isLoading).isFalse()
            assertThat(state.hasUsageAccess).isFalse()
            assertThat(state.apps).isEmpty()
            assertThat(state.screenTimeSegments).isEmpty()
        }

    @Test
    fun `given loaded data, when selecting an app, then the detail is assembled`() =
        runTest(dispatcher) {
            // Given
            val repository = FakeAppUsageRepository(
                screenTimes = listOf(screenTime("a", 6_000, launches = 3, lastUsed = 5_000)),
                dataConsumers = listOf(AppDataUsage(101, "a", "label-a", 150L)),
                apps = listOf(app("a", 5_000L)),
            )
            val notifications = FakeNotificationStats(enabled = true, packageCounts = mapOf("a" to 7))
            val viewModel = AppsViewModel(repository, FakeAppSettingsRepository(), notifications)
            viewModel.refresh()
            advanceUntilIdle()

            // When
            viewModel.onAppSelected("a")

            // Then
            val detail = viewModel.uiState.value.selectedDetail
            assertThat(detail).isNotNull()
            assertThat(detail!!.label).isEqualTo("label-a")
            assertThat(detail.foregroundMillisToday).isEqualTo(6_000)
            assertThat(detail.launchCountToday).isEqualTo(3)
            assertThat(detail.lastOpenedEpochMillis).isEqualTo(5_000)
            assertThat(detail.dataBytesToday).isEqualTo(150L)
            assertThat(detail.notificationsToday).isEqualTo(7)
        }

    @Test
    fun `given listener disabled, when selecting an app, then notifications are unavailable`() =
        runTest(dispatcher) {
            // Given
            val repository = FakeAppUsageRepository(
                screenTimes = listOf(screenTime("a", 6_000)),
            )
            val notifications = FakeNotificationStats(enabled = false, packageCounts = mapOf("a" to 7))
            val viewModel = AppsViewModel(repository, FakeAppSettingsRepository(), notifications)
            viewModel.refresh()
            advanceUntilIdle()

            // When
            viewModel.onAppSelected("a")

            // Then
            assertThat(viewModel.uiState.value.selectedDetail!!.notificationsToday)
                .isEqualTo(UNAVAILABLE_INT)
        }

    @Test
    fun `given an open detail, when dismissed, then it is cleared`() = runTest(dispatcher) {
        // Given
        val repository = FakeAppUsageRepository(screenTimes = listOf(screenTime("a", 6_000)))
        val viewModel = AppsViewModel(
            repository, FakeAppSettingsRepository(), FakeNotificationStats()
        )
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.onAppSelected("a")

        // When
        viewModel.onDetailDismiss()

        // Then
        assertThat(viewModel.uiState.value.selectedDetail).isNull()
    }

    @Test
    fun `given a sort toggle, when toggled, then order is persisted and the list reversed`() =
        runTest(dispatcher) {
            // Given
            val settings = FakeAppSettingsRepository()
            val repository = FakeAppUsageRepository(
                apps = listOf(app("x", 200L), app("y", null), app("z", 100L)),
            )
            val viewModel = AppsViewModel(repository, settings, FakeNotificationStats())
            viewModel.refresh()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.apps.map { it.packageName })
                .containsExactly("y", "z", "x").inOrder()

            // When
            viewModel.onSortToggle()

            // Then
            assertThat(settings.oldestFirst).isFalse()
            assertThat(viewModel.uiState.value.oldestFirst).isFalse()
            assertThat(viewModel.uiState.value.apps.map { it.packageName })
                .containsExactly("x", "z", "y").inOrder()
        }
}

