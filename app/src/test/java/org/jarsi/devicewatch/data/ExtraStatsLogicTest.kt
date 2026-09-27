package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset

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

    @Test
    fun `storage splits into apps, media and the rest of what is used`() {
        val gb = 1_000_000_000L
        val breakdown = ExtraStatsLogic.storageBreakdown(
            usedBytes = 100 * gb,
            appCodeBytes = 20 * gb,
            appDataBytes = 15 * gb,
            appCacheBytes = 4 * gb,
            imageBytes = 10 * gb,
            videoBytes = 30 * gb,
            audioBytes = 5 * gb,
        )

        // App data already includes its cache; the rest is system and other files.
        assertThat(breakdown).isEqualTo(
            StorageBreakdown(
                appsBytes = 35 * gb,
                cacheBytes = 4 * gb,
                imageBytes = 10 * gb,
                videoBytes = 30 * gb,
                audioBytes = 5 * gb,
                otherBytes = 20 * gb,
            )
        )
    }

    @Test
    fun `categories that overshoot the used space leave no negative remainder`() {
        val breakdown = ExtraStatsLogic.storageBreakdown(
            usedBytes = 10L,
            appCodeBytes = 8L,
            appDataBytes = 4L,
            appCacheBytes = 1L,
            imageBytes = 0L,
            videoBytes = 0L,
            audioBytes = 0L,
        )

        assertThat(breakdown.otherBytes).isEqualTo(0L)
    }

    @Test
    fun `app sizes are summed once per app uid`() {
        // Codex round 5: the user-wide queryStatsForUser figure folds the whole
        // shared storage into dataBytes, so a 300 MB download showed up as apps
        // (twice, on the emulator). Per-UID figures hold only each app's own files;
        // packages sharing a UID report the same figures and count once.
        val sizes = mapOf(
            10_100 to AppSizes(codeBytes = 100, dataBytes = 50, cacheBytes = 5),
            10_200 to AppSizes(codeBytes = 300, dataBytes = 20, cacheBytes = 2),
        )
        val total = ExtraStatsLogic.appSizeTotal(uids = listOf(10_100, 10_200, 10_100)) { sizes[it] }

        assertThat(total).isEqualTo(AppSizes(codeBytes = 400, dataBytes = 70, cacheBytes = 7))
    }

    @Test
    fun `an app whose size cannot be read is left out`() {
        val total = ExtraStatsLogic.appSizeTotal(uids = listOf(1, 2)) { uid ->
            if (uid == 1) AppSizes(codeBytes = 10, dataBytes = 1, cacheBytes = 0) else null
        }

        assertThat(total).isEqualTo(AppSizes(codeBytes = 10, dataBytes = 1, cacheBytes = 0))
    }

    @Test
    fun `the charging status names how the battery is being charged`() {
        // EXTRA_CHARGING_STATUS (API 34) carries the health HAL's BatteryChargingState.
        assertThat(ExtraStatsLogic.chargingState(1)).isEqualTo(ChargingState.NORMAL)
        assertThat(ExtraStatsLogic.chargingState(2)).isEqualTo(ChargingState.TOO_COLD)
        assertThat(ExtraStatsLogic.chargingState(3)).isEqualTo(ChargingState.TOO_HOT)
        assertThat(ExtraStatsLogic.chargingState(4)).isEqualTo(ChargingState.LONG_LIFE)
        assertThat(ExtraStatsLogic.chargingState(5)).isEqualTo(ChargingState.ADAPTIVE)
    }

    @Test
    fun `an invalid or unknown charging status names nothing`() {
        assertThat(ExtraStatsLogic.chargingState(0)).isEqualTo(ChargingState.UNKNOWN)
        assertThat(ExtraStatsLogic.chargingState(-1)).isEqualTo(ChargingState.UNKNOWN)
        assertThat(ExtraStatsLogic.chargingState(6)).isEqualTo(ChargingState.UNKNOWN)
    }

    @Test
    fun `advertised memory reads as whole gigabytes in either unit`() {
        // MemoryInfo.advertisedMem (API 34) may be binary or decimal gigabytes.
        assertThat(ExtraStatsLogic.advertisedRamGb(8L * 1024 * 1024 * 1024)).isEqualTo(8)
        assertThat(ExtraStatsLogic.advertisedRamGb(8_000_000_000L)).isEqualTo(8)
        assertThat(ExtraStatsLogic.advertisedRamGb(12L * 1024 * 1024 * 1024)).isEqualTo(12)
    }

    @Test
    fun `no advertised memory reads as none`() {
        assertThat(ExtraStatsLogic.advertisedRamGb(0L)).isNull()
        assertThat(ExtraStatsLogic.advertisedRamGb(-1L)).isNull()
    }

    @Test
    fun `the Android version names the SDK minor version when there is one`() {
        // Build.VERSION.SDK_INT_FULL (API 36) = major * 100000 + minor.
        assertThat(ExtraStatsLogic.androidVersionText("16", sdkInt = 36, sdkIntFull = 3_600_001))
            .isEqualTo("16 (API 36.1)")
        assertThat(ExtraStatsLogic.androidVersionText("16", sdkInt = 36, sdkIntFull = 3_600_000))
            .isEqualTo("16 (API 36)")
        assertThat(ExtraStatsLogic.androidVersionText("15", sdkInt = 35, sdkIntFull = null))
            .isEqualTo("15 (API 35)")
    }

    private val utc = ZoneOffset.UTC
    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 9, day, hour, minute).toInstant(utc).toEpochMilli()

    @Test
    fun `screen-on time sums the intervals between on and off events per day`() {
        val events = listOf(
            ScreenEvent(at(20, 8), on = true),
            ScreenEvent(at(20, 9), on = false),
            ScreenEvent(at(20, 12), on = true),
            ScreenEvent(at(20, 12, 30), on = false),
        )
        val byDay = ExtraStatsLogic.screenOnByDay(events, startMillis = at(20, 0), endMillis = at(21, 0), zone = utc)

        assertThat(byDay).containsExactly(LocalDate.of(2026, 9, 20), 90 * 60_000L)
    }

    @Test
    fun `an interval across midnight is split between the two days`() {
        val events = listOf(
            ScreenEvent(at(20, 23), on = true),
            ScreenEvent(at(21, 1), on = false),
        )
        val byDay = ExtraStatsLogic.screenOnByDay(events, startMillis = at(20, 0), endMillis = at(22, 0), zone = utc)

        assertThat(byDay).containsExactly(
            LocalDate.of(2026, 9, 20), 60 * 60_000L,
            LocalDate.of(2026, 9, 21), 60 * 60_000L,
        )
    }

    @Test
    fun `a screen already on when the window opens counts from its start`() {
        // The first event turns the screen off: it was on before the window.
        val events = listOf(ScreenEvent(at(20, 0, 30), on = false))
        val byDay = ExtraStatsLogic.screenOnByDay(events, startMillis = at(20, 0), endMillis = at(20, 6), zone = utc)

        assertThat(byDay).containsExactly(LocalDate.of(2026, 9, 20), 30 * 60_000L)
    }

    @Test
    fun `a screen still on counts until now`() {
        val events = listOf(ScreenEvent(at(20, 10), on = true))
        val byDay = ExtraStatsLogic.screenOnByDay(events, startMillis = at(20, 0), endMillis = at(20, 10, 45), zone = utc)

        assertThat(byDay).containsExactly(LocalDate.of(2026, 9, 20), 45 * 60_000L)
    }

    @Test
    fun `a repeated on event does not restart the interval`() {
        val events = listOf(
            ScreenEvent(at(20, 10), on = true),
            ScreenEvent(at(20, 10, 20), on = true),
            ScreenEvent(at(20, 11), on = false),
            ScreenEvent(at(20, 11, 5), on = false),
        )
        val byDay = ExtraStatsLogic.screenOnByDay(events, startMillis = at(20, 0), endMillis = at(21, 0), zone = utc)

        assertThat(byDay).containsExactly(LocalDate.of(2026, 9, 20), 60 * 60_000L)
    }

    @Test
    fun `no events give no screen-on time`() {
        assertThat(ExtraStatsLogic.screenOnByDay(emptyList(), at(20, 0), at(21, 0), utc)).isEmpty()
    }
}
