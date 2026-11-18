package org.jarsi.devicewatch.data

import android.app.AppOpsManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UsageAccessTest {

    @Test
    fun `usage access switched on in Settings is access, whatever the permission says`() {
        assertThat(usageAccessGranted(AppOpsManager.MODE_ALLOWED, permissionGranted = false)).isTrue()
        assertThat(usageAccessGranted(AppOpsManager.MODE_ALLOWED, permissionGranted = true)).isTrue()
    }

    @Test
    fun `the permission granted from a computer is access while the switch was never touched`() {
        // `pm grant` leaves the app-op at its default, and Android then goes by the permission.
        assertThat(usageAccessGranted(AppOpsManager.MODE_DEFAULT, permissionGranted = true)).isTrue()
    }

    @Test
    fun `an app that was given neither has no access`() {
        assertThat(usageAccessGranted(AppOpsManager.MODE_DEFAULT, permissionGranted = false)).isFalse()
    }

    @Test
    fun `usage access switched off in Settings wins over the permission`() {
        assertThat(usageAccessGranted(AppOpsManager.MODE_IGNORED, permissionGranted = true)).isFalse()
        assertThat(usageAccessGranted(AppOpsManager.MODE_ERRORED, permissionGranted = true)).isFalse()
    }
}
