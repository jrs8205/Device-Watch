package com.example.modernwidget.di

import com.example.modernwidget.data.AppSettingsRepository
import com.example.modernwidget.data.AppSettingsRepositoryImpl
import com.example.modernwidget.data.AppUsageRepository
import com.example.modernwidget.data.AppUsageRepositoryImpl
import com.example.modernwidget.data.NotificationStats
import com.example.modernwidget.data.NotificationStatsImpl
import com.example.modernwidget.data.SystemStatsRepository
import com.example.modernwidget.data.SystemStatsRepositoryImpl
import com.example.modernwidget.widget.GlanceWidgetController
import com.example.modernwidget.widget.WidgetController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSystemStatsRepository(impl: SystemStatsRepositoryImpl): SystemStatsRepository

    @Binds
    @Singleton
    abstract fun bindWidgetController(impl: GlanceWidgetController): WidgetController

    @Binds
    @Singleton
    abstract fun bindAppSettingsRepository(impl: AppSettingsRepositoryImpl): AppSettingsRepository

    @Binds
    @Singleton
    abstract fun bindAppUsageRepository(impl: AppUsageRepositoryImpl): AppUsageRepository

    @Binds
    @Singleton
    abstract fun bindNotificationStats(impl: NotificationStatsImpl): NotificationStats
}
