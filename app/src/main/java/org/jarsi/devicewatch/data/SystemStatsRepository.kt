package org.jarsi.devicewatch.data

/**
 * Single source of truth for live device statistics.
 *
 * Implementations read real Android and kernel sources and must never return
 * fabricated values: when a metric is unavailable, the corresponding field uses
 * the documented unavailable sentinel ([org.jarsi.devicewatch.system.UNAVAILABLE_TEXT] etc.).
 */
/** Device-level data usage over a window; negative values mean unavailable. */
data class DataUsageSince(val wifiGb: Double, val mobileGb: Double)

interface SystemStatsRepository {
    suspend fun getStats(): SystemStats

    /** Static, root-free device facts (build, SoC, display, memory). Safe to read once. */
    suspend fun getDeviceInfo(): DeviceInfo

    /** Total Wi-Fi and (metered) mobile data used since [startMillis]. */
    suspend fun dataUsedSince(startMillis: Long): DataUsageSince
}
