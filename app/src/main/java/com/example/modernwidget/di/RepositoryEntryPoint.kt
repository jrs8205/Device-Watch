package com.example.modernwidget.di

import com.example.modernwidget.data.SystemStatsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Bridges Hilt's object graph into non-Hilt entry points (Glance [ActionCallback]s),
 * which Android instantiates itself and therefore cannot use `@AndroidEntryPoint`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RepositoryEntryPoint {
    fun systemStatsRepository(): SystemStatsRepository
}
