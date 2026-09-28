package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.system.DreamPreferences
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Android's own backup (the cloud and a move to a new phone) may take the
 * settings and nothing else. Found while writing the release review: with
 * `allowBackup` on and no rules it took everything, the notification log with
 * other apps' notification texts included, although PRIVACY.md says nothing
 * leaves the device.
 */
class BackupRulesTest {

    /** Unit tests run in the module directory under Gradle, in the root under some IDEs. */
    private fun source(path: String): File =
        listOf(File("src/main/$path"), File("app/src/main/$path")).first { it.exists() }

    private fun parse(path: String): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(source(path)).documentElement

    private fun Element.children(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.includes(): Set<String> =
        children("include").map { "${it.getAttribute("domain")}:${it.getAttribute("path")}" }.toSet()

    private val settingsOnly = setOf(
        "sharedpref:${AppSettingsRepositoryImpl.PREFS_NAME}.xml",
        "sharedpref:${DreamPreferences.PREFS_NAME}.xml",
    )

    @Test
    fun `from Android 12 the cloud backup and a move to a new phone carry the settings only`() {
        val rules = parse("res/xml/data_extraction_rules.xml")

        assertThat(rules.children("cloud-backup").single().includes()).isEqualTo(settingsOnly)
        assertThat(rules.children("device-transfer").single().includes()).isEqualTo(settingsOnly)
        assertThat(rules.children("exclude")).isEmpty()
    }

    @Test
    fun `up to Android 11 the backup carries the settings only`() {
        val rules = parse("res/xml/backup_rules.xml")

        assertThat(rules.includes()).isEqualTo(settingsOnly)
        assertThat(rules.children("exclude")).isEmpty()
    }

    @Test
    fun `the manifest applies both rule files`() {
        val application = parse("AndroidManifest.xml").children("application").single()
        val android = "http://schemas.android.com/apk/res/android"

        assertThat(application.getAttributeNS(android, "fullBackupContent")).isEqualTo("@xml/backup_rules")
        assertThat(application.getAttributeNS(android, "dataExtractionRules")).isEqualTo("@xml/data_extraction_rules")
    }
}
