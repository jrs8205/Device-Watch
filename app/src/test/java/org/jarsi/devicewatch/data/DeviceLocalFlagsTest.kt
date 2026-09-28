package org.jarsi.devicewatch.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.presentation.ui.markRuntimePermissionsRequested
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The backed-up settings (BackupRulesTest) must not carry flags about this phone.
 * Restored on a new phone, "intro shown" skipped the intro, and "permission
 * dialog already launched" made the permission button open the app's settings
 * instead of the dialog, since a new phone gives no rationale before a first ask.
 */
@RunWith(AndroidJUnit4::class)
class DeviceLocalFlagsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val backedUp = context.getSharedPreferences(AppSettingsRepositoryImpl.PREFS_NAME, Context.MODE_PRIVATE)

    @Test
    fun `marking the intro shown writes nothing the backup carries`() {
        val settings = AppSettingsRepositoryImpl(context)

        settings.setOnboardingShown()

        assertThat(settings.onboardingShown()).isTrue()
        assertThat(backedUp.contains(AppSettingsRepositoryImpl.KEY_ONBOARDING_SHOWN)).isFalse()
    }

    @Test
    fun `marking the permission dialog launched writes nothing the backup carries`() {
        context.markRuntimePermissionsRequested()

        assertThat(backedUp.contains(AppSettingsRepositoryImpl.KEY_RUNTIME_PERMISSIONS_REQUESTED)).isFalse()
    }

    @Test
    fun `an update from 1_5_0 keeps both flags and moves them out of the backed-up settings`() {
        backedUp.edit()
            .putBoolean(AppSettingsRepositoryImpl.KEY_ONBOARDING_SHOWN, true)
            .putBoolean(AppSettingsRepositoryImpl.KEY_RUNTIME_PERMISSIONS_REQUESTED, true)
            .commit()

        val settings = AppSettingsRepositoryImpl(context)

        assertThat(settings.onboardingShown()).isTrue()
        assertThat(DeviceLocalFlags.prefs(context).getBoolean(AppSettingsRepositoryImpl.KEY_RUNTIME_PERMISSIONS_REQUESTED, false))
            .isTrue()
        assertThat(backedUp.contains(AppSettingsRepositoryImpl.KEY_ONBOARDING_SHOWN)).isFalse()
        assertThat(backedUp.contains(AppSettingsRepositoryImpl.KEY_RUNTIME_PERMISSIONS_REQUESTED)).isFalse()
    }
}
