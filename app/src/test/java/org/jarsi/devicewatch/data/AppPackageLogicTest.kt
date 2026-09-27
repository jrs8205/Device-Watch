package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppPackageLogicTest {

    private val granted = 2 // PackageInfo.REQUESTED_PERMISSION_GRANTED

    @Test
    fun `runtime permissions fall into the groups Android's own settings show`() {
        assertThat(AppPackageLogic.permissionCategory("android.permission.ACCESS_FINE_LOCATION"))
            .isEqualTo(PermissionCategory.LOCATION)
        assertThat(AppPackageLogic.permissionCategory("android.permission.ACCESS_BACKGROUND_LOCATION"))
            .isEqualTo(PermissionCategory.LOCATION)
        assertThat(AppPackageLogic.permissionCategory("android.permission.RECORD_AUDIO"))
            .isEqualTo(PermissionCategory.MICROPHONE)
        assertThat(AppPackageLogic.permissionCategory("android.permission.READ_MEDIA_IMAGES"))
            .isEqualTo(PermissionCategory.FILES_AND_MEDIA)
        assertThat(AppPackageLogic.permissionCategory("android.permission.BLUETOOTH_CONNECT"))
            .isEqualTo(PermissionCategory.NEARBY_DEVICES)
        assertThat(AppPackageLogic.permissionCategory("android.permission.health.READ_HEART_RATE"))
            .isEqualTo(PermissionCategory.BODY_SENSORS)
    }

    @Test
    fun `install-time permissions belong to no group`() {
        assertThat(AppPackageLogic.permissionCategory("android.permission.INTERNET")).isNull()
        assertThat(AppPackageLogic.permissionCategory("android.permission.USE_BIOMETRIC")).isNull()
        assertThat(AppPackageLogic.permissionCategory("com.example.permission.C2D_MESSAGE")).isNull()
    }

    @Test
    fun `only granted runtime permissions count, each group once, in a fixed order`() {
        val requested = arrayOf(
            "android.permission.INTERNET",
            "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.CAMERA",
        )
        val flags = intArrayOf(granted, granted, granted, granted, 0)

        assertThat(AppPackageLogic.grantedCategories(requested, flags))
            .containsExactly(PermissionCategory.LOCATION, PermissionCategory.MICROPHONE)
            .inOrder()
    }

    @Test
    fun `an app that requests nothing has no granted groups`() {
        assertThat(AppPackageLogic.grantedCategories(null, null)).isEmpty()
        // Mismatched arrays from a broken PackageInfo: only the paired entries count.
        assertThat(AppPackageLogic.grantedCategories(arrayOf("android.permission.CAMERA"), intArrayOf()))
            .isEmpty()
    }

    @Test
    fun `a preinstalled app has no install date, and only an updated one has an update date`() {
        // Preinstalled apps report the factory image's time, often 2009, as installed.
        assertThat(AppPackageLogic.installDates(1_000L, 1_000L, systemApp = true, updatedSystemApp = false))
            .isEqualTo(InstallDates(installedMillis = null, updatedMillis = null))
        assertThat(AppPackageLogic.installDates(1_000L, 5_000L, systemApp = true, updatedSystemApp = true))
            .isEqualTo(InstallDates(installedMillis = null, updatedMillis = 5_000L))
    }

    @Test
    fun `an app never updated since it was installed has no update date`() {
        assertThat(AppPackageLogic.installDates(1_000L, 1_000L, systemApp = false, updatedSystemApp = false))
            .isEqualTo(InstallDates(installedMillis = 1_000L, updatedMillis = null))
        assertThat(AppPackageLogic.installDates(1_000L, 5_000L, systemApp = false, updatedSystemApp = false))
            .isEqualTo(InstallDates(installedMillis = 1_000L, updatedMillis = 5_000L))
    }

    @Test
    fun `an API level reads as the Android release it shipped with`() {
        assertThat(AppPackageLogic.androidRelease(34)).isEqualTo("14")
        assertThat(AppPackageLogic.androidRelease(27)).isEqualTo("8.1")
        assertThat(AppPackageLogic.androidRelease(32)).isEqualTo("12L")
        assertThat(AppPackageLogic.androidRelease(37)).isEqualTo("17")
        assertThat(AppPackageLogic.androidRelease(99)).isNull()
    }
}
