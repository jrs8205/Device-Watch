package org.jarsi.devicewatch.data

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The permissions Android keeps for development: no dialog on the phone grants
 * them, only `pm grant` from a computer, once. In the order the switch names them.
 */
enum class AdbPermission(val permission: String) {
    /** Lets the app ask Android's services for their dumps. */
    DUMP("android.permission.DUMP"),

    /** Opens the battery's health figure and dates. */
    BATTERY_STATS("android.permission.BATTERY_STATS"),

    /**
     * The battery statistics and the app-ops record check this one as a permission:
     * usage access switched on in Settings sets the app-op alone, which they do not take.
     */
    PACKAGE_USAGE_STATS("android.permission.PACKAGE_USAGE_STATS"),
}

/** Which of the [AdbPermission]s the app has not been granted. */
interface AdbGrants {
    fun missing(): Set<AdbPermission>
}

class PackageAdbGrants @Inject constructor(
    @ApplicationContext private val context: Context,
) : AdbGrants {
    override fun missing(): Set<AdbPermission> = AdbPermission.entries
        .filter { context.checkSelfPermission(it.permission) != PackageManager.PERMISSION_GRANTED }
        .toSet()
}

/** What the user runs on a computer, one line for each [AdbPermission]. */
object AdbGrantCommands {
    fun commands(packageName: String): List<String> =
        AdbPermission.entries.map { "adb shell pm grant $packageName ${it.permission}" }
}
