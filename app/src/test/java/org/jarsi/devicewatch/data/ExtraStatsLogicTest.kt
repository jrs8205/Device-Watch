package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExtraStatsLogicTest {

    @Test
    fun `the plugged extra names the charge source`() {
        // BatteryManager.BATTERY_PLUGGED_AC / USB / WIRELESS / DOCK (API 33).
        assertThat(ExtraStatsLogic.chargeSource(1)).isEqualTo(ChargeSource.AC)
        assertThat(ExtraStatsLogic.chargeSource(2)).isEqualTo(ChargeSource.USB)
        assertThat(ExtraStatsLogic.chargeSource(4)).isEqualTo(ChargeSource.WIRELESS)
        assertThat(ExtraStatsLogic.chargeSource(8)).isEqualTo(ChargeSource.DOCK)
    }

    @Test
    fun `unplugged or unknown sources name nothing`() {
        assertThat(ExtraStatsLogic.chargeSource(0)).isEqualTo(ChargeSource.NONE)
        assertThat(ExtraStatsLogic.chargeSource(-1)).isEqualTo(ChargeSource.NONE)
        assertThat(ExtraStatsLogic.chargeSource(16)).isEqualTo(ChargeSource.NONE)
    }

    @Test
    fun `the system's discharge prediction reads in whole minutes`() {
        val minute = 60_000L
        assertThat(ExtraStatsLogic.predictionMinutes(320 * minute + 59_000L)).isEqualTo(320)
        assertThat(ExtraStatsLogic.predictionMinutes(minute)).isEqualTo(1)
    }

    @Test
    fun `no prediction, or an empty one, reads as none`() {
        assertThat(ExtraStatsLogic.predictionMinutes(null)).isNull()
        assertThat(ExtraStatsLogic.predictionMinutes(0L)).isNull()
        assertThat(ExtraStatsLogic.predictionMinutes(-5_000L)).isNull()
        assertThat(ExtraStatsLogic.predictionMinutes(59_000L)).isNull()
    }
}
