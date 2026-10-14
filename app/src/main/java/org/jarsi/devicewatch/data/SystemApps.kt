package org.jarsi.devicewatch.data

/** How a system app's removal would go for the phone. */
enum class SystemAppRisk {
    /** Not removable from here: the phone, or this app, does not work without it. */
    BLOCKED,
    /** Removable once the user has read what breaks and said so. */
    WARNING,
    /** Removable after the usual question. */
    NORMAL,
}

/** What a blocked or warned-about system app does for the phone; the screen explains each. */
enum class SystemAppRole {
    CORE,
    SELF,
    HOME_IN_USE,
    HOME,
    KEYBOARD_IN_USE,
    KEYBOARD,
    DIALER_IN_USE,
    DIALER,
    MESSAGING,
    WEBVIEW_IN_USE,
    WEBVIEW,
    PLAY_SERVICES,
    PLAY_STORE,
    RADIO,
    TELEPHONY,
    EMERGENCY,
    SETUP,
    UPDATES,
    VENDOR,
    CHIPSET,
    OVERLAY,
    FILES,
}

data class SystemAppVerdict(
    val risk: SystemAppRisk,
    /** Null for a plain app. */
    val role: SystemAppRole?,
    /** One of Android's own packages, whatever the risk: a hint to look before removing. */
    val aospComponent: Boolean = false,
)

/** A package on the system image, installed for this user or removed from it. */
data class SystemApp(
    val packageName: String,
    val label: String,
    /** False once removed for this user; the system image's copy stays and can come back. */
    val installed: Boolean,
    /** A system app updated from a store: the update is what runs. */
    val updated: Boolean,
    val verdict: SystemAppVerdict,
)

/**
 * What this phone leans on right now: the packages whose removal would leave it
 * without a home screen, a keyboard, a phone app, a messaging app or a web view.
 * Unknown (null, empty) where a read failed: the fixed lists then still catch
 * the known ones.
 */
data class DeviceRoles(
    val ownPackage: String,
    val defaultHome: String? = null,
    val launchers: Set<String> = emptySet(),
    val currentKeyboard: String? = null,
    val enabledKeyboards: Set<String> = emptySet(),
    val defaultDialer: String? = null,
    val defaultSms: String? = null,
    val webViewProvider: String? = null,
)

/**
 * Which system apps the phone cannot do without, and which it is poorer without.
 * A fixed table of the packages Android, Google and the big manufacturers ship,
 * plus what the phone says it is using now ([DeviceRoles]). Everything else is
 * plain: a system app all the same, so the question is still asked.
 */
object SystemAppRules {

    private val blockedCore = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.providers.settings",
        "com.android.shell",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.providers.telephony",
        "com.android.providers.contacts",
        "com.android.providers.media",
        "com.android.providers.media.module",
        "com.google.android.providers.media.module",
        "com.android.providers.downloads",
        "com.android.providers.blockednumber",
        "com.android.keychain",
        "com.android.certinstaller",
        "com.android.location.fused",
        "com.android.inputdevices",
        "com.android.externalstorage",
        "com.android.mtp",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.intentresolver",
        "com.android.credentialmanager",
        "com.android.vpndialogs",
        "com.android.carrierconfig",
        "com.android.ons",
        "com.android.dynsystem",
        "com.android.localtransport",
        "com.android.backupconfirm",
        "com.android.sharedstoragebackup",
        "com.android.wallpaperbackup",
        "com.android.proxyhandler",
        "com.android.pacprocessor",
        "com.android.statementservice",
        "com.android.provision",
        "com.android.ext.services",
        "com.google.android.ext.services",
        "com.android.ext.shared",
        "com.google.android.ext.shared",
        "com.android.sdksandbox",
        "com.google.android.sdksandbox",
        "com.android.wifi.resources",
        "com.google.android.wifi.resources",
        "com.android.wifi.dialog",
        "com.android.connectivity.resources",
        "com.google.android.connectivity.resources",
        "com.android.uwb.resources",
        "com.google.android.uwb.resources",
        "com.android.cellbroadcastservice",
        "com.google.android.cellbroadcastservice",
        "com.android.modulemetadata",
        "com.google.android.modulemetadata",
        "com.android.devicelockcontroller",
        "com.google.android.devicelockcontroller",
        // Samsung
        "com.samsung.android.incallui",
        "com.samsung.android.app.telephonyui",
        "com.sec.imsservice",
        "com.samsung.android.providers.contacts",
        "com.samsung.android.providers.media",
        "com.samsung.android.kgclient",
    )

    private val blockedCorePrefixes = listOf(
        "com.android.networkstack",
        "com.google.android.networkstack",
    )

    /** Manufacturer components whose removal is known to send the phone into a restart loop or lock it. */
    private val blockedVendor = setOf(
        "com.xiaomi.finddevice",
        "com.miui.core",
        "com.miui.system",
        "com.miui.rom",
        "com.lbe.security.miui",
    )

    private val homes = setOf(
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3",
        "com.android.launcher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.oneplus.launcher",
        "com.oppo.launcher",
        "com.coloros.launcher",
        "com.motorola.launcher3",
        "com.sonymobile.home",
        "com.asus.launcher",
        "net.oneplus.launcher",
    )

    private val keyboards = setOf(
        "com.google.android.inputmethod.latin",
        "com.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.sec.android.inputmethod",
        "com.touchtype.swiftkey",
        "com.baidu.input_mi",
        "com.iflytek.inputmethod.miui",
        "com.sohu.inputmethod.sogou.xiaomi",
    )

    private val dialers = setOf(
        "com.google.android.dialer",
        "com.android.dialer",
        "com.samsung.android.dialer",
    )

    private val webViews = setOf(
        "com.google.android.webview",
        "com.android.webview",
        "com.google.android.webview.beta",
        "com.google.android.webview.dev",
        "com.google.android.webview.canary",
    )

    private val webViewPrefixes = listOf("com.google.android.trichromelibrary")

    private val warned: Map<String, SystemAppRole> = buildMap {
        fun put(role: SystemAppRole, vararg names: String) = names.forEach { put(it, role) }
        put(SystemAppRole.PLAY_SERVICES, "com.google.android.gms", "com.google.android.gsf")
        put(SystemAppRole.PLAY_STORE, "com.android.vending")
        put(SystemAppRole.RADIO, "com.android.bluetooth", "com.google.android.bluetooth", "com.android.nfc", "com.android.se")
        put(
            SystemAppRole.TELEPHONY,
            "com.android.stk", "com.android.stk2", "com.android.mms.service", "com.android.service.ims",
            "com.android.imsserviceentitlement", "com.android.carrierdefaultapp",
            "com.google.android.ims", "com.google.android.apps.carrier.carrierwifi", "com.google.android.euicc",
            "com.google.euiccpixel", "com.google.android.telephony.satellite",
            "com.sec.epdg", "com.sec.unifiedwfc",
        )
        put(
            SystemAppRole.EMERGENCY,
            "com.android.cellbroadcastreceiver", "com.android.cellbroadcastreceiver.module",
            "com.google.android.cellbroadcastreceiver", "com.android.emergency", "com.google.android.apps.safetyhub",
            "com.sec.android.app.safetyassurance",
        )
        put(
            SystemAppRole.SETUP,
            "com.google.android.setupwizard", "com.android.managedprovisioning",
            "com.sec.android.app.SecSetupWizard", "com.sec.android.app.setupwizard",
        )
        put(
            SystemAppRole.UPDATES,
            "com.google.android.configupdater", "com.wssyncmldm", "com.sec.android.soagent",
            "com.samsung.android.app.omcagent", "com.android.updater",
        )
        put(
            SystemAppRole.VENDOR,
            "com.samsung.android.mdm", "com.samsung.android.lool", "com.samsung.android.sm.devicesecurity",
            "com.miui.securitycenter", "com.miui.securityadd", "com.miui.powerkeeper", "com.xiaomi.xmsf", "com.miui.guardprovider",
            "com.huawei.hwid", "com.huawei.systemmanager", "com.coloros.safecenter", "com.oplus.safecenter",
        )
        put(SystemAppRole.FILES, "com.android.documentsui", "com.google.android.documentsui")
    }

    private val warnedPrefixes: List<Pair<String, SystemAppRole>> = listOf(
        "com.samsung.android.knox." to SystemAppRole.VENDOR,
        "com.samsung.knox." to SystemAppRole.VENDOR,
        "com.sec.enterprise.knox." to SystemAppRole.VENDOR,
        "com.qualcomm." to SystemAppRole.CHIPSET,
        "com.qti." to SystemAppRole.CHIPSET,
        "com.mediatek." to SystemAppRole.CHIPSET,
        "com.unisoc." to SystemAppRole.CHIPSET,
        "org.codeaurora." to SystemAppRole.CHIPSET,
    )

    /** Runtime resource overlays: theme and layout packages, named a few different ways. */
    private val overlayMarks = listOf("overlay", ".theme.", "auto_generated_rro", ".systemui.navbar.", ".display.cutout.")

    fun verdict(packageName: String, roles: DeviceRoles): SystemAppVerdict {
        val aosp = aospComponent(packageName)
        fun of(risk: SystemAppRisk, role: SystemAppRole?) = SystemAppVerdict(risk, role, aosp)
        return when {
            packageName == roles.ownPackage -> of(SystemAppRisk.BLOCKED, SystemAppRole.SELF)
            packageName in blockedCore || blockedCorePrefixes.any(packageName::startsWith) -> of(SystemAppRisk.BLOCKED, SystemAppRole.CORE)
            packageName in blockedVendor -> of(SystemAppRisk.BLOCKED, SystemAppRole.VENDOR)
            packageName == roles.defaultHome -> of(SystemAppRisk.BLOCKED, SystemAppRole.HOME_IN_USE)
            packageName == roles.currentKeyboard -> of(SystemAppRisk.BLOCKED, SystemAppRole.KEYBOARD_IN_USE)
            packageName == roles.defaultDialer -> of(SystemAppRisk.BLOCKED, SystemAppRole.DIALER_IN_USE)
            packageName == roles.webViewProvider -> of(SystemAppRisk.BLOCKED, SystemAppRole.WEBVIEW_IN_USE)
            packageName in roles.launchers || packageName in homes -> of(SystemAppRisk.WARNING, SystemAppRole.HOME)
            packageName in roles.enabledKeyboards || packageName in keyboards -> of(SystemAppRisk.WARNING, SystemAppRole.KEYBOARD)
            packageName in dialers -> of(SystemAppRisk.WARNING, SystemAppRole.DIALER)
            packageName == roles.defaultSms -> of(SystemAppRisk.WARNING, SystemAppRole.MESSAGING)
            packageName in webViews || webViewPrefixes.any(packageName::startsWith) -> of(SystemAppRisk.WARNING, SystemAppRole.WEBVIEW)
            packageName in warned -> of(SystemAppRisk.WARNING, warned.getValue(packageName))
            else -> {
                val prefixed = warnedPrefixes.firstOrNull { (prefix, _) -> packageName.startsWith(prefix) }?.second
                when {
                    prefixed != null -> of(SystemAppRisk.WARNING, prefixed)
                    overlayMarks.any(packageName::contains) -> of(SystemAppRisk.WARNING, SystemAppRole.OVERLAY)
                    else -> of(SystemAppRisk.NORMAL, null)
                }
            }
        }
    }

    /** Google's apps that carry Android's prefix from the days before the two were told apart. */
    private val googleUnderAndroidPrefix = setOf("com.android.vending", "com.android.chrome")

    /** One of Android's own packages, as opposed to Google's, a manufacturer's or a partner's. */
    fun aospComponent(packageName: String): Boolean =
        packageName == "android" || (packageName.startsWith("com.android.") && packageName !in googleUnderAndroidPrefix)

    /** The package half of an input-method id (`package/class`), or null when there is none. */
    fun imePackage(id: String?): String? = id?.substringBefore('/')?.takeIf { it.isNotBlank() }

    /** The apps whose name or package contains [query], ignoring case; all of them for a blank query. */
    fun matching(apps: List<SystemApp>, query: String): List<SystemApp> {
        val needle = query.trim()
        if (needle.isEmpty()) return apps
        return apps.filter { it.label.contains(needle, ignoreCase = true) || it.packageName.contains(needle, ignoreCase = true) }
    }
}
