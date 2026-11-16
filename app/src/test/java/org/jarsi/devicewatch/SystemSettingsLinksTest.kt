package org.jarsi.devicewatch

import android.service.dreams.DreamService
import android.service.quicksettings.TileService
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The ways from Android's own pages into this app's settings. Each is declared under a name
 * Android looks up, and a misspelt name fails without a word: from the first screensaver on
 * its extra information sat under `android.service.dreams`, which Android never reads, so
 * Android's screensaver page showed neither a settings gear nor the preview picture.
 */
class SystemSettingsLinksTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

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

    private fun Element.android(attribute: String): String = getAttributeNS(androidNs, attribute)

    private fun Element.actions(): List<String> = children("action").map { it.android("name") }

    private val application: Element = parse("AndroidManifest.xml").children("application").single()

    private fun dreamService(): Element =
        application.children("service").single { DreamService.SERVICE_INTERFACE in it.actions() }

    @Test
    fun `the screensaver's extra information is under the name Android reads`() {
        val names = dreamService().children("meta-data").map { it.android("name") }

        assertThat(names).containsExactly(DreamService.DREAM_META_DATA)
    }

    @Test
    fun `the screensaver's gear starts a screen of this app that Android's settings may start`() {
        // DreamService takes "package/.Class" and turns down a class in any other package.
        val (packageName, shortClass) = parse("res/xml/dream_info.xml").android("settingsActivity").split("/")
        assertThat(packageName).isEqualTo(APPLICATION_ID)
        assertThat(packageName + shortClass).isEqualTo(SCREENSAVER_SETTINGS_ALIAS)

        val alias = application.children("activity-alias").single { it.android("name") == shortClass }
        assertThat(alias.android("exported")).isEqualTo("true")
        assertThat(alias.android("targetActivity")).isEqualTo(".MainActivity")
    }

    @Test
    fun `a long press on a tile has a screen of this app to open`() {
        val answering = application.children("activity").filter { activity ->
            activity.children("intent-filter").any { TileService.ACTION_QS_TILE_PREFERENCES in it.actions() }
        }

        assertThat(answering.map { it.android("name") }).containsExactly(".MainActivity")
        assertThat(answering.single().android("exported")).isEqualTo("true")
    }

    @Test
    fun `the floating card's tile is one the manifest declares`() {
        val tiles = application.children("service")
            .filter { TileService.ACTION_QS_TILE in it.actions() }
            .map { APPLICATION_ID + it.android("name") }

        assertThat(tiles).hasSize(10)
        assertThat(tiles).contains(FLOATING_FIGURE_TILE)
    }

    private fun section(
        action: String? = null,
        componentClass: String? = MAIN_ACTIVITY,
        tileClass: String? = null,
        fromRecents: Boolean = false,
    ) = requestedSettingsSection(action, componentClass, tileClass, fromRecents)

    @Test
    fun `a start through the screensaver's gear asks for the screensaver's settings`() {
        assertThat(section(componentClass = SCREENSAVER_SETTINGS_ALIAS)).isEqualTo(SettingsSection.Screensaver)
    }

    @Test
    fun `a long press on a figure's tile asks for the tiles' settings`() {
        val longPress = TileService.ACTION_QS_TILE_PREFERENCES

        assertThat(section(longPress, tileClass = "$APPLICATION_ID.quick.BatteryTileService"))
            .isEqualTo(SettingsSection.QuickTiles)
        // An Android that does not name the tile still gets the tiles' settings.
        assertThat(section(longPress, tileClass = null)).isEqualTo(SettingsSection.QuickTiles)
    }

    @Test
    fun `a long press on the floating card's tile asks for the floating card's settings`() {
        assertThat(section(TileService.ACTION_QS_TILE_PREFERENCES, tileClass = FLOATING_FIGURE_TILE))
            .isEqualTo(SettingsSection.FloatingFigure)
    }

    @Test
    fun `an ordinary start asks for no part of the settings`() {
        assertThat(section("android.intent.action.MAIN")).isNull()
        assertThat(section(componentClass = null)).isNull()
        // The tile's name alone is not a long press.
        assertThat(section("android.intent.action.MAIN", tileClass = FLOATING_FIGURE_TILE)).isNull()
    }

    @Test
    fun `a task brought back from the recents list repeats no request`() {
        assertThat(section(componentClass = SCREENSAVER_SETTINGS_ALIAS, fromRecents = true)).isNull()
        assertThat(section(TileService.ACTION_QS_TILE_PREFERENCES, fromRecents = true)).isNull()
    }

    private companion object {
        const val APPLICATION_ID = "org.jarsi.devicewatch"
        const val MAIN_ACTIVITY = "$APPLICATION_ID.MainActivity"
    }
}
