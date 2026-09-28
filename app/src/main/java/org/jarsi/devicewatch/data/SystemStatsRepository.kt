package org.jarsi.devicewatch.data

import java.time.YearMonth
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for live device statistics.
 *
 * Implementations read real Android and kernel sources and must never return
 * fabricated values: when a metric is unavailable, the corresponding field uses
 * the documented unavailable sentinel ([org.jarsi.devicewatch.system.UNAVAILABLE_TEXT] etc.).
 */
/** Device-level data usage over a window; negative values mean unavailable. */
data class DataUsageSince(val wifiGb: Double, val mobileGb: Double)

/** Device-level data usage for one calendar month; negative values mean unavailable. */
data class MonthlyDataUsage(val month: YearMonth, val mobileGb: Double, val wifiGb: Double)

interface SystemStatsRepository {
    /** Latest reading from any caller, including the widget service; observing performs no I/O. */
    val latestStats: StateFlow<SystemStats?>

    suspend fun getStats(): SystemStats

    /** Static, root-free device facts (build, SoC, display, memory). Safe to read once. */
    suspend fun getDeviceInfo(): DeviceInfo

    /** Total Wi-Fi and (metered) mobile data used since [startMillis]. */
    suspend fun dataUsedSince(startMillis: Long): DataUsageSince

    /**
     * Wi-Fi and (metered) mobile usage for the current plus [monthsBack] previous calendar
     * months, newest first. Served straight from Android's own stats — no local storage.
     */
    suspend fun monthlyDataUsage(monthsBack: Int = 12): List<MonthlyDataUsage>

    /**
     * Wi-Fi and mobile traffic since [startMillis] split into foreground and
     * background shares, plus the mobile traffic spent roaming. A per-UID query,
     * heavier than [getStats], so the screen asks for it and the widget loop does not.
     */
    suspend fun dataBreakdown(startMillis: Long): DataBreakdown

    /**
     * What the used internal storage holds (apps, cache, media, other); null without
     * usage access or when the platform refuses. Scans every app's sizes, so the
     * screen asks for it and the widget loop does not.
     */
    suspend fun storageBreakdown(): StorageBreakdown?

    /** Settings and states that change while the app runs; read on each screen refresh. */
    suspend fun deviceState(): DeviceState
}
