package org.jarsi.devicewatch

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.jarsi.devicewatch.SourceXml.children
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every translation has the same shape as the English texts it stands for: the same keys,
 * the same format arguments in each text, and the plural forms its language needs. A text
 * left out falls back to English in the middle of a translated screen; a wrong argument
 * crashes the screen that formats it.
 */
class TranslationsTest {

    private class Texts(file: File) {
        private val root: Element =
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val raw: String = file.readText()
        val strings: Map<String, Element> = root.children("string").associateBy { it.getAttribute("name") }
        val plurals: Map<String, Map<String, String>> = root.children("plurals").associate { plural ->
            plural.getAttribute("name") to
                plural.children("item").associate { it.getAttribute("quantity") to it.textContent }
        }
    }

    private val english = Texts(SourceXml.source("res/values/strings.xml"))

    /** The translations by folder qualifier: "fi", "zh-rCN", "ar". */
    private val translations: Map<String, Texts> = SourceXml.source("res").listFiles().orEmpty()
        .filter { it.isDirectory && it.name.startsWith("values-") && File(it, "strings.xml").exists() }
        .associate { it.name.removePrefix("values-") to Texts(File(it, "strings.xml")) }

    private fun language(qualifier: String): String = qualifier.substringBefore('-')

    /** "%1$d", "%.1f", "%s", "%%": what `String.format` acts on. A lone per cent sign is not one. */
    private fun arguments(text: String): Set<String> = ARGUMENT.findAll(text).map { it.value }.toSet()

    private fun isTranslatable(element: Element): Boolean = element.getAttribute("translatable") != "false"

    private fun isFormatted(element: Element): Boolean = element.getAttribute("formatted") != "false"

    private fun assertNoProblems(problems: List<String>) {
        assertWithMessage(problems.joinToString("\n", prefix = "\n")).that(problems).isEmpty()
    }

    @Test
    fun `there are translations to check`() {
        assertThat(translations.keys).contains("fi")
    }

    @Test
    fun `every translation has exactly the texts that are translated`() {
        val expected = english.strings.filterValues(::isTranslatable).keys
        val problems = translations.flatMap { (qualifier, texts) ->
            (expected - texts.strings.keys).map { "$qualifier: $it is missing" } +
                (texts.strings.keys - expected).map { "$qualifier: $it is not a text to translate" }
        }
        assertNoProblems(problems)
    }

    @Test
    fun `every translated text has the format arguments of the English one`() {
        val problems = translations.flatMap { (qualifier, texts) ->
            texts.strings.mapNotNull { (name, element) ->
                val original = english.strings[name] ?: return@mapNotNull null
                when {
                    !isFormatted(original) ->
                        "$qualifier: $name must carry formatted=\"false\" like the English text"
                            .takeIf { isFormatted(element) }
                    arguments(element.textContent) != arguments(original.textContent) ->
                        "$qualifier: $name has ${arguments(element.textContent)}, " +
                            "English has ${arguments(original.textContent)}"
                    else -> null
                }
            }
        }
        assertNoProblems(problems)
    }

    @Test
    fun `no translated text is empty`() {
        val problems = translations.flatMap { (qualifier, texts) ->
            texts.strings.filterValues { it.textContent.isBlank() }.keys.map { "$qualifier: $it is empty" } +
                texts.plurals.flatMap { (name, forms) ->
                    forms.filterValues { it.isBlank() }.keys.map { "$qualifier: $name ($it) is empty" }
                }
        }
        assertNoProblems(problems)
    }

    @Test
    fun `every translation has the same plurals, each in the forms its language needs`() {
        val problems = translations.flatMap { (qualifier, texts) ->
            val forms = FORMS[language(qualifier)] ?: FORMS.getValue("en")
            (english.plurals.keys - texts.plurals.keys).map { "$qualifier: plural $it is missing" } +
                (texts.plurals.keys - english.plurals.keys).map { "$qualifier: plural $it is not in English" } +
                texts.plurals.flatMap { (name, items) ->
                    (forms.required - items.keys).map { "$qualifier: $name lacks the form \"$it\"" } +
                        (items.keys - forms.required - forms.allowed).map {
                            "$qualifier: $name has the form \"$it\", which the language does not use"
                        }
                }
        }
        assertNoProblems(problems)
    }

    /**
     * A form for a number (few, many, other) carries every argument of the English text. The
     * forms for exactly none, one and two may spell the number out and leave its argument
     * away ("one day"), but never anything else.
     */
    @Test
    fun `every plural form has the format arguments it needs`() {
        val problems = translations.flatMap { (qualifier, texts) ->
            texts.plurals.flatMap { (name, items) ->
                val all = arguments(english.plurals[name]?.get("other") ?: return@flatMap emptyList())
                val notNumbers = all.filterNot { it.endsWith("d") }.toSet()
                items.mapNotNull { (quantity, text) ->
                    val found = arguments(text)
                    val fits = if (quantity in SPELLED_OUT) {
                        all.containsAll(found) && found.containsAll(notNumbers)
                    } else {
                        found == all
                    }
                    "$qualifier: $name ($quantity) has $found, English has $all".takeIf { !fits }
                }
            }
        }
        assertNoProblems(problems)
    }

    @Test
    fun `the English plurals have their two forms`() {
        val problems = english.plurals.filterValues { it.keys != setOf("one", "other") }.keys
            .map { "values: $it has the forms ${english.plurals.getValue(it).keys}" }
        assertNoProblems(problems)
    }

    @Test
    fun `the two-word name is in no translation but the Finnish one`() {
        val problems = (translations - "fi").filterValues { "Device Watch" in it.raw }.keys
            .map { "$it: the name is written \"Device Watch\"" }
        assertNoProblems(problems)
    }

    @Test
    fun `Arabic is written with the digits 0 to 9`() {
        val problems = translations.filterKeys { language(it) == "ar" }.flatMap { (qualifier, texts) ->
            texts.raw.lines().withIndex()
                .filter { line -> line.value.any { it.code in 0x0660..0x0669 || it.code in 0x06F0..0x06F9 } }
                .map { "$qualifier: line ${it.index + 1} has Arabic-Indic digits" }
        }
        assertNoProblems(problems)
    }

    /**
     * In a sentence written from the right a number and the Latin unit after it come apart on
     * the line ("mAh 10000") unless the two are kept as one left-to-right piece: the marks
     * U+2066 and U+2069 around them, written out as escapes so that they can be seen.
     */
    @Test
    fun `in a text written from the right a number keeps its Latin unit beside it`() {
        val problems = translations.filterKeys { Bidi.isRtl(Locale.forLanguageTag(language(it))) }
            .flatMap { (qualifier, texts) ->
                val all = texts.strings.map { it.key to it.value.textContent } +
                    texts.plurals.flatMap { (name, items) -> items.map { "$name (${it.key})" to it.value } }
                all.flatMap { (name, raw) ->
                    // An argument may be a name or a number: both are tried.
                    listOf(asShown(raw, textArgument = Char(0x2066) + "x" + Char(0x2069)), asShown(raw, textArgument = "12"))
                        .distinct()
                        .flatMap { it.lines() }
                        .flatMap { line ->
                            val seen = VisualOrder.rightToLeft(line)
                            val plain = line.filterNot { it.code in 0x2066..0x2069 }
                            FIGURE.findAll(plain).map { it.value }.filterNot { it in seen }
                                .map { "$qualifier: $name draws \"$it\" apart: $seen" }.toList()
                        }
                        .distinct()
                }
            }
        assertNoProblems(problems)
    }

    // Android's own Chinese calls a Quick Settings tile 功能块 (its dialog for adding one, the
    // editor's reset dialog). 抬腕唤醒 is raising a wrist to wake a watch; a phone is lifted.
    @Test
    fun `Chinese names a tile as Android does and speaks of lifting a phone, not a wrist`() {
        val problems = translations.filterKeys { language(it) == "zh" }.flatMap { (qualifier, texts) ->
            val all = texts.strings.map { it.key to it.value.textContent } +
                texts.plurals.flatMap { (name, items) -> items.map { "$name (${it.key})" to it.value } }
            all.flatMap { (name, text) ->
                listOf("磁贴", "抬腕").filter { it in text }.map { "$qualifier: $name says $it" }
            }
        }
        assertNoProblems(problems)
    }

    // Two wordings the review ruled on. The switch shows the time of day at which the charge
    // is expected to be complete, not how long it takes; and a battery that Android reports as
    // worn out is not a corroded one.
    @Test
    fun `the estimate's switch names a time of day and a worn battery is not a corroded one`() {
        val problems = translations.flatMap { (qualifier, texts) ->
            listOfNotNull(
                "$qualifier: dream_show_estimate speaks of a length of time".takeIf {
                    val name = texts.strings["dream_show_estimate"]?.textContent.orEmpty()
                    "所需时间" in name || "الوقت حتى" in name
                },
                "$qualifier: battery_health_dead says corroded".takeIf {
                    "متآكل" in texts.strings["battery_health_dead"]?.textContent.orEmpty()
                },
            )
        }
        assertNoProblems(problems)
    }

    // A removal that never ran because the app was no longer installed for the user by then.
    // The Chinese said that the user no longer installs the app.
    @Test
    fun `Chinese says an app is no longer installed, not that the user no longer installs it`() {
        val problems = translations.filterKeys { language(it) == "zh" }.flatMap { (qualifier, texts) ->
            listOfNotNull(
                "$qualifier: system_apps_not_installed_now makes the user the one who installs".takeIf {
                    "已不再安装" in texts.strings["system_apps_not_installed_now"]?.textContent.orEmpty()
                },
            )
        }
        assertNoProblems(problems)
    }

    /**
     * A text as the app shows it: its escapes read, a sample number in place of each number
     * argument and [textArgument] in place of each text argument.
     */
    private fun asShown(raw: String, textArgument: String): String {
        return ARGUMENT.replace(raw) { match ->
            when (match.value.last()) {
                'd' -> "12"
                'f' -> "1.5"
                '%' -> "%"
                else -> textArgument
            }
        }
            .replace(ESCAPED_CHARACTER) { it.groupValues[1].toInt(16).toChar().toString() }
            .replace("\\n", "\n")
            .replace("\\'", "'")
    }

    private class Forms(val required: Set<String>, val allowed: Set<String> = emptySet())

    private companion object {
        val ARGUMENT = Regex("""%(?:\d+\$)?[-#+0,(]*\d*(?:\.\d+)?[a-zA-Z%]""")

        /** An escape as a text's source writes a character that cannot be seen. */
        val ESCAPED_CHARACTER = Regex("""\\u([0-9a-fA-F]{4})""")

        /**
         * What has to stand on the line as it is written: a number and a Latin unit after a
         * space ("45 °C", "6 GHz", "12 mAh"), a date as a search takes it ("2026-08-14",
         * "2026-08"), numbers joined by a sign ("3 / 4", "10–15", "12 → 15"), a factor ("2.5×").
         */
        val FIGURE = Regex(
            """\d[\d.,]*""" + """\s(?:°[CF]|[A-Za-z]+)""" +
                """|\d{4}-\d{2}(?:-\d{2})?""" +
                """|\d[\d.,]*(?:\s?[–/×→]\s?\d[\d.,]*)+×?""" +
                """|\d[\d.,]*×"""
        )

        /** The plural forms a language tells apart; any language not listed has English's two. */
        val FORMS = mapOf(
            "en" to Forms(setOf("one", "other")),
            "fi" to Forms(setOf("one", "other")),
            "zh" to Forms(setOf("other"), allowed = setOf("one")),
            "ar" to Forms(setOf("zero", "one", "two", "few", "many", "other")),
        )

        /** The forms that stand for one exact number in the languages above. */
        val SPELLED_OUT = setOf("zero", "one", "two")
    }
}
