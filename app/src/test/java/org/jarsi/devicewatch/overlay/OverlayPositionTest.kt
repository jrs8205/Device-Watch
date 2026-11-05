package org.jarsi.devicewatch.overlay

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

/** The floating card's stored place: "x,y" as shares of the screen, so it survives a change of screen size. */
class OverlayPositionTest {

    @Test
    fun `a stored pair parses to shares`() {
        assertThat(overlayPositionOf("0.25,0.5")).isEqualTo(OverlayPosition(0.25f, 0.5f))
        assertThat(overlayPositionOf("1,0")).isEqualTo(OverlayPosition(1f, 0f))
    }

    @Test
    fun `anything but two shares in range is no position`() {
        assertThat(overlayPositionOf(null)).isNull()
        assertThat(overlayPositionOf("")).isNull()
        assertThat(overlayPositionOf("a,b")).isNull()
        assertThat(overlayPositionOf("1.5,0")).isNull()
        assertThat(overlayPositionOf("0.5")).isNull()
    }

    @Test
    fun `the stored form uses a dot whatever the phone's language`() {
        val saved = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("fi-FI"))
        try {
            val stored = overlayPositionToStored(OverlayPosition(0.25f, 0.5f))
            assertThat(stored).isEqualTo("0.2500,0.5000")
            assertThat(overlayPositionOf(stored)).isEqualTo(OverlayPosition(0.25f, 0.5f))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `a position round trips through its stored form`() {
        val stored = overlayPositionToStored(OverlayPosition(0.333f, 1f))
        val parsed = overlayPositionOf(stored)!!
        assertThat(parsed.xFraction).isWithin(0.001f).of(0.333f)
        assertThat(parsed.yFraction).isWithin(0.001f).of(1f)
    }
}
