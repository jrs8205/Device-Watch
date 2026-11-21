package org.jarsi.devicewatch

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * A date written in the phone's language has the language's own names in it, and a name can
 * hold the language's own digits. Each time a date reached a page by a route of its own, that
 * route was the one left out (external review rounds 8 and 9). The routes are therefore few
 * and named: three files make every format that writes a language's own words, and each of
 * them hands its text through [LatinDigits.text]. Read from the source, as
 * `LocationReadGuardTest` reads its own: a format made anywhere else fails here, whatever it
 * is for.
 */
class DateRouteGuardTest {

    private val main: File = listOf(File("src/main/java/org/jarsi/devicewatch"), File("app/src/main/java/org/jarsi/devicewatch"))
        .first { it.isDirectory }

    /** The code of every Kotlin file by its path under the app's package, comments left out. */
    private val code: Map<String, String> by lazy {
        main.walkTopDown().filter { it.isFile && it.extension == "kt" }.associate { file ->
            val lines = file.readLines().filterNot { line ->
                val start = line.trimStart()
                start.startsWith("*") || start.startsWith("//") || start.startsWith("/*")
            }
            file.relativeTo(main).invariantSeparatorsPath to lines.joinToString("\n")
        }
    }

    @Test
    fun `the source is read`() {
        assertThat(code.size).isAtLeast(100)
        assertThat(code.keys).containsAtLeast(PAGES, CHARGE_ESTIMATE, SCREENSAVER)
    }

    @Test
    fun `a format of the language's own is asked for in three files, and each writes the app's digits`() {
        val askers = code.filterValues { OWN_FORMAT.containsMatchIn(it) }.keys

        assertThat(askers).containsExactly(PAGES, CHARGE_ESTIMATE, SCREENSAVER)
        for (file in askers) assertWithMessage(file).that(code.getValue(file)).contains("LatinDigits.text(")
    }

    @Test
    fun `a pattern written anywhere else is spelled out there and holds figures only`() {
        val wrong = mutableListOf<String>()
        for ((file, text) in code) {
            if (file in WRITERS) continue
            for (made in PATTERN_FORMAT.findAll(text)) {
                val pattern = LITERAL.matchEntire(made.groupValues[2].trim())?.groupValues?.get(1)
                if (pattern == null || !FIGURES_ONLY.matches(pattern)) wrong += "$file: ${made.value}"
            }
        }

        assertWithMessage(wrong.joinToString("\n", prefix = "\n")).that(wrong).isEmpty()
    }

    @Test
    fun `the check of a pattern tells figures from names`() {
        for (figures in listOf("yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd-HHmmss", "MM-dd HH:mm", "HH:mm:ss")) {
            assertWithMessage(figures).that(FIGURES_ONLY.matches(figures)).isTrue()
        }
        for (names in listOf("LLLL yyyy", "d MMM yyyy", "EEE HH:mm", "h:mm a", "yyyy 'at' HH")) {
            assertWithMessage(names).that(FIGURES_ONLY.matches(names)).isFalse()
        }
    }

    private companion object {
        /** The pages', the widgets' and the lists' dates and times. */
        const val PAGES = "TimeOfDay.kt"

        /** When the battery is full, for the screensaver and the charging notification. */
        const val CHARGE_ESTIMATE = "system/ChargeCompletionText.kt"

        /** The screensaver's clock, dates and alarm. */
        const val SCREENSAVER = "system/dream/DreamUiState.kt"

        val WRITERS = setOf(PAGES, CHARGE_ESTIMATE, SCREENSAVER)

        /** Asking Android or `java.time` for a language's own date or time, or for a pattern of one. */
        val OWN_FORMAT = Regex(
            """ofLocalized(Date|Time|DateTime)\(|\.get(Medium|Long)?DateFormat\(|\.getTimeFormat\(""" +
                """|\.get(Date|Time|DateTime)Instance\(|getBestDateTimePattern\("""
        )

        /** A format made from a pattern, and the pattern's place in the call. */
        val PATTERN_FORMAT = Regex("""(SimpleDateFormat|ofPattern)\(\s*([^,)]*)""")

        val LITERAL = Regex(""""([^"$]*)"""")

        /** Years, months, days, hours, minutes, seconds and their fractions as numbers, with what stands between them. */
        val FIGURES_ONLY = Regex("""(?!.*MMM)[yMdHhmsS\-:. /]*""")
    }
}
