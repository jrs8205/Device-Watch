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
 * matching, already-localized widget label, and [wifiDataSpan] and
 * [mobileDataSpan] the span alone, for the compact widget's short headings.
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
    /** Monotonic time (elapsedRealtime) the reading was taken, so a late write can tell it is stale. */
    val readAtElapsedMillis: Long = 0L,
    val storagePercent: Int,
    val wifiSsid: String,
    val wifiSsidName: String,
    val wifiBand: String,
    val wifiSpeedDown: Int,
    val wifiSpeedUp: Int,
    val wifiBytesTodayGb: Double,
    val wifiDataLabel: String,
    val wifiDataSpan: DataSpan = DataSpan.UNKNOWN,
    val operatorName: String,
    val mobileNetworkType: String,
    val mobileSignalDbm: Int,
    val mobileDataUsedGb: Double,
    val mobileDataTotalGb: Double,
    val mobileDataLabel: String,
    val mobileDataSpan: DataSpan = DataSpan.UNKNOWN,
    val simOperator: String,
    val simState: String,
    val simSlots: Int,
    /** Active data-SIM carrier for display; [UNAVAILABLE_TEXT] unless ≥2 active SIMs. */
    val dataSimName: String,
    val networkCountry: String,
    val wifiRssiDbm: Int,
    val wifiLinkSpeedMbps: Int,
    val wifiStandard: String,
    val ipAddress: String,
    val uptimeText: String,
    /** Raw uptime so a caller too narrow for [uptimeText] can format its own. */
    val uptimeMillis: Long = 0L,
    /**
     * First day (epoch day) of the counting period the data figures cover. Carried
     * with the reading so a consumer keys its per-period state to the period the
     * figures were actually taken in, not to whatever period it is by the time the
     * reading arrives.
     */
    val dataPeriodStartEpochDay: Long = 0L,
    /**
     * [AppSettingsRepository.dataSettingsGeneration] as read before the quota and
     * period settings this reading was computed from.
     */
    val dataSettingsGeneration: Long = 0L,
    /** What the phone is plugged into; [ChargeSource.NONE] on battery. */
    val chargeSource: ChargeSource = ChargeSource.NONE,
    /** How the battery is being charged while plugged in (Android 14+). */
    val chargingState: ChargingState = ChargingState.UNKNOWN,
    /**
     * The system's own time-left estimate (Android 12+, on battery), formatted
     * like [timeRemainingText]; [UNAVAILABLE_TEXT] when the system has none.
     */
    val systemEstimateText: String = UNAVAILABLE_TEXT,
    /** True when [systemEstimateText] is learned from this user's usage. */
    val systemEstimatePersonalized: Boolean = false,
    /** Deep sleep since boot and its share of the uptime; [UNAVAILABLE_TEXT] when unknown. */
    val deepSleepText: String = UNAVAILABLE_TEXT,
    /** The system's thermal state (Android 10+). */
    val thermalLevel: ThermalLevel = ThermalLevel.UNKNOWN,
    /** Share of the severe-throttling threshold reached (Android 11+); [UNAVAILABLE_INT] when unknown. */
    val thermalHeadroomPercent: Int = UNAVAILABLE_INT,
    /** The graphics processor's load; on most phones only a privileged shell can read it. */
    val gpuLoadPercent: Int = UNAVAILABLE_INT,
    /** Graphics processor temperature in °C, through a privileged shell. */
    val gpuTemp: Double = UNAVAILABLE_DOUBLE,
    /** The phone's surface ("skin") temperature in °C as its thermal service reckons it, through a privileged shell. */
    val skinTemp: Double = UNAVAILABLE_DOUBLE,
)

/** The span a data figure covers; UNKNOWN when there is no figure to cover one. */
enum class DataSpan { TODAY, PERIOD, SINCE_BOOT, UNKNOWN }
