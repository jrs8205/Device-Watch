package org.jarsi.devicewatch.presentation

import org.jarsi.devicewatch.data.DataCounterMode
import org.jarsi.devicewatch.data.DataPeriodCalculator
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Usage totals for the elapsed part of this period and the same-length slice of the previous one. */
data class PeriodComparisonData(
    val screenTimeNowMillis: Long,
    val screenTimePrevMillis: Long,
    val unlocksNow: Int,
    val unlocksPrev: Int,
    val notificationsNow: Int,
    val notificationsPrev: Int,
    val daysCompared: Int,
)

object PeriodComparison {

    /** Both tally stores retain 62 days; a window reaching past that would read zeros. */
    private const val RETAINED_DAYS = 62L

    data class Windows(
        val currentStart: LocalDate,
        val currentEnd: LocalDate,
        val previousStart: LocalDate,
        val previousEnd: LocalDate,
    ) {
        val daysCompared: Int
            get() = (ChronoUnit.DAYS.between(currentStart, currentEnd) + 1).toInt()
    }

    /**
     * Day-count-aligned comparison windows: the elapsed part of the current period
     * against the first equally many days of the previous one. DAY mode is today
     * vs yesterday. Null if the previous window would start before the 62-day
     * store retention — unreachable for the current modes (two billing cycles
     * never exceed it), kept as a guard for longer periods.
     */
    fun windows(mode: DataCounterMode, cycleStartDay: Int, today: LocalDate): Windows? {
        val currentStart = DataPeriodCalculator.periodStart(mode, cycleStartDay, today)
        val previousStart =
            DataPeriodCalculator.periodStart(mode, cycleStartDay, currentStart.minusDays(1))
        val elapsedDays = ChronoUnit.DAYS.between(currentStart, today) + 1
        val oldestRetained = today.minusDays(RETAINED_DAYS - 1)
        if (previousStart.isBefore(oldestRetained)) return null
        return Windows(
            currentStart = currentStart,
            currentEnd = today,
            previousStart = previousStart,
            previousEnd = previousStart.plusDays(elapsedDays - 1),
        )
    }

    /** Signed whole-percent change from [previous] to [now]; null when there is no baseline. */
    fun changePercent(now: Long, previous: Long): Int? {
        if (previous <= 0L) return null
        return ((now - previous).toDouble() / previous * 100).roundToInt()
    }
}
