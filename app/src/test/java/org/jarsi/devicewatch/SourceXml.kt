package org.jarsi.devicewatch

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The manifest and the XML resources as they are written, for tests of what they declare. */
internal object SourceXml {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    /** Unit tests run in the module directory under Gradle, in the root under some IDEs. */
    fun source(path: String): File =
        listOf(File("src/main/$path"), File("app/src/main/$path")).first { it.exists() }

    fun parse(path: String): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(source(path)).documentElement

    /** The manifest's `<application>`. */
    val application: Element by lazy { parse("AndroidManifest.xml").children("application").single() }

    fun Element.children(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    fun Element.android(attribute: String): String = getAttributeNS(ANDROID_NS, attribute)

    /** Every `android:` attribute of the element, by its local name. */
    fun Element.androidAttributes(): Map<String, String> =
        (0 until attributes.length).map { attributes.item(it) }
            .filter { it.namespaceURI == ANDROID_NS }
            .associate { it.localName to it.nodeValue }

    fun Element.actions(): List<String> = children("action").map { it.android("name") }

    fun Element.categories(): List<String> = children("category").map { it.android("name") }

    /** The manifest's component of this tag whose `android:name` is [shortName] (".Foo"). */
    fun component(tag: String, shortName: String): Element =
        application.children(tag).single { it.android("name") == shortName }
}
