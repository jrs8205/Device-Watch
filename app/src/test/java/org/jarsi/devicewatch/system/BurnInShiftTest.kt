package org.jarsi.devicewatch.system

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BurnInShiftTest {

    @Test
    fun `free shift spans 15 dp each way on both axes`() {
        assertThat(burnInOffset(0.0, 0.0, verticalOnly = false)).isEqualTo(BurnInOffset(-15f, -15f))
        assertThat(burnInOffset(0.5, 0.5, verticalOnly = false)).isEqualTo(BurnInOffset(0f, 0f))
        val far = burnInOffset(0.999999, 0.999999, verticalOnly = false)
        assertThat(far.xDp).isWithin(0.001f).of(15f)
        assertThat(far.yDp).isWithin(0.001f).of(15f)
    }

    @Test
    fun `vertical-only shift keeps the content centred sideways`() {
        listOf(0.0, 0.3, 0.999999).forEach { randomX ->
            assertThat(burnInOffset(randomX, 0.7, verticalOnly = true).xDp).isEqualTo(0f)
        }
    }

    @Test
    fun `vertical-only shift spans 20 dp up and down`() {
        assertThat(burnInOffset(0.4, 0.0, verticalOnly = true).yDp).isEqualTo(-20f)
        assertThat(burnInOffset(0.4, 0.5, verticalOnly = true).yDp).isEqualTo(0f)
        assertThat(burnInOffset(0.4, 0.999999, verticalOnly = true).yDp).isWithin(0.001f).of(20f)
    }
}
