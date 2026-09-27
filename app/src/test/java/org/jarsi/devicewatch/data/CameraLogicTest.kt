package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class CameraLogicTest {

    private val fi = Locale.forLanguageTag("fi-FI")

    private fun lens(mp: Int?, mm: Int?, f: Float? = 1.8f, ois: Boolean = false, facing: CameraFacing = CameraFacing.BACK) =
        CameraLens(facing, megapixels = mp, equivalentFocalMm = mm, aperture = f, ois = ois)

    @Test
    fun `the focal length reads as its 35 mm equivalent from the sensor size`() {
        // 6.9 mm on a 9.8 x 7.4 mm sensor: a 24 mm lens on full frame.
        assertThat(CameraLogic.equivalentFocalMm(6.9f, 9.8f, 7.4f)).isEqualTo(24)
    }

    @Test
    fun `an unknown sensor size gives no equivalent`() {
        assertThat(CameraLogic.equivalentFocalMm(6.9f, 0f, 0f)).isNull()
        assertThat(CameraLogic.equivalentFocalMm(0f, 9.8f, 7.4f)).isNull()
    }

    @Test
    fun `rear lenses are named by their field of view, widest first`() {
        val lenses = listOf(lens(50, 25), lens(12, 70), lens(13, 14))

        assertThat(CameraLogic.labelled(lenses).map { it.role to it.lens.megapixels }).containsExactly(
            LensRole.ULTRAWIDE to 13,
            LensRole.MAIN to 50,
            LensRole.TELEPHOTO to 12,
        ).inOrder()
    }

    @Test
    fun `a small sensor beside the main camera is listed without a name`() {
        // A 2 MP macro at a normal focal length is not a second main camera.
        val lenses = listOf(lens(2, 26), lens(64, 26))

        val labelled = CameraLogic.labelled(lenses)

        assertThat(labelled.map { it.role to it.lens.megapixels }).containsExactly(
            LensRole.MAIN to 64,
            null to 2,
        ).inOrder()
    }

    @Test
    fun `a single lens needs no name, and the same lens twice is listed once`() {
        val labelled = CameraLogic.labelled(listOf(lens(13, 20), lens(13, 20)))

        assertThat(labelled.map { it.role }).containsExactly(null)
    }

    @Test
    fun `a lens reads as megapixels, aperture, focal length and stabilisation`() {
        assertThat(CameraLogic.lensText(lens(50, 25, f = 1.9f, ois = true), fi)).isEqualTo("50 MP · f/1,9 · 25 mm · OIS")
        assertThat(CameraLogic.lensText(lens(null, null, f = null), fi)).isNull()
        assertThat(CameraLogic.lensText(lens(8, null, f = null), fi)).isEqualTo("8 MP")
    }

    @Test
    fun `the zoom range drops needless decimals`() {
        assertThat(CameraLogic.zoomText(0.67f, 8f, fi)).isEqualTo("0,7–8×")
        assertThat(CameraLogic.zoomText(1f, 10f, fi)).isEqualTo("1–10×")
        assertThat(CameraLogic.zoomText(1f, 1f, fi)).isNull()
    }

    @Test
    fun `camera2 support levels read by their API names`() {
        assertThat(CameraLogic.hardwareLevelName(3)).isEqualTo("Level 3")
        assertThat(CameraLogic.hardwareLevelName(1)).isEqualTo("Full")
        assertThat(CameraLogic.hardwareLevelName(0)).isEqualTo("Limited")
        assertThat(CameraLogic.hardwareLevelName(2)).isEqualTo("Legacy")
        assertThat(CameraLogic.hardwareLevelName(4)).isEqualTo("External")
        assertThat(CameraLogic.hardwareLevelName(9)).isNull()
    }
}
