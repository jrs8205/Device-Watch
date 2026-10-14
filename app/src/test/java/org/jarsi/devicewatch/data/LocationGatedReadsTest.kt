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
    fun `a redacted copy does not serve where the full one may now be read`() {
        // Android 10–11: the refused background read is a redacted copy (no name) or an empty cell
        // list; so is a Wi-Fi read made without the location permission on 10–12, where the
        // nearby-devices gate lets it through. Either stops serving once the full copy is readable.
        assertThat(LocationGatedReads.cachedCopyUsable(readAtMillis = 10_000L, nowMillis = 20_000L, sameSource = true, readInFull = false, fullReadableNow = true)).isFalse()
        // While the full copy cannot be read either, the redacted one is as good as it gets.
        assertThat(LocationGatedReads.cachedCopyUsable(readAtMillis = 10_000L, nowMillis = 20_000L, sameSource = true, readInFull = false, fullReadableNow = false)).isTrue()
        assertThat(LocationGatedReads.cachedCopyUsable(readAtMillis = 10_000L, nowMillis = 20_000L, sameSource = true, readInFull = true, fullReadableNow = true)).isTrue()
        // A full copy still serves the widget after the app leaves the screen.
        assertThat(LocationGatedReads.cachedCopyUsable(readAtMillis = 10_000L, nowMillis = 20_000L, sameSource = true, readInFull = true, fullReadableNow = false)).isTrue()
        // The minute and the source still rule.
        assertThat(LocationGatedReads.cachedCopyUsable(readAtMillis = 10_000L, nowMillis = 70_000L, sameSource = true, readInFull = true, fullReadableNow = true)).isFalse()
        assertThat(LocationGatedReads.cachedCopyUsable(readAtMillis = 10_000L, nowMillis = 20_000L, sameSource = false, readInFull = true, fullReadableNow = true)).isFalse()
    }

    @Test
    fun `a Wi-Fi copy is full only with the fine location permission and the app on screen`() {
        // Android 10–12: the gate lets a read without the location permission through (the
        // nearby-devices permission exists only from 13), and it answers with the name redacted.
        assertThat(LocationGatedReads.wifiReadInFull(fineLocationGranted = false, appVisible = true)).isFalse()
        // Android 10–11: a read from the background is refused and answers redacted even with the permission.
        assertThat(LocationGatedReads.wifiReadInFull(fineLocationGranted = true, appVisible = false)).isFalse()
        assertThat(LocationGatedReads.wifiReadInFull(fineLocationGranted = true, appVisible = true)).isTrue()
        assertThat(LocationGatedReads.wifiReadInFull(fineLocationGranted = false, appVisible = false)).isFalse()
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
