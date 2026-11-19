package org.jarsi.devicewatch.system.dream

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Glow's and Edge's upright layout: the clock block over the battery block. On a low screen
 * a plain column gave the last line, the charge estimate, the few pixels that were left
 * (5 px in Chinese, 4 px in Arabic at 320 x 480 dp). The two blocks give way together instead.
 */
class ClockOverBatteryTest {

    @Test
    fun `with room to spare it is split evenly above, between and below the blocks`() {
        val fit = fitBlocksInHeight(heights = listOf(200, 240), available = 500, minGap = 8)

        assertThat(fit.scale).isEqualTo(1f)
        assertThat(fit.tops).containsExactly(20, 240).inOrder()
    }

    @Test
    fun `blocks that just fit are drawn at their own size`() {
        val fit = fitBlocksInHeight(heights = listOf(200, 232), available = 432, minGap = 8)

        assertThat(fit.scale).isEqualTo(1f)
        assertThat(fit.tops).containsExactly(0, 200).inOrder()
    }

    @Test
    fun `blocks too tall for the screen shrink by the same measure and stay inside it`() {
        val heights = listOf(220, 250)
        val fit = fitBlocksInHeight(heights, available = 432, minGap = 8)

        assertThat(fit.scale).isLessThan(1f)
        assertThat(fit.scale).isWithin(0.001f).of(424f / 470f)
        assertThat(fit.tops.first()).isEqualTo(0)
        // The gap between the two is kept, and the lower block ends where the screen does.
        val firstBottom = fit.tops[0] + heights[0] * fit.scale
        assertThat(fit.tops[1] - firstBottom).isWithin(1f).of(8f)
        assertThat(fit.tops[1] + heights[1] * fit.scale).isAtMost(432.5f)
        assertThat(fit.tops[1] + heights[1] * fit.scale).isAtLeast(431f)
    }
}
