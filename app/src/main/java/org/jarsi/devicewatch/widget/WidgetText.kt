package org.jarsi.devicewatch.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.currentState
import androidx.glance.text.TextDefaults
import androidx.glance.text.TextStyle
import org.jarsi.devicewatch.textInOwnDirection
import org.jarsi.devicewatch.textLocale
import java.util.Locale

/**
 * How a widget's texts read where they are drawn.
 *
 * A widget is drawn by the launcher, in the launcher's process, and Glance's text view lays
 * every text out in the direction of that process's language (`textDirection="locale"`),
 * whatever the text holds. When the app's texts read the other way (English chosen for the
 * app on an Arabic phone, Arabic on an English one, or the English texts a Hebrew phone gets)
 * each line was laid out against its own direction: "1.0 GB / 2.0 GB" was drawn
 * "GB / 2.0 GB 1.0", a network called "123 456" as "456 123".
 *
 * The launcher's language is not the app's to know. It is usually the phone's, and the texts
 * were first marked only where the phone's language read the other way; but a launcher can be
 * given a language of its own (Android 13 and later), and with the phone and the app in
 * English and the launcher in Arabic the lines came apart again. So every line carries its own
 * direction, that of [texts], the language the app's texts are written in, on every phone.
 * Each widget's root provides one ([LocalWidgetHost]) for all its texts.
 */
internal data class WidgetHost(private val texts: Locale) {

    /**
     * [text] as it has to be handed to the launcher: each line one piece in the direction of
     * the app's own texts, line by line because a mark's reach ends with its line.
     */
    fun shown(text: String): String = textInOwnDirection(text, texts)

    companion object {
        fun of(context: Context): WidgetHost = WidgetHost(texts = textLocale(context))
    }
}

/** One reading of the texts' language for a widget's whole content; a text outside any scope asks for itself. */
internal val LocalWidgetHost = staticCompositionLocalOf<WidgetHost?> { null }

/**
 * The texts' language as it is now, for a widget's root to provide. A widget's composition
 * lives on between updates, and the language chosen for the app can change under it: read
 * once at the root, it stayed what it had been, and a widget switched to Arabic went on in
 * the direction of English. The widget's state is read here so that this is asked again at
 * every update; when the language changes, everything below is drawn again.
 */
@Composable
internal fun currentWidgetHost(): WidgetHost {
    currentState<Preferences>()
    return WidgetHost.of(LocalContext.current)
}

/**
 * Every text of a widget. It stands in for Glance's own `Text` in this package (the widget
 * files do not import that one), so that no text can reach the launcher without [WidgetHost].
 */
@Composable
internal fun Text(
    text: String,
    modifier: GlanceModifier = GlanceModifier,
    style: TextStyle = TextDefaults.defaultTextStyle,
    maxLines: Int = Int.MAX_VALUE,
) {
    val host = LocalWidgetHost.current ?: WidgetHost.of(LocalContext.current)
    androidx.glance.text.Text(text = host.shown(text), modifier = modifier, style = style, maxLines = maxLines)
}
