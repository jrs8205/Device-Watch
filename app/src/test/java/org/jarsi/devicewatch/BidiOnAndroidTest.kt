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

    // With the app in English on a phone set to Fulah in Adlam, a tile's line was left
    // unprotected: the phone's direction was taken for left to right.
    @Test
    fun `a line the system draws is kept on a phone whose language is written in Adlam`() {
        val line = Bidi.forHost("1.5 / 2.4 GB", hostLocale = Locale.forLanguageTag("ff-Adlm-GN"), locale = Locale.US)

        assertThat(line.first().code).isEqualTo(0x2066)
        assertThat(line.last().code).isEqualTo(0x2069)
    }
}
