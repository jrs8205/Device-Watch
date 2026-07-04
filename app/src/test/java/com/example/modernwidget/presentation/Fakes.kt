package com.example.modernwidget.presentation

import com.example.modernwidget.data.AppDataUsage
import com.example.modernwidget.data.AppScreenTime
import com.example.modernwidget.data.AppSettingsRepository
import com.example.modernwidget.data.AppUsageRepository
import com.example.modernwidget.data.DataCounterMode
import com.example.modernwidget.data.LaunchableApp
import com.example.modernwidget.data.NotificationStats
import com.example.modernwidget.data.UsageHistory
import com.example.modernwidget.data.UsageTotals
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
    var supportsUnlocks: Boolean = true,
    var unlocksByDay: Map<LocalDate, Int> = emptyMap(),
    var screenByDay: Map<LocalDate, Long> = emptyMap(),
    var totalsToday: UsageTotals? = null,
) : AppUsageRepository {
    override fun hasUsageAccess(): Boolean = hasAccess

    override suspend fun screenTimeToday(): List<AppScreenTime> =
        if (hasAccess) screenTimes else emptyList()

    override suspend fun dataConsumersToday(): List<AppDataUsage> =
        if (hasAccess) dataConsumers else emptyList()

    override suspend fun launchableAppsByLastUse(): List<LaunchableApp> =
        if (hasAccess) apps else emptyList()

    override fun supportsUnlockCounting(): Boolean = supportsUnlocks

    override suspend fun unlockCountsByDay(days: Int): Map<LocalDate, Int> =
        if (hasAccess && supportsUnlocks) unlocksByDay else emptyMap()

    override suspend fun screenTimeByDay(days: Int): Map<LocalDate, Long> =
        if (hasAccess) screenByDay else emptyMap()

    override suspend fun usageTotalsToday(): UsageTotals? =
        if (hasAccess) totalsToday else null
}

internal class FakeNotificationStats(
    var enabled: Boolean = false,
    var totalsByDay: Map<LocalDate, Int> = emptyMap(),
    private val packageCounts: Map<String, Int> = emptyMap(),
) : NotificationStats {
    override fun totalForDay(day: LocalDate): Int = totalsByDay[day] ?: 0

    override fun countForPackage(packageName: String, day: LocalDate): Int =
        packageCounts[packageName] ?: 0

    override fun totalBetween(start: LocalDate, end: LocalDate): Int {
        var day = start
        var sum = 0
        while (!day.isAfter(end)) {
            sum += totalForDay(day)
            day = day.plusDays(1)
        }
        return sum
    }

    override fun increment(packageName: String, day: LocalDate) = Unit

    override fun purge(today: LocalDate) = Unit

    override fun isListenerEnabled(): Boolean = enabled
}

/** In-memory UsageHistory with real summing logic so period math is exercised. */
internal class FakeUsageHistory : UsageHistory {
    val unlocks = mutableMapOf<LocalDate, Int>()
    val screen = mutableMapOf<LocalDate, Long>()
    val boots = mutableMapOf<LocalDate, Int>()
    val charges = mutableMapOf<LocalDate, Int>()
    var lastBootCount: Int = -1
    var purgedWith: LocalDate? = null

    override fun recordUnlocks(day: LocalDate, count: Int) {
        unlocks[day] = count
    }

    override fun recordScreenTime(day: LocalDate, millis: Long) {
        screen[day] = millis
    }

    override fun registerBootCount(day: LocalDate, bootCountTotal: Int) {
        if (lastBootCount in 0 until bootCountTotal) {
            boots.merge(day, bootCountTotal - lastBootCount, Int::plus)
        }
        lastBootCount = bootCountTotal
    }

    override fun incrementCharge(day: LocalDate) {
        charges.merge(day, 1, Int::plus)
    }

    override fun unlocksBetween(start: LocalDate, end: LocalDate): Int =
        sumInt(unlocks, start, end)

    override fun screenTimeBetween(start: LocalDate, end: LocalDate): Long =
        screen.filterKeys { !it.isBefore(start) && !it.isAfter(end) }.values.sum()

    override fun bootsBetween(start: LocalDate, end: LocalDate): Int =
        sumInt(boots, start, end)

    override fun chargesBetween(start: LocalDate, end: LocalDate): Int =
        sumInt(charges, start, end)

    override fun purge(today: LocalDate) {
        purgedWith = today
    }

    private fun sumInt(source: Map<LocalDate, Int>, start: LocalDate, end: LocalDate): Int =
        source.filterKeys { !it.isBefore(start) && !it.isAfter(end) }.values.sum()
}
