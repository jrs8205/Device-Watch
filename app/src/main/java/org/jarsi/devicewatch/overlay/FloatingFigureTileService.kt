package org.jarsi.devicewatch.overlay

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dagger.hilt.android.EntryPointAccessors
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.quick.QuickEntryPoint
import org.jarsi.devicewatch.system.SystemMonitorService

/**
 * The Quick Settings tile that brings the floating card up and takes it down
 * without opening the app. Without the permission it is inactive, says so, and a
 * tap opens the system's page for it; with the permission a tap flips the card and
 * tells the monitor, starting it if it is not running.
 */
class FloatingFigureTileService : TileService() {

    private val appSettings by lazy {
        EntryPointAccessors.fromApplication(applicationContext, QuickEntryPoint::class.java).appSettings()
    }

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { toggle() } else toggle()
    }

    private fun toggle() {
        if (!Settings.canDrawOverlays(this)) {
            openPermissionPage()
            return
        }
        appSettings.setOverlayShown(!appSettings.overlayShown())
        ContextCompat.startForegroundService(
            this,
            Intent(this, SystemMonitorService::class.java).setAction(SystemMonitorService.ACTION_OVERLAY_CHANGED),
        )
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val look = overlayTileLook(permission = Settings.canDrawOverlays(this), shown = appSettings.overlayShown())
        val subtitle = if (look.needsPermission) getString(R.string.overlay_tile_needs_permission) else null
        tile.label = getString(R.string.overlay_tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = subtitle
        tile.state = if (look.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_quick_overlay)
        tile.contentDescription = listOfNotNull(tile.label, subtitle).joinToString(", ")
        tile.updateTile()
    }

    // The Intent form only runs before Android 14, where it is the only form there is.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openPermissionPage() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    REQUEST_PERMISSION_PAGE,
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
        const val REQUEST_PERMISSION_PAGE = 1104
    }
}
