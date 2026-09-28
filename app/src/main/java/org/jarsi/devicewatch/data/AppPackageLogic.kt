package org.jarsi.devicewatch.data

/** The permission groups of Android's own app settings, in the order they are shown here. */
enum class PermissionCategory {
    LOCATION,
    CAMERA,
    MICROPHONE,
    CONTACTS,
    CALENDAR,
    PHONE,
    CALL_LOG,
    SMS,
    FILES_AND_MEDIA,
    BODY_SENSORS,
    PHYSICAL_ACTIVITY,
    NEARBY_DEVICES,
    NOTIFICATIONS,
}

data class InstallDates(val installedMillis: Long?, val updatedMillis: Long?)

/** Where an installed app came from. */
sealed interface InstallSource {
    /** A store, or another app that installed it itself, by its label. */
    data class App(val label: String) : InstallSource
    /** `adb install` from a computer. */
    data object Adb : InstallSource
    /** An APK file opened with the package installer, or installed from a file by another app. */
    data object ApkFile : InstallSource
    data object Preinstalled : InstallSource
    data object Unknown : InstallSource
}

/** Facts about one installed package, read from its PackageInfo. */
data class AppPackageFacts(
    val versionName: String?,
    val versionCode: Long,
    /** Null for a preinstalled app, whose install time is the factory image's. */
    val installedMillis: Long?,
    /** Null when the app has not been updated since it was installed. */
    val updatedMillis: Long?,
    val targetSdk: Int,
    /** Null before Android 7, where ApplicationInfo has no minSdkVersion. */
    val minSdk: Int?,
    val installSource: InstallSource,
    val systemApp: Boolean,
    val grantedCategories: List<PermissionCategory>,
    val requestedPermissionCount: Int,
)

object AppPackageLogic {

    private const val REQUESTED_PERMISSION_GRANTED = 2

    private const val SHELL = "com.android.shell"
    private val PACKAGE_INSTALLERS = setOf("com.google.android.packageinstaller", "com.android.packageinstaller")

    /** PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE and PACKAGE_SOURCE_DOWNLOADED_FILE (API 33). */
    private val FILE_SOURCES = setOf(3, 4)

    /**
     * Where an app came from, from its install source record. [installer] is the
     * installing package, [initiator] the one that started the install (API 30+,
     * else null) and [packageSource] the PackageInstaller.PACKAGE_SOURCE_* value
     * (API 33+, else null). `adb install` leaves no installer and the shell as the
     * initiator; the package installer, which installs any APK a user opens, is
     * an APK file rather than a store. [labelOf] names any other installer.
     */
    fun installSource(
        installer: String?,
        initiator: String?,
        packageSource: Int?,
        systemApp: Boolean,
        labelOf: (String) -> String,
    ): InstallSource = when {
        installer in PACKAGE_INSTALLERS || packageSource in FILE_SOURCES -> InstallSource.ApkFile
        installer != null && installer != SHELL -> InstallSource.App(labelOf(installer))
        installer == SHELL || initiator == SHELL -> InstallSource.Adb
        systemApp -> InstallSource.Preinstalled
        else -> InstallSource.Unknown
    }

    private val categories: Map<String, PermissionCategory> = buildMap {
        fun put(category: PermissionCategory, vararg names: String) =
            names.forEach { put("android.permission.$it", category) }
        put(PermissionCategory.LOCATION, "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "ACCESS_BACKGROUND_LOCATION")
        put(PermissionCategory.CAMERA, "CAMERA")
        put(PermissionCategory.MICROPHONE, "RECORD_AUDIO")
        put(PermissionCategory.CONTACTS, "READ_CONTACTS", "WRITE_CONTACTS", "GET_ACCOUNTS")
        put(PermissionCategory.CALENDAR, "READ_CALENDAR", "WRITE_CALENDAR")
        put(
            PermissionCategory.PHONE,
            "READ_PHONE_STATE", "READ_PHONE_NUMBERS", "CALL_PHONE", "ANSWER_PHONE_CALLS",
            "ADD_VOICEMAIL", "USE_SIP", "ACCEPT_HANDOVER",
        )
        put(PermissionCategory.CALL_LOG, "READ_CALL_LOG", "WRITE_CALL_LOG", "PROCESS_OUTGOING_CALLS")
        put(PermissionCategory.SMS, "SEND_SMS", "RECEIVE_SMS", "READ_SMS", "RECEIVE_WAP_PUSH", "RECEIVE_MMS")
        put(
            PermissionCategory.FILES_AND_MEDIA,
            "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO",
            "READ_MEDIA_AUDIO", "READ_MEDIA_VISUAL_USER_SELECTED", "ACCESS_MEDIA_LOCATION",
        )
        put(PermissionCategory.BODY_SENSORS, "BODY_SENSORS", "BODY_SENSORS_BACKGROUND")
        put(PermissionCategory.PHYSICAL_ACTIVITY, "ACTIVITY_RECOGNITION")
        put(
            PermissionCategory.NEARBY_DEVICES,
            "BLUETOOTH_SCAN", "BLUETOOTH_CONNECT", "BLUETOOTH_ADVERTISE", "NEARBY_WIFI_DEVICES",
            "UWB_RANGING", "RANGING",
        )
        put(PermissionCategory.NOTIFICATIONS, "POST_NOTIFICATIONS")
    }

    /**
     * The group a runtime [permission] belongs to, or null for install-time and
     * app-defined permissions. Android 10+ hides the platform's own grouping from
     * apps, hence the fixed table; Android 16's health permissions replace
     * BODY_SENSORS and count with it.
     */
    fun permissionCategory(permission: String): PermissionCategory? =
        categories[permission]
            ?: PermissionCategory.BODY_SENSORS.takeIf { permission.startsWith("android.permission.health.") }

    /** The groups with at least one granted permission, in [PermissionCategory] order. */
    fun grantedCategories(requested: Array<String>?, flags: IntArray?): List<PermissionCategory> {
        if (requested == null || flags == null) return emptyList()
        return requested.indices
            .filter { it < flags.size && flags[it] and REQUESTED_PERMISSION_GRANTED != 0 }
            .mapNotNull { permissionCategory(requested[it]) }
            .distinct()
            .sorted()
    }

    /**
     * The install and update times worth showing. A preinstalled app reports the
     * factory image's time as installed, so it has none, and an update date only
     * once an update actually replaced the preinstalled copy.
     */
    fun installDates(
        firstInstallMillis: Long,
        lastUpdateMillis: Long,
        systemApp: Boolean,
        updatedSystemApp: Boolean,
    ): InstallDates = if (systemApp) {
        InstallDates(installedMillis = null, updatedMillis = lastUpdateMillis.takeIf { updatedSystemApp })
    } else {
        InstallDates(
            installedMillis = firstInstallMillis,
            updatedMillis = lastUpdateMillis.takeIf { it > firstInstallMillis },
        )
    }

    private val releases = mapOf(
        21 to "5.0", 22 to "5.1", 23 to "6", 24 to "7.0", 25 to "7.1", 26 to "8.0", 27 to "8.1",
        28 to "9", 29 to "10", 30 to "11", 31 to "12", 32 to "12L", 33 to "13", 34 to "14",
        35 to "15", 36 to "16", 37 to "17",
    )

    /** A foreground service that stops within this of leaving the app was part of using it. */
    private const val BACKGROUND_MARGIN_MS = 60_000L

    /**
     * When the app last ran in the background: its foreground service's last use,
     * if that came clearly after it was last opened. The only background use
     * Android tells an app about; widget updates, receivers and other component
     * use are system API.
     */
    fun backgroundUseMillis(openedMillis: Long?, foregroundServiceMillis: Long?): Long? {
        val service = foregroundServiceMillis ?: return null
        return service.takeIf { openedMillis == null || it - openedMillis > BACKGROUND_MARGIN_MS }
    }

    /** The Android release an API [level] shipped with, or null for one this build does not know. */
    fun androidRelease(level: Int): String? = releases[level]
}
