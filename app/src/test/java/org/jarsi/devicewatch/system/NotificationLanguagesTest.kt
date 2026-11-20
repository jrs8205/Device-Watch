package org.jarsi.devicewatch.system

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.util.Locale

/**
 * What tells the monitor that the notification it has up was written for other languages
 * than the ones in force now. The monitor lives through a change of the app's language and
 * of the phone's, and its silent notification is posted once: left alone it stayed in
 * English after the app was switched to Arabic (external review round 6).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, shadows = [NotificationLanguagesTest.Phone::class])
class NotificationLanguagesTest {

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private fun inLanguage(tag: String): Context =
        app.createConfigurationContext(Configuration().apply { setLocale(Locale.forLanguageTag(tag)) })

    @After
    fun englishPhoneAgain() {
        Phone.language = "en-US"
    }

    @Test
    fun `the same languages read twice are the same`() {
        assertThat(NotificationLanguages.of(inLanguage("ar-EG"))).isEqualTo(NotificationLanguages.of(inLanguage("ar-EG")))
        assertThat(NotificationLanguages.of(app)).isEqualTo(NotificationLanguages.of(app))
    }

    @Test
    fun `another language for the app's texts is a change`() {
        assertThat(NotificationLanguages.of(inLanguage("ar-EG"))).isNotEqualTo(NotificationLanguages.of(inLanguage("en-US")))
        assertThat(NotificationLanguages.of(inLanguage("fi-FI"))).isNotEqualTo(NotificationLanguages.of(inLanguage("en-US")))
    }

    // The texts stay English on a Hebrew phone, but the shade that draws them reads from the
    // right there: the lines have to go up again, marked.
    @Test
    fun `another language for the phone is a change although the texts stay the same`() {
        val english = inLanguage("en-US")
        val before = NotificationLanguages.of(english)

        Phone.language = "he-IL"

        assertThat(NotificationLanguages.of(english)).isNotEqualTo(before)
    }

    /** A phone whose own language the test sets, whatever language the app under test runs in. */
    @Implements(LocaleManager::class)
    class Phone {
        @Suppress("unused")
        @Implementation
        fun getSystemLocales(): LocaleList = LocaleList.forLanguageTags(language)

        companion object {
            @JvmStatic
            var language = "en-US"
        }
    }
}
