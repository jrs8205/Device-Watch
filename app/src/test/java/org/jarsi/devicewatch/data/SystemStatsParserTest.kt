package org.jarsi.devicewatch.data

import android.telephony.TelephonyManager
import org.jarsi.devicewatch.data.SystemStatsParser.CoreFreq
import org.jarsi.devicewatch.data.SystemStatsParser.CpuSnapshot
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure-JVM tests for the parsing/maths behind the repository. No Robolectric needed:
 * [SystemStatsParser] is Context-free and the `TelephonyManager.NETWORK_TYPE_*`
 * constants inline at compile time.
 */
class SystemStatsParserTest {

    // --- /proc/stat snapshot ---

    @Test
    fun `parseCpuSnapshot sums total and idle plus iowait`() {
        // user nice system idle iowait irq softirq ...
        val snapshot = SystemStatsParser.parseCpuSnapshot("cpu  100 20 30 700 50 0 10 0 0 0")
        assertThat(snapshot).isEqualTo(CpuSnapshot(idle = 750, total = 910))
    }

    @Test
    fun `parseCpuSnapshot returns null for null input`() {
        assertThat(SystemStatsParser.parseCpuSnapshot(null)).isNull()
    }

    @Test
    fun `parseCpuSnapshot returns null when too few columns`() {
        assertThat(SystemStatsParser.parseCpuSnapshot("cpu 1 2 3")).isNull()
    }

    // --- CPU load delta ---

    @Test
    fun `cpuLoadPercent computes busy fraction from two snapshots`() {
        val previous = CpuSnapshot(idle = 100, total = 200)
        val current = CpuSnapshot(idle = 150, total = 300)
        // total delta 100, idle delta 50 -> 50% busy
        assertThat(SystemStatsParser.cpuLoadPercent(previous, current)).isEqualTo(50)
    }

    @Test
    fun `cpuLoadPercent is unavailable without forward progress`() {
        val same = CpuSnapshot(idle = 100, total = 200)
        assertThat(SystemStatsParser.cpuLoadPercent(same, same)).isEqualTo(UNAVAILABLE_INT)
    }

    // --- time_in_state ---

    @Test
    fun `parseTimeInState parses valid rows and skips malformed`() {
        val map = SystemStatsParser.parseTimeInState("1000000 50\n2000000 150\ngarbage\n")
        assertThat(map).containsExactly(1_000_000L, 50L, 2_000_000L, 150L)
    }

    @Test
    fun `parseTimeInState returns empty for null`() {
        assertThat(SystemStatsParser.parseTimeInState(null)).isEmpty()
    }

    // --- residency-based load ---

    @Test
    fun `residencyLoadPercent reports full load when all time at max freq`() {
        val previous = mapOf(0 to mapOf(1_000L to 0L, 2_000L to 0L))
        val current = mapOf(0 to mapOf(1_000L to 0L, 2_000L to 100L))
        assertThat(SystemStatsParser.residencyLoadPercent(previous, current)).isEqualTo(100)
    }

    @Test
    fun `residencyLoadPercent is unavailable without data`() {
        assertThat(SystemStatsParser.residencyLoadPercent(emptyMap(), emptyMap()))
            .isEqualTo(UNAVAILABLE_INT)
    }

    // --- current-frequency pressure ---

    @Test
    fun `frequencyPressurePercent positions current between min and max`() {
        val cores = listOf(CoreFreq(current = 1_500, max = 2_000, min = 1_000))
        assertThat(SystemStatsParser.frequencyPressurePercent(cores)).isEqualTo(50)
    }

    @Test
    fun `frequencyPressurePercent is unavailable for no usable cores`() {
        assertThat(SystemStatsParser.frequencyPressurePercent(emptyList())).isEqualTo(UNAVAILABLE_INT)
    }

    // --- battery wear ---

    @Test
    fun `batteryCapacityPercent computes full vs design`() {
        assertThat(SystemStatsParser.batteryCapacityPercent(4_500_000, 5_000_000)).isEqualTo(90)
    }

    @Test
    fun `batteryCapacityPercent is unavailable when inputs missing or zero`() {
        assertThat(SystemStatsParser.batteryCapacityPercent(null, 5_000_000)).isEqualTo(UNAVAILABLE_INT)
        assertThat(SystemStatsParser.batteryCapacityPercent(4_500_000, 0)).isEqualTo(UNAVAILABLE_INT)
    }

    // --- mobile generation ---

    @Test
    fun `mobileGenerationLabel maps network types`() {
        assertThat(SystemStatsParser.mobileGenerationLabel(TelephonyManager.NETWORK_TYPE_LTE)).isEqualTo("4G")
        assertThat(SystemStatsParser.mobileGenerationLabel(TelephonyManager.NETWORK_TYPE_NR)).isEqualTo("5G")
        assertThat(SystemStatsParser.mobileGenerationLabel(TelephonyManager.NETWORK_TYPE_UMTS)).isEqualTo("3G")
        assertThat(SystemStatsParser.mobileGenerationLabel(TelephonyManager.NETWORK_TYPE_GPRS)).isEqualTo("2G")
        assertThat(SystemStatsParser.mobileGenerationLabel(TelephonyManager.NETWORK_TYPE_IWLAN)).isEqualTo(UNAVAILABLE_TEXT)
        assertThat(SystemStatsParser.mobileGenerationLabel(TelephonyManager.NETWORK_TYPE_UNKNOWN)).isEqualTo(UNAVAILABLE_TEXT)
    }

    @Test
    fun `highestMobileGeneration prefers the newest available`() {
        assertThat(SystemStatsParser.highestMobileGeneration(listOf("3G", "5G", "4G"))).isEqualTo("5G")
        assertThat(SystemStatsParser.highestMobileGeneration(listOf("2G", "3G"))).isEqualTo("3G")
        assertThat(SystemStatsParser.highestMobileGeneration(emptyList())).isNull()
    }

    // --- Wi-Fi ---

    @Test
    fun `normalizedWifiSsid strips quotes and rejects hidden placeholders`() {
        assertThat(SystemStatsParser.normalizedWifiSsid("\"MyWifi\"")).isEqualTo("MyWifi")
        assertThat(SystemStatsParser.normalizedWifiSsid("<unknown ssid>")).isNull()
        assertThat(SystemStatsParser.normalizedWifiSsid("0x")).isNull()
        assertThat(SystemStatsParser.normalizedWifiSsid("   ")).isNull()
        assertThat(SystemStatsParser.normalizedWifiSsid(null)).isNull()
    }

    @Test
    fun `wifiBandLabel maps frequency ranges`() {
        assertThat(SystemStatsParser.wifiBandLabel(2_412)).isEqualTo("2,4 GHz")
        assertThat(SystemStatsParser.wifiBandLabel(5_180)).isEqualTo("5 GHz")
        assertThat(SystemStatsParser.wifiBandLabel(6_000)).isEqualTo("6 GHz")
        assertThat(SystemStatsParser.wifiBandLabel(0)).isEqualTo(UNAVAILABLE_TEXT)
    }

    // --- signal ---

    @Test
    fun `filterValidDbm keeps only plausible readings`() {
        val filtered = SystemStatsParser.filterValidDbm(listOf(-200, -90, 0, -40, -41, -140))
        assertThat(filtered).containsExactly(-90, -40, -41, -140).inOrder()
    }

    // --- battery discharge time remaining ---

    @Test
    fun `dischargeTimeRemainingMinutes computes from a microamp reading`() {
        // Documented unit: CHARGE_COUNTER µAh, CURRENT_NOW µA. 3 000 000 / 500 000 = 6 h.
        assertThat(SystemStatsParser.dischargeTimeRemainingMinutes(3_000_000, -500_000))
            .isEqualTo(360)
    }

    @Test
    fun `dischargeTimeRemainingMinutes rescales a milliamp reading`() {
        // Real Samsung Z Flip 4 values: 1 530 780 µAh at 46 %, CURRENT_NOW -253 — that is
        // mA, not µA. Read as µA this showed ~6 050 h; correct is 1 530 780 / 253 000
        // ≈ 6,05 h = 363 min.
        assertThat(SystemStatsParser.dischargeTimeRemainingMinutes(1_530_780, -253))
            .isEqualTo(363)
    }

    @Test
    fun `dischargeTimeRemainingMinutes ignores the sign convention`() {
        assertThat(SystemStatsParser.dischargeTimeRemainingMinutes(3_000_000, 500_000))
            .isEqualTo(360)
    }

    @Test
    fun `dischargeTimeRemainingMinutes is null for missing or implausible readings`() {
        assertThat(SystemStatsParser.dischargeTimeRemainingMinutes(0, -500_000)).isNull()
        assertThat(SystemStatsParser.dischargeTimeRemainingMinutes(-1, -500_000)).isNull()
        assertThat(SystemStatsParser.dischargeTimeRemainingMinutes(3_000_000, 0)).isNull()
        assertThat(SystemStatsParser.dischargeTimeRemainingMinutes(3_000_000, Int.MIN_VALUE)).isNull()
        // Implausible in both units: 1 µA and 0,001 A.
        assertThat(SystemStatsParser.dischargeTimeRemainingMinutes(3_000_000, 1)).isNull()
        // 20 000 000 µA = 20 A and 20 000 A as mA — both implausible.
        assertThat(SystemStatsParser.dischargeTimeRemainingMinutes(3_000_000, -20_000_000)).isNull()
    }

    // --- display density bucket ---

    @Test
    fun `densityBucketLabel maps dpi to Android buckets`() {
        assertThat(SystemStatsParser.densityBucketLabel(160)).isEqualTo("mdpi")
        assertThat(SystemStatsParser.densityBucketLabel(213)).isEqualTo("tvdpi")
        assertThat(SystemStatsParser.densityBucketLabel(240)).isEqualTo("hdpi")
        assertThat(SystemStatsParser.densityBucketLabel(320)).isEqualTo("xhdpi")
        assertThat(SystemStatsParser.densityBucketLabel(420)).isEqualTo("xxhdpi")
        assertThat(SystemStatsParser.densityBucketLabel(640)).isEqualTo("xxxhdpi")
        assertThat(SystemStatsParser.densityBucketLabel(0)).isEqualTo(UNAVAILABLE_TEXT)
    }
}
