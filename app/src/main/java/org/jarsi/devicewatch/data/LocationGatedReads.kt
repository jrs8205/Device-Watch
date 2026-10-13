package org.jarsi.devicewatch.data

import android.os.Build

/**
 * When the telephony reads that Android counts as location reads may run. The
 * location permission is granted "while in use": an app not on screen is refused,
 * and the refusal goes into Android's privacy log against the app's name. Before
 * 1.9.0 the monitor's five-second poll made four such asks every round: the cell
 * list twice, the service state and the Wi-Fi network's name.
 */
internal object LocationGatedReads {

    /**
     * `getAllCellInfo`, `WifiManager.getConnectionInfo` and their kin: with the
     * permission, and only while the app is on screen.
     */
    fun locationReadAllowed(permissionGranted: Boolean, appVisible: Boolean): Boolean = permissionGranted && appVisible

    /**
     * `getServiceState`: from API 33 the call can be asked to leave the location
     * out, which Android then does not check; before that it is a location read
     * like the others.
     */
    fun serviceStateAllowed(sdk: Int, appVisible: Boolean): Boolean = sdk >= Build.VERSION_CODES.TIRAMISU || appVisible
}
