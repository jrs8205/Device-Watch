package org.jarsi.devicewatch.presentation.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
    val compose = createComposeRule()

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

    @Test
    fun `the missing notification access is pointed out only while the icons are switched on`() {
        show(notificationAccessEnabled = false)
        val note = context.getString(R.string.dream_notifications_access_missing)

        compose.onNodeWithText(note).assertExists()

        switchBeside(R.string.dream_notifications_title).toggle()
        compose.onNodeWithText(note).assertDoesNotExist()
    }
}
