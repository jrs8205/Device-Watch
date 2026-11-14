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

private fun Window.handOverAttributes() {
    windowManager.updateViewLayout(decorView, attributes)
}
