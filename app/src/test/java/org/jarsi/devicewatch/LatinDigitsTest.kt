package org.jarsi.devicewatch

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.jarsi.devicewatch.overlay.FloatingFigureTileService
import org.jarsi.devicewatch.quick.BatteryTileService
import org.jarsi.devicewatch.system.MonitorDreamService
import org.jarsi.devicewatch.system.SystemMonitorService
import org.jarsi.devicewatch.widget.SingleMetricConfigActivity
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Numbers are written with the digits 0 to 9 in every language. Left alone, a phone set to
 * Arabic (Egypt) prints some of the app's numbers as Arabic-Indic digits and the rest as
 * 0 to 9, on the same screen, depending on which formatting call produced them.
 */
@RunWith(RobolectricTestRunner::class)
class LatinDigitsTest {

    private val egypt: Locale = Locale.forLanguageTag("ar-EG")
    private val defaultsBefore = Triple(
        Locale.getDefault(),
        Locale.getDefault(Locale.Category.DISPLAY),
        Locale.getDefault(Locale.Category.FORMAT),
    )

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    /** The context Android gave the application, before the application's own say on it. */
    private val plainContext: Context get() = (app as ContextWrapper).baseContext

    /** A context in this language and nothing else: what Android hands a component on such a phone. */
    private fun contextIn(locale: Locale): Context =
        plainContext.createConfigurationContext(Configuration().apply { setLocale(locale) })

    @After
    fun restoreDefaults() {
        Locale.setDefault(defaultsBefore.first)
        Locale.setDefault(Locale.Category.DISPLAY, defaultsBefore.second)
        Locale.setDefault(Locale.Category.FORMAT, defaultsBefore.third)
    }

    private fun String.hasOtherDigits(): Boolean =
        any { it.code in 0x0660..0x0669 || it.code in 0x06F0..0x06F9 }

    // Were these two false, the tests below would pass without the code they test.
    @Test
    fun `this runtime prints Egypt's numbers and times with Arabic-Indic digits when left alone`() {
        assertThat(String.format(egypt, "%d %.1f", 45, 3.8).hasOtherDigits()).isTrue()
        assertThat(SimpleDateFormat("HH:mm", egypt).format(Date(0)).hasOtherDigits()).isTrue()
    }

    @Test
    fun `an Arabic context prints a text's number with Arabic-Indic digits when left alone`() {
        assertThat(contextIn(egypt).getString(R.string.dream_percent_spaced, 45).hasOtherDigits()).isTrue()
    }

    @Test
    fun `an Arabic locale gets the Latin numbering system and keeps its language and country`() {
        val fixed = LatinDigits.locale(egypt)

        assertThat(fixed.getUnicodeLocaleType("nu")).isEqualTo("latn")
        assertThat(fixed.language).isEqualTo("ar")
        assertThat(fixed.country).isEqualTo("EG")
    }

    @Test
    fun `numbers and times formatted with it use the digits 0 to 9`() {
        val fixed = LatinDigits.locale(egypt)

        assertThat(String.format(fixed, "%d %.1f", 45, 3.8).hasOtherDigits()).isFalse()
        assertThat(String.format(fixed, "%d", 45)).isEqualTo("45")
        assertThat(SimpleDateFormat("HH:mm", fixed).format(Date(0)).hasOtherDigits()).isFalse()
    }

    @Test
    fun `a numbering system the user chose in Android's settings is left alone`() {
        val chosen = Locale.forLanguageTag("ar-EG-u-nu-arab")

        assertThat(LatinDigits.locale(chosen)).isSameInstanceAs(chosen)
    }

    @Test
    fun `a locale that prints 0 to 9 already comes back as it is`() {
        for (locale in listOf(Locale.US, Locale.forLanguageTag("fi-FI"), Locale.forLanguageTag("zh-CN"))) {
            assertWithMessage(locale.toLanguageTag()).that(LatinDigits.locale(locale)).isSameInstanceAs(locale)
        }
    }

    @Test
    fun `a text with a number comes out with the digits 0 to 9 in Arabic`() {
        val arabic = LatinDigits.context(contextIn(egypt))

        assertThat(arabic.getString(R.string.dream_percent_spaced, 45)).isEqualTo("45%")
        assertThat(arabic.resources.configuration.locales[0].language).isEqualTo("ar")
    }

    @Test
    fun `a context that prints 0 to 9 already is used as it is`() {
        val finnish = contextIn(Locale.forLanguageTag("fi-FI"))

        assertThat(LatinDigits.context(finnish)).isSameInstanceAs(finnish)
    }

    @Test
    fun `the process default prints 0 to 9 in every category`() {
        Locale.setDefault(egypt)

        LatinDigits.applyToDefault()

        assertThat(Locale.getDefault().getUnicodeLocaleType("nu")).isEqualTo("latn")
        assertThat(Locale.getDefault(Locale.Category.DISPLAY).getUnicodeLocaleType("nu")).isEqualTo("latn")
        assertThat(Locale.getDefault(Locale.Category.FORMAT).getUnicodeLocaleType("nu")).isEqualTo("latn")
        assertThat("%d %.1f".format(45, 3.8).hasOtherDigits()).isFalse()
        assertThat(Locale.getDefault().language).isEqualTo("ar")
    }

    @Test
    fun `a format category set apart from the default keeps its own language`() {
        Locale.setDefault(Locale.US)
        Locale.setDefault(Locale.Category.FORMAT, egypt)

        LatinDigits.applyToDefault()

        assertThat(Locale.getDefault()).isEqualTo(Locale.US)
        assertThat(Locale.getDefault(Locale.Category.FORMAT).language).isEqualTo("ar")
        assertThat("%d".format(45)).isEqualTo("45")
    }

    @Test
    fun `long-lived resources follow the language when it changes`() {
        var base = contextIn(egypt)
        val resources = LatinDigitResources { base }
        assertThat(resources.get()!!.getString(R.string.dream_percent_spaced, 45)).isEqualTo("45%")
        assertThat(resources.get()!!.configuration.locales[0].language).isEqualTo("ar")

        base = contextIn(Locale.forLanguageTag("fi-FI"))

        assertThat(resources.get()!!.configuration.locales[0].language).isEqualTo("fi")
        assertThat(resources.get()!!.getString(R.string.app_name)).isEqualTo("Laitevahti")
    }

    @Test
    fun `long-lived resources are the same object until the language changes`() {
        val base = contextIn(egypt)
        val resources = LatinDigitResources { base }

        assertThat(resources.get()).isSameInstanceAs(resources.get())
    }

    @Test
    fun `there are no resources before Android has attached a context`() {
        assertThat(LatinDigitResources { null }.get()).isNull()
    }

    @Test
    @Config(qualifiers = "ar-rEG")
    fun `the application sets the process default as it starts`() {
        assertThat(Locale.getDefault().language).isEqualTo("ar")
        assertThat(Locale.getDefault().getUnicodeLocaleType("nu")).isEqualTo("latn")
        assertThat("%d".format(45)).isEqualTo("45")
    }

    @Test
    fun `the application sets the process default again when the language changes`() {
        Locale.setDefault(egypt) // as Android does just before it tells the application

        app.onConfigurationChanged(Configuration(app.resources.configuration))

        assertThat(Locale.getDefault().getUnicodeLocaleType("nu")).isEqualTo("latn")
    }

    /**
     * What Android does when the phone's or the app's language changes: it updates the
     * context underneath the application. Robolectric's own qualifiers will not do here: it
     * writes them straight into whatever resources the application returns, over the very
     * resources under test.
     */
    @Suppress("DEPRECATION") // the test stands in for the system, which updates in place
    private fun phoneLanguageBecomes(locale: Locale) {
        val plain = plainContext.resources
        plain.updateConfiguration(Configuration(plain.configuration).apply { setLocale(locale) }, plain.displayMetrics)
    }

    @Test
    fun `the application's texts follow the language and print 0 to 9`() {
        phoneLanguageBecomes(egypt)
        assertThat(plainContext.getString(R.string.dream_percent_spaced, 45).hasOtherDigits()).isTrue()

        assertThat(app.getString(R.string.dream_percent_spaced, 45)).isEqualTo("45%")

        phoneLanguageBecomes(Locale.forLanguageTag("fi-FI"))

        assertThat(app.getString(R.string.app_name)).isEqualTo("Laitevahti")
    }

    @Test
    fun `every screen and every service that shows text prints 0 to 9`() {
        phoneLanguageBecomes(egypt)
        assertThat(plainContext.getString(R.string.dream_percent_spaced, 45).hasOtherDigits()).isTrue()

        val components: Map<String, Context> = mapOf(
            "MainActivity" to Robolectric.buildActivity(MainActivity::class.java).get(),
            "SingleMetricConfigActivity" to Robolectric.buildActivity(SingleMetricConfigActivity::class.java).get(),
            "SystemMonitorService" to Robolectric.buildService(SystemMonitorService::class.java).get(),
            "MonitorDreamService" to Robolectric.buildService(MonitorDreamService::class.java).get(),
            "BatteryTileService" to Robolectric.buildService(BatteryTileService::class.java).get(),
            "FloatingFigureTileService" to Robolectric.buildService(FloatingFigureTileService::class.java).get(),
        )

        val texts = components.mapValues { it.value.getString(R.string.dream_percent_spaced, 45) }
        assertThat(texts.filterValues { it != "45%" }).isEmpty()
    }
}
