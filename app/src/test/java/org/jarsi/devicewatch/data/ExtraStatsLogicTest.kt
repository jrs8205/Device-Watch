package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.YearMonth

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

    @Test
    fun `private DNS with a named server is the strict mode`() {
        assertThat(ExtraStatsLogic.privateDns(active = true, serverName = "dns.google"))
            .isEqualTo(PrivateDns(PrivateDnsMode.HOSTNAME, "dns.google"))
    }

    @Test
    fun `private DNS active without a name is the automatic mode`() {
        assertThat(ExtraStatsLogic.privateDns(active = true, serverName = null))
            .isEqualTo(PrivateDns(PrivateDnsMode.AUTOMATIC, null))
        assertThat(ExtraStatsLogic.privateDns(active = true, serverName = " "))
            .isEqualTo(PrivateDns(PrivateDnsMode.AUTOMATIC, null))
    }

    @Test
    fun `inactive private DNS is off whatever name is left over`() {
        assertThat(ExtraStatsLogic.privateDns(active = false, serverName = "dns.google"))
            .isEqualTo(PrivateDns(PrivateDnsMode.OFF, null))
    }

    @Test
    fun `the network's own MTU wins`() {
        assertThat(ExtraStatsLogic.mtu(networkMtu = 1280, interfaceMtu = 1500)).isEqualTo(1280)
    }

    @Test
    fun `the interface MTU fills in when the network sets none`() {
        // LinkProperties.getMtu() is 0 unless the network configured one.
        assertThat(ExtraStatsLogic.mtu(networkMtu = 0, interfaceMtu = 1500)).isEqualTo(1500)
    }

    @Test
    fun `an unknown MTU reads as none`() {
        assertThat(ExtraStatsLogic.mtu(networkMtu = 0, interfaceMtu = null)).isNull()
        assertThat(ExtraStatsLogic.mtu(networkMtu = -1, interfaceMtu = 0)).isNull()
    }

    @Test
    fun `the module metadata version names the Play system update's day`() {
        // Settings reads this package's versionName, e.g. "2025-09-01".
        assertThat(ExtraStatsLogic.moduleUpdate("2025-09-01"))
            .isEqualTo(ModuleUpdate(YearMonth.of(2025, 9), day = 1))
    }

    @Test
    fun `a suffix after the date is ignored, as Settings does`() {
        // The API 35 emulator image reports "2024-07-01S+".
        assertThat(ExtraStatsLogic.moduleUpdate("2024-07-01S+"))
            .isEqualTo(ModuleUpdate(YearMonth.of(2024, 7), day = 1))
    }

    @Test
    fun `a month-only version names the month`() {
        assertThat(ExtraStatsLogic.moduleUpdate("2025-09"))
            .isEqualTo(ModuleUpdate(YearMonth.of(2025, 9), day = null))
    }

    @Test
    fun `a version that is not a date names no update`() {
        assertThat(ExtraStatsLogic.moduleUpdate("350902000")).isNull()
        assertThat(ExtraStatsLogic.moduleUpdate("2025-13-01")).isNull()
        assertThat(ExtraStatsLogic.moduleUpdate("2025-02-30")).isNull()
        assertThat(ExtraStatsLogic.moduleUpdate("")).isNull()
        assertThat(ExtraStatsLogic.moduleUpdate(null)).isNull()
    }

    @Test
    fun `the bucket state tells foreground from background traffic`() {
        // NetworkStats.Bucket.STATE_DEFAULT (1) is background, STATE_FOREGROUND (2).
        assertThat(ExtraStatsLogic.trafficState(2)).isEqualTo(TrafficState.FOREGROUND)
        assertThat(ExtraStatsLogic.trafficState(1)).isEqualTo(TrafficState.BACKGROUND)
        assertThat(ExtraStatsLogic.trafficState(-1)).isEqualTo(TrafficState.UNKNOWN)
    }

    @Test
    fun `traffic splits into foreground and background shares per network`() {
        val breakdown = ExtraStatsLogic.dataBreakdown(
            listOf(
                TrafficRecord(mobile = false, TrafficState.FOREGROUND, roaming = false, bytes = 700),
                TrafficRecord(mobile = false, TrafficState.BACKGROUND, roaming = false, bytes = 300),
                TrafficRecord(mobile = true, TrafficState.FOREGROUND, roaming = false, bytes = 1),
                TrafficRecord(mobile = true, TrafficState.BACKGROUND, roaming = false, bytes = 2),
            )
        )

        assertThat(breakdown.wifi).isEqualTo(TrafficSplit(foregroundPercent = 70, backgroundPercent = 30))
        // Rounded foreground, background as the rest: the two always make 100.
        assertThat(breakdown.mobile).isEqualTo(TrafficSplit(foregroundPercent = 33, backgroundPercent = 67))
        assertThat(breakdown.mobileRoamingBytes).isEqualTo(0L)
    }

    @Test
    fun `a network with no traffic, or traffic of unknown state, has no split`() {
        val breakdown = ExtraStatsLogic.dataBreakdown(
            listOf(
                TrafficRecord(mobile = true, TrafficState.FOREGROUND, roaming = false, bytes = 500),
                TrafficRecord(mobile = true, TrafficState.UNKNOWN, roaming = false, bytes = 10),
            )
        )

        assertThat(breakdown.wifi).isNull()
        assertThat(breakdown.mobile).isNull()
    }

    @Test
    fun `roaming counts only mobile traffic flagged as roaming`() {
        val breakdown = ExtraStatsLogic.dataBreakdown(
            listOf(
                TrafficRecord(mobile = true, TrafficState.FOREGROUND, roaming = true, bytes = 400),
                TrafficRecord(mobile = true, TrafficState.BACKGROUND, roaming = true, bytes = 100),
                TrafficRecord(mobile = true, TrafficState.BACKGROUND, roaming = false, bytes = 500),
                TrafficRecord(mobile = false, TrafficState.FOREGROUND, roaming = true, bytes = 900),
            )
        )

        assertThat(breakdown.mobileRoamingBytes).isEqualTo(500L)
    }
}
