package org.jarsi.devicewatch

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.util.Locale

/**
 * A text another process draws for the app: a widget's in the launcher, a tile's and a
 * notification's in the system's own interface.
 *
 * A widget's text view lays every text out in the direction of the launcher's language,
 * whatever the text holds. With English chosen for the app on an Arabic phone, "1.0 GB /
 * 2.0 GB" was drawn "GB / 2.0 GB 1.0": used and total could not be told apart any more. A
 * notification's view goes by the first letter of the text instead, and the Arabic line of
 * figures begins with "RAM". Neither that process's language nor its rule is the app's to
 * know, so every line handed over carries the direction of the language it is written in
 * ([textInOwnDirection]).
 */
class HostTextTest {

    private val english: Locale = Locale.US
    private val arabic: Locale = Locale.forLanguageTag("ar-EG")

    private val lines = listOf(
        "1.0 GB / 2.0 GB", "123 456", "123 456 · 2.4 GHz", "4 cores · 4.7 GHz", "Uptime 1 h 5 min", "↓ 30 Mb/s", "25.0°C · 5.00V",
    )

    private val word = String(charArrayOf(Char(0x645), Char(0x646), Char(0x632), Char(0x644)))

    private val arabicLines = listOf(
        word + " " + Bidi.ltr("1.0 GB", arabic),
        Bidi.ltr("1.0 GB / 2.0 GB", arabic),
        Bidi.beside(word, "2.4 GHz", arabic),
        "4 $word · " + Bidi.ltr("4.7 GHz", arabic),
    )

    @Test
    fun `left alone, English lines come apart on a launcher that reads from the right`() {
        assertThat(VisualOrder.rightToLeft("1.0 GB / 2.0 GB")).doesNotContain("1.0 GB / 2.0 GB")
        assertThat(VisualOrder.rightToLeft("123 456")).isEqualTo("456 123")
        assertThat(VisualOrder.rightToLeft("123 456 · 2.4 GHz")).doesNotContain("123 456")
    }

    // External review round 6: a launcher can have a language of its own, which the app
    // cannot ask for. A line carries its direction with it and reads the same wherever it is
    // drawn, also where the phone and the app read the same way.
    @Test
    fun `a line keeps its written order whichever way the process that draws it reads`() {
        for (line in lines) {
            val shown = textInOwnDirection(line, texts = english)
            assertWithMessage(line).that(VisualOrder.rightToLeft(shown)).isEqualTo(line)
            assertWithMessage(line).that(VisualOrder.leftToRight(shown)).isEqualTo(line)
            assertWithMessage(line).that(VisualOrder.byContent(shown)).isEqualTo(line)
        }
        for (line in arabicLines) {
            val shown = textInOwnDirection(line, texts = arabic)
            assertWithMessage(line).that(VisualOrder.leftToRight(shown)).isEqualTo(VisualOrder.rightToLeft(line))
            assertWithMessage(line).that(VisualOrder.rightToLeft(shown)).isEqualTo(VisualOrder.rightToLeft(line))
            assertWithMessage(line).that(VisualOrder.byContent(shown)).isEqualTo(VisualOrder.rightToLeft(line))
        }
    }

    // External review round 7: with the phone and the app both in Arabic the line went out
    // unmarked, and the shade laid it out by its first letter. Memory's short name is "RAM"
    // in Arabic too, so the line of figures read from the left, its first figure at the left
    // end, where an Arabic line has its last.
    @Test
    fun `an Arabic line that begins with a Latin name still reads from the right`() {
        val figures = "RAM " + Bidi.ltr("5.1 / 7.6 GB", arabic) + " · " + word + " " + Bidi.ltr("84 / 128 GB", arabic)

        // Left alone and laid out by its first letter: the first figure at the left end.
        assertThat(VisualOrder.byContent(figures)).startsWith("RAM 5.1 / 7.6 GB")

        // Handed over in its own direction: the first figure at the right end, the second on
        // its left, by whichever rule the line is laid out.
        val shown = textInOwnDirection(figures, texts = arabic)
        for (seen in listOf(VisualOrder.byContent(shown), VisualOrder.leftToRight(shown), VisualOrder.rightToLeft(shown))) {
            assertThat(seen).isEqualTo(VisualOrder.rightToLeft(figures))
            assertThat(seen).endsWith("5.1 / 7.6 GB RAM")
            assertThat(seen).startsWith("84 / 128 GB")
        }
    }

    // A mark's reach ends where its line does.
    @Test
    fun `a text is marked line by line, and an empty one not at all`() {
        val shown = textInOwnDirection("RAM 1.0 / 2.0 GB\n\nData 185 MB", texts = english)

        assertThat(shown.split('\n').map { it.length }).containsExactly(18, 0, 13).inOrder()
        assertThat(shown.split('\n').first().first().code).isEqualTo(0x2066)
        assertThat(shown.split('\n').first().last().code).isEqualTo(0x2069)
        assertThat(shown.split('\n').filter { it.isNotEmpty() }.map { VisualOrder.rightToLeft(it) })
            .containsExactly("RAM 1.0 / 2.0 GB", "Data 185 MB").inOrder()
        assertThat(textInOwnDirection("", texts = english)).isEmpty()
        assertThat(textInOwnDirection("", texts = arabic)).isEmpty()
    }
}
