package org.jarsi.devicewatch.presentation

import kotlinx.coroutines.CompletableDeferred
import org.jarsi.devicewatch.data.AppLastUse
import org.jarsi.devicewatch.data.AppPackageFacts
import org.jarsi.devicewatch.data.InstallSource
import org.jarsi.devicewatch.data.PermissionCategory
import org.jarsi.devicewatch.data.AppDataUsage
import org.jarsi.devicewatch.data.AppScreenTime
import org.jarsi.devicewatch.data.AppSettingsRepository
import org.jarsi.devicewatch.data.AppStorageUsage
import org.jarsi.devicewatch.data.AppUsageRepository
import org.jarsi.devicewatch.data.DataCounterMode
import org.jarsi.devicewatch.data.LaunchableApp
import org.jarsi.devicewatch.data.NotificationStats
import org.jarsi.devicewatch.data.UNAVAILABLE_INT
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
    fun `given app sizes, when refreshing, then the largest apps come first and empty ones drop`() =
        runTest(dispatcher) {
            val repository = FakeAppUsageRepository(
                storage = listOf(
                    AppStorageUsage("small", "Small", 10L),
                    AppStorageUsage("none", "None", 0L),
                    AppStorageUsage("big", "Big", 900L),
                    AppStorageUsage("mid", "Mid", 300L),
                ),
            )
            val viewModel = AppsViewModel(
                repository, FakeAppSettingsRepository(), FakeNotificationStats(enabled = true)
            )

            viewModel.refresh()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.storageConsumers.map { it.packageName })
                .containsExactly("big", "mid", "small").inOrder()
        }

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
    fun `given a launcher package, when selecting it, then the detail still shows its usage`() =
        runTest(dispatcher) {
            // Given
            val repository = FakeAppUsageRepository(
                screenTimes = listOf(
                    screenTime("com.sec.android.app.launcher", 9_000, launches = 47, lastUsed = 5_000),
                    screenTime("com.whatsapp", 3_000),
                ),
                launchers = setOf("com.sec.android.app.launcher"),
            )
            val viewModel = AppsViewModel(
                repository, FakeAppSettingsRepository(), FakeNotificationStats()
            )
            viewModel.refresh()
            advanceUntilIdle()

            // When
            viewModel.onAppSelected("com.sec.android.app.launcher")

            // Then
            val detail = viewModel.uiState.value.selectedDetail
            assertThat(detail).isNotNull()
            assertThat(detail!!.label).isEqualTo("label-com.sec.android.app.launcher")
            assertThat(detail.foregroundMillisToday).isEqualTo(9_000)
            assertThat(detail.launchCountToday).isEqualTo(47)
            assertThat(detail.lastOpenedEpochMillis).isEqualTo(5_000)
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

    private fun facts(version: String) = AppPackageFacts(
        versionName = version,
        versionCode = 17L,
        installedMillis = 1_000L,
        updatedMillis = 2_000L,
        targetSdk = 36,
        minSdk = 29,
        installSource = InstallSource.App("F-Droid"),
        systemApp = false,
        grantedCategories = listOf(PermissionCategory.CAMERA),
        requestedPermissionCount = 9,
    )

    @Test
    fun `given package facts, when selecting an app, then the detail carries them`() =
        runTest(dispatcher) {
            val repository = FakeAppUsageRepository(
                apps = listOf(app("a", 5_000L)),
                facts = mapOf("a" to facts("1.5.0")),
            )
            val viewModel = AppsViewModel(repository, FakeAppSettingsRepository(), FakeNotificationStats())
            viewModel.refresh()
            advanceUntilIdle()

            viewModel.onAppSelected("a")
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.selectedDetail!!.facts).isEqualTo(facts("1.5.0"))
        }

    @Test
    fun `an app without a launcher icon gets its last opening from Android's usage history`() =
        runTest(dispatcher) {
            // Opened from the largest-apps list, a system app read "never opened":
            // only today's screen time and the launcher list were consulted.
            val repository = FakeAppUsageRepository(
                storage = listOf(AppStorageUsage("sys", "System app", 900L)),
                facts = mapOf("sys" to facts("1.0")),
                lastUse = mapOf("sys" to AppLastUse(openedMillis = 8_000L, foregroundServiceMillis = null)),
            )
            val viewModel = AppsViewModel(repository, FakeAppSettingsRepository(), FakeNotificationStats())
            viewModel.refresh()
            advanceUntilIdle()

            viewModel.onAppSelected("sys")
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.selectedDetail!!.lastOpenedEpochMillis).isEqualTo(8_000L)
        }

    @Test
    fun `the sheet shows when the app last ran in the background, after it was last opened`() =
        runTest(dispatcher) {
            val opened = 1_000_000L
            val service = opened + 30 * 60_000L
            val repository = FakeAppUsageRepository(
                apps = listOf(app("a", opened)),
                facts = mapOf("a" to facts("1.0")),
                lastUse = mapOf("a" to AppLastUse(openedMillis = opened, foregroundServiceMillis = service)),
            )
            val viewModel = AppsViewModel(repository, FakeAppSettingsRepository(), FakeNotificationStats())
            viewModel.refresh()
            advanceUntilIdle()

            viewModel.onAppSelected("a")
            advanceUntilIdle()

            val detail = viewModel.uiState.value.selectedDetail!!
            assertThat(detail.lastOpenedEpochMillis).isEqualTo(opened)
            assertThat(detail.lastBackgroundMillis).isEqualTo(service)
        }

    @Test
    fun `an opening seen today is not replaced by an older one from the usage history`() =
        runTest(dispatcher) {
            val repository = FakeAppUsageRepository(
                screenTimes = listOf(screenTime("a", 6_000, lastUsed = 9_000L)),
                facts = mapOf("a" to facts("1.0")),
                lastUse = mapOf("a" to AppLastUse(openedMillis = 7_000L, foregroundServiceMillis = null)),
            )
            val viewModel = AppsViewModel(repository, FakeAppSettingsRepository(), FakeNotificationStats())
            viewModel.refresh()
            advanceUntilIdle()

            viewModel.onAppSelected("a")
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.selectedDetail!!.lastOpenedEpochMillis).isEqualTo(9_000L)
        }

    @Test
    fun `facts read for one app never land on the sheet of the next`() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val repository = FakeAppUsageRepository(
                apps = listOf(app("a", 5_000L), app("b", 4_000L)),
                facts = mapOf("a" to facts("a-version"), "b" to facts("b-version")),
                factsGate = gate,
            )
            val viewModel = AppsViewModel(repository, FakeAppSettingsRepository(), FakeNotificationStats())
            viewModel.refresh()
            advanceUntilIdle()

            viewModel.onAppSelected("a")
            advanceUntilIdle()
            viewModel.onAppSelected("b")
            advanceUntilIdle()
            gate.complete(Unit)
            advanceUntilIdle()

            val detail = viewModel.uiState.value.selectedDetail!!
            assertThat(detail.packageName).isEqualTo("b")
            assertThat(detail.facts!!.versionName).isEqualTo("b-version")
        }

    @Test
    fun `coming back from the app's settings re-reads the open sheet's permissions`() =
        runTest(dispatcher) {
            // Codex round 8: camera revoked in the system settings, back in the app,
            // and the open sheet still listed the camera as allowed.
            val repository = FakeAppUsageRepository(
                apps = listOf(app("a", 5_000L)),
                facts = mapOf("a" to facts("1.5.0")),
            )
            val viewModel = AppsViewModel(repository, FakeAppSettingsRepository(), FakeNotificationStats())
            viewModel.refresh()
            advanceUntilIdle()
            viewModel.onAppSelected("a")
            advanceUntilIdle()

            repository.facts = mapOf("a" to facts("1.5.0").copy(grantedCategories = emptyList()))
            viewModel.refresh()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.selectedDetail!!.facts!!.grantedCategories).isEmpty()
        }

    @Test
    fun `facts arriving after the sheet closed do not reopen it`() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val repository = FakeAppUsageRepository(
                apps = listOf(app("a", 5_000L)),
                facts = mapOf("a" to facts("1.5.0")),
                factsGate = gate,
            )
            val viewModel = AppsViewModel(repository, FakeAppSettingsRepository(), FakeNotificationStats())
            viewModel.refresh()
            advanceUntilIdle()

            viewModel.onAppSelected("a")
            advanceUntilIdle()
            viewModel.onDetailDismiss()
            gate.complete(Unit)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.selectedDetail).isNull()
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

