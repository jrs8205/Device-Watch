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
     * permission, and from Android 12 only while the app is on screen. Before 12
     * there is no record for a refusal to land in, so nothing is gained by waiting,
     * and something is lost: on Android 8–9 the permission has no "while in use"
     * form and a background read goes through, while on 10–11 the refused
     * `getConnectionInfo()` still answers with a location-redacted copy that carries
     * the frequency, the widget's only source of the Wi-Fi band there (`WifiInfo` is
     * a `TransportInfo` only from API 31).
     */
    fun locationReadAllowed(sdk: Int, permissionGranted: Boolean, appVisible: Boolean): Boolean =
        permissionGranted && (sdk < Build.VERSION_CODES.S || appVisible)

    /**
     * Whether the last read still serves: made, under a minute ago, and of the same
     * source (the same Wi-Fi network; a new one must not wear the old one's name).
     */
    fun cachedReadUsable(readAtMillis: Long, nowMillis: Long, sameSource: Boolean): Boolean =
        readAtMillis != 0L && nowMillis - readAtMillis < CACHE_MAX_AGE_MILLIS && sameSource

    /**
     * [cachedReadUsable], and the copy must be as good as one read now: a read made
     * off screen on Android 10–11 was refused and came back redacted (the Wi-Fi
     * details without the name) or empty (the cell list), and so did one made
     * without the location permission or with the phone's location switch off (see
     * [fullCopyReadable]). Such a copy stops serving the moment the full one may be
     * read: the app on screen, the permission granted or the switch turned on
     * meanwhile. A full copy goes on serving after the app leaves the screen.
     */
    fun cachedCopyUsable(readAtMillis: Long, nowMillis: Long, sameSource: Boolean, readInFull: Boolean, fullReadableNow: Boolean): Boolean =
        cachedReadUsable(readAtMillis, nowMillis, sameSource) && (readInFull || !fullReadableNow)

    /**
     * Whether a location read answers in full, `getConnectionInfo()` with the
     * network's name and `getAllCellInfo()` with the cells: with the fine location
     * permission (from Android 10 the coarse one leaves the name redacted), the
     * phone's own location switch on (off, Android redacts the name and empties the
     * list whatever the permission), and the app on screen. The gate lets a Wi-Fi read
     * without the permission through on Android 10–12, where the nearby-devices
     * permission does not exist yet, and on 10–11 a read from the background goes
     * through refused: all of these answer redacted.
     */
    fun fullCopyReadable(fineLocationGranted: Boolean, locationEnabled: Boolean, appVisible: Boolean): Boolean =
        fineLocationGranted && locationEnabled && appVisible

    /**
     * `getServiceState`: from API 33 the call can be asked to leave the location
     * out, which Android then does not check. Before API 31 there is no record for
     * a refusal to land in, so nothing is gained by waiting. On 31 and 32 the call
     * is a location read like the others.
     */
    fun serviceStateAllowed(sdk: Int, appVisible: Boolean): Boolean =
        sdk >= Build.VERSION_CODES.TIRAMISU || sdk < Build.VERSION_CODES.S || appVisible
}
