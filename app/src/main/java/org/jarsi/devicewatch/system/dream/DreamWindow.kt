package org.jarsi.devicewatch.system.dream

import android.content.pm.ActivityInfo
import android.view.Window

// What the screensaver changes in its own window. A window tells the window manager of a
// changed attribute through its callback. For a screensaver that callback is DreamService,
// which leaves the method empty and hands over only the flags it sets itself. So each
// change here is handed over by hand; without that the window keeps what it started with.

/** Switches the screensaver's window to wide colour, for the Edge style's Display P3 line. */
internal fun Window.useWideColorGamut() {
    colorMode = ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT
    handOverAttributes()
}

/**
 * Asks for a screen brightness for as long as the screensaver's window is up: 0..1, or
 * [android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE] for the phone's own.
 * Asked again every minute; the window is laid out again only when the answer changes.
 */
internal fun Window.setDreamBrightness(brightness: Float) {
    val current = attributes
    if (current.screenBrightness == brightness) return
    current.screenBrightness = brightness
    attributes = current
    handOverAttributes()
}

/**
 * A window that is not on the screen keeps the change for itself: the window manager
 * refuses a view it does not know, and a screensaver must not stop on that.
 */
private fun Window.handOverAttributes() {
    val decor = peekDecorView() ?: return
    if (decor.isAttachedToWindow) windowManager.updateViewLayout(decor, attributes)
}
