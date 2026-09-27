package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale

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
    fun `a shutdown ends the interval even without a screen-off event`() {
        // Codex round 6: use 10-11, shutdown, next-day use 12-13 read as 27 hours.
        val events = listOf(
            ScreenEvent(at(20, 10), on = true),
            ScreenEvent(at(20, 11), ScreenEventKind.SHUTDOWN),
            ScreenEvent(at(21, 11, 30), ScreenEventKind.STARTUP),
            ScreenEvent(at(21, 12), on = true),
            ScreenEvent(at(21, 13), on = false),
        )
        val byDay = ExtraStatsLogic.screenOnByDay(events, startMillis = at(20, 0), endMillis = at(22, 0), zone = utc)

        assertThat(byDay).containsExactly(
            LocalDate.of(2026, 9, 20), 60 * 60_000L,
            LocalDate.of(2026, 9, 21), 60 * 60_000L,
        )
    }

    @Test
    fun `an interval still open at a startup lost its end and is dropped`() {
        // No off, no shutdown: the battery died. When the screen went off is unknown,
        // and counting the whole outage would be far worse than counting nothing.
        val events = listOf(
            ScreenEvent(at(20, 10), on = true),
            ScreenEvent(at(21, 9), ScreenEventKind.STARTUP),
            ScreenEvent(at(21, 12), on = true),
            ScreenEvent(at(21, 13), on = false),
        )
        val byDay = ExtraStatsLogic.screenOnByDay(events, startMillis = at(20, 0), endMillis = at(22, 0), zone = utc)

        assertThat(byDay).containsExactly(LocalDate.of(2026, 9, 21), 60 * 60_000L)
    }

    @Test
    fun `a window that opens with a startup begins with the screen off`() {
        val events = listOf(
            ScreenEvent(at(20, 6), ScreenEventKind.STARTUP),
            ScreenEvent(at(20, 7), on = true),
            ScreenEvent(at(20, 7, 30), on = false),
        )
        val byDay = ExtraStatsLogic.screenOnByDay(events, startMillis = at(20, 0), endMillis = at(21, 0), zone = utc)

        assertThat(byDay).containsExactly(LocalDate.of(2026, 9, 20), 30 * 60_000L)
    }

    @Test
    fun `a window that opens with a shutdown says nothing about the screen before it`() {
        // Codex round 7: shutdown 6, startup 8, screen on 8:01-8:31 read as 6 h 30 min.
        val events = listOf(
            ScreenEvent(at(20, 6), ScreenEventKind.SHUTDOWN),
            ScreenEvent(at(20, 8), ScreenEventKind.STARTUP),
            ScreenEvent(at(20, 8, 1), on = true),
            ScreenEvent(at(20, 8, 31), on = false),
        )
        val byDay = ExtraStatsLogic.screenOnByDay(events, startMillis = at(20, 0), endMillis = at(21, 0), zone = utc)

        assertThat(byDay).containsExactly(LocalDate.of(2026, 9, 20), 30 * 60_000L)
    }

    @Test
    fun `no events give no screen-on time`() {
        assertThat(ExtraStatsLogic.screenOnByDay(emptyList(), at(20, 0), at(21, 0), utc)).isEmpty()
    }

    @Test
    fun `only removable volumes are listed, internal storage has its own row`() {
        val volumes = listOf(
            VolumeInfo("Internal shared storage", primary = true, removable = false, mounted = true, 64_000L, 20_000L),
            VolumeInfo("SanDisk SD card", primary = false, removable = true, mounted = true, 32_000L, 8_000L),
            VolumeInfo("USB drive", primary = false, removable = true, mounted = false, 0L, 0L),
        )

        assertThat(ExtraStatsLogic.removableVolumes(volumes).map { it.description })
            .containsExactly("SanDisk SD card", "USB drive").inOrder()
    }

    @Test
    fun `a USB device is named by maker and product, or by its ids`() {
        assertThat(ExtraStatsLogic.usbDeviceName("Logitech", "USB Receiver", 0x046d, 0xc52b))
            .isEqualTo("Logitech USB Receiver")
        // A product name that already starts with the maker is not doubled.
        assertThat(ExtraStatsLogic.usbDeviceName("SanDisk", "SanDisk Ultra", 0x0781, 0x5581))
            .isEqualTo("SanDisk Ultra")
        assertThat(ExtraStatsLogic.usbDeviceName(null, " ", 0x046d, 0xc52b))
            .isEqualTo("USB 046d:c52b")
    }

    @Test
    fun `the unavailable cell value reads as none`() {
        // CellInfo.UNAVAILABLE is Integer.MAX_VALUE.
        assertThat(ExtraStatsLogic.cellValue(Int.MAX_VALUE)).isNull()
        assertThat(ExtraStatsLogic.cellValue(-95)).isEqualTo(-95)
    }

    @Test
    fun `signal details name what the modem reports and skip the rest`() {
        assertThat(ExtraStatsLogic.signalDetails(rsrp = -95, rsrq = -11, sinr = 12))
            .isEqualTo("RSRP -95 dBm · RSRQ -11 dB · SINR 12 dB")
        assertThat(ExtraStatsLogic.signalDetails(rsrp = -101, rsrq = null, sinr = null))
            .isEqualTo("RSRP -101 dBm")
        assertThat(ExtraStatsLogic.signalDetails(rsrp = null, rsrq = null, sinr = null)).isNull()
    }

    @Test
    fun `bands read in the notation of their technology`() {
        assertThat(ExtraStatsLogic.bandsText(nr = true, bands = intArrayOf(78, 28))).isEqualTo("n78, n28")
        assertThat(ExtraStatsLogic.bandsText(nr = false, bands = intArrayOf(20))).isEqualTo("B20")
        assertThat(ExtraStatsLogic.bandsText(nr = false, bands = intArrayOf())).isNull()
    }

    @Test
    fun `the Wi-Fi security type reads by its standard name`() {
        // WifiInfo.SECURITY_TYPE_* (API 31).
        assertThat(ExtraStatsLogic.wifiSecurityName(2)).isEqualTo("WPA2-Personal")
        assertThat(ExtraStatsLogic.wifiSecurityName(4)).isEqualTo("WPA3-Personal")
        assertThat(ExtraStatsLogic.wifiSecurityName(6)).isEqualTo("Enhanced Open")
        assertThat(ExtraStatsLogic.wifiSecurityName(9)).isEqualTo("WPA3-Enterprise")
    }

    @Test
    fun `an open, unknown or new security type has no standard name`() {
        // Open is the caller's to say in the user's language.
        assertThat(ExtraStatsLogic.wifiSecurityName(0)).isNull()
        assertThat(ExtraStatsLogic.wifiSecurityName(-1)).isNull()
        assertThat(ExtraStatsLogic.wifiSecurityName(99)).isNull()
    }

    @Test
    fun `a captive portal outranks the validation flag`() {
        assertThat(ExtraStatsLogic.internetState(validated = true, captivePortal = false))
            .isEqualTo(InternetState.VALIDATED)
        assertThat(ExtraStatsLogic.internetState(validated = false, captivePortal = true))
            .isEqualTo(InternetState.CAPTIVE_PORTAL)
        assertThat(ExtraStatsLogic.internetState(validated = true, captivePortal = true))
            .isEqualTo(InternetState.CAPTIVE_PORTAL)
        assertThat(ExtraStatsLogic.internetState(validated = false, captivePortal = false))
            .isEqualTo(InternetState.NOT_VALIDATED)
    }

    @Test
    fun `the bandwidth estimate reads in megabits`() {
        assertThat(ExtraStatsLogic.bandwidthText(downKbps = 30_000, upKbps = 12_000, locale = Locale.US)).isEqualTo("↓ 30 Mb/s · ↑ 12 Mb/s")
        // Under 10 Mb/s one decimal keeps a slow link from reading as zero.
        assertThat(ExtraStatsLogic.bandwidthText(downKbps = 1_500, upKbps = 400, locale = Locale.US)).isEqualTo("↓ 1.5 Mb/s · ↑ 0.4 Mb/s")
        assertThat(ExtraStatsLogic.bandwidthText(downKbps = 0, upKbps = 0, locale = Locale.US)).isNull()
    }

    @Test
    fun `brightness reads as the position of the Settings slider`() {
        // Settings draws the slider on a perceptual (HLG) curve over the linear
        // 1..255 setting: the ends meet, and half the linear range sits high up.
        assertThat(ExtraStatsLogic.brightnessSliderPercent(255)).isEqualTo(100)
        assertThat(ExtraStatsLogic.brightnessSliderPercent(1)).isEqualTo(0)
        assertThat(ExtraStatsLogic.brightnessSliderPercent(128)).isEqualTo(87)
    }

    @Test
    fun `an out-of-range brightness is clamped to the slider`() {
        assertThat(ExtraStatsLogic.brightnessSliderPercent(0)).isEqualTo(0)
        assertThat(ExtraStatsLogic.brightnessSliderPercent(400)).isEqualTo(100)
    }

    @Test
    fun `display size reads as a share of the default density`() {
        assertThat(ExtraStatsLogic.displaySizePercent(densityDpi = 546, defaultDpi = 420)).isEqualTo(130)
        assertThat(ExtraStatsLogic.displaySizePercent(densityDpi = 420, defaultDpi = 420)).isEqualTo(100)
        assertThat(ExtraStatsLogic.displaySizePercent(densityDpi = 420, defaultDpi = 0)).isNull()
    }

    @Test
    fun `swap is read from meminfo in kilobytes`() {
        val meminfo = """
            MemTotal:        7843156 kB
            SwapTotal:       4194300 kB
            SwapFree:        3145725 kB
        """.trimIndent()

        assertThat(ExtraStatsLogic.swapFromMeminfo(meminfo)).isEqualTo(SwapInfo(totalKb = 4_194_300, freeKb = 3_145_725))
    }

    @Test
    fun `no swap, or no meminfo, reads as none`() {
        assertThat(ExtraStatsLogic.swapFromMeminfo("SwapTotal: 0 kB\nSwapFree: 0 kB")).isNull()
        assertThat(ExtraStatsLogic.swapFromMeminfo("MemTotal: 100 kB")).isNull()
        assertThat(ExtraStatsLogic.swapFromMeminfo(null)).isNull()
    }

    @Test
    fun `cores with the same frequency range form a cluster, slowest first`() {
        val cores = listOf(
            300_000L to 1_800_000L, 300_000L to 1_800_000L, 300_000L to 1_800_000L, 300_000L to 1_800_000L,
            400_000L to 2_400_000L, 400_000L to 2_400_000L, 400_000L to 2_400_000L,
            500_000L to 2_900_000L,
        ).shuffled(java.util.Random(7))

        assertThat(ExtraStatsLogic.cpuClustersText(cores, Locale.US))
            .isEqualTo("4 \u00d7 0.3\u20131.8 GHz + 3 \u00d7 0.4\u20132.4 GHz + 1 \u00d7 0.5\u20132.9 GHz")
    }

    @Test
    fun `cores reporting an implausible frequency are left out`() {
        // The emulator's cpufreq nodes report a few kHz; no real core runs under 100 MHz.
        assertThat(ExtraStatsLogic.cpuClustersText(listOf(1_000L to 40_000L), Locale.US)).isNull()
    }

    @Test
    fun `no readable cores give no clusters`() {
        assertThat(ExtraStatsLogic.cpuClustersText(emptyList(), Locale.US)).isNull()
    }

    @Test
    fun `the GNSS chip reads by model and hardware year`() {
        // LocationManager.getGnssHardwareModelName / getGnssYearOfHardware (API 28).
        assertThat(ExtraStatsLogic.gnssHardwareText("Broadcom BCM4776", 2023)).isEqualTo("Broadcom BCM4776 (2023)")
        assertThat(ExtraStatsLogic.gnssHardwareText(null, 2019)).isEqualTo("2019")
        assertThat(ExtraStatsLogic.gnssHardwareText(" ", 0)).isNull()
        // Years before 2016 are the platform's "unknown" placeholder, not a real chip year.
        assertThat(ExtraStatsLogic.gnssHardwareText("Qualcomm", 2015)).isEqualTo("Qualcomm")
    }

    @Test
    fun `the Vulkan feature version reads as major dot minor`() {
        // FEATURE_VULKAN_HARDWARE_VERSION packs major << 22 | minor << 12 | patch.
        assertThat(ExtraStatsLogic.vulkanVersionText((1 shl 22) or (3 shl 12))).isEqualTo("1.3")
        assertThat(ExtraStatsLogic.vulkanVersionText((1 shl 22) or (1 shl 12) or 73)).isEqualTo("1.1")
        assertThat(ExtraStatsLogic.vulkanVersionText(0)).isNull()
    }

    @Test
    fun `hardware decoders are named for the formats worth knowing`() {
        val codecs = listOf(
            CodecRecord(types = listOf("video/av01"), hardware = true, encoder = false),
            CodecRecord(types = listOf("video/hevc"), hardware = true, encoder = false),
            // A software decoder or an encoder says nothing about playback hardware.
            CodecRecord(types = listOf("video/x-vnd.on2.vp9"), hardware = false, encoder = false),
            CodecRecord(types = listOf("video/dolby-vision"), hardware = true, encoder = true),
            CodecRecord(types = listOf("video/avc"), hardware = true, encoder = false),
        )

        assertThat(ExtraStatsLogic.hardwareDecoders(codecs)).containsExactly("AV1", "HEVC").inOrder()
    }

    @Test
    fun `biometric sensors are listed from the system features`() {
        // FEATURE_FINGERPRINT / FEATURE_FACE / FEATURE_IRIS need no permission,
        // unlike asking BiometricManager whether they are enrolled.
        assertThat(ExtraStatsLogic.biometricSensors(fingerprint = true, face = true, iris = false))
            .containsExactly(BiometricSensor.FINGERPRINT, BiometricSensor.FACE).inOrder()
        assertThat(ExtraStatsLogic.biometricSensors(fingerprint = false, face = false, iris = false)).isEmpty()
    }

    @Test
    fun `NFC reads as missing, off or on`() {
        assertThat(ExtraStatsLogic.nfcState(present = false, enabled = false)).isEqualTo(NfcState.NONE)
        assertThat(ExtraStatsLogic.nfcState(present = true, enabled = false)).isEqualTo(NfcState.OFF)
        assertThat(ExtraStatsLogic.nfcState(present = true, enabled = true)).isEqualTo(NfcState.ON)
    }

    @Test
    fun `Android 10 reports LTE RSSNR in tenths of a decibel`() {
        // AOSP converts to whole dB only from Android 11 (Codex round 6).
        assertThat(ExtraStatsLogic.lteRssnrDb(raw = 120, sdkInt = 29)).isEqualTo(12)
        assertThat(ExtraStatsLogic.lteRssnrDb(raw = -36, sdkInt = 29)).isEqualTo(-4)
        assertThat(ExtraStatsLogic.lteRssnrDb(raw = 12, sdkInt = 30)).isEqualTo(12)
        assertThat(ExtraStatsLogic.lteRssnrDb(raw = null, sdkInt = 29)).isNull()
    }

    @Test
    fun `GNSS capabilities name what the chip reports, in a fixed order`() {
        assertThat(
            ExtraStatsLogic.gnssCapabilities(
                measurements = true, navigationMessages = false, antennaInfo = true, corrections = true,
            )
        ).containsExactly(GnssCapability.MEASUREMENTS, GnssCapability.ANTENNA_INFO, GnssCapability.CORRECTIONS).inOrder()
    }

    @Test
    fun `no dual-frequency claim is derived from the power-measurement flags`() {
        // Codex round 6: hasPowerMultibandTracking() says the chip can report the
        // power a multiband fix costs, not that it receives two frequencies.
        assertThat(GnssCapability.entries.map { it.name }).doesNotContain("MULTIBAND")
    }

    @Test
    fun `apps with an icon are named, background services counted, this app left out`() {
        // A preinstalled app such as Chrome is still an app to the person: whether
        // it has a launcher icon decides, not the system flag.
        val summary = ExtraStatsLogic.exemptAppsSummary(
            listOf(
                ExemptApp("Tehtävät", launchable = true, self = false),
                ExemptApp("chrome", launchable = true, self = false),
                ExemptApp("Laitevahti", launchable = true, self = true),
                ExemptApp("Google Play services", launchable = false, self = false),
                ExemptApp("Carrier services", launchable = false, self = false),
            )
        )

        // Sorted as a person reads them, not by code point.
        assertThat(summary.userApps).containsExactly("chrome", "Tehtävät").inOrder()
        assertThat(summary.systemCount).isEqualTo(2)
    }

    @Test
    fun `the biometric status says whether strong biometrics are set up`() {
        // BiometricManager.BIOMETRIC_SUCCESS / _ERROR_NONE_ENROLLED / _ERROR_NO_HARDWARE;
        // anything else (temporarily unavailable, security update) is unavailable.
        assertThat(ExtraStatsLogic.biometricState(0)).isEqualTo(BiometricState.ENROLLED)
        assertThat(ExtraStatsLogic.biometricState(11)).isEqualTo(BiometricState.NOT_ENROLLED)
        assertThat(ExtraStatsLogic.biometricState(12)).isEqualTo(BiometricState.NO_HARDWARE)
        assertThat(ExtraStatsLogic.biometricState(1)).isEqualTo(BiometricState.UNAVAILABLE)
        assertThat(ExtraStatsLogic.biometricState(15)).isEqualTo(BiometricState.UNAVAILABLE)
    }

    @Test
    fun `a size setting reads as the default or as a share of it`() {
        assertThat(ExtraStatsLogic.relativeToDefault(100)).isNull()
        assertThat(ExtraStatsLogic.relativeToDefault(130)).isEqualTo(130)
        assertThat(ExtraStatsLogic.relativeToDefault(85)).isEqualTo(85)
    }

    @Test
    fun `the full-charge capacity is estimated from the charge counter and the level`() {
        // Pixel 8a on Android 17: 3 468 000 µAh at 83 % -> about 4 178 mAh.
        assertThat(ExtraStatsLogic.estimatedFullCapacityMah(chargeCounter = 3_468_000, levelPercent = 83))
            .isEqualTo(4178)
    }

    @Test
    fun `a counter reported in mAh is recognised`() {
        // Some makers report the counter in mAh, as they do CURRENT_NOW.
        assertThat(ExtraStatsLogic.estimatedFullCapacityMah(chargeCounter = 3_468, levelPercent = 83))
            .isEqualTo(4178)
    }

    @Test
    fun `a low level or an implausible result gives no estimate`() {
        // Under 20 % the whole-percent level makes the division too coarse.
        assertThat(ExtraStatsLogic.estimatedFullCapacityMah(chargeCounter = 600_000, levelPercent = 15)).isNull()
        assertThat(ExtraStatsLogic.estimatedFullCapacityMah(chargeCounter = 0, levelPercent = 80)).isNull()
        assertThat(ExtraStatsLogic.estimatedFullCapacityMah(chargeCounter = Int.MIN_VALUE, levelPercent = 80)).isNull()
        assertThat(ExtraStatsLogic.estimatedFullCapacityMah(chargeCounter = 3_468_000, levelPercent = 0)).isNull()
    }

    @Test
    fun `Android 17 hides the developer switches from apps`() {
        // The Pixel 8a on Android 17 (SDK 37) reads developer options and USB
        // debugging as off while both are on; Android 15 reads them correctly.
        assertThat(ExtraStatsLogic.developerFlagsReadable(sdkInt = 35)).isTrue()
        assertThat(ExtraStatsLogic.developerFlagsReadable(sdkInt = 36)).isTrue()
        assertThat(ExtraStatsLogic.developerFlagsReadable(sdkInt = 37)).isFalse()
    }
}
