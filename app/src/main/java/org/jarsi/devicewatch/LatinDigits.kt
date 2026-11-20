package org.jarsi.devicewatch

import android.annotation.SuppressLint
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.icu.text.NumberingSystem
import android.icu.util.ULocale
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
        if (writesLatinDigits(locale)) return locale
        return try {
            Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword(NUMBERING_KEY, LATIN).build()
        } catch (_: IllformedLocaleException) {
            locale // a locale the builder cannot take is used as it is
        }
    }

    /**
     * Whether [locale]'s numbers come out as 0 to 9 without being told to. Two things are
     * asked, because a number is written by two kinds of formatter: the numbering system
     * Android formats the language's numbers with, and the zero of the language's number
     * symbols, which the older formatters go by.
     *
     * Only the zero was asked before. It is one UTF-16 unit, and the digits of Chakma and
     * Adlam take two: for them the answer was a plain "0" standing in for a zero that does
     * not fit, the language was taken for one that writes 0 to 9, and its dates and times
     * came out in its own digits.
     */
    private fun writesLatinDigits(locale: Locale): Boolean =
        DecimalFormatSymbols.getInstance(locale).zeroDigit == '0' && numberingSystemIsLatin(locale)

    /** True where there is no Android to ask: the unit tests that run on the plain JVM. */
    private fun numberingSystemIsLatin(locale: Locale): Boolean = try {
        NumberingSystem.getInstance(ULocale.forLocale(locale)).name == LATIN
    } catch (_: RuntimeException) {
        true
    }

    /**
     * [text], a date or a time written for [locale], with every digit in it as 0 to 9.
     *
     * The numbering system sees to the numbers a format writes, but a language's own names can
     * hold digits too. Tibetan and Dzongkha name their months by number, in their own digits,
     * and the tenth month stood beside a day written "10". A numbering system the user chose
     * stands here as everywhere.
     */
    fun text(text: String, locale: Locale): String {
        val chosen = locale.getUnicodeLocaleType(NUMBERING_KEY)
        if (chosen != null && chosen != LATIN) return text
        if (text.none { it.code > LAST_ASCII }) return text
        val plain = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val point = text.codePointAt(index)
            if (point > LAST_ASCII && Character.isDigit(point)) {
                plain.append('0' + Character.digit(point, 10))
            } else {
                plain.appendCodePoint(point)
            }
            index += Character.charCount(point)
        }
        return plain.toString()
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
    private const val LAST_ASCII = 0x7F
}

/**
 * The language to show as the device's own: the phone's, written as the user knows it
 * ("ar-EG", never the app's "ar-EG-u-nu-latn").
 */
fun deviceLanguageTag(context: Context): String = phoneLocale(context).toLanguageTag()

/**
 * The phone's own language, which is not the app's when the user has chosen another for the
 * app on Android's per-app language page (Android 13 and later). The Device tab names it.
 *
 * Android's own resources are no source for it from Android 13 on: they follow the language
 * chosen for the app. Before that they are the phone's language, without the numbering
 * system the app adds to the process default.
 */
fun phoneLocale(context: Context): Locale {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val system = context.getSystemService(LocaleManager::class.java)?.systemLocales
        if (system != null && !system.isEmpty) return system[0]
    }
    return Resources.getSystem().configuration.locales[0]
}

/**
 * The language of the texts [context] gives, which is not always the phone's nor the
 * process's default: a phone set to a language the app is not written in (Hebrew, say) gets
 * the English texts. Each language's resources say themselves which they are
 * (`text_language.xml`), so this is whatever Android chose, by Android's own rules.
 *
 * It decides everything that depends on how the app's texts read: the direction of the HTML
 * report, and the direction a line is handed over in to whatever else draws it, the launcher
 * for a widget and the system for a tile or a notification ([textInOwnDirection]). The
 * phone's locale still formats the numbers.
 */
fun textLocale(context: Context): Locale = Locale.forLanguageTag(context.getString(R.string.text_language))

/**
 * [text] as it has to be handed to the system's own interface, which draws the app's tiles
 * and notifications: every line one piece in the direction of the language the app's texts
 * are in, always ([textInOwnDirection]).
 *
 * The lines were first marked only where the phone's language read the other way from the
 * texts'. But the shade does not lay a line out by the phone's language alone: an unmarked
 * line takes its direction there from its first letter, and the Arabic line of figures begins
 * with "RAM". On an Arabic phone with the app in Arabic it was laid out from the left, its
 * first figure at the left end.
 */
fun textForPhone(context: Context, text: String): String = textInOwnDirection(text, textLocale(context))

/**
 * [text] as another process has to be handed it to draw (a widget's launcher, the system's
 * shade and tiles): every line one piece in the direction of the language the app's texts
 * are in ([texts]), always.
 *
 * That process's own language cannot be asked for. It is usually the phone's, but a launcher
 * can be given a language of its own on Android's per-app language page (Android 13 and
 * later), and on a phone with several languages it takes the first one it is itself written
 * in. A widget's text view lays every text out in the direction of that language, whatever
 * the text holds, and a notification's by the text's first letter; a line that carries its
 * own direction reads the same under any of them.
 */
fun textInOwnDirection(text: String, texts: Locale): String =
    // Line by line: a mark's reach ends where its line does.
    text.split('\n').joinToString("\n") { if (it.isEmpty()) it else Bidi.inOwnDirection(it, texts) }

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
