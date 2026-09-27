package org.jarsi.devicewatch.widget

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.data.UNAVAILABLE_DOUBLE
import org.jarsi.devicewatch.data.UNAVAILABLE_TEXT
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale

class CompactFitTest {

    private lateinit var originalLocale: Locale

    @Before
    fun pinLocale() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `compact amounts keep at most three significant digits`() {
        assertThat(compactDataAmountText(0.00015)).isEqualTo("0.2 MB")
        assertThat(compactDataAmountText(15.0 / 1024.0)).isEqualTo("15 MB")
        assertThat(compactDataAmountText(0.5)).isEqualTo("512 MB")
        assertThat(compactDataAmountText(1.5)).isEqualTo("1.5 GB")
        assertThat(compactDataAmountText(9.94)).isEqualTo("9.9 GB")
        assertThat(compactDataAmountText(12.3)).isEqualTo("12 GB")
        assertThat(compactDataAmountText(100.0)).isEqualTo("100 GB")
    }

    @Test
    fun `just under a gigabyte reads as gigabytes, never as a four-digit MB figure`() {
        assertThat(compactDataAmountText(0.99)).isEqualTo("1.0 GB")
    }

    @Test
    fun `a rounded-up amount moves to the shorter form`() {
        // 9.96 rounds to "10.0" with one decimal, which is four digits wide.
        assertThat(compactDataAmountText(9.96)).isEqualTo("10 GB")
    }

    @Test
    fun `compact amounts keep the unavailable and zero forms`() {
        assertThat(compactDataAmountText(UNAVAILABLE_DOUBLE)).isEqualTo(UNAVAILABLE_TEXT)
        assertThat(compactDataAmountText(0.0)).isEqualTo("0 MB")
    }

    @Test
    fun `text that fits keeps its size`() {
        assertThat(shrinkToFitDp(sizeDp = 16f, widthAtSizeDp = 40f, availableDp = 64f)).isEqualTo(16f)
    }

    @Test
    fun `text that would overflow shrinks in proportion`() {
        // 100.00 GB at 16 dp measured 75 dp in a 39 dp cell (Codex round 3).
        assertThat(shrinkToFitDp(sizeDp = 16f, widthAtSizeDp = 75f, availableDp = 39f))
            .isWithin(1e-4f).of(16f * 39f / 75f)
    }

    @Test
    fun `unmeasurable input leaves the size alone`() {
        assertThat(shrinkToFitDp(16f, widthAtSizeDp = 0f, availableDp = 39f)).isEqualTo(16f)
        assertThat(shrinkToFitDp(16f, widthAtSizeDp = 40f, availableDp = 0f)).isEqualTo(16f)
        assertThat(shrinkToFitDp(16f, widthAtSizeDp = Float.NaN, availableDp = 39f)).isEqualTo(16f)
    }
}
