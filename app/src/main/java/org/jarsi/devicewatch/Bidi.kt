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
     * [text] kept in the direction of the app's language where the system draws it. A tile's
     * lines and a notification's are laid out in the direction of the phone's language
     * ([hostLocale]); with the app in Arabic on an English phone, or the other way round, a
     * line of numbers and units came out with its parts swapped. Unchanged when both
     * languages read the same way, which is nearly always.
     */
    fun forHost(text: String, hostLocale: Locale, locale: Locale = Locale.getDefault()): String {
        val own = isRtl(locale)
        if (own == isRtl(hostLocale)) return text
        val open = if (own) RIGHT_TO_LEFT_ISOLATE else LEFT_TO_RIGHT_ISOLATE
        return "$open$text$POP_ISOLATE"
    }

    /** Whether [text] holds a letter of a script written from the right. */
    fun hasRtl(text: String): Boolean = text.codePoints().anyMatch(::isRtlLetter)

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
