package org.jarsi.devicewatch

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.util.Locale

/**
 * A text the launcher or the system draws for the app, on a phone whose language reads the
 * other way from the app's texts.
 *
 * A widget's text view lays every text out in the direction of the launcher's language,
 * whatever the text holds. With English chosen for the app on an Arabic phone, "1.0 GB /
 * 2.0 GB" was drawn "GB / 2.0 GB 1.0": used and total could not be told apart any more. A
 * line handed over is kept in the direction of the app's own texts ([textForHost]).
 */
class HostTextTest {

    private val english: Locale = Locale.US
    private val arabic: Locale = Locale.forLanguageTag("ar-EG")
    private val hebrew: Locale = Locale.forLanguageTag("he-IL")

    private val lines = listOf(
        "1.0 GB / 2.0 GB", "123 456", "123 456 · 2.4 GHz", "4 cores · 4.7 GHz", "Uptime 1 h 5 min", "↓ 30 Mb/s", "25.0°C · 5.00V",
    )

    @Test
    fun `left alone, English lines come apart on a launcher that reads from the right`() {
        assertThat(VisualOrder.rightToLeft("1.0 GB / 2.0 GB")).doesNotContain("1.0 GB / 2.0 GB")
        assertThat(VisualOrder.rightToLeft("123 456")).isEqualTo("456 123")
        assertThat(VisualOrder.rightToLeft("123 456 · 2.4 GHz")).doesNotContain("123 456")
    }

    @Test
    fun `English texts keep their written order on an Arabic or a Hebrew launcher`() {
        for (host in listOf(arabic, hebrew)) {
            for (line in lines) {
                val seen = VisualOrder.rightToLeft(textForHost(line, texts = english, host = host))
                assertWithMessage("${host.language}: $line").that(seen).isEqualTo(line)
            }
        }
    }

    // The other way round: Arabic chosen for the app on an English phone.
    @Test
    fun `Arabic texts read on an English launcher as they do on an Arabic one`() {
        val word = String(charArrayOf(Char(0x645), Char(0x646), Char(0x632), Char(0x644)))
        val arabicLines = listOf(
            word + " " + Bidi.ltr("1.0 GB", arabic),
            Bidi.ltr("1.0 GB / 2.0 GB", arabic),
            Bidi.beside(word, "2.4 GHz", arabic),
            "4 $word · " + Bidi.ltr("4.7 GHz", arabic),
        )
        for (line in arabicLines) {
            val onEnglish = VisualOrder.leftToRight(textForHost(line, texts = arabic, host = english))
            assertWithMessage(line).that(onEnglish).isEqualTo(VisualOrder.rightToLeft(line))
        }
    }

    @Test
    fun `where the texts and the phone read the same way nothing is added`() {
        for (line in lines) {
            assertThat(textForHost(line, texts = english, host = english)).isEqualTo(line)
            assertThat(textForHost(line, texts = english, host = Locale.forLanguageTag("fi-FI"))).isEqualTo(line)
            assertThat(textForHost(line, texts = arabic, host = hebrew)).isEqualTo(line)
        }
        assertThat(textForHost("", texts = english, host = arabic)).isEmpty()
    }

    // External review round 6: a launcher can have a language of its own, which the app
    // cannot ask for. A widget's line carries its direction with it and reads the same on
    // any launcher, also where the phone and the app read the same way.
    @Test
    fun `a widget's line keeps its written order whichever way its launcher reads`() {
        for (line in lines) {
            val shown = textInOwnDirection(line, texts = english)
            assertWithMessage(line).that(VisualOrder.rightToLeft(shown)).isEqualTo(line)
            assertWithMessage(line).that(VisualOrder.leftToRight(shown)).isEqualTo(line)
        }
        val word = String(charArrayOf(Char(0x645), Char(0x646), Char(0x632), Char(0x644)))
        val arabicLines = listOf(
            word + " " + Bidi.ltr("1.0 GB", arabic),
            Bidi.ltr("1.0 GB / 2.0 GB", arabic),
            Bidi.beside(word, "2.4 GHz", arabic),
            "4 $word · " + Bidi.ltr("4.7 GHz", arabic),
        )
        for (line in arabicLines) {
            val shown = textInOwnDirection(line, texts = arabic)
            assertWithMessage(line).that(VisualOrder.leftToRight(shown)).isEqualTo(VisualOrder.rightToLeft(line))
            assertWithMessage(line).that(VisualOrder.rightToLeft(shown)).isEqualTo(VisualOrder.rightToLeft(line))
        }
    }

    @Test
    fun `a widget's text is marked line by line, and an empty one not at all`() {
        val shown = textInOwnDirection("RAM 1.0 / 2.0 GB\n\nData 185 MB", texts = english)

        assertThat(shown.split('\n').map { it.length }).containsExactly(18, 0, 13).inOrder()
        assertThat(shown.split('\n').first().first().code).isEqualTo(0x2066)
        assertThat(shown.split('\n').first().last().code).isEqualTo(0x2069)
        assertThat(textInOwnDirection("", texts = english)).isEmpty()
        assertThat(textInOwnDirection("", texts = arabic)).isEmpty()
    }

    // A mark's reach ends where its line does.
    @Test
    fun `each line of a text is kept by itself`() {
        val shown = textForHost("RAM 1.0 / 2.0 GB\nData 185 MB", texts = english, host = arabic)

        for (line in shown.split('\n')) {
            assertThat(line.first().code).isEqualTo(0x2066)
            assertThat(line.last().code).isEqualTo(0x2069)
        }
        assertThat(shown.split('\n').map { VisualOrder.rightToLeft(it) }).containsExactly("RAM 1.0 / 2.0 GB", "Data 185 MB").inOrder()
    }
}
