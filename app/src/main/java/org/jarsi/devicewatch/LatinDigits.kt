package org.jarsi.devicewatch

import android.annotation.SuppressLint
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.text.DecimalFormatSymbols
import java.util.IllformedLocaleException
import java.util.Locale

/**
 * Numbers are written with the digits 0 to 9 in every language.
 *
 * A number reaches the screen by several routes: a text's own `%d`, `String.format`, a date
 * format, a string template. Left alone, a phone set to Arabic (Egypt) or Persian prints
 * the first three with its own digits and the rest with 0 to 9, on the same screen. One
 * rule for the locale, used wherever texts are formatted, keeps every route the same.
 */
object LatinDigits {

    /**
     * The locale whose numbers print as 0 to 9: [locale] itself when it does so already, or
     * when it names a numbering system (the user chose one in Android's settings, and that
     * choice stands); otherwise the same locale with the Latin numbering system added.
     */
    fun locale(locale: Locale): Locale {
        if (locale.getUnicodeLocaleType(NUMBERING_KEY) != null) return locale
        if (DecimalFormatSymbols.getInstance(locale).zeroDigit == '0') return locale
        return try {
            Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword(NUMBERING_KEY, LATIN).build()
        } catch (_: IllformedLocaleException) {
            locale // a locale the builder cannot take is used as it is
        }
    }

    /** [base] itself when its texts print 0 to 9 already, otherwise a context over it that does. */
    fun context(base: Context): Context {
        val override = overrideFor(base.resources.configuration.locales) ?: return base
        return base.createConfigurationContext(override)
    }

    /**
     * The process default, which `String.format` without a locale and the date formats use.
     * Android sets it again whenever the phone's or the app's language changes.
     */
    fun applyToDefault() {
        val display = locale(Locale.getDefault(Locale.Category.DISPLAY))
        val format = locale(Locale.getDefault(Locale.Category.FORMAT))
        val default = Locale.getDefault()
        val fixed = locale(default)
        // Setting the default resets both categories to it, so they are put back after it.
        if (fixed !== default) Locale.setDefault(fixed)
        if (Locale.getDefault(Locale.Category.DISPLAY) != display) Locale.setDefault(Locale.Category.DISPLAY, display)
        if (Locale.getDefault(Locale.Category.FORMAT) != format) Locale.setDefault(Locale.Category.FORMAT, format)
    }

    /**
     * The change to lay over a configuration with these languages, or null when none is
     * needed. Only the languages are set: everything else (size, night mode, font scale)
     * keeps following the context underneath.
     */
    // The language stays the one Android chose; only its numbering system is named. The
    // check is about app bundles that leave languages out, and the app ships as one APK.
    @SuppressLint("AppBundleLocaleChanges")
    internal fun overrideFor(locales: LocaleList): Configuration? {
        val fixed = Array(locales.size()) { locale(locales[it]) }
        if (fixed.indices.all { fixed[it] === locales[it] }) return null
        return Configuration().apply { setLocales(LocaleList(*fixed)) }
    }

    private const val NUMBERING_KEY = "nu"
    private const val LATIN = "latn"
}

/**
 * The language to show as the device's own. Android's own resources are never given the
 * numbering system the app adds to the process default, so the tag is the one the user
 * knows ("ar-EG"), not "ar-EG-u-nu-latn".
 */
fun deviceLanguageTag(): String = Resources.getSystem().configuration.locales[0].toLanguageTag()

/**
 * The phone's own language, which is not the app's when the user has chosen another for the
 * app on Android's per-app language page (Android 13 and later). The system draws the
 * app's tiles and notifications in the direction of this one (see [Bidi.forHost]).
 */
fun phoneLocale(context: Context): Locale {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val system = context.getSystemService(LocaleManager::class.java)?.systemLocales
        if (system != null && !system.isEmpty) return system[0]
    }
    return Locale.getDefault()
}

/**
 * Resources that print 0 to 9, for a context that outlives a change of language: the
 * application and the services. An activity is made anew when the language changes and
 * takes [LatinDigits.context] once; these are asked every time and follow [base]'s
 * language as it changes.
 *
 * [base] is the context Android attached, never the wrapper asking: the wrapper's own
 * resources are these.
 */
class LatinDigitResources(private val base: () -> Context?) {

    private class Built(val locales: LocaleList, val resources: Resources)

    @Volatile
    private var built: Built? = null

    /**
     * Null before Android has attached a context.
     *
     * No lock is held here. When the language changes, Android asks the application for its
     * resources while holding its own resources lock, the one making resources needs on
     * Android 8 to 11: a lock of ours around the call into Android could leave the main
     * thread and a background reader waiting for each other. Two readers that both find
     * the language changed each make the resources, and either result will do.
     */
    fun get(): Resources? {
        val context = base() ?: return null
        val plain = context.resources
        val locales = plain.configuration.locales
        built?.let { if (it.locales == locales) return it.resources }
        val resources =
            LatinDigits.overrideFor(locales)?.let { context.createConfigurationContext(it).resources } ?: plain
        built = Built(locales, resources)
        return resources
    }
}
