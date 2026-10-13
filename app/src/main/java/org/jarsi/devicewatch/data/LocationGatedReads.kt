package org.jarsi.devicewatch.data

import android.os.Build

/**
 * When the telephony reads that Android counts as location reads may run. From
 * Android 10 the location permission is granted "while in use": an app not on
 * screen is refused, and from Android 12 the refusal goes into Android's privacy
 * log against the app's name. Before
 * 1.9.0 the monitor's five-second poll made four such asks every round: the cell
 * list twice, the service state and the Wi-Fi network's name.
 */
internal object LocationGatedReads {

    /** How long a cell list or a Wi-Fi connection's details are served from the last read. */
    const val CACHE_MAX_AGE_MILLIS = 60_000L

    /**
     * `getAllCellInfo`, `WifiManager.getConnectionInfo` and their kin: with the
     * permission, and from Android 10 only while the app is on screen. Before 10
     * the permission has no "while in use" form, a read from the background goes
     * through, and the widget's signal strength and Wi-Fi band have no other source
     * there (the signal-strength and transport-info calls are Android 10's).
     */
    fun locationReadAllowed(sdk: Int, permissionGranted: Boolean, appVisible: Boolean): Boolean =
        permissionGranted && (sdk < Build.VERSION_CODES.Q || appVisible)

    /**
     * Whether the last read still serves: made, under a minute ago, and of the same
     * source (the same Wi-Fi network; a new one must not wear the old one's name).
     */
    fun cachedReadUsable(readAtMillis: Long, nowMillis: Long, sameSource: Boolean): Boolean =
        readAtMillis != 0L && nowMillis - readAtMillis < CACHE_MAX_AGE_MILLIS && sameSource

    /**
     * `getServiceState`: from API 33 the call can be asked to leave the location
     * out, which Android then does not check. Before API 31 there is no record for
     * a refusal to land in, so nothing is gained by waiting. On 31 and 32 the call
     * is a location read like the others.
     */
    fun serviceStateAllowed(sdk: Int, appVisible: Boolean): Boolean =
        sdk >= Build.VERSION_CODES.TIRAMISU || sdk < Build.VERSION_CODES.S || appVisible
}
