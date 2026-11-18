package org.jarsi.devicewatch.data

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process

/**
 * Whether Android lets the app read usage statistics. Switched on in Settings,
 * the app-op says allowed. Granted from a computer instead (`pm grant`, as the
 * adb route asks), the app-op stays at its default and the permission itself
 * decides: that is how Android's own check goes. Switched off in Settings wins
 * over the permission.
 */
internal fun usageAccessGranted(appOpMode: Int, permissionGranted: Boolean): Boolean = when (appOpMode) {
    AppOpsManager.MODE_ALLOWED -> true
    AppOpsManager.MODE_DEFAULT -> permissionGranted
    else -> false
}

internal fun Context.hasUsageAccess(): Boolean {
    val appOps = getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
    }
    val permissionGranted =
        checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
    return usageAccessGranted(mode, permissionGranted)
}
