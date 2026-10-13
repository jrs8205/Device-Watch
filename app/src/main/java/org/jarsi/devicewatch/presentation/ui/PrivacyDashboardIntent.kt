package org.jarsi.devicewatch.presentation.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Opens Android's own Privacy dashboard. Android 12 and later ship it behind a
 * non-SDK action string; where that is missing (an OEM build, an older Android)
 * the plain privacy settings open instead.
 */
internal fun openPrivacyDashboard(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        try {
            context.startActivity(Intent("android.settings.PRIVACY_DASHBOARD").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            return
        } catch (_: Exception) {
            // Fall through to the generic privacy settings.
        }
    }
    try {
        context.startActivity(Intent(Settings.ACTION_PRIVACY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
