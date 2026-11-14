package org.jarsi.devicewatch.system.dream

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.system.DreamPreferences
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The screensaver's options as read from its settings file. The file is part of Android's
 * backup, so a value may be missing, of another type, or written by a newer version.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DreamOptionsTest {

    private val prefs: SharedPreferences = ApplicationProvider.getApplicationContext<Application>()
        .getSharedPreferences(DreamPreferences.PREFS_NAME, Context.MODE_PRIVATE)

    @Test
    fun `empty prefs give the defaults`() {
        assertThat(readDreamOptions(prefs)).isEqualTo(DreamOptions())
        assertThat(DreamOptions().style).isEqualTo(DreamStyle.GLOW)
        assertThat(DreamOptions().layoutSwapped).isFalse()
        assertThat(DreamOptions().verticalShiftOnly).isFalse()
        assertThat(DreamOptions().showDeviceName).isTrue()
        assertThat(DreamOptions().showNotifications).isTrue()
    }

    @Test
    fun `the existing switches are honoured`() {
        prefs.edit()
            .putBoolean("layout_swapped", true)
            .putBoolean("vertical_shift_only", true)
            .putBoolean("show_device_name", false)
            .putBoolean("show_notifications", false)
            .commit()

        val options = readDreamOptions(prefs)

        assertThat(options.layoutSwapped).isTrue()
        assertThat(options.verticalShiftOnly).isTrue()
        assertThat(options.showDeviceName).isFalse()
        assertThat(options.showNotifications).isFalse()
    }

    @Test
    fun `a stored style id is read`() {
        prefs.edit().putString("dream_style", "glow").commit()

        assertThat(readDreamOptions(prefs).style).isEqualTo(DreamStyle.GLOW)
    }

    @Test
    fun `a wrong-typed value falls back to the default`() {
        prefs.edit()
            .putInt("dream_style", 3)
            .putString("layout_swapped", "yes")
            .putInt("show_device_name", 0)
            .commit()

        assertThat(readDreamOptions(prefs)).isEqualTo(DreamOptions())
    }

    @Test
    fun `a style id from a newer version means Glow`() {
        prefs.edit().putString("dream_style", "hologram").commit()

        assertThat(readDreamOptions(prefs).style).isEqualTo(DreamStyle.GLOW)
    }

    @Test
    fun `a switched-off field is not shown`() {
        assertThat(DreamOptions(showDeviceName = false).shows(DreamField.DEVICE_NAME)).isFalse()
        assertThat(DreamOptions(showNotifications = false).shows(DreamField.NOTIFICATIONS)).isFalse()
        assertThat(DreamOptions().shows(DreamField.DEVICE_NAME)).isTrue()
        assertThat(DreamOptions().shows(DreamField.NOTIFICATIONS)).isTrue()
    }

    @Test
    fun `a field the style cannot show is not shown`() {
        assertThat(DreamOptions().shows(DreamField.CALENDAR)).isFalse()
    }
}
