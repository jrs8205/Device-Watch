package org.jarsi.devicewatch.system.dream

import android.app.Application
import androidx.compose.ui.graphics.PathMeasure
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The path the Edge style draws must be the route its arithmetic describes: the line is
 * cut from the path, and its head is placed by the arithmetic. A real path needs the
 * native graphics mode.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EdgePathTest {

    private fun measure(width: Float, height: Float, inset: Float, radius: Float): PathMeasure =
        PathMeasure().apply { setPath(edgePath(width, height, inset, radius), forceClosed = false) }

    @Test
    fun `the drawn path is as long as the computed one`() {
        val cases = listOf(
            listOf(400f, 800f, 4f, 20f),
            listOf(1080f, 2400f, 34f, 90f),
            listOf(800f, 400f, 13f, 34f),
            listOf(112f, 60f, 13f, 17f),
            listOf(300f, 300f, 10f, 0f),
        )
        for ((width, height, inset, radius) in cases) {
            assertWithMessage("$width x $height, inset $inset, radius $radius")
                .that(measure(width, height, inset, radius).length)
                .isWithin(1f)
                .of(edgeLength(width, height, inset, radius))
        }
    }

    @Test
    fun `the drawn path runs through the computed points`() {
        val width = 400f
        val height = 800f
        val inset = 7f
        val radius = 21f
        val measure = measure(width, height, inset, radius)
        for (step in 0..20) {
            val fraction = step / 20f
            val onPath = measure.getPosition(fraction * measure.length)
            val (x, y) = edgePoint(fraction, width, height, inset, radius)
            assertWithMessage("fraction $fraction x").that(onPath.x).isWithin(0.75f).of(x)
            assertWithMessage("fraction $fraction y").that(onPath.y).isWithin(0.75f).of(y)
        }
    }

    @Test
    fun `the path starts and ends at the bottom centre`() {
        val measure = measure(400f, 800f, 4f, 20f)

        val start = measure.getPosition(0f)
        val end = measure.getPosition(measure.length)
        assertThat(start.x).isWithin(0.01f).of(200f)
        assertThat(start.y).isWithin(0.01f).of(796f)
        assertThat(end.x).isWithin(0.01f).of(200f)
        assertThat(end.y).isWithin(0.01f).of(796f)
    }
}
