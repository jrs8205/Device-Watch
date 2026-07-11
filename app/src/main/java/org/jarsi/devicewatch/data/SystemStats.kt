package org.jarsi.devicewatch.data

/** Sentinel used for any string metric the platform refuses to expose. */
const val UNAVAILABLE_TEXT = "—"

/** Sentinel used for any integer metric the platform refuses to expose. */
const val UNAVAILABLE_INT = -1

/** Sentinel used for any floating-point metric the platform refuses to expose. */
const val UNAVAILABLE_DOUBLE = -1.0

/**
 * Immutable snapshot of live device statistics produced by [SystemStatsRepository].
 *
 * Every field is real data read from Android/kernel sources. When a value is not
 * available with the app's granted permissions, the corresponding `UNAVAILABLE_*`
 * sentinel is used so the UI can render a dash instead of a fabricated value.
 *
 * Note on naming: the `*TodayGb`/`*UsedGb` data-usage fields cover the current
 * counting period selected in [AppSettingsRepository] — a calendar day by default,
 * or a one-month billing cycle. [wifiDataLabel] and [mobileDataLabel] carry the
 * matching, already-localized widget label.
 */
data class SystemStats(
    val batteryLevel: Int,
    val batteryStatus: String,
    val batteryHealth: String,
    val batteryTemp: Double,
    val batteryVoltage: Double,
    val timeRemainingText: String,
    val batteryCycleCount: Int,
    val batteryCapacityPercent: Int,
    val totalRamGb: Double,
    val usedRamGb: Double,
    val ramPercent: Int,
    val cpuCores: Int,
    val cpuAbi: String,
    val cpuFreqGhz: Double,
    val cpuLoadPercent: Int,
    val cpuLoadLabel: String,
    val cpuTemp: Double,
    val totalStorageGb: Double,
    val usedStorageGb: Double,
    val storagePercent: Int,
    val wifiSsid: String,
    val wifiSsidName: String,
    val wifiBand: String,
    val wifiSpeedDown: Int,
    val wifiSpeedUp: Int,
    val wifiBytesTodayGb: Double,
    val wifiDataLabel: String,
    val operatorName: String,
    val mobileNetworkType: String,
    val mobileSignalDbm: Int,
    val mobileDataUsedGb: Double,
    val mobileDataTotalGb: Double,
    val mobileDataLabel: String,
    val simOperator: String,
    val simState: String,
    val simSlots: Int,
    val networkCountry: String,
    val wifiRssiDbm: Int,
    val wifiLinkSpeedMbps: Int,
    val wifiStandard: String,
    val ipAddress: String,
    val uptimeText: String
)
