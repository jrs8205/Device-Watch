package org.jarsi.devicewatch.overlay

import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.provider.Settings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jarsi.devicewatch.MainActivity
import org.jarsi.devicewatch.data.AppSettingsRepository
import org.jarsi.devicewatch.data.PrivilegedAccess
import org.jarsi.devicewatch.data.QuickMetric
import org.jarsi.devicewatch.data.SystemStats
import org.jarsi.devicewatch.quick.QuickMetricFormatter
import org.jarsi.devicewatch.quick.QuickMetricReading
import org.jarsi.devicewatch.quick.QuickStatsSource
import org.jarsi.devicewatch.quick.quickMetricLabels
import org.jarsi.devicewatch.widget.WidgetAppearanceSettings

/**
 * Keeps the floating card in step with the monitor's readings and the settings, the
 * way [org.jarsi.devicewatch.system.LiveNotificationController] keeps the notification.
 * Every reading the widgets get goes through [refresh]; what to do with it is
 * [overlayAction]'s call. The window is touched only on the main thread, and a
 * window the system refuses turns the card off rather than failing the monitor.
 */
class FloatingFigureController(
    private val context: Context,
    private val appSettings: AppSettingsRepository,
    private val quickStats: QuickStatsSource,
    private val mainHandler: Handler,
    private val isScreenOn: () -> Boolean,
    private val hasPermission: () -> Boolean = { Settings.canDrawOverlays(context) },
) {
    private val mutex = Mutex()
    private var lastStats: SystemStats? = null

    // Main-thread state.
    private var window: FloatingFigureWindow? = null
    private var shownLandscape: Boolean? = null
    private var shownBlack: Boolean? = null
    private var destroyed = false

    /** Shows [stats] on the card when it is on and allowed; hides or turns it off otherwise. */
    suspend fun refresh(stats: SystemStats) = mutex.withLock {
        lastStats = stats
        apply(stats)
    }

    /** The card was switched on or off, or its figures changed; the caller follows with a fresh reading. */
    suspend fun onSettingsChanged() = mutex.withLock {
        val stats = lastStats
        if (stats == null) hideOnMain() else apply(stats)
    }

    /** With the screen off the card comes down at once; the next reading brings it back. */
    fun onScreenChanged(on: Boolean) {
        if (!on) hideOnMain()
    }

    /** The next reading re-measures and re-places the card for the new orientation or size. */
    fun onConfigurationChanged() {
        mainHandler.post { shownLandscape = null }
    }

    fun destroy() {
        mainHandler.post { destroyed = true }
        hideOnMain()
    }

    private suspend fun apply(stats: SystemStats) {
        when (overlayAction(appSettings.overlayShown(), hasPermission(), isScreenOn())) {
            OverlayAction.TURN_OFF -> {
                appSettings.setOverlayShown(false)
                hideOnMain()
            }
            OverlayAction.HIDE -> hideOnMain()
            OverlayAction.SHOW -> {
                val readings = read(stats)
                val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val black = WidgetAppearanceSettings.blackBackground(context) == true
                mainHandler.post {
                    // A hide (×, long press, screen off, destroy) that landed while this reading was
                    // on its way wins: the card must not come back for a cycle after it was dismissed.
                    if (destroyed || !appSettings.overlayShown() || !isScreenOn()) return@post
                    try {
                        val w = window()
                        if (w.isShowing && landscape == shownLandscape && black == shownBlack) {
                            w.update(readings)
                        } else {
                            val position = overlayPositionChoice(
                                landscape,
                                portrait = appSettings.overlayPosition(landscape = false),
                                landscapePosition = appSettings.overlayPosition(landscape = true),
                            )
                            w.show(readings, position, landscape, black)
                            shownLandscape = landscape
                            shownBlack = black
                        }
                    } catch (e: RuntimeException) {
                        // Whatever the window manager refused: the card goes, the monitor stays.
                        e.printStackTrace()
                        turnOff()
                    }
                }
            }
        }
    }

    private suspend fun read(stats: SystemStats): List<QuickMetricReading> {
        val chosen = overlayMetricsOrDefault(appSettings.overlayMetrics())
        val dataToday = if (QuickMetric.DATA_TODAY in chosen) quickStats.dataToday() else null
        val extendedAccess = appSettings.privilegedAccess() != PrivilegedAccess.OFF
        val labels = quickMetricLabels(context)
        return QuickMetric.entries.filter { it in chosen }
            .map { QuickMetricFormatter.read(it, stats, dataToday, extendedAccess, labels) }
    }

    private fun hideOnMain() {
        mainHandler.post {
            window?.hide()
            shownLandscape = null
        }
    }

    private fun window(): FloatingFigureWindow = window ?: FloatingFigureWindow(
        context,
        onHide = ::turnOff,
        onOpenApp = {
            try {
                context.startActivity(MainActivity.openOverviewIntent(context))
            } catch (e: Exception) {
                // The system may refuse a start from the background; the card stays.
                e.printStackTrace()
            }
        },
        onPositionChanged = { landscape, position -> appSettings.setOverlayPosition(landscape, position) },
        onFailed = ::turnOff,
    ).also { window = it }

    /** The user hid the card, or the system refused it: off until switched on again. */
    private fun turnOff() {
        appSettings.setOverlayShown(false)
        window?.hide()
        shownLandscape = null
    }
}
