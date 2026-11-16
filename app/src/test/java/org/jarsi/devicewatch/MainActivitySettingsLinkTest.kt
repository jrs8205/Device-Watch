package org.jarsi.devicewatch

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * What [MainActivity] keeps of a request from one of Android's own pages. The request waits
 * for the Settings tab, which may not be on screen yet (the intro, the first load), and the
 * screen can be recreated while it waits.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivitySettingsLinkTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** The start Android's screensaver page makes: the alias by name, nothing else. */
    private fun gearStart(): Intent = Intent().setComponent(ComponentName(context, SCREENSAVER_SETTINGS_ALIAS))

    @Test
    fun `the gear's start leaves a request for the screensaver's settings`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java, gearStart()).create().get()

        assertThat(activity.pendingSettingsSection).isEqualTo(SettingsSection.Screensaver)
    }

    @Test
    fun `a request not yet shown survives a recreation of the screen`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java, gearStart()).create()

        controller.recreate()

        assertThat(controller.get().pendingSettingsSection).isEqualTo(SettingsSection.Screensaver)
    }

    @Test
    fun `a request already shown is not brought back by a recreation`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java, gearStart()).create()
        controller.get().settingsSectionShown(SettingsSection.Screensaver)

        controller.recreate()

        assertThat(controller.get().pendingSettingsSection).isNull()
    }

    @Test
    fun `an ordinary start leaves no request, before or after a recreation`() {
        val launcher = Intent(Intent.ACTION_MAIN).setComponent(ComponentName(context, MainActivity::class.java))
        val controller = Robolectric.buildActivity(MainActivity::class.java, launcher).create()
        assertThat(controller.get().pendingSettingsSection).isNull()

        controller.recreate()

        assertThat(controller.get().pendingSettingsSection).isNull()
    }
}
