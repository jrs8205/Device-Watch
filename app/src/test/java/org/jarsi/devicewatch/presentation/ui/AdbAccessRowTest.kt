package org.jarsi.devicewatch.presentation.ui

import android.app.Application
import android.content.ClipboardManager
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.AdbGrantCommands
import org.jarsi.devicewatch.data.AdbPermission
import org.jarsi.devicewatch.presentation.AdbStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The ADB permissions row of the settings: what each state of the switch shows. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w400dp-h1200dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdbAccessRowTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Application get() = ApplicationProvider.getApplicationContext()

    private val packageName = "org.jarsi.devicewatch"
    private val commands = AdbGrantCommands.commands(packageName).joinToString("\n")

    private fun text(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun show(
        status: AdbStatus,
        missing: Set<AdbPermission> = emptySet(),
        onCheckedChange: (Boolean) -> Unit = {},
    ) {
        compose.setContent {
            MaterialTheme {
                Column { AdbAccessRow(status, missing, packageName, onCheckedChange) }
            }
        }
    }

    @Test
    fun `off shows the switch off, with neither a status nor the commands`() {
        show(AdbStatus.OFF)

        compose.onNode(isToggleable()).assertIsOff()
        compose.onNodeWithText(text(R.string.adb_mode_description)).assertExists()
        compose.onNodeWithText(text(R.string.adb_status_on)).assertDoesNotExist()
        compose.onNodeWithText(commands).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.adb_copy_commands)).assertDoesNotExist()
    }

    @Test
    fun `permissions still to grant are named, with the three commands to run`() {
        show(AdbStatus.MISSING, missing = setOf(AdbPermission.PACKAGE_USAGE_STATS, AdbPermission.BATTERY_STATS))

        compose.onNode(isToggleable()).assertIsOff()
        // Named in the order the commands come in, whatever order the set has.
        compose.onNodeWithText(text(R.string.adb_status_missing, "BATTERY_STATS, PACKAGE_USAGE_STATS")).assertExists()
        compose.onNodeWithText(commands).assertExists()
    }

    @Test
    fun `the button copies the three commands, one per line`() {
        show(AdbStatus.MISSING, missing = setOf(AdbPermission.DUMP))

        compose.onNodeWithText(text(R.string.adb_copy_commands)).performClick()

        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
        assertThat(clip?.getItemAt(0)?.text?.toString()).isEqualTo(commands)
    }

    @Test
    fun `on shows the status and no commands`() {
        show(AdbStatus.ON)

        compose.onNode(isToggleable()).assertIsOn()
        compose.onNodeWithText(text(R.string.adb_status_on)).assertExists()
        compose.onNodeWithText(commands).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.adb_copy_commands)).assertDoesNotExist()
    }

    @Test
    fun `on its way on the switch already shows on, without a status yet`() {
        show(AdbStatus.STARTING)

        compose.onNode(isToggleable()).assertIsOn()
        compose.onNodeWithText(text(R.string.adb_status_on)).assertDoesNotExist()
    }

    @Test
    fun `the switch reports what the user asked for`() {
        val asked = mutableListOf<Boolean>()
        show(AdbStatus.OFF, onCheckedChange = { asked += it })

        compose.onNode(isToggleable()).performClick()

        assertThat(asked).containsExactly(true)
    }
}
