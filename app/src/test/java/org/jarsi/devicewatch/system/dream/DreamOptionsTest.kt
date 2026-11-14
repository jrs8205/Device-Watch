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

    @Test
    fun `the automatic Night style is off until it is switched on`() {
        assertThat(readDreamOptions(prefs).nightStyleAuto).isFalse()

        prefs.edit().putBoolean("night_style_auto", true).commit()

        assertThat(readDreamOptions(prefs).nightStyleAuto).isTrue()
    }

    @Test
    fun `the night window is active only with night dim on`() {
        val lateEvening = 23 * 60
        val noon = 12 * 60

        // Night dim is off: no window, and the manual dim switch does not make one.
        assertThat(nightWindowActive(prefs, lateEvening)).isFalse()
        prefs.edit().putBoolean("dim_screensaver", true).commit()
        assertThat(nightWindowActive(prefs, lateEvening)).isFalse()

        // Night dim on, with its default 22:00 to 07:00.
        prefs.edit().putBoolean("night_dim", true).commit()
        assertThat(nightWindowActive(prefs, lateEvening)).isTrue()
        assertThat(nightWindowActive(prefs, noon)).isFalse()

        // The user's own times.
        prefs.edit().putInt("night_dim_start_minutes", 11 * 60).putInt("night_dim_end_minutes", 13 * 60).commit()
        assertThat(nightWindowActive(prefs, noon)).isTrue()
        assertThat(nightWindowActive(prefs, lateEvening)).isFalse()
    }

    @Test
    fun `the screensaver dims by the manual switch or inside the night window`() {
        val noon = 12 * 60
        assertThat(shouldDimNow(prefs, noon)).isFalse()

        prefs.edit().putBoolean("night_dim", true).commit()
        assertThat(shouldDimNow(prefs, noon)).isFalse()
        assertThat(shouldDimNow(prefs, 23 * 60)).isTrue()

        prefs.edit().putBoolean("dim_screensaver", true).commit()
        assertThat(shouldDimNow(prefs, noon)).isTrue()
    }

    @Test
    fun `the new switches default to on and the accent to sky`() {
        val options = readDreamOptions(prefs)

        assertThat(options.showSeconds).isTrue()
        assertThat(options.showDate).isTrue()
        assertThat(options.showAlarm).isTrue()
        assertThat(options.showBatteryDetails).isTrue()
        assertThat(options.showEstimate).isTrue()
        assertThat(options.accent).isEqualTo(DreamAccent.SKY)
    }

    @Test
    fun `each new key is read`() {
        prefs.edit()
            .putBoolean("show_seconds", false)
            .putBoolean("show_date", false)
            .putBoolean("show_alarm", false)
            .putBoolean("show_battery_details", false)
            .putBoolean("show_estimate", false)
            .putString("accent_color", "rose")
            .commit()

        val options = readDreamOptions(prefs)

        assertThat(options.showSeconds).isFalse()
        assertThat(options.showDate).isFalse()
        assertThat(options.showAlarm).isFalse()
        assertThat(options.showBatteryDetails).isFalse()
        assertThat(options.showEstimate).isFalse()
        assertThat(options.accent).isEqualTo(DreamAccent.ROSE)
    }

    @Test
    fun `an unknown accent id means sky`() {
        assertThat(dreamAccentOf(null)).isEqualTo(DreamAccent.SKY)
        assertThat(dreamAccentOf("")).isEqualTo(DreamAccent.SKY)
        assertThat(dreamAccentOf("ultraviolet")).isEqualTo(DreamAccent.SKY)
        for (accent in DreamAccent.entries) {
            assertThat(dreamAccentOf(accent.id)).isEqualTo(accent)
        }
        assertThat(DreamAccent.entries.map { it.id }).containsNoDuplicates()

        prefs.edit().putInt("accent_color", 4).commit()
        assertThat(readDreamOptions(prefs).accent).isEqualTo(DreamAccent.SKY)
    }

    @Test
    fun `every field's switch decides whether it is shown`() {
        val everything = DreamOptions(style = DreamStyle.GAUGES, showCalendar = true)
        assertThat(DreamField.entries.filter { !everything.shows(it) }).isEmpty()

        assertThat(everything.copy(showCalendar = false).shows(DreamField.CALENDAR)).isFalse()

        assertThat(everything.copy(showDeviceName = false).shows(DreamField.DEVICE_NAME)).isFalse()
        assertThat(everything.copy(showSeconds = false).shows(DreamField.SECONDS)).isFalse()
        assertThat(everything.copy(showDate = false).shows(DreamField.DATE)).isFalse()
        assertThat(everything.copy(showAlarm = false).shows(DreamField.ALARM)).isFalse()
        assertThat(everything.copy(showNotifications = false).shows(DreamField.NOTIFICATIONS)).isFalse()
        assertThat(everything.copy(showBatteryDetails = false).shows(DreamField.BATTERY_DETAILS)).isFalse()
        assertThat(everything.copy(showEstimate = false).shows(DreamField.ESTIMATE)).isFalse()
    }

    @Test
    fun `a switch cannot bring back what the style has no place for`() {
        val minimal = DreamOptions(style = DreamStyle.MINIMAL, showSeconds = true, showAlarm = true)

        assertThat(minimal.shows(DreamField.SECONDS)).isFalse()
        assertThat(minimal.shows(DreamField.ALARM)).isFalse()
        assertThat(minimal.shows(DreamField.DATE)).isTrue()
    }

    @Test
    fun `the calendar is off until it is switched on, and its titles show unless switched off`() {
        val defaults = readDreamOptions(prefs)
        assertThat(defaults.showCalendar).isFalse()
        assertThat(defaults.showCalendarTitles).isTrue()

        prefs.edit().putBoolean("show_calendar", true).putBoolean("show_calendar_titles", false).commit()

        val chosen = readDreamOptions(prefs)
        assertThat(chosen.showCalendar).isTrue()
        assertThat(chosen.showCalendarTitles).isFalse()
    }

    @Test
    fun `Glow never shows the calendar even with the switch on`() {
        assertThat(DreamOptions(style = DreamStyle.GLOW, showCalendar = true).shows(DreamField.CALENDAR)).isFalse()
        assertThat(DreamOptions(style = DreamStyle.GAUGES, showCalendar = true).shows(DreamField.CALENDAR)).isTrue()
        assertThat(DreamOptions(style = DreamStyle.GAUGES).shows(DreamField.CALENDAR)).isFalse()
    }

    @Test
    fun `Edge's full brightness is off until it is switched on`() {
        assertThat(readDreamOptions(prefs).edgeFullBrightness).isFalse()

        prefs.edit().putBoolean("edge_full_brightness", true).commit()
        assertThat(readDreamOptions(prefs).edgeFullBrightness).isTrue()

        // Stored with another type, as a backup from elsewhere might: the default.
        prefs.edit().putString("edge_full_brightness", "true").commit()
        assertThat(readDreamOptions(prefs).edgeFullBrightness).isFalse()
    }

    @Test
    fun `full brightness is asked for only by Edge, only when switched on, and never while dimmed`() {
        val none = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        val edge = DreamOptions(style = DreamStyle.EDGE, edgeFullBrightness = true)

        assertThat(dreamWindowBrightness(edge, dimNow = false)).isEqualTo(1f)
        assertThat(dreamWindowBrightness(edge, dimNow = true)).isEqualTo(none)
        assertThat(dreamWindowBrightness(edge.copy(edgeFullBrightness = false), dimNow = false)).isEqualTo(none)
        for (style in DreamStyle.entries - DreamStyle.EDGE) {
            assertThat(dreamWindowBrightness(edge.copy(style = style), dimNow = false)).isEqualTo(none)
        }
    }
}
