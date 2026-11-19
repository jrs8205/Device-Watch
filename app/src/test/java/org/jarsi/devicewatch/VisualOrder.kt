package org.jarsi.devicewatch

import java.text.Bidi as UnicodeBidi

/**
 * What a reader sees: a text's characters in the order the Unicode rules put them on a line,
 * the invisible marks left out.
 *
 * The tests' own text engine cannot show this: it lays every text out by its first letter,
 * where an Arabic phone lays an ordinary text out from the right. So the order is worked out
 * with the JDK's implementation of the same rules, which knows the isolating marks
 * [org.jarsi.devicewatch.Bidi] writes. The letters of a right-to-left run come out reversed,
 * as they stand on the line; the tests look at the left-to-right pieces, which read as written.
 */
internal object VisualOrder {

    /** An ordinary text in an Arabic layout: the line reads from the right whatever it holds. */
    fun rightToLeft(text: String): String = ordered(text, UnicodeBidi.DIRECTION_RIGHT_TO_LEFT)

    /**
     * A value's text in an Arabic layout: the line reads the way its first letter does, and
     * from the right when it has none (`TextDirection.Content`). A piece kept left to right
     * is not looked at for that letter.
     */
    fun byContent(text: String): String = ordered(text, UnicodeBidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT)

    /** A text the system draws on a phone whose own language reads from the left. */
    fun leftToRight(text: String): String = ordered(text, UnicodeBidi.DIRECTION_LEFT_TO_RIGHT)

    private fun ordered(text: String, direction: Int): String {
        val bidi = UnicodeBidi(text, direction)
        val count = bidi.runCount
        val levels = ByteArray(count) { bidi.getRunLevel(it).toByte() }
        val pieces = Array<Any>(count) {
            val piece = text.substring(bidi.getRunStart(it), bidi.getRunLimit(it))
            if (levels[it].toInt() % 2 == 1) piece.reversed() else piece
        }
        UnicodeBidi.reorderVisually(levels, 0, pieces, 0, count)
        return pieces.joinToString("").filterNot { it.code in 0x2066..0x2069 }
    }
}
