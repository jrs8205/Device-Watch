package com.example.modernwidget.data

/**
 * Single source of truth for live device statistics.
 *
 * Implementations read real Android and kernel sources and must never return
 * fabricated values: when a metric is unavailable, the corresponding field uses
 * the documented unavailable sentinel ([com.example.modernwidget.system.UNAVAILABLE_TEXT] etc.).
 */
interface SystemStatsRepository {
    suspend fun getStats(): SystemStats

    /** Static, root-free device facts (build, SoC, display, memory). Safe to read once. */
    suspend fun getDeviceInfo(): DeviceInfo
}
