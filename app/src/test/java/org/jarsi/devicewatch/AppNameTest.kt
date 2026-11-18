package org.jarsi.devicewatch

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.jarsi.devicewatch.SourceXml.children
import org.junit.Test
import java.io.File

/**
 * The app is called "Device-Watch", with a hyphen, in every language but Finnish, where it
 * is "Laitevahti". The English texts, the documents and the store listing used to spell it
 * as two words.
 */
class AppNameTest {

    @Test
    fun `the English name is written with a hyphen`() {
        val english = strings("res/values/strings.xml")

        assertThat(english["app_name"]).isEqualTo("Device-Watch")
        assertThat(english["dream_service_name"]).isEqualTo("Device-Watch")
    }

    @Test
    fun `the Finnish name stays Laitevahti`() {
        assertThat(strings("res/values-fi/strings.xml")["app_name"]).isEqualTo("Laitevahti")
    }

    @Test
    fun `the two-word form is in none of the English texts`() {
        val storeTexts = File(repoRoot, "fastlane/metadata/android/en-US").listFiles().orEmpty()
            .filter { it.isFile && it.extension == "txt" }
        assertThat(storeTexts).isNotEmpty()
        val texts = listOf(
            SourceXml.source("res/values/strings.xml"),
            File(repoRoot, "README.md"),
            File(repoRoot, "FAQ.md"),
            File(repoRoot, "PRIVACY.md"),
        ) + storeTexts

        for (file in texts) {
            val lines = file.readLines().withIndex()
                .filter { OLD_NAME in it.value }
                .map { "${it.index + 1}: ${it.value.trim().take(80)}" }
            assertWithMessage("\"$OLD_NAME\" in ${file.name}").that(lines).isEmpty()
        }
    }

    private fun strings(path: String): Map<String, String> =
        SourceXml.parse(path).children("string").associate { it.getAttribute("name") to it.textContent }

    private companion object {
        const val OLD_NAME = "Device Watch"

        /** Unit tests run in the module directory under Gradle, in the root under some IDEs. */
        val repoRoot: File = listOf(File(".."), File(".")).first { File(it, "fastlane").isDirectory }
    }
}
