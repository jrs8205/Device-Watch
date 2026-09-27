package org.jarsi.devicewatch.data

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class CameraFacing { BACK, FRONT, EXTERNAL }

enum class LensRole { ULTRAWIDE, MAIN, TELEPHOTO }

/** One physical camera, as far as Camera2 describes it. */
data class CameraLens(
    val facing: CameraFacing,
    val megapixels: Int?,
    /** Focal length as its 35 mm (full-frame) equivalent. */
    val equivalentFocalMm: Int?,
    /** The widest f-number the lens offers. */
    val aperture: Float?,
    val ois: Boolean,
)

data class LabelledLens(val role: LensRole?, val lens: CameraLens)

object CameraLogic {

    /** The diagonal of a 36 x 24 mm frame. */
    private const val FULL_FRAME_DIAGONAL_MM = 43.2666

    private const val ULTRAWIDE_BELOW_MM = 20
    private const val TELEPHOTO_ABOVE_MM = 40

    fun equivalentFocalMm(focalMm: Float, sensorWidthMm: Float, sensorHeightMm: Float): Int? {
        val diagonal = sqrt((sensorWidthMm * sensorWidthMm + sensorHeightMm * sensorHeightMm).toDouble())
        if (focalMm <= 0f || diagonal <= 0.0) return null
        return (focalMm * FULL_FRAME_DIAGONAL_MM / diagonal).roundToInt()
    }

    private fun roleFor(equivalentFocalMm: Int?): LensRole? = when {
        equivalentFocalMm == null -> null
        equivalentFocalMm < ULTRAWIDE_BELOW_MM -> LensRole.ULTRAWIDE
        equivalentFocalMm > TELEPHOTO_ABOVE_MM -> LensRole.TELEPHOTO
        else -> LensRole.MAIN
    }

    /**
     * The lenses of one side, widest first, each named by its field of view. A
     * lone lens needs no name; where two share a field of view, only the one with
     * the most megapixels takes the name, since the other is a macro or depth
     * helper rather than a second camera of that kind.
     */
    fun labelled(lenses: List<CameraLens>): List<LabelledLens> {
        val distinct = lenses.distinct().sortedWith(
            compareBy<CameraLens>({ it.equivalentFocalMm ?: Int.MAX_VALUE }, { -(it.megapixels ?: 0) })
        )
        if (distinct.size <= 1) return distinct.map { LabelledLens(null, it) }
        val holders = distinct
            .groupBy { roleFor(it.equivalentFocalMm) }
            .filterKeys { it != null }
            .mapValues { (_, same) -> same.maxBy { it.megapixels ?: 0 } }
        return distinct.map { lens ->
            val role = roleFor(lens.equivalentFocalMm)
            LabelledLens(role.takeIf { holders[role] === lens }, lens)
        }
    }

    /** "50 MP · f/1,9 · 25 mm · OIS", leaving out what is unknown; null when nothing is known. */
    fun lensText(lens: CameraLens, locale: Locale): String? {
        val parts = buildList {
            lens.megapixels?.let { add("$it MP") }
            lens.aperture?.let { add("f/" + String.format(locale, "%.1f", it)) }
            lens.equivalentFocalMm?.let { add("$it mm") }
        }
        if (parts.isEmpty()) return null
        return (if (lens.ois) parts + "OIS" else parts).joinToString(" · ")
    }

    /** "0,7–8×"; null when the camera cannot zoom at all. */
    fun zoomText(min: Float, max: Float, locale: Locale): String? {
        if (max <= min) return null
        fun ratio(value: Float): String =
            if (abs(value - value.roundToInt()) < 0.05f) value.roundToInt().toString()
            else String.format(locale, "%.1f", value)
        return "${ratio(min)}–${ratio(max)}×"
    }

    /** INFO_SUPPORTED_HARDWARE_LEVEL by its API name, which camera apps also use. */
    fun hardwareLevelName(level: Int): String? = when (level) {
        0 -> "Limited"
        1 -> "Full"
        2 -> "Legacy"
        3 -> "Level 3"
        4 -> "External"
        else -> null
    }
}
