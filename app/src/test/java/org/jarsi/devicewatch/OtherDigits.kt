package org.jarsi.devicewatch

/**
 * Whether the text holds a digit other than 0 to 9. Asked of whole characters, not of UTF-16
 * units: the digits of Chakma and Adlam take two units each, and neither half is a digit by
 * itself.
 */
internal fun String.hasOtherDigits(): Boolean =
    codePoints().anyMatch { Character.isDigit(it) && it !in '0'.code..'9'.code }

/** The character with this code, as a text: one UTF-16 unit or two. */
internal fun codePoint(code: Int): String = String(Character.toChars(code))
