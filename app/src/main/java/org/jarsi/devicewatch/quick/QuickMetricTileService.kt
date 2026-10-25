package org.jarsi.devicewatch.quick

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.TileService
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jarsi.devicewatch.MainActivity
import org.jarsi.devicewatch.data.PrivilegedAccess
import org.jarsi.devicewatch.data.QuickMetric
import org.jarsi.devicewatch.data.UNAVAILABLE_TEXT

/**
 * A Quick Settings tile showing one figure (GitHub #7: Android lets apps into the
 * panel with tiles only, never widgets). It reads only while the panel shows it:
 * at once when the panel opens, then every [REFRESH_MS], and nothing once it closes.
 * A tap opens the Overview, where the figure is shown in full.
 */
abstract class QuickMetricTileService : TileService() {

    abstract val metric: QuickMetric

    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also {
            it.launch {
                while (isActive) {
                    refresh()
                    delay(REFRESH_MS)
                }
            }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { openOverview() } else openOverview()
    }

    private suspend fun refresh() {
        val tile = qsTile ?: return
        val labels = quickMetricLabels(this)
        val (reading, iconText) = try {
            withContext(Dispatchers.Default) {
                val entry = EntryPointAccessors.fromApplication(applicationContext, QuickEntryPoint::class.java)
                val source = entry.quickStatsSource()
                val stats = source.stats()
                val dataToday = if (metric == QuickMetric.DATA_TODAY) source.dataToday() else null
                val extendedAccess = entry.appSettings().privilegedAccess() != PrivilegedAccess.OFF
                QuickMetricFormatter.read(metric, stats, dataToday, extendedAccess, labels) to
                    // A small tile shows nothing but its icon, so by default the figure is the icon.
                    if (entry.appSettings().tileValueIcons()) {
                        QuickMetricFormatter.iconText(metric, stats, dataToday, labels)
                    } else {
                        null
                    }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            QuickMetricReading(metric, UNAVAILABLE_TEXT, null, labels.notAvailable) to null
        }
        val look = quickTileAppearance(reading, labels.names.getValue(metric), Build.VERSION.SDK_INT)
        tile.label = look.label
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = look.subtitle
        tile.state = look.state
        tile.icon = iconText?.let(::quickTileValueIcon) ?: Icon.createWithResource(this, quickMetricIconRes(metric))
        tile.contentDescription = listOfNotNull(look.label, look.subtitle).joinToString(", ")
        tile.updateTile()
    }

    // The Intent form only runs before Android 14, where it is the only form there is.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openOverview() {
        val intent = MainActivity.openOverviewIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    REQUEST_OPEN_OVERVIEW,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
        } else {
            @Suppress("DEPRECATION") // the Intent form is the only one before Android 14
            startActivityAndCollapse(intent)
        }
    }

    private companion object {
        const val REFRESH_MS = 5_000L
        const val REQUEST_OPEN_OVERVIEW = 1103
    }
}

class BatteryTileService : QuickMetricTileService() {
    override val metric = QuickMetric.BATTERY
}

class BatteryTempTileService : QuickMetricTileService() {
    override val metric = QuickMetric.BATTERY_TEMP
}

class CpuTempTileService : QuickMetricTileService() {
    override val metric = QuickMetric.CPU_TEMP
}

class DataTodayTileService : QuickMetricTileService() {
    override val metric = QuickMetric.DATA_TODAY
}

class UptimeTileService : QuickMetricTileService() {
    override val metric = QuickMetric.UPTIME
}

class CpuLoadTileService : QuickMetricTileService() {
    override val metric = QuickMetric.CPU_LOAD
}

class MemoryTileService : QuickMetricTileService() {
    override val metric = QuickMetric.MEMORY
}

class StorageTileService : QuickMetricTileService() {
    override val metric = QuickMetric.STORAGE
}
