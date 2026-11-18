package org.jarsi.devicewatch

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

    /** Whether the language is written from the right, going by the script of its own name. */
    fun isRtl(locale: Locale): Boolean {
        val first = locale.getDisplayName(locale).firstOrNull() ?: return false
        return when (Character.getDirectionality(first)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> true
            else -> false
        }
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

    // Written as numbers: the marks themselves cannot be seen in the source.
    private val LEFT_TO_RIGHT_ISOLATE = Char(0x2066)
    private val FIRST_STRONG_ISOLATE = Char(0x2068)
    private val POP_ISOLATE = Char(0x2069)
}
