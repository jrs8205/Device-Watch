package org.jarsi.devicewatch.ui.theme

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import org.jarsi.devicewatch.Bidi
import org.jarsi.devicewatch.textLocale

/**
 * Which way the app's own screens are laid out: the way their texts read.
 *
 * Android lays a screen out by the phone's language. On a phone set to a language that is
 * written from the right and that the app is not written in (Hebrew, Persian, Urdu) that is
 * from the right, while the texts shown are the English ones: every English sentence stood
 * in a right-to-left paragraph, where "12 h 30 min" reads "h 30 min 12". The language of the
 * texts ([textLocale]) decides instead. For the languages the app is written in, the two
 * agree and nothing changes.
 */
fun textsLayoutDirection(context: Context): LayoutDirection =
    if (Bidi.isRtl(textLocale(context))) LayoutDirection.Rtl else LayoutDirection.Ltr

/** [textsLayoutDirection] for a view the app builds itself (the floating card). */
fun textsViewLayoutDirection(context: Context): Int =
    if (textsLayoutDirection(context) == LayoutDirection.Rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR

/**
 * The root of every screen the app composes: everything inside is laid out, and every text
 * is read, in the direction of the app's own texts. Dialogs and sheets opened from inside
 * take it with them.
 */
@Composable
fun ProvideTextsDirection(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLayoutDirection provides textsLayoutDirection(LocalContext.current),
        content = content,
    )
}
