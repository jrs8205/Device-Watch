package org.jarsi.devicewatch.widget

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.jarsi.devicewatch.SourceXml
import org.jarsi.devicewatch.phoneLocale
import org.jarsi.devicewatch.textLocale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * The three widgets as they are handed to a launcher on an Arabic phone while the app's own
 * texts are English. The launcher lays every text out from the right there, so every text
 * must arrive as one left-to-right piece; before, "1.0 GB / 2.0 GB" was drawn
 * "GB / 2.0 GB 1.0" and a network called "123 456" as "456 123".
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, shadows = [WidgetHostRenderTest.ArabicPhone::class])
class WidgetHostRenderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val leftToRightPiece = Char(0x2066).toString()

    private fun render(
        size: DpSize,
        prefs: Preferences,
        content: @androidx.compose.runtime.Composable () -> Unit,
        checks: GlanceAppWidgetUnitTest.() -> Unit,
    ) = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(size)
        setState(prefs)
        provideComposable { content() }
        checks()
    }

    /** Every text there is has been made one left-to-right piece. */
    private fun GlanceAppWidgetUnitTest.assertEveryTextIsKept() {
        val texts = onAllNodes(hasText(""))
        texts.assertAny(hasText(leftToRightPiece))
        texts.assertAll(hasText(leftToRightPiece))
    }

    @Test
    fun `the test's phone is Arabic and the app's texts are English`() {
        assertThat(phoneLocale(context).language).isEqualTo("ar")
        assertThat(textLocale(context).language).isEqualTo("en")
    }

    @Test
    fun `the dashboard's texts keep their order on an Arabic launcher`() = render(
        DpSize(360.dp, 420.dp),
        mutablePreferencesOf().apply {
            this[RefreshStatsAction.WIFI_SSID] = "123 456"
            this[RefreshStatsAction.MOBILE_DATA_USED] = 1.5
        },
        { WidgetContent() },
    ) {
        assertEveryTextIsKept()
        // The network's name as the reader has to see it: whole, not "456 123".
        onNode(hasText(leftToRightPiece + "123 456")).assertExists()
    }

    @Test
    fun `the compact widget's texts keep their order on an Arabic launcher`() = render(
        DpSize(180.dp, 180.dp),
        mutablePreferencesOf().apply { this[RefreshStatsAction.MOBILE_DATA_USED] = 1.5 },
        { CompactWidgetContent() },
    ) { assertEveryTextIsKept() }

    @Test
    fun `the one-number widget's texts keep their order on an Arabic launcher`() = render(
        DpSize(90.dp, 90.dp),
        mutablePreferencesOf(),
        { SingleMetricWidgetContent() },
    ) { assertEveryTextIsKept() }

    // The widgets' files must not reach for Glance's own text: the one in this package is the
    // only way a text gets to the launcher.
    @Test
    fun `no widget draws a text past the one that keeps its direction`() {
        val folder = SourceXml.source("java/org/jarsi/devicewatch/widget")
        val offenders = folder.listFiles().orEmpty()
            .filter { it.extension == "kt" && it.name != "WidgetText.kt" }
            .filter { file -> file.readLines().any { "androidx.glance.text.Text" in it && !it.contains("TextStyle") && !it.contains("TextAlign") && !it.contains("TextDefaults") && !it.contains("TextDecoration") } }
            .map { it.name }

        assertWithMessage("files that use Glance's own Text").that(offenders).isEmpty()
        assertThat(java.io.File(folder, "WidgetText.kt").readText()).contains("host.shown(text)")
    }

    /** A phone set to Arabic, whatever language the app under test runs in. */
    @Implements(LocaleManager::class)
    class ArabicPhone {
        @Suppress("unused")
        @Implementation
        fun getSystemLocales(): LocaleList = LocaleList.forLanguageTags("ar-EG")
    }
}
