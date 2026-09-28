package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppPackageLogicTest {

    private val granted = 2 // PackageInfo.REQUESTED_PERMISSION_GRANTED

    // Values as `dumpsys package` showed them on a Pixel 8a (Android 16).
    private fun source(
        installer: String?,
        initiator: String? = installer,
        packageSource: Int? = 0,
        systemApp: Boolean = false,
    ) = AppPackageLogic.installSource(installer, initiator, packageSource, systemApp) { "label:$it" }

    @Test
    fun `an app installed over adb reads as installed from a computer`() {
        // The app sheet said "unknown" for these: no installer, the shell initiated it.
        assertThat(source(installer = null, initiator = "com.android.shell", packageSource = 1))
            .isEqualTo(InstallSource.Adb)
    }

    @Test
    fun `an app installed by the package installer reads as an APK file`() {
        // Was shown as the package installer's own label.
        assertThat(source("com.google.android.packageinstaller", packageSource = 3)).isEqualTo(InstallSource.ApkFile)
        assertThat(source("com.android.packageinstaller", packageSource = 3)).isEqualTo(InstallSource.ApkFile)
    }

    @Test
    fun `an app another app installed from a file reads as an APK file`() {
        assertThat(source("com.example.files", packageSource = 3)).isEqualTo(InstallSource.ApkFile)
        assertThat(source("com.example.files", packageSource = 4)).isEqualTo(InstallSource.ApkFile)
    }

    @Test
    fun `a store or other installing app is named by its label`() {
        assertThat(source("com.android.vending")).isEqualTo(InstallSource.App("label:com.android.vending"))
        assertThat(source("org.fdroid.fdroid", packageSource = 2)).isEqualTo(InstallSource.App("label:org.fdroid.fdroid"))
        // A web app Chrome installed on Play's behalf.
        assertThat(source("com.android.chrome", initiator = "com.android.vending"))
            .isEqualTo(InstallSource.App("label:com.android.chrome"))
    }

    @Test
    fun `a preinstalled app keeps its store once the store has updated it`() {
        assertThat(source(installer = null, systemApp = true)).isEqualTo(InstallSource.Preinstalled)
        assertThat(source("com.android.vending", systemApp = true)).isEqualTo(InstallSource.App("label:com.android.vending"))
    }

    @Test
    fun `a foreground service running on after the app was left is background use`() {
        // WhatsApp on the 8a: opened yesterday, its service ran this morning.
        val opened = 1_000_000L
        assertThat(AppPackageLogic.backgroundUseMillis(opened, foregroundServiceMillis = opened + 10 * 60_000L))
            .isEqualTo(opened + 10 * 60_000L)
        assertThat(AppPackageLogic.backgroundUseMillis(openedMillis = null, foregroundServiceMillis = 5_000L))
            .isEqualTo(5_000L)
    }

    @Test
    fun `a foreground service that stopped with the app's own use is not background use`() {
        // Vivaldi on the 8a: its service stopped three seconds after the app was left.
        assertThat(AppPackageLogic.backgroundUseMillis(10_000L, foregroundServiceMillis = 13_000L)).isNull()
        assertThat(AppPackageLogic.backgroundUseMillis(10_000L, foregroundServiceMillis = 5_000L)).isNull()
        assertThat(AppPackageLogic.backgroundUseMillis(10_000L, foregroundServiceMillis = null)).isNull()
    }

    @Test
    fun `an app with no installer on record is unknown`() {
        // Android 10 has only the installer, and an app can be installed without one.
        assertThat(source(installer = null, initiator = null, packageSource = null)).isEqualTo(InstallSource.Unknown)
    }

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
