package org.jarsi.devicewatch

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Configuration
import android.os.LocaleList
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

    private fun onList(phone: String): Context =
        app.createConfigurationContext(Configuration().apply { setLocales(LocaleList.forLanguageTags(phone)) })

    // External review round 6: the libraries the app is built with bring their own texts in
    // some eighty languages. With French first on the phone's list Android found those,
    // settled on French, and the app's own texts came from the English ones although
    // Arabic or Chinese stood next on the list.
    @Test
    fun `the first language of the phone's list that the app is written in is the one shown`() {
        val cases = mapOf(
            "fr-FR,ar-EG" to "ar", "fr-FR,zh-CN" to "zh-CN", "fr-FR,fi-FI" to "fi",
            "de-DE,fr-FR,ar-EG" to "ar", "he-IL,fi-FI" to "fi", "ja-JP,ko-KR,zh-CN" to "zh-CN",
            "he-IL,ar-EG" to "ar", "ar-EG,fr-FR" to "ar", "fi-FI,ar-EG" to "fi",
            // English is one of the app's languages, wherever it stands on the list.
            "fr-FR,en-US,ar-EG" to "en", "en-GB,ar-EG" to "en",
            // None of the list's languages: the English texts.
            "fr-FR,de-DE" to "en", "he-IL,fa-IR" to "en",
        )
        for ((phone, texts) in cases) {
            val context = onList(phone)
            assertWithMessage(phone).that(textLocale(context).toLanguageTag()).isEqualTo(texts)
            assertWithMessage(phone).that(context.getString(R.string.action_cancel))
                .isEqualTo(on(texts).getString(R.string.action_cancel))
        }
    }

    // What Android chooses from: the languages the package holds texts in, the libraries'
    // included. Asked of Android itself, the way it asks when it settles on a language.
    @Test
    fun `the package holds texts in the app's own languages only`() {
        @Suppress("UNCHECKED_CAST")
        val packaged = AssetManager::class.java.getMethod("getNonSystemLocales").invoke(app.assets) as Array<String>
        val languages = packaged.map { Locale.forLanguageTag(it).language }.filter { it.isNotEmpty() }.toSet()

        assertThat(languages).containsAtLeast("fi", "zh", "ar")
        assertThat(languages - setOf("en", "fi", "zh", "ar")).isEmpty()
        // Chinese as it is written in mainland China, the only one the app has.
        assertThat(packaged.filter { it.startsWith("zh") }).containsExactly("zh-CN")
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
