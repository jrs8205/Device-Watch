package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Lines as `dumpsys batterystats --charged` printed them on a Galaxy Z Flip 4
 * (Android 16) and a Pixel 8a (Android 17), app names replaced.
 */
class BatteryStatsDumpParserTest {

    private val dump = """
        Discharge step durations:
          #0: +11m46s195ms to 95 (power-save-off, device-idle-off)
          Estimated discharge time remaining: +9h41m46s610ms

        Statistics since last charge:
          System starts: 1, currently on battery: true
          Estimated battery capacity: 3595 mAh
          Last learned battery capacity: 3700 mAh
          Time on battery: 22m 21s 465ms (96,6%) realtime, 19m 5s 933ms (85,4%) uptime
          Time on battery screen off: 4m 59s 372ms (22,3%) realtime, 1m 43s 841ms (7,7%) uptime
          Time on battery screen doze: 0ms (0,0%)
          Total run time: 23m 8s 973ms realtime, 19m 53s 441ms uptime
          Discharge: 109 mAh
          Screen off discharge: 6.58 mAh
          Screen doze discharge: 0 mAh
          Screen on discharge: 102 mAh
          Device light doze discharge: 9.88 mAh
          Start clock time: 2026-10-02-19-48-52
          Screen on: 17m 22s 93ms (77,7%) 2x, Interactive: 17m 21s 320ms (77,6%)

          Estimated power use (mAh):
            Capacity: 3595, Rated: 3595, Typical: 3700, Computed drain: 132, actual drain: 132
            Global
            screen: 39.1 apps: 39.1
            cpu: 81.9 apps: 81.7 duration: 20m 53s 909ms
              (on battery, screen on)
              screen: 39.1 apps: 39.1
              (on battery, screen off/doze)
              cpu: 29.6 apps: 29.4 duration: 3m 46s 128ms
          UID 1000: 27.8 bg: 27.8
              cpu=27.4 cpu:bg=27.4 bluetooth=0.317 bluetooth:bg=0.317
              (on battery, screen on) cpu=18.9 cpu:bg=18.9
          UID u0a345: 18.6 fg: 0.511 (5m 22s 117ms) bg: 0.0474 fgs: 0.0189 (12s 180ms) cached: 0.0224 (14s 337ms)
              screen=18.0 cpu=0.600 cpu:fg=0.511
          UID 0: 9.68 bg: 9.68
              cpu=9.68 cpu:bg=9.68 wifi=0.00208 wifi:bg=0.00208
          UID u10a12: 0.0000490 bg: 0.0000490
              cpu=0.0000490 cpu:bg=0.0000490

          All kernel wake locks:
          Kernel Wake lock alarmtimer.0.auto: 6s 69ms (3 times) realtime
          Kernel Wake lock sscrpcd:1187: 14ms (48 times) realtime
          Kernel Wake lock qcom_rx_wakelock: 1h 2m 3s 4ms (36 times) realtime

          All partial wake locks:
          Wake lock u0a343 *job*r/@eM_ADDR/androidx.work.impl.background.systemjob.SystemJobService: 1m 26s 954ms (1 times) max=45508 actual=88044 (running for 0ms) realtime
          Wake lock 1000 VK_WakeLock: 1s 512ms (0 times) max=2213 actual=2213 realtime
          Wake lock u0a238 Calendar notification wake:1: 36ms (0 times) max=108 actual=108 realtime

          All wakeup reasons:
          Wakeup reason Abort: Pending Wakeup Sources: [timerfd]: 46s 546ms (3 times) realtime
          Wakeup reason 266 pm8xxx_rtc_alarm: 1s 325ms (2 times) realtime
          Wakeup reason 423 glink-native-modem realtime

          All screen wake reasons:
          1000 android.policy:POWER: 1 times

          CPU scaling:
            policy0: 300000 441600
    """.trimIndent()

    private val usage = BatteryStatsDumpParser.parse(dump)!!

    @Test
    fun `the period and what was drained with the screen on and off`() {
        assertThat(usage.capacityMah).isEqualTo(3595.0)
        assertThat(usage.onBatteryMillis).isEqualTo(22 * 60_000L + 21_465L)
        assertThat(usage.screenOffMillis).isEqualTo(4 * 60_000L + 59_372L)
        assertThat(usage.dischargeMah).isEqualTo(109.0)
        assertThat(usage.screenOffDischargeMah).isEqualTo(6.58)
        assertThat(usage.screenOnDischargeMah).isEqualTo(102.0)
    }

    @Test
    fun `every uid with its share, in the order of the dump`() {
        assertThat(usage.apps).containsExactly(
            UidPower(1000, 27.8),
            UidPower(10345, 18.6),
            UidPower(0, 9.68),
            UidPower(1_010_012, 0.0000490),
        ).inOrder()
    }

    @Test
    fun `kernel wake locks keep a name that itself contains a colon`() {
        assertThat(usage.kernelWakeLocks).containsExactly(
            WakeHold("alarmtimer.0.auto", 6_069L, 3),
            WakeHold("sscrpcd:1187", 14L, 48),
            WakeHold("qcom_rx_wakelock", 3_723_004L, 36),
        ).inOrder()
    }

    @Test
    fun `partial wake locks carry the uid that held them`() {
        assertThat(usage.partialWakeLocks).containsExactly(
            WakeHold(
                "*job*r/@eM_ADDR/androidx.work.impl.background.systemjob.SystemJobService",
                86_954L, 1, uid = 10343,
            ),
            WakeHold("VK_WakeLock", 1_512L, 0, uid = 1000),
            WakeHold("Calendar notification wake:1", 36L, 0, uid = 10238),
        ).inOrder()
    }

    @Test
    fun `a wakeup reason without a duration is left out`() {
        assertThat(usage.wakeupReasons).containsExactly(
            WakeHold("Abort: Pending Wakeup Sources: [timerfd]", 46_546L, 3),
            WakeHold("266 pm8xxx_rtc_alarm", 1_325L, 2),
        ).inOrder()
    }

    @Test
    fun `drain rates come from each state's own time`() {
        // 102 mAh over 17 min 22 s of screen-on: 352 mA, and 9.8 % of 3595 mAh an hour.
        assertThat(usage.screenOnMillis).isEqualTo(1_042_093L)
        assertThat(usage.screenOnMilliamps!!).isWithin(0.5).of(352.4)
        assertThat(usage.screenOnPercentPerHour!!).isWithin(0.05).of(9.8)
        // 109 mAh of 3595 mAh.
        assertThat(usage.dischargePercent!!).isWithin(0.05).of(3.0)
    }

    @Test
    fun `too short a stretch gives no rate instead of a wild one`() {
        // The five minutes of screen-off here: 6.58 mAh would read as 2.2 %/h.
        assertThat(usage.screenOffMilliamps).isNull()
        assertThat(usage.screenOffPercentPerHour).isNull()
        assertThat(usage.idleDrain).isNull()
    }

    @Test
    fun `the screen-off rate says how well the phone sleeps`() {
        // Eight hours of screen-off on a 4000 mAh battery.
        fun asleep(mah: Double) = usage.copy(
            capacityMah = 4000.0,
            onBatteryMillis = 9 * 3_600_000L,
            screenOffMillis = 8 * 3_600_000L,
            screenOffDischargeMah = mah,
        )

        assertThat(asleep(160.0).screenOffPercentPerHour!!).isWithin(0.001).of(0.5)
        assertThat(asleep(160.0).idleDrain).isEqualTo(IdleDrain.LOW)
        assertThat(asleep(480.0).idleDrain).isEqualTo(IdleDrain.NORMAL)
        assertThat(asleep(1280.0).idleDrain).isEqualTo(IdleDrain.HIGH)
    }

    @Test
    fun `a rate turns into the hours a full battery would last`() {
        assertThat(BatteryUsage.fullBatteryHours(10.0)).isEqualTo(10.0)
        assertThat(BatteryUsage.fullBatteryHours(0.5)).isEqualTo(200.0)
        assertThat(BatteryUsage.fullBatteryHours(0.0)).isNull()
        assertThat(BatteryUsage.fullBatteryHours(null)).isNull()
    }

    @Test
    fun `kernel names are read for the part of the phone they belong to`() {
        // Names as the Flip 4 (Qualcomm) and the Pixel 8a (Tensor, Broadcom Wi-Fi) print them.
        mapOf(
            "qcom_rx_wakelock" to WakeCategory.WIFI,
            "wlan_pno_wl" to WakeCategory.WIFI,
            "471 dhdpcie_host_wake" to WakeCategory.WIFI,
            "hal_bluetooth_lock" to WakeCategory.BLUETOOTH,
            "alarmtimer.0.auto" to WakeCategory.ALARM,
            "266 pm8xxx_rtc_alarm" to WakeCategory.ALARM,
            "423 glink-native-modem" to WakeCategory.MODEM,
            "radio-interface" to WakeCategory.MODEM,
            "sec-battery-monitor" to WakeCategory.CHARGING,
            "SensorsHAL_WAKEUP" to WakeCategory.SENSORS,
            "286 smp2p_3:422 glink-native-slpi" to WakeCategory.SENSORS,
            "nfc_wake_lock" to WakeCategory.NFC,
            "301 pmic_pwrkey" to WakeCategory.INPUT,
            "PowerManagerService.WakeLocks" to WakeCategory.APPS,
            "PowerManagerService.Display" to WakeCategory.SCREEN,
            "Abort: Pending Wakeup Sources: [timerfd]" to WakeCategory.SLEEP_INTERRUPTED,
            "Abort: Pending Wakeup Sources: wlan_txfl_wake wlan_rx_wake" to WakeCategory.SLEEP_INTERRUPTED,
        ).forEach { (name, category) ->
            assertThat(WakeGlossary.categoryOf(name)).isEqualTo(category)
        }
        // A name that says nothing known stays unnamed rather than guessed.
        assertThat(WakeGlossary.categoryOf("894000.qcom,qup_uart")).isNull()
        assertThat(WakeGlossary.categoryOf("SuspendControl.TotalSuspendDelay")).isNull()
    }

    @Test
    fun `durations spell days down to milliseconds`() {
        assertThat(BatteryStatsDumpParser.durationMillis("1d 2h 3m 4s 5ms")).isEqualTo(93_784_005L)
        assertThat(BatteryStatsDumpParser.durationMillis("0ms")).isEqualTo(0L)
        assertThat(BatteryStatsDumpParser.durationMillis("soon")).isNull()
    }

    @Test
    fun `uid labels of apps, system users and other profiles`() {
        assertThat(BatteryStatsDumpParser.uid("1000")).isEqualTo(1000)
        assertThat(BatteryStatsDumpParser.uid("u0a345")).isEqualTo(10345)
        assertThat(BatteryStatsDumpParser.uid("u10a12")).isEqualTo(1_010_012)
        assertThat(BatteryStatsDumpParser.uid("u0i7")).isNull()
    }

    @Test
    fun `the uid line of Android 12 and older reads the same`() {
        val old = """
            Statistics since last charge:
              Time on battery: 1h 0m 0s 0ms (100.0%) realtime, 1h 0m 0s 0ms (100.0%) uptime
              Time on battery screen off: 30m 0s 0ms (50.0%) realtime, 30m 0s 0ms (50.0%) uptime
              Discharge: 300 mAh
              Screen off discharge: 100 mAh
              Screen on discharge: 200 mAh

              Estimated power use (mAh):
                Capacity: 3000, Computed drain: 345, actual drain: 300-330
                Screen: 123 Excluded from smearing
                Uid u0a123: 45.6 ( cpu=40.1 wake=1.2 wifi=3.4 ) Including smearing: 50.2 ( screen=4.0 proportional=0.6 )
                Uid 1000: 30.1 ( cpu=30.1 )
        """.trimIndent()

        val parsed = BatteryStatsDumpParser.parse(old)!!

        assertThat(parsed.apps).containsExactly(UidPower(10123, 45.6), UidPower(1000, 30.1)).inOrder()
        assertThat(parsed.capacityMah).isNull()
    }

    @Test
    fun `the report names the uids, adds up namesakes and keeps the largest`() {
        val many = usage.copy(
            apps = listOf(UidPower(1, 6.0), UidPower(2, 3.0), UidPower(3, 1.0), UidPower(4, 0.0)) +
                (10..30).map { UidPower(it, 0.5) },
        )

        val report = BatteryUsageReport.from(many) { uid -> if (uid == 2 || uid == 3) "Pair" else "App $uid" }

        // 6 + 3 + 1 + 21 × 0.5 = 20.5 mAh across all uids.
        assertThat(report.apps).hasSize(BatteryUsageReport.APP_LIMIT)
        assertThat(report.apps[0]).isEqualTo(NamedPower("App 1", 6.0, 6.0 / 20.5))
        assertThat(report.apps[1]).isEqualTo(NamedPower("Pair", 4.0, 4.0 / 20.5))
        assertThat(report.apps.map { it.label }).doesNotContain("App 4")
    }

    @Test
    fun `an app's wake locks are added up under its name`() {
        val held = usage.copy(
            partialWakeLocks = listOf(
                WakeHold("sync", 4_000L, 2, uid = 10001),
                WakeHold("upload", 9_000L, 1, uid = 10001),
                WakeHold("AudioMix", 3_000L, 0, uid = 10002),
                WakeHold("blip", 36L, 5, uid = 10003),
            ),
        )

        val report = BatteryUsageReport.from(held) { uid -> "App $uid" }

        // The 36 ms hold is noise and left out.
        assertThat(report.appWakeLocks).containsExactly(
            WakeEntry("App 10001", null, listOf("upload", "sync"), 13_000L, 3),
            WakeEntry("App 10002", null, listOf("AudioMix"), 3_000L, 0),
        ).inOrder()
    }

    @Test
    fun `kernel wake locks and wakeup reasons are gathered by the part of the phone`() {
        val held = usage.copy(
            kernelWakeLocks = listOf(
                WakeHold("qcom_rx_wakelock", 2_000L, 36),
                WakeHold("wlan_pno_wl", 5_000L, 2),
                WakeHold("894000.qcom,qup_uart", 6_000L, 5),
                WakeHold("event6", 71L, 2),
            ),
        )

        val report = BatteryUsageReport.from(held) { uid -> "App $uid" }

        assertThat(report.kernelWakeLocks).containsExactly(
            WakeEntry(null, WakeCategory.WIFI, listOf("wlan_pno_wl", "qcom_rx_wakelock"), 7_000L, 38),
            WakeEntry(null, null, listOf("894000.qcom,qup_uart"), 6_000L, 5),
        ).inOrder()
        assertThat(report.wakeupReasons.map { it.category })
            .containsExactly(WakeCategory.SLEEP_INTERRUPTED, WakeCategory.ALARM).inOrder()
    }

    @Test
    fun `system users without a package still get a name`() {
        assertThat(SystemUids.name(1082)).isEqualTo("artd")
        assertThat(SystemUids.name(1041)).isEqualTo("audioserver")
        assertThat(SystemUids.name(10345)).isNull()
    }

    @Test
    fun `something that is not a battery stats dump is no usage at all`() {
        assertThat(BatteryStatsDumpParser.parse("")).isNull()
        assertThat(BatteryStatsDumpParser.parse("Permission Denial: can't dump BatteryStats")).isNull()
    }
}
