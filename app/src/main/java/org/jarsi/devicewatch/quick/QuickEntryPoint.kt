package org.jarsi.devicewatch.quick

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.jarsi.devicewatch.data.AppSettingsRepository

/** For the tiles and widgets, which Android creates itself and Hilt cannot inject. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface QuickEntryPoint {
    fun quickStatsSource(): QuickStatsSource
    fun appSettings(): AppSettingsRepository
}
