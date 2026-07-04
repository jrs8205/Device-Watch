package com.example.modernwidget.data

import android.net.wifi.ScanResult
import android.telephony.TelephonyManager
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure, Context-free calculations behind [SystemStatsRepositoryImpl].
 *
 * Everything here is deterministic given its inputs (no Android Context, no I/O,
 * no mutable state), which makes the tricky parsing and math unit-testable on the JVM.
 * The repository owns the actual file/system reads and the snapshot lifecycle.
 */
internal object SystemStatsParser {

    data class CpuSnapshot(val idle: Long, val total: Long)

    /** A single CPU core's current / max / min scaling frequency in kHz. */
    data class CoreFreq(val current: Long, val max: Long, val min: Long)

    /** Maps a display's densityDpi to its Android density bucket name (mdpi, xxhdpi, …). */
    fun densityBucketLabel(densityDpi: Int): String = when {
        densityDpi <= 0 -> UNAVAILABLE_TEXT
        densityDpi <= 120 -> "ldpi"
        densityDpi <= 160 -> "mdpi"
        densityDpi <= 213 -> "tvdpi"
        densityDpi <= 240 -> "hdpi"
        densityDpi <= 320 -> "xhdpi"
        densityDpi <= 480 -> "xxhdpi"
        densityDpi <= 640 -> "xxxhdpi"
        else -> "xxxhdpi+"
    }

    /** Maps a `WifiInfo.getWifiStandard()` value to a marketing Wi-Fi generation name. */
    fun wifiStandardLabel(standard: Int): String = when (standard) {
        ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4"
        ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5"
        ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6"
        ScanResult.WIFI_STANDARD_11BE -> "Wi-Fi 7"
        else -> UNAVAILABLE_TEXT
    }

    /** Parses the aggregate `cpu` line of `/proc/stat` into idle/total jiffies. */
    fun parseCpuSnapshot(procStatFirstLine: String?): CpuSnapshot? {
        val line = procStatFirstLine ?: return null
        val parts = line
            .trim()
            .split(Regex("\\s+"))
            .drop(1)
            .mapNotNull { it.toLongOrNull() }
        if (parts.size < 7) return null
        val idle = parts[3] + parts.getOrElse(4) { 0L }
        val total = parts.sum()
        return CpuSnapshot(idle = idle, total = total)
    }

    /** Busy percentage from the delta of two `/proc/stat` snapshots. */
    fun cpuLoadPercent(previous: CpuSnapshot, current: CpuSnapshot): Int {
        val totalDelta = current.total - previous.total
        val idleDelta = current.idle - previous.idle
        return if (totalDelta > 0L) {
            (((totalDelta - idleDelta).toDouble() / totalDelta.toDouble()) * 100).toInt().coerceIn(0, 100)
        } else {
            UNAVAILABLE_INT
        }
    }

    /** Parses a `cpufreq/stats/time_in_state` table into frequency(kHz) -> ticks. */
    fun parseTimeInState(text: String?): Map<Long, Long> {
        if (text == null) return emptyMap()
        return text.lineSequence()
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val frequency = parts[0].toLongOrNull()
                    val ticks = parts[1].toLongOrNull()
                    if (frequency != null && ticks != null) frequency to ticks else null
                } else {
                    null
                }
            }
            .toMap()
    }

    /**
     * Average busy estimate from time-in-state residency deltas across cores:
     * how far the weighted-average frequency sits between each core's min and max.
     */
    fun residencyLoadPercent(
        previousByCore: Map<Int, Map<Long, Long>>,
        currentByCore: Map<Int, Map<Long, Long>>,
    ): Int {
        var totalPercent = 0.0
        var countedCores = 0

        for ((coreIndex, currentStates) in currentByCore) {
            val previousStates = previousByCore[coreIndex] ?: continue
            val minFreq = currentStates.keys.minOrNull() ?: continue
            val maxFreq = currentStates.keys.maxOrNull() ?: continue
            if (maxFreq <= minFreq) continue

            var totalTicks = 0L
            var weightedFreq = 0.0
            for ((freq, ticks) in currentStates) {
                val delta = ticks - (previousStates[freq] ?: 0L)
                if (delta > 0L) {
                    totalTicks += delta
                    weightedFreq += freq.toDouble() * delta.toDouble()
                }
            }

            if (totalTicks > 0L) {
                val averageFreq = weightedFreq / totalTicks.toDouble()
                val percent = ((averageFreq - minFreq.toDouble()) / (maxFreq - minFreq).toDouble()) * 100.0
                totalPercent += percent.coerceIn(0.0, 100.0)
                countedCores++
            }
        }

        return if (countedCores > 0) {
            (totalPercent / countedCores.toDouble()).roundToInt().coerceIn(0, 100)
        } else {
            UNAVAILABLE_INT
        }
    }

    /** Last-resort load estimate from where the current frequency sits in [min, max]. */
    fun frequencyPressurePercent(cores: List<CoreFreq>): Int {
        var totalPercent = 0.0
        var countedCores = 0

        for (core in cores) {
            if (core.max > 0L && core.current > 0L) {
                val percent = if (core.max > core.min) {
                    ((core.current - core.min).toDouble() / (core.max - core.min).toDouble()) * 100.0
                } else {
                    (core.current.toDouble() / core.max.toDouble()) * 100.0
                }
                totalPercent += percent.coerceIn(0.0, 100.0)
                countedCores++
            }
        }

        return if (countedCores > 0) {
            (totalPercent / countedCores.toDouble()).roundToInt().coerceIn(0, 100)
        } else {
            UNAVAILABLE_INT
        }
    }

    /** Battery wear: full charge capacity as a percentage of the design capacity. */
    fun batteryCapacityPercent(chargeFull: Long?, chargeFullDesign: Long?): Int {
        return if (chargeFull != null && chargeFullDesign != null && chargeFullDesign > 0L) {
            ((chargeFull.toDouble() / chargeFullDesign.toDouble()) * 100).toInt().coerceIn(0, 150)
        } else {
            UNAVAILABLE_INT
        }
    }

    /** Maps a [TelephonyManager] network-type constant to a 2G/3G/4G/5G label. */
    @Suppress("DEPRECATION") // legacy NETWORK_TYPE_* constants are still reported by real devices
    fun mobileGenerationLabel(networkType: Int): String {
        return when (networkType) {
            TelephonyManager.NETWORK_TYPE_LTE -> "4G"
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA,
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_GSM,
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G"
            TelephonyManager.NETWORK_TYPE_IWLAN -> UNAVAILABLE_TEXT
            else -> UNAVAILABLE_TEXT
        }
    }

    /** Picks the most advanced generation from a set of detected labels. */
    fun highestMobileGeneration(labels: List<String>): String? {
        return when {
            labels.any { it == "5G" } -> "5G"
            labels.any { it == "4G" } -> "4G"
            labels.any { it == "3G" } -> "3G"
            labels.any { it == "2G" } -> "2G"
            else -> labels.firstOrNull()
        }
    }

    /** Cleans a raw SSID, rejecting blanks and Android's hidden placeholders. */
    fun normalizedWifiSsid(rawSsid: String?): String? {
        return rawSsid
            ?.trim()
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" && it != "0x" }
    }

    /** Human label for a Wi-Fi channel frequency in MHz. */
    fun wifiBandLabel(frequencyMhz: Int): String {
        return when (frequencyMhz) {
            in 2400..2500 -> "2,4 GHz"
            in 4900..5900 -> "5 GHz"
            in 5925..7125 -> "6 GHz"
            else -> UNAVAILABLE_TEXT
        }
    }

    /** Plausible cellular signal range; filters out sentinel / out-of-range readings. */
    fun isValidDbm(dbm: Int): Boolean = dbm in -140..-40

    fun filterValidDbm(values: Iterable<Int>): List<Int> = values.filter { isValidDbm(it) }

    /**
     * Remaining discharge time in minutes from BATTERY_PROPERTY_CHARGE_COUNTER (µAh) and
     * BATTERY_PROPERTY_CURRENT_NOW. CURRENT_NOW is documented as µA, but some OEMs
     * (e.g. Samsung) report mA and the sign convention for discharge also varies — so the
     * magnitude is used, and a reading that is only plausible as mA is rescaled.
     * Returns null when a reading is missing or implausible (never show a fabricated value).
     */
    fun dischargeTimeRemainingMinutes(chargeCounterMicroAh: Int, currentNowRaw: Int): Int? {
        if (chargeCounterMicroAh <= 0) return null
        if (currentNowRaw == 0 || currentNowRaw == Int.MIN_VALUE) return null
        val plausibleAmps = 0.01..15.0
        val magnitude = abs(currentNowRaw.toDouble())
        val currentMicroAmps = when {
            magnitude / 1_000_000.0 in plausibleAmps -> magnitude
            magnitude / 1_000.0 in plausibleAmps -> magnitude * 1_000.0
            else -> return null
        }
        return (chargeCounterMicroAh.toDouble() / currentMicroAmps * 60.0).toInt()
    }
}
