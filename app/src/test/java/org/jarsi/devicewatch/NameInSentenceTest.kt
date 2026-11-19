package org.jarsi.devicewatch

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.jarsi.devicewatch.data.ExtraStatsLogic
import org.jarsi.devicewatch.data.SystemApp
import org.jarsi.devicewatch.data.SystemAppActionResult
import org.jarsi.devicewatch.data.SystemAppRisk
import org.jarsi.devicewatch.data.SystemAppVerdict
import org.jarsi.devicewatch.data.UNAVAILABLE_TEXT
import org.jarsi.devicewatch.presentation.SystemAppAction
import org.jarsi.devicewatch.presentation.reportDeviceName
import org.jarsi.devicewatch.presentation.ui.appVersionText
import org.jarsi.devicewatch.presentation.ui.clusterTechnicalText
import org.jarsi.devicewatch.presentation.ui.opNameText
import org.jarsi.devicewatch.presentation.ui.removeQuestionTitle
import org.jarsi.devicewatch.presentation.ui.systemAppNoticeText
import org.jarsi.devicewatch.presentation.ui.wakeDetailText
import org.jarsi.devicewatch.widget.wifiTitleText
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * A name the app did not write, inside one of the app's own Arabic sentences.
 *
 * An app's name, the package manager's reply, a kernel's name for a thing: they come in any
 * script, may begin with a number and may be nothing but a code. Put into a sentence that
 * reads from the right, "123 456" was drawn "456 123": the question before a removal named
 * another app than the one about to be removed. Each such name is one piece that keeps its
 * own order ([Bidi.outside]), and the sentence around it still reads from the right.
 */
@RunWith(RobolectricTestRunner::class)
class NameInSentenceTest {

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val arabicLocale: Locale = Locale.forLanguageTag("ar-EG-u-nu-latn")

    private val arabic: Context
        get() = app.createConfigurationContext(Configuration().apply { setLocale(arabicLocale) })

    private val defaultBefore: Locale = Locale.getDefault()

    @After
    fun restoreDefault() {
        Locale.setDefault(defaultBefore)
    }

    /** Names that a line read from the right turns round when they are left alone in it. */
    private val names = listOf("123 456", "7 Minute Workout", "3 / 4 Code", "2048")

    /** Each of [written] stands on the line as a reader sees it, exactly as it is written. */
    private fun assertWritten(text: String, vararg written: String) {
        val seen = VisualOrder.rightToLeft(text)
        for (piece in written) assertWithMessage(seen).that(seen).contains(piece)
    }

    private fun systemApp(label: String) = SystemApp(
        packageName = "com.example.app",
        label = label,
        installed = true,
        updated = false,
        verdict = SystemAppVerdict(SystemAppRisk.NORMAL, role = null),
    )

    @Test
    fun `left alone the names are turned round, which is what the check must see`() {
        val question = arabic.getString(R.string.system_apps_remove_title, "123 456")

        assertThat(VisualOrder.rightToLeft(question)).contains("456 123")
    }

    @Test
    fun `the question before a removal names the app as it is written`() {
        Locale.setDefault(arabicLocale)
        for (name in names) assertWritten(removeQuestionTitle(arabic, name), name)

        Locale.setDefault(Locale.US)
        assertThat(removeQuestionTitle(app, "Gmail 2")).isEqualTo("Remove Gmail 2?")
    }

    @Test
    fun `what became of a removal or a restore names the app as it is written`() {
        Locale.setDefault(arabicLocale)
        for (name in names) {
            for (restore in listOf(false, true)) {
                for (result in listOf(
                    SystemAppActionResult.Done,
                    SystemAppActionResult.NoShell,
                    SystemAppActionResult.NotInstalled,
                    SystemAppActionResult.Unchanged,
                    SystemAppActionResult.Blocked(SystemAppVerdict(SystemAppRisk.BLOCKED, role = null)),
                )) {
                    assertWritten(systemAppNoticeText(arabic, SystemAppAction(systemApp(name), restore, result)), name)
                }
            }
        }

        Locale.setDefault(Locale.US)
        val done = SystemAppAction(systemApp("Gmail 2"), restore = false, result = SystemAppActionResult.Done)
        assertThat(systemAppNoticeText(app, done)).isEqualTo("Gmail 2 removed.")
    }

    // "policy0 · governor schedutil · driver intel_pstate". The line was laid out as a name is,
    // by its first letter: beginning with "policy0", the whole Arabic line read from the left,
    // so that a reader from the right met the driver first.
    @Test
    fun `the line under a group of cores reads from the right with the kernel's names as written`() {
        Locale.setDefault(arabicLocale)
        val line = clusterTechnicalText(arabic, "policy0", "schedutil", "18800000.cpufreq-hw")

        // The text as it was, laid out by its first letter: the first name stands at the far left.
        val bare = arabic.getString(R.string.kernel_cluster_technical, "policy0", "schedutil", "18800000.cpufreq-hw")
        assertThat(VisualOrder.byContent(bare)).startsWith("policy0")
        // As a line of the page, the first name said stands furthest right.
        val seen = VisualOrder.rightToLeft(line)
        val places = listOf("policy0", "schedutil", "18800000.cpufreq-hw").map { seen.indexOf(it) }
        assertWithMessage(seen).that(places).doesNotContain(-1)
        assertWithMessage(seen).that(places).isInStrictOrder(reverseOrder<Int>())

        Locale.setDefault(Locale.US)
        assertThat(clusterTechnicalText(app, "policy0", "schedutil", null)).isEqualTo("policy0 · governor schedutil · driver –")
    }

    // "3 times · 18800000.usb, wlan_wake": after the Arabic count a name that begins with a
    // number was taken apart at its first full stop ("usb.18800000").
    @Test
    fun `the technical names under what kept the phone awake stay whole`() {
        Locale.setDefault(arabicLocale)
        val times = arabic.resources.getQuantityString(R.plurals.battery_usage_times, 3, 3)

        assertThat(VisualOrder.rightToLeft("$times · 18800000.usb, wlan_wake")).doesNotContain("18800000.usb")
        assertWritten(wakeDetailText(times, listOf("18800000.usb", "wlan_wake", "third")), "18800000.usb", "wlan_wake")

        Locale.setDefault(Locale.US)
        assertThat(wakeDetailText("3 times", listOf("a.b", "c", "d"))).isEqualTo("3 times · a.b, c")
        assertThat(wakeDetailText(null, emptyList())).isEmpty()
    }

    @Test
    fun `an operation Android names itself is a piece of its own in a list`() {
        Locale.setDefault(arabicLocale)
        val unknown = opNameText(arabic, "android:made_up_2")

        assertThat(unknown.first().code).isEqualTo(0x2068)
        assertThat(unknown.last().code).isEqualTo(0x2069)

        Locale.setDefault(Locale.US)
        assertThat(opNameText(app, "android:made_up_2")).isEqualTo("android:made_up_2")
    }

    // Apps left out of battery saving, USB devices: names listed after each other, alone or
    // inside "… + 12 system apps". After an Arabic name "7 Zip" lost its number to that name.
    @Test
    fun `names listed after each other stay whole`() {
        val arabicName = arabic.getString(R.string.camera_lens_main)
        val bare = listOf(arabicName, "7 Zip", "123 456").joinToString(", ")
        assertThat(VisualOrder.byContent(bare)).doesNotContain("7 Zip")

        val listed = ExtraStatsLogic.namesText(listOf(arabicName, "7 Zip", "123 456"), arabicLocale)
        assertWritten(listed, "7 Zip", "123 456")
        val system = arabic.resources.getQuantityString(R.plurals.exempt_system_apps, 12, 12)
        assertWritten(arabic.getString(R.string.exempt_user_and_system, listed, system), "7 Zip", "123 456")

        assertThat(ExtraStatsLogic.namesText(listOf("Gmail", "7 Zip"), Locale.US)).isEqualTo("Gmail, 7 Zip")
    }

    // A USB device gives a maker's and a product's name: two texts, each in any script. Joined
    // first and kept as one piece, a maker written from the right set the direction for both
    // and the product "7 Pro" was drawn "Pro 7".
    @Test
    fun `a USB device's maker and product are each kept whole`() {
        val maker = arabic.getString(R.string.camera_lens_main)
        assertThat(VisualOrder.rightToLeft(Bidi.outside("$maker 7 Pro", arabicLocale))).doesNotContain("7 Pro")

        val onArabic = ExtraStatsLogic.usbDeviceName(maker, "7 Pro", 0x046d, 0xc52b, arabicLocale)
        assertWritten(onArabic, "7 Pro")
        assertThat(VisualOrder.byContent(onArabic)).contains("7 Pro")
        val onEnglish = ExtraStatsLogic.usbDeviceName(maker, "7 Pro", 0x046d, 0xc52b, Locale.US)
        assertThat(VisualOrder.leftToRight(onEnglish)).contains("7 Pro")
        // Several devices after each other: every part of every name is whole.
        val listed = ExtraStatsLogic.usbDevicesText(listOf(onArabic, ExtraStatsLogic.usbDeviceName(null, "3 Button Mouse", 1, 2, arabicLocale)))
        assertWritten(listed, "7 Pro", "3 Button Mouse")

        // What repeats is told from the names as the device gave them, and nothing is added
        // where neither the language nor the names read from the right.
        assertThat(ExtraStatsLogic.usbDeviceName("SanDisk", "SanDisk Ultra", 1, 2, Locale.US)).isEqualTo("SanDisk Ultra")
        assertThat(ExtraStatsLogic.usbDeviceName("Logitech", "USB Receiver", 1, 2, Locale.US)).isEqualTo("Logitech USB Receiver")
        val repeated = ExtraStatsLogic.usbDeviceName("SanDisk", "SanDisk Ultra", 1, 2, arabicLocale)
        assertThat(repeated.filterNot { it.code in 0x2066..0x2069 }).isEqualTo("SanDisk Ultra")
    }

    // The report's heading names the phone by its maker and its model: two texts as well.
    @Test
    fun `the report's phone name keeps its maker and its model each whole`() {
        val maker = arabic.getString(R.string.camera_lens_main)

        for (locale in listOf(arabicLocale, Locale.US)) {
            val name = reportDeviceName(maker, "7 Pro", locale)
            // The report puts the name into an element that reads by its own first letter.
            assertWithMessage(locale.toString()).that(VisualOrder.byContent(name)).contains("7 Pro")
        }
        assertThat(reportDeviceName("Google", "Pixel 8a", Locale.US)).isEqualTo("Google Pixel 8a")
        assertThat(reportDeviceName("samsung", "Samsung Galaxy", Locale.US)).isEqualTo("Samsung Galaxy")
        assertThat(reportDeviceName(null, "Pixel 8a", Locale.US)).isEqualTo("Pixel 8a")
        assertThat(reportDeviceName("Google", null, Locale.US)).isEqualTo("Google")
        assertThat(reportDeviceName(null, " ", Locale.US)).isEmpty()
    }

    // "1.2.3 (45)" has no letter in it: on an Arabic page it was drawn "(45) 1.2.3".
    @Test
    fun `an app's version reads as its maker wrote it`() {
        // Left alone the build number comes first (the drawn brackets are mirrored as well).
        assertThat(VisualOrder.rightToLeft("1.2.3 (45)")).endsWith("1.2.3")

        Locale.setDefault(arabicLocale)
        assertThat(VisualOrder.rightToLeft(appVersionText("1.2.3", 45L))).isEqualTo("1.2.3 (45)")
        assertThat(VisualOrder.rightToLeft(appVersionText(null, 45L))).isEqualTo("45")

        Locale.setDefault(Locale.US)
        assertThat(appVersionText("1.2.3", 45L)).isEqualTo("1.2.3 (45)")
    }

    // The app's own version on the Settings page, after the Arabic word for it: the build
    // number in brackets was drawn before the version ("(27) 1.13.0"), while an app's version
    // on its own line reads "1.13.0 (27)".
    @Test
    fun `the app's own version line keeps the version before its build number`() {
        assertWritten(arabic.getString(R.string.app_version_line, "1.13.0", 27), "1.13.0 (27)")
        assertThat(app.getString(R.string.app_version_line, "1.13.0", 27)).isEqualTo("Version 1.13.0 (27)")
    }

    // The widget shows the network's name alone when its band is not known.
    @Test
    fun `the widget's network name alone keeps its written order`() {
        Locale.setDefault(arabicLocale)
        assertThat(VisualOrder.rightToLeft("123 456")).isEqualTo("456 123")
        assertThat(VisualOrder.rightToLeft(wifiTitleText("123 456", UNAVAILABLE_TEXT))).isEqualTo("123 456")

        Locale.setDefault(Locale.US)
        assertThat(wifiTitleText("Home 5", UNAVAILABLE_TEXT)).isEqualTo("Home 5")
    }

    // The package manager answers in its own words, in English and often with a code in them.
    @Test
    fun `the package manager's own words stay as it wrote them`() {
        Locale.setDefault(arabicLocale)
        val reply = "Failure [DELETE_FAILED_USER_RESTRICTED] 0x12 / 3"
        val failed = SystemAppAction(systemApp("123 456"), restore = false, result = SystemAppActionResult.Failed(reply))

        assertWritten(systemAppNoticeText(arabic, failed), "123 456", reply)
    }
}
