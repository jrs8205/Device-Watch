package org.jarsi.devicewatch.overlay

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.jarsi.devicewatch.data.AppSettingsRepositoryImpl
import org.jarsi.devicewatch.data.DataBreakdown
import org.jarsi.devicewatch.data.DataUsageSince
import org.jarsi.devicewatch.data.DeviceInfo
import org.jarsi.devicewatch.data.DeviceState
import org.jarsi.devicewatch.data.MonthlyDataUsage
import org.jarsi.devicewatch.data.StorageBreakdown
import org.jarsi.devicewatch.data.SystemStats
import org.jarsi.devicewatch.data.SystemStatsRepository
import org.jarsi.devicewatch.quick.QuickStatsSource
import org.jarsi.devicewatch.quick.quickStats
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.time.LocalDate
import java.time.ZoneId

/** The controller with the settings switched on, the permission held and the screen on. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FloatingFigureControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val settings = AppSettingsRepositoryImpl(context).apply { setOverlayShown(true) }

    private fun controller(isDreaming: () -> Boolean) = FloatingFigureController(
        context = context,
        appSettings = settings,
        quickStats = QuickStatsSource(UnusedStatsRepository(), now = { 0L }, today = { LocalDate.of(2026, 11, 5) }, zone = { ZoneId.of("Europe/Helsinki") }),
        mainHandler = Handler(Looper.getMainLooper()),
        isScreenOn = { true },
        hasPermission = { true },
        isDreaming = isDreaming,
    )

    @Test
    fun `a monitor started while the screensaver runs keeps the card down`() = runTest {
        // External review round 2: DREAMING_STARTED is not sticky, so a monitor restarted
        // in the middle of a screensaver put the card over it with its first reading.
        val controller = controller(isDreaming = { true })

        controller.refresh(quickStats(batteryTemp = 25.0))
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(windowViews()).isEmpty()
        assertThat(settings.overlayShown()).isTrue()
    }

    @Test
    fun `the first reading brings the card up when no screensaver runs`() = runTest {
        val controller = controller(isDreaming = { false })

        controller.refresh(quickStats(batteryTemp = 25.0))
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(windowViews()).hasSize(1)
    }

    @Test
    fun `a screensaver that ends lets the next reading bring the card up`() = runTest {
        var dreaming = true
        val controller = controller(isDreaming = { dreaming })
        controller.refresh(quickStats(batteryTemp = 25.0))
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(windowViews()).isEmpty()

        dreaming = false
        controller.onDreamingChanged(false)
        controller.refresh(quickStats(batteryTemp = 25.0))
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(windowViews()).hasSize(1)
    }

    /** The windows the card has put up through the window manager. */
    private fun windowViews(): List<View> = Shadow.extract<ShadowWindowManagerImpl>(windowManager).views

    /** The card's default figures never ask for the data of the day, so nothing here is read. */
    private class UnusedStatsRepository : SystemStatsRepository {
        override val latestStats = MutableStateFlow<SystemStats?>(null)
        override suspend fun getStats(): SystemStats = error("unused")
        override suspend fun getDeviceInfo(): DeviceInfo = error("unused")
        override suspend fun dataUsedSince(startMillis: Long): DataUsageSince = error("unused")
        override suspend fun monthlyDataUsage(monthsBack: Int): List<MonthlyDataUsage> = error("unused")
        override suspend fun dataBreakdown(startMillis: Long): DataBreakdown = error("unused")
        override suspend fun storageBreakdown(): StorageBreakdown? = error("unused")
        override suspend fun deviceState(): DeviceState = error("unused")
    }
}
