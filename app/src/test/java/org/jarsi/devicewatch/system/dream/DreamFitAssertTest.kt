package org.jarsi.devicewatch.system.dream

import android.app.Application
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.jarsi.devicewatch.codePoint
import org.jarsi.devicewatch.hasOtherDigits
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * External review round 9: the check that every digit drawn by the screensaver is one of 0 to
 * 9 went through a text's UTF-16 units. A digit of Chakma or Adlam takes two, and neither half
 * is a digit by itself: the check passed the very digits it is there to find. The check is
 * checked here, with texts drawn as the screensaver's are.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DreamFitAssertTest {

    @get:Rule
    val compose = createComposeRule()

    private var shown by mutableStateOf("")

    private val chakmaTwenty = codePoint(0x11138) + codePoint(0x11136)
    private val adlamSeven = codePoint(0x1E957)
    private val arabicTwenty = codePoint(0x0662) + codePoint(0x0660)
    private val tibetanEleven = codePoint(0x0F21) + codePoint(0x0F21)

    /** Whether the check fails with [text] on the screen. */
    private fun found(text: String): Boolean {
        shown = text
        compose.waitForIdle()
        return try {
            compose.assertOnlyDigitsZeroToNine("the check")
            false
        } catch (_: AssertionError) {
            true
        }
    }

    @Test
    fun `a digit that takes two UTF-16 units is found on the screen`() {
        compose.setContent { BasicText(shown) }

        assertThat(chakmaTwenty.length).isEqualTo(4)
        assertThat(adlamSeven.length).isEqualTo(2)
        assertWithMessage("Chakma").that(found("$chakmaTwenty:$chakmaTwenty")).isTrue()
        assertWithMessage("Chakma, one digit among the app's").that(found("20:2" + codePoint(0x11136))).isTrue()
        assertWithMessage("Adlam").that(found("Fri $adlamSeven Nov")).isTrue()
    }

    @Test
    fun `a digit of one unit is found as before, and a text without other digits passes`() {
        compose.setContent { BasicText(shown) }

        assertWithMessage("Arabic").that(found("$arabicTwenty:$arabicTwenty")).isTrue()
        assertWithMessage("Tibetan").that(found("ཟླ་$tibetanEleven")).isTrue()
        assertWithMessage("0 to 9").that(found("Fri 20 Nov 20:20")).isFalse()
        // Letters of the same scripts are no digits.
        assertWithMessage("letters").that(found("ཟླ་ " + codePoint(0x11103) + codePoint(0x1E900))).isFalse()
    }

    @Test
    fun `the question itself is asked of whole characters`() {
        assertThat(chakmaTwenty.hasOtherDigits()).isTrue()
        assertThat(adlamSeven.hasOtherDigits()).isTrue()
        assertThat(arabicTwenty.hasOtherDigits()).isTrue()
        assertThat("20:20".hasOtherDigits()).isFalse()
        assertThat("".hasOtherDigits()).isFalse()
    }
}
