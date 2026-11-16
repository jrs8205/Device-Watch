package org.jarsi.devicewatch

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.SourceXml.android
import org.jarsi.devicewatch.SourceXml.application
import org.jarsi.devicewatch.SourceXml.children
import org.junit.Test
import java.io.File

/**
 * Android's own per-app language page (Android 13+) lists an app only when its manifest
 * names the languages it is written in. The app was missing from that page although it has
 * been in English and Finnish from the start.
 */
class AppLanguagesTest {

    @Test
    fun `the manifest names the app's language list`() {
        assertThat(application.android("localeConfig")).isEqualTo("@xml/locales_config")
    }

    @Test
    fun `the list offers exactly the languages the app is written in`() {
        val offered = SourceXml.parse("res/xml/locales_config.xml").children("locale").map { it.android("name") }

        // English is the unqualified `values`; every translation is a `values-xx` with its own strings.
        val translated = SourceXml.source("res").listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values-") && File(it, "strings.xml").exists() }
            .map { it.name.removePrefix("values-") }
        assertThat(translated).isNotEmpty()
        assertThat(offered).containsExactlyElementsIn(translated + "en")
    }
}
