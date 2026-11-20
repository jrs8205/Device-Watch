package org.jarsi.devicewatch

import android.icu.util.ULocale
import java.util.Locale

/**
 * Figures in a language written from the right.
 *
 * A figure made of numbers and signs ("12 → 15", "5.1 / 7.6 GB", "+12 %") has no direction
 * of its own. Inside a right-to-left text its parts are laid out from the right: the later
 * number comes first and the arrow points at the wrong one. [ltr] keeps such a figure as one
 * left-to-right piece.
 */
object Bidi {

    /**
     * Whether the language is written from the right. Android's own answer, the one it lays
     * the interface out by: it knows a language by its script, also where the language's name
     * is not written in it (Dhivehi) or begins beyond the basic plane (Fulah in Adlam).
     */
    fun isRtl(locale: Locale): Boolean = androidSays(locale) ?: nameReadsFromTheRight(locale)

    /** Null where there is no Android to ask: the unit tests that run on the plain JVM. */
    private fun androidSays(locale: Locale): Boolean? = try {
        ULocale.forLocale(locale).isRightToLeft
    } catch (_: RuntimeException) {
        null
    }

    /** Without Android: by the first letter of the language's own name, read as a whole letter. */
    private fun nameReadsFromTheRight(locale: Locale): Boolean {
        val name = locale.getDisplayName(locale)
        return name.isNotEmpty() && isRtlLetter(name.codePointAt(0))
    }

    private fun isRtlLetter(codePoint: Int): Boolean = when (Character.getDirectionality(codePoint)) {
        Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> true
        else -> false
    }

    /**
     * [text] as one left-to-right piece when [locale] is written from the right; unchanged
     * otherwise, so no other language gets the two invisible marks in its texts.
     */
    fun ltr(text: String, locale: Locale = Locale.getDefault()): String =
        if (isRtl(locale)) "$LEFT_TO_RIGHT_ISOLATE$text$POP_ISOLATE" else text

    /**
     * [text] kept in its own direction, whatever stands beside it, when [locale] is written
     * from the right. For a value put into a left-to-right figure: an Arabic unit inside
     * "55 min → 60 min" would otherwise pull the arrow and the next number to its side.
     */
    fun isolate(text: String, locale: Locale = Locale.getDefault()): String =
        if (isRtl(locale)) "$FIRST_STRONG_ISOLATE$text$POP_ISOLATE" else text

    /**
     * [text] as one piece in the direction [locale] is written in, wherever it is drawn and
     * whatever language reads there. For a line another process draws for the app: a widget's
     * in the launcher, a tile's and a notification's in the system's own interface. Neither
     * the language of that process nor the rule its text view picks a direction by is the
     * app's to know, so the line brings its direction with it.
     */
    fun inOwnDirection(text: String, locale: Locale): String {
        val open = if (isRtl(locale)) RIGHT_TO_LEFT_ISOLATE else LEFT_TO_RIGHT_ISOLATE
        return "$open$text$POP_ISOLATE"
    }

    /** Whether [text] holds a letter of a script written from the right. */
    fun hasRtl(text: String): Boolean = text.codePoints().anyMatch(::isRtlLetter)

    /**
     * A text the app did not write (an app's name, a calendar event's title, the kernel's
     * name for a thing, the package manager's own words), put into one of the app's sentences
     * or lists, or handed to something the system draws. It keeps its own direction and its
     * written order: left alone in an Arabic sentence, an app called "123 456" was asked
     * about as "456 123" and a name that ends in a number ran into the sentence's punctuation.
     * Marked where the app's language or the text itself is written from the right; unchanged
     * otherwise, so a Latin name in a left-to-right language gets no invisible marks.
     */
    fun outside(text: String, locale: Locale = Locale.getDefault()): String =
        if (text.isNotEmpty() && (isRtl(locale) || hasRtl(text))) "$FIRST_STRONG_ISOLATE$text$POP_ISOLATE" else text

    /**
     * A name the app did not write (a Wi-Fi network, an operator) and a reading after it:
     * "Home · 2.4 GHz". The name is the user's, in any script: one in Arabic turns the whole
     * line round whatever the app's language is, and the reading's unit left its number
     * ("GHz 2.4"). Where the name or the app's language is written from the right, the name
     * keeps its own direction and the reading is one left-to-right piece; otherwise the line
     * is left as it is.
     */
    fun beside(name: String, reading: String, locale: Locale = Locale.getDefault()): String =
        if (isRtl(locale) || hasRtl(name)) {
            "$FIRST_STRONG_ISOLATE$name$POP_ISOLATE · $LEFT_TO_RIGHT_ISOLATE$reading$POP_ISOLATE"
        } else {
            "$name · $reading"
        }

    // Written as numbers: the marks themselves cannot be seen in the source.
    private val LEFT_TO_RIGHT_ISOLATE = Char(0x2066)
    private val RIGHT_TO_LEFT_ISOLATE = Char(0x2067)
    private val FIRST_STRONG_ISOLATE = Char(0x2068)
    private val POP_ISOLATE = Char(0x2069)
}
