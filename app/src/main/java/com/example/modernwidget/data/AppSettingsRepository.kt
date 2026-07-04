package com.example.modernwidget.data

/**
 * Process-wide user settings that both the UI and [SystemStatsRepository] read.
 * Synchronous by design: the stats repository consults these inside its
 * synchronous compute path (see [AppSettingsRepositoryImpl]).
 */
interface AppSettingsRepository {
    fun dataCounterMode(): DataCounterMode

    fun setDataCounterMode(mode: DataCounterMode)

    /** Billing-cycle start day of month, always in 1..31. */
    fun cycleStartDay(): Int

    fun setCycleStartDay(day: Int)
}
