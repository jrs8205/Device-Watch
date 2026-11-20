package org.jarsi.devicewatch

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * Which languages are written from the right, as Android itself answers it. [BidiTest] runs
 * without Android and sees the answer the app falls back on there; this one runs with it.
 */
@RunWith(RobolectricTestRunner::class)
class BidiOnAndroidTest {

    @Test
    fun `the languages Android lays out from the right`() {
        for (tag in listOf("ar", "ar-EG-u-nu-latn", "he", "fa-IR", "ur", "ps", "sd", "ug", "yi", "dv", "ckb", "ff-Adlm-GN")) {
            assertWithMessage(tag).that(Bidi.isRtl(Locale.forLanguageTag(tag))).isTrue()
        }
    }

    @Test
    fun `the languages Android lays out from the left`() {
        for (tag in listOf("en-US", "fi-FI", "zh-CN", "ff-Latn-SN", "ja", "ru", "und")) {
            assertWithMessage(tag).that(Bidi.isRtl(Locale.forLanguageTag(tag))).isFalse()
        }
    }

    // The direction a line is handed over in is the one Android gives its language, also for
    // a language whose script the name-based fallback got wrong (Fulah in Adlam).
    @Test
    fun `a line another process draws takes its direction from Android's answer for its language`() {
        val english = Bidi.inOwnDirection("1.5 / 2.4 GB", Locale.US)
        assertThat(english.first().code).isEqualTo(0x2066)
        assertThat(english.last().code).isEqualTo(0x2069)

        assertThat(Bidi.inOwnDirection("x", Locale.forLanguageTag("ff-Adlm-GN")).first().code).isEqualTo(0x2067)
        assertThat(Bidi.inOwnDirection("x", Locale.forLanguageTag("ff-Latn-SN")).first().code).isEqualTo(0x2066)
    }
}
