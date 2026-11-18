package org.jarsi.devicewatch.presentation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Which pages the three extended-access switches open. */
class ExtendedAccessGateTest {

    @Test
    fun `the adb route opens the pages that read, and not what takes the shell user`() {
        val state = DashboardUiState(adbStatus = AdbStatus.ON)

        assertThat(state.extendedAccessOn).isTrue()
        // Removing a system app runs `pm uninstall`, which Android refuses the app's own user.
        assertThat(state.shellAccessOn).isFalse()
    }

    @Test
    fun `Shizuku and root open both`() {
        for (state in listOf(DashboardUiState(shizukuStatus = ShizukuStatus.ON), DashboardUiState(rootStatus = RootStatus.ON))) {
            assertThat(state.extendedAccessOn).isTrue()
            assertThat(state.shellAccessOn).isTrue()
        }
    }

    @Test
    fun `a switch that is off, still asking or refused opens nothing`() {
        val states = listOf(
            DashboardUiState(),
            DashboardUiState(adbStatus = AdbStatus.MISSING),
            DashboardUiState(adbStatus = AdbStatus.STARTING),
            DashboardUiState(shizukuStatus = ShizukuStatus.REQUESTING),
            DashboardUiState(shizukuStatus = ShizukuStatus.WAITING),
            DashboardUiState(rootStatus = RootStatus.DENIED),
            DashboardUiState(rootStatus = RootStatus.RESTORING),
        )
        for (state in states) {
            assertThat(state.extendedAccessOn).isFalse()
            assertThat(state.shellAccessOn).isFalse()
        }
    }
}
