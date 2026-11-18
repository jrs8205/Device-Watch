package org.jarsi.devicewatch.system.dream

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The Glow style's battery bar in a language written from the right. */
class GlowStyleTest {

    private val deep = Color(0xFF34D399)
    private val light = Color(0xFF6EE7A8)

    @Test
    fun `left to right the bar's colours run as they are given`() {
        assertThat(batteryBarColors(listOf(deep, light), LayoutDirection.Ltr)).containsExactly(deep, light).inOrder()
    }

    @Test
    fun `right to left the bar fills from the right, so its colours turn round with it`() {
        assertThat(batteryBarColors(listOf(deep, light), LayoutDirection.Rtl)).containsExactly(light, deep).inOrder()
    }
}
