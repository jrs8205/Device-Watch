package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SystemAppRulesTest {

    private val roles = DeviceRoles(
        ownPackage = "org.jarsi.devicewatch",
        defaultHome = "com.google.android.apps.nexuslauncher",
        launchers = setOf("com.google.android.apps.nexuslauncher", "com.teslacoilsw.launcher"),
        currentKeyboard = "com.google.android.inputmethod.latin",
        enabledKeyboards = setOf("com.google.android.inputmethod.latin", "com.touchtype.swiftkey"),
        defaultDialer = "com.google.android.dialer",
        defaultSms = "com.google.android.apps.messaging",
        webViewProvider = "com.google.android.webview",
    )

    private fun verdict(packageName: String) = SystemAppRules.verdict(packageName, roles)

    @Test
    fun `the app itself and Android's core are never removable from here`() {
        assertThat(verdict("org.jarsi.devicewatch")).isEqualTo(SystemAppVerdict(SystemAppRisk.BLOCKED, SystemAppRole.SELF))
        assertThat(verdict("android")).isEqualTo(SystemAppVerdict(SystemAppRisk.BLOCKED, SystemAppRole.CORE, aospComponent = true))
        assertThat(verdict("com.android.systemui").risk).isEqualTo(SystemAppRisk.BLOCKED)
        assertThat(verdict("com.android.settings").risk).isEqualTo(SystemAppRisk.BLOCKED)
        assertThat(verdict("com.android.phone").risk).isEqualTo(SystemAppRisk.BLOCKED)
        assertThat(verdict("com.google.android.permissioncontroller").risk).isEqualTo(SystemAppRisk.BLOCKED)
        assertThat(verdict("com.google.android.networkstack.tethering").role).isEqualTo(SystemAppRole.CORE)
        assertThat(verdict("com.samsung.android.incallui").risk).isEqualTo(SystemAppRisk.BLOCKED)
        assertThat(verdict("com.xiaomi.finddevice")).isEqualTo(SystemAppVerdict(SystemAppRisk.BLOCKED, SystemAppRole.VENDOR))
    }

    @Test
    fun `the home screen, keyboard, phone app and web view in use are blocked, their alternatives warned about`() {
        assertThat(verdict("com.google.android.apps.nexuslauncher")).isEqualTo(SystemAppVerdict(SystemAppRisk.BLOCKED, SystemAppRole.HOME_IN_USE))
        assertThat(verdict("com.teslacoilsw.launcher")).isEqualTo(SystemAppVerdict(SystemAppRisk.WARNING, SystemAppRole.HOME))
        assertThat(verdict("com.google.android.inputmethod.latin")).isEqualTo(SystemAppVerdict(SystemAppRisk.BLOCKED, SystemAppRole.KEYBOARD_IN_USE))
        assertThat(verdict("com.touchtype.swiftkey")).isEqualTo(SystemAppVerdict(SystemAppRisk.WARNING, SystemAppRole.KEYBOARD))
        // A keyboard this build knows, enabled or not: the settings read may have failed.
        assertThat(verdict("com.samsung.android.honeyboard")).isEqualTo(SystemAppVerdict(SystemAppRisk.WARNING, SystemAppRole.KEYBOARD))
        assertThat(verdict("com.google.android.dialer")).isEqualTo(SystemAppVerdict(SystemAppRisk.BLOCKED, SystemAppRole.DIALER_IN_USE))
        assertThat(verdict("com.samsung.android.dialer")).isEqualTo(SystemAppVerdict(SystemAppRisk.WARNING, SystemAppRole.DIALER))
        assertThat(verdict("com.google.android.apps.messaging")).isEqualTo(SystemAppVerdict(SystemAppRisk.WARNING, SystemAppRole.MESSAGING))
        assertThat(verdict("com.google.android.webview")).isEqualTo(SystemAppVerdict(SystemAppRisk.BLOCKED, SystemAppRole.WEBVIEW_IN_USE))
        assertThat(verdict("com.android.webview")).isEqualTo(SystemAppVerdict(SystemAppRisk.WARNING, SystemAppRole.WEBVIEW, aospComponent = true))
        assertThat(verdict("com.google.android.trichromelibrary_671010433")).isEqualTo(SystemAppVerdict(SystemAppRisk.WARNING, SystemAppRole.WEBVIEW))
    }

    @Test
    fun `without the roles read, the known home screens, keyboards and phone apps are still warned about`() {
        val unknown = DeviceRoles(ownPackage = "org.jarsi.devicewatch")
        assertThat(SystemAppRules.verdict("com.google.android.apps.nexuslauncher", unknown)).isEqualTo(SystemAppVerdict(SystemAppRisk.WARNING, SystemAppRole.HOME))
        assertThat(SystemAppRules.verdict("com.sec.android.app.launcher", unknown)).isEqualTo(SystemAppVerdict(SystemAppRisk.WARNING, SystemAppRole.HOME))
        assertThat(SystemAppRules.verdict("com.google.android.inputmethod.latin", unknown)).isEqualTo(SystemAppVerdict(SystemAppRisk.WARNING, SystemAppRole.KEYBOARD))
        assertThat(SystemAppRules.verdict("com.google.android.dialer", unknown)).isEqualTo(SystemAppVerdict(SystemAppRisk.WARNING, SystemAppRole.DIALER))
        assertThat(SystemAppRules.verdict("com.google.android.webview", unknown)).isEqualTo(SystemAppVerdict(SystemAppRisk.WARNING, SystemAppRole.WEBVIEW))
    }

    @Test
    fun `the services the phone leans on carry a warning that names what breaks`() {
        assertThat(verdict("com.google.android.gms")).isEqualTo(SystemAppVerdict(SystemAppRisk.WARNING, SystemAppRole.PLAY_SERVICES))
        assertThat(verdict("com.google.android.gsf").role).isEqualTo(SystemAppRole.PLAY_SERVICES)
        assertThat(verdict("com.android.vending")).isEqualTo(SystemAppVerdict(SystemAppRisk.WARNING, SystemAppRole.PLAY_STORE))
        assertThat(verdict("com.android.bluetooth")).isEqualTo(SystemAppVerdict(SystemAppRisk.WARNING, SystemAppRole.RADIO, aospComponent = true))
        assertThat(verdict("com.android.nfc").role).isEqualTo(SystemAppRole.RADIO)
        assertThat(verdict("com.google.android.ims")).isEqualTo(SystemAppVerdict(SystemAppRisk.WARNING, SystemAppRole.TELEPHONY))
        assertThat(verdict("com.android.stk").role).isEqualTo(SystemAppRole.TELEPHONY)
        assertThat(verdict("com.android.cellbroadcastreceiver")).isEqualTo(SystemAppVerdict(SystemAppRisk.WARNING, SystemAppRole.EMERGENCY, aospComponent = true))
        assertThat(verdict("com.google.android.setupwizard")).isEqualTo(SystemAppVerdict(SystemAppRisk.WARNING, SystemAppRole.SETUP))
        assertThat(verdict("com.wssyncmldm")).isEqualTo(SystemAppVerdict(SystemAppRisk.WARNING, SystemAppRole.UPDATES))
        assertThat(verdict("com.samsung.android.knox.containercore")).isEqualTo(SystemAppVerdict(SystemAppRisk.WARNING, SystemAppRole.VENDOR))
        assertThat(verdict("com.miui.securitycenter").role).isEqualTo(SystemAppRole.VENDOR)
        assertThat(verdict("com.qualcomm.qti.telephonyservice")).isEqualTo(SystemAppVerdict(SystemAppRisk.WARNING, SystemAppRole.CHIPSET))
        assertThat(verdict("org.codeaurora.ims").role).isEqualTo(SystemAppRole.CHIPSET)
        assertThat(verdict("com.google.android.overlay.modules.permissioncontroller")).isEqualTo(SystemAppVerdict(SystemAppRisk.WARNING, SystemAppRole.OVERLAY))
        assertThat(verdict("com.android.internal.systemui.navbar.gestural").role).isEqualTo(SystemAppRole.OVERLAY)
        assertThat(verdict("com.google.android.documentsui")).isEqualTo(SystemAppVerdict(SystemAppRisk.WARNING, SystemAppRole.FILES))
    }

    @Test
    fun `anything else is plain, with Android's own parts marked as such`() {
        assertThat(verdict("com.google.android.youtube")).isEqualTo(SystemAppVerdict(SystemAppRisk.NORMAL, null))
        assertThat(verdict("com.facebook.services")).isEqualTo(SystemAppVerdict(SystemAppRisk.NORMAL, null))
        assertThat(verdict("com.android.egg")).isEqualTo(SystemAppVerdict(SystemAppRisk.NORMAL, null, aospComponent = true))
        assertThat(verdict("com.android.bips")).isEqualTo(SystemAppVerdict(SystemAppRisk.NORMAL, null, aospComponent = true))
        // Google's, under Android's prefix: the Play Store and Chrome are not parts of Android.
        assertThat(verdict("com.android.chrome")).isEqualTo(SystemAppVerdict(SystemAppRisk.NORMAL, null))
        assertThat(SystemAppRules.aospComponent("com.android.vending")).isFalse()
    }

    @Test
    fun `the input method setting names a package ahead of its class`() {
        assertThat(SystemAppRules.imePackage("com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"))
            .isEqualTo("com.google.android.inputmethod.latin")
        assertThat(SystemAppRules.imePackage("com.touchtype.swiftkey/.KeyboardService")).isEqualTo("com.touchtype.swiftkey")
        assertThat(SystemAppRules.imePackage("")).isNull()
        assertThat(SystemAppRules.imePackage(null)).isNull()
    }

    @Test
    fun `a search matches the name or the package, in any case, and an empty one matches all`() {
        val apps = listOf(
            SystemApp("com.google.android.youtube", "YouTube", installed = true, updated = true, verdict = SystemAppVerdict(SystemAppRisk.NORMAL, null)),
            SystemApp("com.android.egg", "Android Easter Egg", installed = true, updated = false, verdict = SystemAppVerdict(SystemAppRisk.NORMAL, null, aospComponent = true)),
        )
        assertThat(SystemAppRules.matching(apps, "")).isEqualTo(apps)
        assertThat(SystemAppRules.matching(apps, "  ")).isEqualTo(apps)
        assertThat(SystemAppRules.matching(apps, "you").map { it.packageName }).containsExactly("com.google.android.youtube")
        assertThat(SystemAppRules.matching(apps, "EGG").map { it.packageName }).containsExactly("com.android.egg")
        assertThat(SystemAppRules.matching(apps, "android").map { it.packageName })
            .containsExactly("com.google.android.youtube", "com.android.egg").inOrder()
        assertThat(SystemAppRules.matching(apps, "zzz")).isEmpty()
    }
}
