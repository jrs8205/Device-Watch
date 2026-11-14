package org.jarsi.devicewatch.system.dream

import android.app.Activity
import android.app.Application
import android.content.pm.ActivityInfo
import android.view.Window
import android.view.WindowManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * The screensaver's window. A window tells the window manager of a changed attribute
 * through its callback, and for a screensaver that callback is DreamService, which leaves
 * the method empty. What the screensaver changes itself has to reach the screen anyway.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DreamWindowTest {

    /** A window on the screen whose callback ignores attribute changes, as DreamService's does. */
    private fun dreamLikeWindow(): Window {
        val window = Robolectric.buildActivity(Activity::class.java).setup().get().window
        val original = window.callback
        window.callback = object : Window.Callback by original {
            override fun onWindowAttributesChanged(attrs: WindowManager.LayoutParams?) = Unit
        }
        return window
    }

    /** The attributes the window is shown with: the view root's own copy. */
    private fun shownAttributes(window: Window): WindowManager.LayoutParams =
        ReflectionHelpers.getField(window.decorView.parent, "mWindowAttributes")

    @Test
    fun `wide colour reaches the screen though the window's callback ignores it`() {
        val window = dreamLikeWindow()
        assertThat(shownAttributes(window).colorMode).isEqualTo(ActivityInfo.COLOR_MODE_DEFAULT)

        window.useWideColorGamut()

        assertThat(shownAttributes(window).colorMode).isEqualTo(ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT)
    }
}
