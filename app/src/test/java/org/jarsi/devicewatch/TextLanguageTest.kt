package org.jarsi.devicewatch

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.jarsi.devicewatch.SourceXml.android
import org.jarsi.devicewatch.SourceXml.children
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * The language of the texts the app shows is not always the phone's. A phone set to Hebrew,
 * or to any language the app is not written in, gets the English texts, while its own
 * language stays the process's default. Whatever is decided by the way the texts read (the
 * report's direction, a line handed to the launcher or to the system to draw) has to ask the
 * texts, not the phone: an English report was laid out from the right on a Hebrew phone, and
 * its "12 h 30 min" read "h 30 min 12".
 */
@RunWith(RobolectricTestRunner::class)
class TextLanguageTest {

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private fun on(phone: String): Context =
        app.createConfigurationContext(Configuration().apply { setLocale(Locale.forLanguageTag(phone)) })

    @Test
    fun `the texts say which language they are written in`() {
        val cases = mapOf(
            "en-US" to "en", "en-GB" to "en", "fi-FI" to "fi", "zh-CN" to "zh-CN",
            "ar-EG" to "ar", "ar-SA" to "ar", "ar-EG-u-nu-latn" to "ar",
        )
        for ((phone, texts) in cases) {
            assertWithMessage(phone).that(textLocale(on(phone)).toLanguageTag()).isEqualTo(texts)
        }
    }

    @Test
    fun `a phone in a language the app is not written in gets English texts, and they say so`() {
        val english = on("en-US").getString(R.string.action_cancel)

        for (phone in listOf("he-IL", "fa-IR", "ur-PK", "fr-FR", "ja-JP")) {
            val context = on(phone)
            assertWithMessage(phone).that(context.getString(R.string.action_cancel)).isEqualTo(english)
            assertWithMessage(phone).that(textLocale(context).toLanguageTag()).isEqualTo("en")
            assertWithMessage(phone).that(Bidi.isRtl(textLocale(context))).isFalse()
        }
        // The phone itself is still written from the right.
        assertThat(Bidi.isRtl(Locale.forLanguageTag("he-IL"))).isTrue()
    }

    @Test
    fun `every language of the app's language list has its own mark`() {
        val offered = SourceXml.parse("res/xml/locales_config.xml").children("locale").map { it.android("name") }

        assertThat(offered).isNotEmpty()
        for (tag in offered) {
            assertWithMessage(tag).that(textLocale(on(tag)).toLanguageTag()).isEqualTo(tag)
        }
    }
}
