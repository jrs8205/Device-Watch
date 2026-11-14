package org.jarsi.devicewatch.presentation.ui

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import android.view.WindowManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.system.DreamPreferences
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The screensaver section of the settings: each control writes the key the screensaver
 * reads. The screen is tall so the whole section is laid out at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w400dp-h3000dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreensaverSettingsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Application get() = ApplicationProvider.getApplicationContext()

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(DreamPreferences.PREFS_NAME, Context.MODE_PRIVATE)

    private fun show(notificationAccessEnabled: Boolean = true) {
        compose.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ScreensaverSettingsSection(
                        notificationAccessEnabled = notificationAccessEnabled,
                        onOpenDreamSettings = {},
                    )
                }
            }
        }
    }

    /** The switch on the row with this title: the one whose top edge is level with it. */
    private fun switchBeside(@StringRes title: Int): SemanticsNodeInteraction {
        val titleTop = compose.onNodeWithText(context.getString(title)).fetchSemanticsNode().boundsInRoot.top
        val switches = compose.onAllNodes(isToggleable())
        val nodes = switches.fetchSemanticsNodes()
        val index = nodes.indices.minBy { abs(nodes[it].boundsInRoot.top - titleTop) }
        return switches[index]
    }

    private fun SemanticsNodeInteraction.toggle() = performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun `the flip switch writes the key the old button wrote`() {
        show()

        switchBeside(R.string.dream_flip_title).toggle()
        assertThat(prefs.getBoolean("layout_swapped", false)).isTrue()
        switchBeside(R.string.dream_flip_title).assertIsOn()

        switchBeside(R.string.dream_flip_title).toggle()
        assertThat(prefs.getBoolean("layout_swapped", true)).isFalse()
        switchBeside(R.string.dream_flip_title).assertIsOff()
    }

    @Test
    fun `a flip chosen with the old button shows as on`() {
        prefs.edit().putBoolean("layout_swapped", true).commit()

        show()

        switchBeside(R.string.dream_flip_title).assertIsOn()
    }

    @Test
    fun `the existing switches still write their keys`() {
        show()

        // Title, key, and the value before the first toggle.
        val rows = listOf(
            Triple(R.string.force_portrait_title, "force_portrait", false),
            Triple(R.string.dim_screensaver_title, "dim_screensaver", false),
            Triple(R.string.night_dim_title, "night_dim", false),
            Triple(R.string.dream_notifications_title, "show_notifications", true),
            Triple(R.string.dream_vertical_shift_title, "vertical_shift_only", false),
            Triple(R.string.dream_device_name_title, "show_device_name", true),
            Triple(R.string.dream_start_on_charge_title, "start_on_charge", false),
        )
        for ((title, key, before) in rows) {
            switchBeside(title).toggle()
            compose.waitForIdle()

            assertWithMessage(key).that(prefs.getBoolean(key, before)).isEqualTo(!before)
        }
    }

    /** The style picker's item with this name. */
    private fun styleItem(@StringRes name: Int): SemanticsNodeInteraction =
        compose.onNode(hasText(context.getString(name)) and isSelectable())

    @Test
    fun `choosing a style writes its id`() {
        show()

        styleItem(R.string.dream_style_minimal).toggle()
        assertThat(prefs.getString("dream_style", null)).isEqualTo("minimal")
        styleItem(R.string.dream_style_minimal).assertIsSelected()
        styleItem(R.string.dream_style_glow).assertIsNotSelected()

        styleItem(R.string.dream_style_glow).toggle()
        assertThat(prefs.getString("dream_style", null)).isEqualTo("glow")
    }

    @Test
    fun `the stored style is the selected one`() {
        prefs.edit().putString("dream_style", "minimal").commit()

        show()

        styleItem(R.string.dream_style_minimal).assertIsSelected()
        styleItem(R.string.dream_style_glow).assertIsNotSelected()
    }

    @Test
    fun `without a stored style Glow is selected`() {
        show()

        styleItem(R.string.dream_style_glow).assertIsSelected()
        assertThat(prefs.contains("dream_style")).isFalse()
    }

    @Test
    fun `the automatic Night style can be switched on only with night dim`() {
        show()
        switchBeside(R.string.dream_night_style_title).assertIsNotEnabled()

        switchBeside(R.string.night_dim_title).toggle()
        switchBeside(R.string.dream_night_style_title).assertIsEnabled()

        switchBeside(R.string.dream_night_style_title).toggle()
        assertThat(prefs.getBoolean("night_style_auto", false)).isTrue()
    }

    /** The accent row's swatch with this colour name. */
    private fun accentItem(@StringRes name: Int): SemanticsNodeInteraction =
        compose.onNode(hasContentDescription(context.getString(name)) and isSelectable())

    @Test
    fun `a switch the style cannot show is disabled`() {
        prefs.edit().putString("dream_style", "minimal").commit()

        show()

        for (title in listOf(
            R.string.dream_device_name_title,
            R.string.dream_show_seconds,
            R.string.dream_show_alarm,
            R.string.dream_show_estimate,
        )) {
            switchBeside(title).assertIsNotEnabled()
        }
        for (title in listOf(
            R.string.dream_show_date,
            R.string.dream_notifications_title,
            R.string.dream_show_battery_details,
        )) {
            switchBeside(title).assertIsEnabled()
        }
    }

    @Test
    fun `choosing another style changes which switches can be used`() {
        show()
        switchBeside(R.string.dream_show_seconds).assertIsEnabled()

        styleItem(R.string.dream_style_night).toggle()

        switchBeside(R.string.dream_show_seconds).assertIsNotEnabled()
        switchBeside(R.string.dream_show_alarm).assertIsEnabled()
    }

    @Test
    fun `the new switches write their keys`() {
        prefs.edit().putString("dream_style", "gauges").commit()
        show()

        val rows = listOf(
            R.string.dream_show_seconds to "show_seconds",
            R.string.dream_show_date to "show_date",
            R.string.dream_show_alarm to "show_alarm",
            R.string.dream_show_battery_details to "show_battery_details",
            R.string.dream_show_estimate to "show_estimate",
        )
        for ((title, key) in rows) {
            switchBeside(title).assertIsOn()
            switchBeside(title).toggle()
            compose.waitForIdle()

            assertWithMessage(key).that(prefs.getBoolean(key, true)).isFalse()
        }
    }

    @Test
    fun `choosing an accent writes its id`() {
        show()
        accentItem(R.string.dream_accent_sky).assertIsSelected()

        accentItem(R.string.dream_accent_rose).toggle()

        assertThat(prefs.getString("accent_color", null)).isEqualTo("rose")
        accentItem(R.string.dream_accent_rose).assertIsSelected()
        accentItem(R.string.dream_accent_sky).assertIsNotSelected()
    }

    @Test
    fun `the accent row is disabled without seconds`() {
        show()
        accentItem(R.string.dream_accent_teal).assertIsEnabled()

        // The seconds are switched off...
        switchBeside(R.string.dream_show_seconds).toggle()
        accentItem(R.string.dream_accent_teal).assertIsNotEnabled()

        // ...or the style has none.
        switchBeside(R.string.dream_show_seconds).toggle()
        accentItem(R.string.dream_accent_teal).assertIsEnabled()
        styleItem(R.string.dream_style_minimal).toggle()
        accentItem(R.string.dream_accent_teal).assertIsNotEnabled()
    }

    private fun countOf(@StringRes text: Int): Int =
        compose.onAllNodesWithText(context.getString(text)).fetchSemanticsNodes().size

    @Test
    fun `a stored value of the wrong type does not stop the settings page`() {
        // The settings file travels in Android's backup; a value of another type under one of
        // the new keys must read as the default here, as it does in the screensaver.
        prefs.edit()
            .putString("dream_style", "gauges")
            .putString("show_seconds", "yes")
            .putInt("show_calendar", 1)
            .commit()

        show()

        switchBeside(R.string.dream_show_seconds).assertIsOn()
        switchBeside(R.string.dream_calendar_title).assertIsOff()

        // Using the switch puts a proper value in its place.
        switchBeside(R.string.dream_show_seconds).toggle()
        assertThat(prefs.getBoolean("show_seconds", true)).isFalse()
    }

    @Test
    fun `the calendar switches appear only for Gauges`() {
        show()
        assertThat(countOf(R.string.dream_calendar_title)).isEqualTo(0)
        assertThat(countOf(R.string.dream_calendar_titles_title)).isEqualTo(0)

        styleItem(R.string.dream_style_gauges).toggle()
        assertThat(countOf(R.string.dream_calendar_title)).isEqualTo(1)
        assertThat(countOf(R.string.dream_calendar_titles_title)).isEqualTo(1)

        styleItem(R.string.dream_style_ring).toggle()
        assertThat(countOf(R.string.dream_calendar_title)).isEqualTo(0)
    }

    private val isSlider = SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)

    private fun brightnessLabel(percent: Int): String = context.getString(R.string.dream_brightness_level, percent)

    @Test
    fun `the brightness slider comes with its switch and writes the level`() {
        show()
        compose.onAllNodes(isSlider).assertCountEquals(0)
        switchBeside(R.string.dream_brightness_title).assertIsOff()

        switchBeside(R.string.dream_brightness_title).toggle()
        assertThat(prefs.getBoolean("own_brightness", false)).isTrue()
        // Until the slider is moved the level is the default.
        compose.onNodeWithText(brightnessLabel(30)).assertExists()

        compose.onNode(isSlider).performSemanticsAction(SemanticsActions.SetProgress) { it(70f) }
        compose.waitForIdle()
        assertThat(prefs.getInt("brightness_percent", -1)).isEqualTo(70)
        compose.onNodeWithText(brightnessLabel(70)).assertExists()

        // Switched off, the slider goes and the level is kept for the next time.
        switchBeside(R.string.dream_brightness_title).toggle()
        assertThat(prefs.getBoolean("own_brightness", true)).isFalse()
        compose.onAllNodes(isSlider).assertCountEquals(0)
        assertThat(prefs.getInt("brightness_percent", -1)).isEqualTo(70)
    }

    @Test
    fun `the stored brightness level is where the slider stands`() {
        prefs.edit().putBoolean("own_brightness", true).putInt("brightness_percent", 45).commit()

        show()

        compose.onNodeWithText(brightnessLabel(45)).assertExists()
    }

    @Test
    fun `a brightness level of the wrong type shows as the default`() {
        prefs.edit().putBoolean("own_brightness", true).putString("brightness_percent", "70").commit()

        show()

        compose.onNodeWithText(brightnessLabel(30)).assertExists()
    }

    @Test
    fun `moving the brightness slider shows that brightness on the page, and letting go gives the phone's back`() {
        prefs.edit().putBoolean("own_brightness", true).putInt("brightness_percent", 30).commit()
        show()
        val window = compose.activity.window
        val none = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        assertThat(window.attributes.screenBrightness).isEqualTo(none)

        compose.onNode(isSlider).performTouchInput {
            down(center)
            moveBy(Offset(60f, 0f))
        }
        compose.waitForIdle()
        val shown = window.attributes.screenBrightness
        assertThat(shown).isGreaterThan(0f)
        assertThat(shown).isAtMost(1f)

        compose.onNode(isSlider).performTouchInput { up() }
        compose.waitForIdle()
        assertThat(window.attributes.screenBrightness).isEqualTo(none)
        // From the middle of the slider to the right of it.
        assertThat(prefs.getInt("brightness_percent", -1)).isGreaterThan(50)
    }

    /** Moves the slider and switches Own brightness off while that finger is still down. */
    private fun switchOffInTheMiddleOfAMove() {
        compose.onNode(isSlider).performTouchInput {
            down(center)
            moveBy(Offset(60f, 0f))
        }
        compose.waitForIdle()
        assertThat(compose.activity.window.attributes.screenBrightness).isGreaterThan(0f)

        switchBeside(R.string.dream_brightness_title).toggle()
        compose.waitForIdle()
        compose.onAllNodes(isSlider).assertCountEquals(0)
        // The finger lifts where the slider was; nothing is left there to hear of it.
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
    }

    @Test
    fun `switching own brightness off in the middle of a move gives the phone's brightness back`() {
        prefs.edit().putBoolean("own_brightness", true).putInt("brightness_percent", 30).commit()
        show()

        switchOffInTheMiddleOfAMove()

        assertThat(compose.activity.window.attributes.screenBrightness)
            .isEqualTo(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
    }

    @Test
    fun `a move cut short by the switch leaves the level as it was stored`() {
        prefs.edit().putBoolean("own_brightness", true).putInt("brightness_percent", 30).commit()
        show()

        switchOffInTheMiddleOfAMove()
        assertThat(prefs.getInt("brightness_percent", -1)).isEqualTo(30)

        // Switched on again, the slider stands where the screensaver's level is.
        switchBeside(R.string.dream_brightness_title).toggle()
        compose.onNodeWithText(brightnessLabel(30)).assertExists()
    }

    @Test
    fun `no permission is requested until the calendar switch is turned on`() {
        prefs.edit().putString("dream_style", "gauges").commit()
        show()
        assertThat(shadowOf(compose.activity).lastRequestedPermission).isNull()

        switchBeside(R.string.dream_calendar_title).toggle()
        compose.waitForIdle()

        assertThat(prefs.getBoolean("show_calendar", false)).isTrue()
        val request = shadowOf(compose.activity).lastRequestedPermission
        assertThat(request).isNotNull()
        assertThat(request.requestedPermissions.toList()).containsExactly(Manifest.permission.READ_CALENDAR)
    }

    @Test
    fun `with the permission already granted the calendar switch asks for nothing`() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_CALENDAR)
        prefs.edit().putString("dream_style", "gauges").commit()
        show()

        switchBeside(R.string.dream_calendar_title).toggle()
        compose.waitForIdle()

        assertThat(prefs.getBoolean("show_calendar", false)).isTrue()
        assertThat(shadowOf(compose.activity).lastRequestedPermission).isNull()
        assertThat(countOf(R.string.dream_calendar_permission_missing)).isEqualTo(0)
    }

    @Test
    fun `switching the calendar off asks for nothing`() {
        prefs.edit().putString("dream_style", "gauges").putBoolean("show_calendar", true).commit()
        show()

        switchBeside(R.string.dream_calendar_title).toggle()
        compose.waitForIdle()

        assertThat(prefs.getBoolean("show_calendar", true)).isFalse()
        assertThat(shadowOf(compose.activity).lastRequestedPermission).isNull()
    }

    @Test
    fun `the red note shows when the calendar is switched on without the permission`() {
        // A setting restored from a backup on a new phone: on, but never granted here.
        prefs.edit().putString("dream_style", "gauges").putBoolean("show_calendar", true).commit()

        show()

        assertThat(countOf(R.string.dream_calendar_permission_missing)).isEqualTo(1)
        switchBeside(R.string.dream_calendar_title).assertIsOn()
        // Showing the page asks for nothing by itself.
        assertThat(shadowOf(compose.activity).lastRequestedPermission).isNull()
    }

    @Test
    fun `the event titles switch writes its key and needs the calendar switched on`() {
        prefs.edit().putString("dream_style", "gauges").commit()
        show()
        switchBeside(R.string.dream_calendar_titles_title).assertIsNotEnabled()

        switchBeside(R.string.dream_calendar_title).toggle()
        switchBeside(R.string.dream_calendar_titles_title).assertIsEnabled()
        switchBeside(R.string.dream_calendar_titles_title).assertIsOn()

        switchBeside(R.string.dream_calendar_titles_title).toggle()
        assertThat(prefs.getBoolean("show_calendar_titles", true)).isFalse()
    }

    @Test
    fun `the missing notification access is pointed out only while the icons are switched on`() {
        show(notificationAccessEnabled = false)
        val note = context.getString(R.string.dream_notifications_access_missing)

        compose.onNodeWithText(note).assertExists()

        switchBeside(R.string.dream_notifications_title).toggle()
        compose.onNodeWithText(note).assertDoesNotExist()
    }
}
