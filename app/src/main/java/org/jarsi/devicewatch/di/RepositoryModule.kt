package org.jarsi.devicewatch.di

import org.jarsi.devicewatch.data.AppSettingsRepository
import org.jarsi.devicewatch.data.AppSettingsRepositoryImpl
import org.jarsi.devicewatch.data.AppUsageRepository
import org.jarsi.devicewatch.data.AppUsageRepositoryImpl
import org.jarsi.devicewatch.data.BatteryStatusReader
import org.jarsi.devicewatch.data.BatteryStatusReaderImpl
import org.jarsi.devicewatch.data.ChargeAnchorStore
import org.jarsi.devicewatch.data.ChargeAnchorStoreImpl
import org.jarsi.devicewatch.data.NotificationLog
import org.jarsi.devicewatch.data.NotificationLogImpl
import org.jarsi.devicewatch.data.NotificationStats
import org.jarsi.devicewatch.data.NotificationStatsImpl
import org.jarsi.devicewatch.data.SystemStatsRepository
import org.jarsi.devicewatch.data.SystemStatsRepositoryImpl
import org.jarsi.devicewatch.data.UsageHistory
import org.jarsi.devicewatch.data.UsageHistoryImpl
import org.jarsi.devicewatch.widget.GlanceWidgetController
import org.jarsi.devicewatch.widget.WidgetController
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

    @Binds
    @Singleton
    abstract fun bindUsageHistory(impl: UsageHistoryImpl): UsageHistory

    @Binds
    @Singleton
    abstract fun bindNotificationLog(impl: NotificationLogImpl): NotificationLog

    @Binds
    @Singleton
    abstract fun bindChargeAnchorStore(impl: ChargeAnchorStoreImpl): ChargeAnchorStore

    @Binds
    @Singleton
    abstract fun bindBatteryStatusReader(impl: BatteryStatusReaderImpl): BatteryStatusReader
}
