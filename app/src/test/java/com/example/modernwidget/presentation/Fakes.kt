package com.example.modernwidget.presentation

import com.example.modernwidget.data.AppDataUsage
import com.example.modernwidget.data.AppScreenTime
import com.example.modernwidget.data.AppSettingsRepository
import com.example.modernwidget.data.AppUsageRepository
import com.example.modernwidget.data.DataCounterMode
import com.example.modernwidget.data.LaunchableApp
import com.example.modernwidget.data.NotificationStats
import com.example.modernwidget.data.UNAVAILABLE_INT
import java.time.LocalDate

/** Hand-written fakes shared by the presentation-layer ViewModel tests. */

internal class FakeAppSettingsRepository(
    var mode: DataCounterMode = DataCounterMode.DAY,
    var cycleDay: Int = 1,
    var oldestFirst: Boolean = true,
) : AppSettingsRepository {
    override fun dataCounterMode(): DataCounterMode = mode

    override fun setDataCounterMode(mode: DataCounterMode) {
        this.mode = mode
    }

    override fun cycleStartDay(): Int = cycleDay

    override fun setCycleStartDay(day: Int) {
        cycleDay = day
    }

    override fun appsOldestFirst(): Boolean = oldestFirst

    override fun setAppsOldestFirst(oldestFirst: Boolean) {
        this.oldestFirst = oldestFirst
    }
}

internal class FakeAppUsageRepository(
    var hasAccess: Boolean = true,
    var screenTimes: List<AppScreenTime> = emptyList(),
    var dataConsumers: List<AppDataUsage> = emptyList(),
    var apps: List<LaunchableApp> = emptyList(),
    var unlockCount: Int = UNAVAILABLE_INT,
) : AppUsageRepository {
    override fun hasUsageAccess(): Boolean = hasAccess

    override suspend fun screenTimeToday(): List<AppScreenTime> = screenTimes

    override suspend fun dataConsumersToday(): List<AppDataUsage> = dataConsumers

    override suspend fun launchableAppsByLastUse(): List<LaunchableApp> = apps

    override suspend fun unlockCountToday(): Int = unlockCount
}

internal class FakeNotificationStats(
    var enabled: Boolean = false,
    var total: Int = 0,
    private val packageCounts: Map<String, Int> = emptyMap(),
) : NotificationStats {
    override fun totalForDay(day: LocalDate): Int = total

    override fun countForPackage(packageName: String, day: LocalDate): Int =
        packageCounts[packageName] ?: 0

    override fun increment(packageName: String, day: LocalDate) = Unit

    override fun purge(today: LocalDate) = Unit

    override fun isListenerEnabled(): Boolean = enabled
}
