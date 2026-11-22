package org.jarsi.devicewatch

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.lang.ref.WeakReference

/**
 * A counted text ("1 time", "3 times") in the form its own language asks for.
 *
 * Android picks the form by the first language of the phone, and the texts are not always in
 * that language ([textLocale]). A phone in Japanese or Tibetan gets the English texts, and
 * as neither language has a singular it read "1 times"; Russian counts 21 like 1, and a
 * Russian phone read "21 time" and called 21 wake-ups "once"; with French before Arabic on
 * the phone's list the Arabic texts were counted the French way. The form is therefore asked
 * of resources whose language is the texts' own. The number itself is still written as the
 * phone writes numbers, as everywhere else ([LatinDigits]).
 *
 * Every counted text in the app comes through here or through [quantityTextResource].
 */
fun quantityText(context: Context, @PluralsRes id: Int, count: Int, vararg args: Any?): String =
    String.format(context.resources.configuration.locales[0], quantityPattern(context, id, count), *args)

/** [quantityText] for a composable; drawn anew when the phone's or the app's language changes. */
@Composable
@ReadOnlyComposable
fun quantityTextResource(@PluralsRes id: Int, count: Int, vararg args: Any?): String {
    LocalConfiguration.current // read so that a change of language is a change here
    return quantityText(LocalContext.current, id, count, *args)
}

/** The text for [count] with its arguments still to be written in. */
internal fun quantityPattern(context: Context, @PluralsRes id: Int, count: Int): String =
    TextsResources.of(context).getQuantityText(id, count).toString()

/**
 * Resources that count the way the texts' language does: [Context.getResources] itself where
 * that is the phone's first language, which is nearly everywhere; otherwise resources made
 * for the texts' language alone. They hold the same texts, since the texts' language is by
 * its definition the one Android settled on.
 */
private object TextsResources {

    // The last ones made, for the page that asks for a row's worth of counted texts at once.
    private var madeFor: WeakReference<Resources>? = null
    private var madeLocales: LocaleList? = null
    private var made: Resources? = null

    fun of(context: Context): Resources {
        val base = context.resources
        val locales = base.configuration.locales
        val texts = textLocale(context)
        if (locales[0].language == texts.language) return base
        synchronized(this) {
            made?.takeIf { madeFor?.get() === base && madeLocales == locales }?.let { return it }
            val own = context.createConfigurationContext(Configuration(base.configuration).apply { setLocale(texts) }).resources
            madeFor = WeakReference(base)
            madeLocales = locales
            made = own
            return own
        }
    }
}
