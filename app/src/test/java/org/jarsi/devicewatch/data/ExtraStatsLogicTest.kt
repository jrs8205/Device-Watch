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

    @Test
    fun `deep sleep is the share of time since boot the CPU spent asleep`() {
        val hour = 3_600_000L
        // 10 h since boot, 2 h of it awake: 8 h asleep.
        assertThat(ExtraStatsLogic.deepSleepMillis(elapsedMillis = 10 * hour, awakeMillis = 2 * hour))
            .isEqualTo(8 * hour)
        assertThat(ExtraStatsLogic.deepSleepPercent(elapsedMillis = 10 * hour, awakeMillis = 2 * hour))
            .isEqualTo(80)
    }

    @Test
    fun `inconsistent clocks give no deep sleep figure`() {
        assertThat(ExtraStatsLogic.deepSleepPercent(elapsedMillis = 0L, awakeMillis = 0L)).isNull()
        assertThat(ExtraStatsLogic.deepSleepPercent(elapsedMillis = 1_000L, awakeMillis = 2_000L)).isNull()
        assertThat(ExtraStatsLogic.deepSleepMillis(elapsedMillis = 1_000L, awakeMillis = 2_000L)).isNull()
        assertThat(ExtraStatsLogic.deepSleepMillis(elapsedMillis = 1_000L, awakeMillis = -1L)).isNull()
    }

    @Test
    fun `the thermal status names its level`() {
        // PowerManager.THERMAL_STATUS_NONE (0) .. THERMAL_STATUS_SHUTDOWN (6).
        assertThat(ExtraStatsLogic.thermalLevel(0)).isEqualTo(ThermalLevel.NONE)
        assertThat(ExtraStatsLogic.thermalLevel(3)).isEqualTo(ThermalLevel.SEVERE)
        assertThat(ExtraStatsLogic.thermalLevel(6)).isEqualTo(ThermalLevel.SHUTDOWN)
        assertThat(ExtraStatsLogic.thermalLevel(7)).isEqualTo(ThermalLevel.UNKNOWN)
        assertThat(ExtraStatsLogic.thermalLevel(-1)).isEqualTo(ThermalLevel.UNKNOWN)
    }

    @Test
    fun `thermal headroom reads as a share of the throttling threshold`() {
        // 1.0 is the point where the system starts severe throttling.
        assertThat(ExtraStatsLogic.headroomPercent(0.384f)).isEqualTo(38)
        assertThat(ExtraStatsLogic.headroomPercent(1.2f)).isEqualTo(120)
        assertThat(ExtraStatsLogic.headroomPercent(0f)).isEqualTo(0)
    }

    @Test
    fun `an unsupported or rate-limited headroom reads as none`() {
        assertThat(ExtraStatsLogic.headroomPercent(Float.NaN)).isNull()
        assertThat(ExtraStatsLogic.headroomPercent(-0.5f)).isNull()
        assertThat(ExtraStatsLogic.headroomPercent(Float.POSITIVE_INFINITY)).isNull()
    }

    @Test
    fun `the headroom is asked again only after its rate limit`() {
        // The platform returns NaN when asked more often than every 10 s; the
        // service reads stats every 5 s, so a cached value is reused in between.
        assertThat(ExtraStatsLogic.headroomDue(lastReadMillis = null, nowMillis = 5_000L)).isTrue()
        assertThat(ExtraStatsLogic.headroomDue(lastReadMillis = 1_000L, nowMillis = 6_000L)).isFalse()
        assertThat(ExtraStatsLogic.headroomDue(lastReadMillis = 1_000L, nowMillis = 11_000L)).isTrue()
        // A clock that went backwards must not freeze the value forever.
        assertThat(ExtraStatsLogic.headroomDue(lastReadMillis = 50_000L, nowMillis = 1_000L)).isTrue()
    }
}
