package org.jarsi.devicewatch.quick

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Icon

private const val ICON_PX = 96

/**
 * [text] drawn as a tile icon, as large as fits. White on transparent: the panel
 * tints tile icons by their shape, as it does the app's own vector icons.
 */
fun quickTileValueIcon(text: String): Icon {
    val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        textSize = ICON_PX * 0.62f
    }
    val width = paint.measureText(text)
    if (width > ICON_PX * 0.98f) paint.textSize *= ICON_PX * 0.98f / width
    val bounds = Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    Canvas(bitmap).drawText(text, ICON_PX / 2f, ICON_PX / 2f - bounds.exactCenterY(), paint)
    return Icon.createWithBitmap(bitmap)
}
