package org.jarsi.devicewatch.widget

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.Bidi
import org.jarsi.devicewatch.phoneLocale
import org.jarsi.devicewatch.textLocale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The widgets as they are handed to a launcher whose language the app cannot know. A launcher
 * can be given a language of its own on Android's per-app language page (Android 13 and
 * later), and nothing tells another app which. With the phone and the app in English and the
 * launcher in Arabic the texts went out as they were, because the phone's language was taken
 * for the launcher's, and "1.0 GB / 2.0 GB" was drawn "GB / 2.0 GB 1.0" (external review
 * round 6). Every text is now handed over as one piece in the direction of the app's own
 * texts, whatever the phone's language is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WidgetOwnDirectionTest {

    private val app: Context = ApplicationProvider.getApplicationContext()

    private fun inLanguage(tag: String): Context =
        app.createConfigurationContext(Configuration().apply { setLocale(Locale.forLanguageTag(tag)) })

    private val leftToRightPiece = Char(0x2066).toString()
    private val rightToLeftPiece = Char(0x2067).toString()

    private val sizes = listOf(DpSize(360.dp, 420.dp), DpSize(180.dp, 180.dp), DpSize(90.dp, 90.dp))
    private val widgets: List<@androidx.compose.runtime.Composable () -> Unit> =
        listOf({ WidgetContent() }, { CompactWidgetContent() }, { SingleMetricWidgetContent() })

    private val readings: Preferences = mutablePreferencesOf().apply {
        this[RefreshStatsAction.WIFI_SSID] = "123 456"
        this[RefreshStatsAction.MOBILE_DATA_USED] = 1.5
    }

    /** Every text of every widget, drawn with [context]'s texts, starts as a piece of [direction]. */
    private fun assertEveryTextStarts(context: Context, direction: String) {
        for ((index, widget) in widgets.withIndex()) {
            runGlanceAppWidgetUnitTest {
                setContext(context)
                setAppWidgetSize(sizes[index])
                setState(readings)
                provideComposable { widget() }
                everyTextStarts(direction)
            }
        }
    }

    private fun GlanceAppWidgetUnitTest.everyTextStarts(direction: String) {
        val texts = onAllNodes(hasText(""))
        texts.assertAny(hasText(direction))
        texts.assertAll(hasText(direction))
    }

    @Test
    fun `with the phone and the app in English every text still goes out as a left-to-right piece`() {
        assertThat(Bidi.isRtl(phoneLocale(app))).isFalse()
        assertThat(textLocale(app).language).isEqualTo("en")

        assertEveryTextStarts(app, leftToRightPiece)
    }

    @Test
    @Config(shadows = [WidgetHostRenderTest.ArabicPhone::class])
    fun `with the phone and the app in Arabic every text still goes out as a right-to-left piece`() {
        val arabic = inLanguage("ar-EG")
        assertThat(Bidi.isRtl(phoneLocale(arabic))).isTrue()
        assertThat(textLocale(arabic).language).isEqualTo("ar")

        assertEveryTextStarts(arabic, rightToLeftPiece)
    }

    // The phone's language no longer has a say: the same texts go out on any phone.
    @Test
    fun `the phone's language does not change what a widget hands over`() {
        val text = "1.0 GB / 2.0 GB"

        assertThat(WidgetHost.of(app).shown(text)).isEqualTo(WidgetHost.of(inLanguage("en-GB")).shown(text))
        assertThat(WidgetHost.of(app).shown(text)).isEqualTo(leftToRightPiece + text + Char(0x2069))
        assertThat(WidgetHost.of(inLanguage("ar-EG")).shown(text)).isEqualTo(rightToLeftPiece + text + Char(0x2069))
    }
}
