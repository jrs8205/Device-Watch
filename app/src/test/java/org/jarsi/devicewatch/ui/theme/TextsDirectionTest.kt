package org.jarsi.devicewatch.ui.theme

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.jarsi.devicewatch.SourceXml
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * The app's own screens are laid out the way their texts read. Android lays a screen out by
 * the phone's language: on a phone set to Hebrew, Persian or Urdu, languages the app is not
 * written in, that is from the right, while the texts shown are the English ones. Every
 * English sentence then stood in a right-to-left paragraph, where "12 h 30 min" reads
 * "h 30 min 12".
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "iw-rIL-ldrtl-w360dp-h780dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextsDirectionTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private fun on(phone: String): Context =
        app.createConfigurationContext(Configuration().apply { setLocale(Locale.forLanguageTag(phone)) })

    @Test
    fun `on a Hebrew phone a screen is laid out from the left, as its English texts read`() {
        var given: LayoutDirection? = null
        var used: LayoutDirection? = null
        compose.setContent {
            given = LocalLayoutDirection.current
            ProvideTextsDirection { used = LocalLayoutDirection.current }
        }
        compose.waitForIdle()

        // What Android hands the screen on this phone, and what the screen makes of it.
        assertThat(given).isEqualTo(LayoutDirection.Rtl)
        assertThat(used).isEqualTo(LayoutDirection.Ltr)
    }

    @Test
    fun `the direction is that of the language the texts are in`() {
        val expected = mapOf(
            "en-US" to LayoutDirection.Ltr, "fi-FI" to LayoutDirection.Ltr, "zh-CN" to LayoutDirection.Ltr,
            "ar-EG" to LayoutDirection.Rtl, "ar-EG-u-nu-latn" to LayoutDirection.Rtl,
            // Languages the app is not written in: the texts are English.
            "he-IL" to LayoutDirection.Ltr, "fa-IR" to LayoutDirection.Ltr, "ur-PK" to LayoutDirection.Ltr,
            "fr-FR" to LayoutDirection.Ltr,
        )
        for ((phone, direction) in expected) {
            assertWithMessage(phone).that(textsLayoutDirection(on(phone))).isEqualTo(direction)
        }
    }

    // Every screen the app composes starts from this: the two activities and the screensaver.
    @Test
    fun `every screen is started inside the texts' direction`() {
        val roots = listOf("MainActivity.kt", "widget/SingleMetricConfigActivity.kt", "system/MonitorDreamService.kt")
        for (root in roots) {
            val source = SourceXml.source("java/org/jarsi/devicewatch/$root").readText()
            val start = source.indexOf("setContent {")
            assertWithMessage(root).that(start).isAtLeast(0)
            assertWithMessage(root).that(source.indexOf("setContent {", start + 1)).isEqualTo(-1)
            assertWithMessage(root).that(source.substring(start, start + 220)).contains("ProvideTextsDirection {")
        }
        val all = SourceXml.source("java/org/jarsi/devicewatch").walkTopDown()
            .filter { it.extension == "kt" && "setContent {" in it.readText() }
            .map { it.name }.toList()
        assertThat(all).containsExactly("MainActivity.kt", "SingleMetricConfigActivity.kt", "MonitorDreamService.kt")
    }
}
