package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocationGatedReadsTest {

    @Test
    fun `a location read runs only with the permission and the app on screen`() {
        assertThat(LocationGatedReads.locationReadAllowed(permissionGranted = true, appVisible = true)).isTrue()
        assertThat(LocationGatedReads.locationReadAllowed(permissionGranted = true, appVisible = false)).isFalse()
        assertThat(LocationGatedReads.locationReadAllowed(permissionGranted = false, appVisible = true)).isFalse()
        assertThat(LocationGatedReads.locationReadAllowed(permissionGranted = false, appVisible = false)).isFalse()
    }

    @Test
    fun `the service state is read anywhere from API 33, before that only on screen`() {
        assertThat(LocationGatedReads.serviceStateAllowed(sdk = 33, appVisible = false)).isTrue()
        assertThat(LocationGatedReads.serviceStateAllowed(sdk = 36, appVisible = false)).isTrue()
        assertThat(LocationGatedReads.serviceStateAllowed(sdk = 32, appVisible = false)).isFalse()
        assertThat(LocationGatedReads.serviceStateAllowed(sdk = 32, appVisible = true)).isTrue()
        assertThat(LocationGatedReads.serviceStateAllowed(sdk = 26, appVisible = false)).isFalse()
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
