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
    fun `the screensaver keeps the phone's brightness until its own is switched on`() {
        assertThat(readDreamOptions(prefs).brightnessPercent).isNull()

        // A level alone, without the switch, changes nothing.
        prefs.edit().putInt("brightness_percent", 55).commit()
        assertThat(readDreamOptions(prefs).brightnessPercent).isNull()

        prefs.edit().putBoolean("own_brightness", true).commit()
        assertThat(readDreamOptions(prefs).brightnessPercent).isEqualTo(55)
    }

    @Test
    fun `own brightness without a stored level starts at the default, and a level stays within 1 to 100`() {
        prefs.edit().putBoolean("own_brightness", true).commit()
        assertThat(readDreamOptions(prefs).brightnessPercent).isEqualTo(30)

        prefs.edit().putInt("brightness_percent", 0).commit()
        assertThat(readDreamOptions(prefs).brightnessPercent).isEqualTo(1)

        prefs.edit().putInt("brightness_percent", 250).commit()
        assertThat(readDreamOptions(prefs).brightnessPercent).isEqualTo(100)
    }

    @Test
    fun `brightness values of another type fall back to their defaults`() {
        // As a backup from elsewhere might store them.
        prefs.edit().putBoolean("own_brightness", true).putString("brightness_percent", "70").commit()
        assertThat(readDreamOptions(prefs).brightnessPercent).isEqualTo(30)

        prefs.edit().putString("own_brightness", "true").putInt("brightness_percent", 70).commit()
        assertThat(readDreamOptions(prefs).brightnessPercent).isNull()
    }

    @Test
    fun `own brightness is asked for in every style, and never while dimmed`() {
        val none = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        for (style in DreamStyle.entries) {
            val own = DreamOptions(style = style, brightnessPercent = 40)
            assertThat(dreamWindowBrightness(own, dimNow = false)).isEqualTo(dreamBrightnessOf(40))
            assertThat(dreamWindowBrightness(own, dimNow = true)).isEqualTo(none)
            assertThat(dreamWindowBrightness(DreamOptions(style = style), dimNow = false)).isEqualTo(none)
        }
    }

    @Test
    fun `the brightness scale runs from nearly dark to the brightest a window can ask for`() {
        assertThat(dreamBrightnessOf(100)).isEqualTo(1f)
        // Never the negative value that means "no brightness of my own".
        assertThat(dreamBrightnessOf(1)).isGreaterThan(0f)
        assertThat(dreamBrightnessOf(1)).isLessThan(0.001f)
        // Every step is brighter than the one before.
        for (percent in 2..100) {
            assertThat(dreamBrightnessOf(percent)).isGreaterThan(dreamBrightnessOf(percent - 1))
        }
        // The eye judges by ratio: half the scale is about a fifth of the light.
        assertThat(dreamBrightnessOf(50)).isWithin(0.03f).of(0.22f)
        // Outside the scale: the nearer end.
        assertThat(dreamBrightnessOf(0)).isEqualTo(dreamBrightnessOf(1))
        assertThat(dreamBrightnessOf(140)).isEqualTo(1f)
    }
}
