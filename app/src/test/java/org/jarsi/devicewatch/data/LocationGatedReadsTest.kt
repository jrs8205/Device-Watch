package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocationGatedReadsTest {

    @Test
    fun `a location read runs only with the permission, and from API 31 only with the app on screen`() {
        assertThat(LocationGatedReads.locationReadAllowed(sdk = 37, permissionGranted = true, appVisible = true)).isTrue()
        assertThat(LocationGatedReads.locationReadAllowed(sdk = 37, permissionGranted = true, appVisible = false)).isFalse()
        assertThat(LocationGatedReads.locationReadAllowed(sdk = 31, permissionGranted = true, appVisible = false)).isFalse()
        assertThat(LocationGatedReads.locationReadAllowed(sdk = 31, permissionGranted = true, appVisible = true)).isTrue()
        assertThat(LocationGatedReads.locationReadAllowed(sdk = 37, permissionGranted = false, appVisible = true)).isFalse()
        assertThat(LocationGatedReads.locationReadAllowed(sdk = 37, permissionGranted = false, appVisible = false)).isFalse()
        assertThat(LocationGatedReads.locationReadAllowed(sdk = 26, permissionGranted = false, appVisible = false)).isFalse()
    }

    @Test
    fun `before API 31 a read from the background goes through, since no record exists for a refusal`() {
        // Android 8–9: the permission has no "while in use" form at all.
        assertThat(LocationGatedReads.locationReadAllowed(sdk = 26, permissionGranted = true, appVisible = false)).isTrue()
        assertThat(LocationGatedReads.locationReadAllowed(sdk = 28, permissionGranted = true, appVisible = false)).isTrue()
        // Android 10–11: the background call is refused but nothing is written down, and the
        // widget's Wi-Fi band has no other source there: WifiInfo is a TransportInfo only from
        // API 31, so the location-redacted getConnectionInfo() is the one that carries the frequency.
        assertThat(LocationGatedReads.locationReadAllowed(sdk = 29, permissionGranted = true, appVisible = false)).isTrue()
        assertThat(LocationGatedReads.locationReadAllowed(sdk = 30, permissionGranted = true, appVisible = false)).isTrue()
    }

    @Test
    fun `a cached read serves for a minute, and only while it is of the same source`() {
        assertThat(LocationGatedReads.cachedReadUsable(readAtMillis = 0L, nowMillis = 10_000L, sameSource = true)).isFalse()
        assertThat(LocationGatedReads.cachedReadUsable(readAtMillis = 10_000L, nowMillis = 69_999L, sameSource = true)).isTrue()
        assertThat(LocationGatedReads.cachedReadUsable(readAtMillis = 10_000L, nowMillis = 70_000L, sameSource = true)).isFalse()
        // Another Wi-Fi network within the minute: the cached name and security are the old network's.
        assertThat(LocationGatedReads.cachedReadUsable(readAtMillis = 10_000L, nowMillis = 20_000L, sameSource = false)).isFalse()
    }

    @Test
    fun `the service state is read anywhere from API 33 and before 31, on 31 and 32 only on screen`() {
        assertThat(LocationGatedReads.serviceStateAllowed(sdk = 33, appVisible = false)).isTrue()
        assertThat(LocationGatedReads.serviceStateAllowed(sdk = 36, appVisible = false)).isTrue()
        assertThat(LocationGatedReads.serviceStateAllowed(sdk = 32, appVisible = false)).isFalse()
        assertThat(LocationGatedReads.serviceStateAllowed(sdk = 31, appVisible = false)).isFalse()
        assertThat(LocationGatedReads.serviceStateAllowed(sdk = 32, appVisible = true)).isTrue()
        // No privacy record before Android 12: nothing to keep out of.
        assertThat(LocationGatedReads.serviceStateAllowed(sdk = 30, appVisible = false)).isTrue()
        assertThat(LocationGatedReads.serviceStateAllowed(sdk = 26, appVisible = false)).isTrue()
    }

    @Test
    fun `visibility counts starts against stops and never goes below none`() {
        val visibility = AppVisibility()
        assertThat(visibility.visible).isFalse()
        visibility.onStart()
        assertThat(visibility.visible).isTrue()
        // A second activity instance during a recreate overlaps the first.
        visibility.onStart()
        visibility.onStop()
        assertThat(visibility.visible).isTrue()
        visibility.onStop()
        assertThat(visibility.visible).isFalse()
        visibility.onStop()
        assertThat(visibility.visible).isFalse()
        visibility.onStart()
        assertThat(visibility.visible).isTrue()
    }
}
