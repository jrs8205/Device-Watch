package org.jarsi.devicewatch

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.system.DataQuotaNotifier
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController

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

    private fun requestAfter(start: Intent): SettingsSection? =
        Robolectric.buildActivity(MainActivity::class.java, start).create().get().pendingSettingsSection

    /** The start Android makes for a long press on a tile: the action, and the tile by name. */
    private fun longPressOn(tileClass: String): Intent = Intent(TileService.ACTION_QS_TILE_PREFERENCES)
        .setComponent(ComponentName(context, MainActivity::class.java))
        .putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(context.packageName, tileClass))

    /** The start Android's notification settings make, from the app's page or from one channel's. */
    private fun notificationSettingsStart(channel: String? = null): Intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Notification.INTENT_CATEGORY_NOTIFICATION_PREFERENCES)
        .setComponent(ComponentName(context, MainActivity::class.java))
        .apply { if (channel != null) putExtra(Notification.EXTRA_CHANNEL_ID, channel) }

    @Test
    fun `a long press reads the tile Android names`() {
        assertThat(requestAfter(longPressOn("org.jarsi.devicewatch.quick.CpuLoadTileService")))
            .isEqualTo(SettingsSection.QuickTiles)
        assertThat(requestAfter(longPressOn(FLOATING_FIGURE_TILE))).isEqualTo(SettingsSection.FloatingFigure)
    }

    @Test
    fun `the notification settings' link reads the channel Android names`() {
        assertThat(requestAfter(notificationSettingsStart())).isEqualTo(SettingsSection.Alerts)
        assertThat(requestAfter(notificationSettingsStart(DataQuotaNotifier.CHANNEL_ID)))
            .isEqualTo(SettingsSection.DataCounter)
    }

    @Test
    fun `the app's own shortcut to a section is read`() {
        assertThat(requestAfter(MainActivity.openSettingsSectionIntent(context, SettingsSection.Widgets)))
            .isEqualTo(SettingsSection.Widgets)
    }

    @Test
    fun `a request reaches a screen that is already open`() {
        val launcher = Intent(Intent.ACTION_MAIN).setComponent(ComponentName(context, MainActivity::class.java))
        val controller = Robolectric.buildActivity(MainActivity::class.java, launcher).create()

        controller.newIntent(longPressOn(FLOATING_FIGURE_TILE))

        assertThat(controller.get().pendingSettingsSection).isEqualTo(SettingsSection.FloatingFigure)
    }

    /**
     * What a recreation does to the screen: its state is saved, and a new screen is made from
     * the same start and that state. The screens are created and no further, so nothing is
     * drawn: drawing brings up the whole app, and what that leaves behind in the test JVM
     * (the widgets' stores are kept for its lifetime) breaks the widget tests that run after.
     */
    private fun recreated(first: ActivityController<MainActivity>): MainActivity {
        val state = Bundle()
        first.saveInstanceState(state)
        return Robolectric.buildActivity(MainActivity::class.java, first.get().intent).create(state).get()
    }

    @Test
    fun `a request not yet shown survives a recreation of the screen`() {
        val first = Robolectric.buildActivity(MainActivity::class.java, gearStart()).create()

        assertThat(recreated(first).pendingSettingsSection).isEqualTo(SettingsSection.Screensaver)
    }

    @Test
    fun `a request already shown is not brought back by a recreation`() {
        val first = Robolectric.buildActivity(MainActivity::class.java, gearStart()).create()
        first.get().settingsSectionShown(SettingsSection.Screensaver)

        assertThat(recreated(first).pendingSettingsSection).isNull()
    }

    @Test
    fun `an ordinary start leaves no request, before or after a recreation`() {
        val launcher = Intent(Intent.ACTION_MAIN).setComponent(ComponentName(context, MainActivity::class.java))
        val first = Robolectric.buildActivity(MainActivity::class.java, launcher).create()
        assertThat(first.get().pendingSettingsSection).isNull()

        assertThat(recreated(first).pendingSettingsSection).isNull()
    }
}
