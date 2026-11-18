package org.jarsi.devicewatch

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.jarsi.devicewatch.SourceXml.children
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
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

    private class Forms(val required: Set<String>, val allowed: Set<String> = emptySet())

    private companion object {
        val ARGUMENT = Regex("""%(?:\d+\$)?[-#+0,(]*\d*(?:\.\d+)?[a-zA-Z%]""")

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
