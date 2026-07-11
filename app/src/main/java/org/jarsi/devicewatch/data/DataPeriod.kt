package com.example.modernwidget.data

import java.time.LocalDate
import java.time.YearMonth

/** How the data-usage counters accumulate: per calendar day or per monthly billing cycle. */
enum class DataCounterMode { DAY, BILLING_CYCLE }

/** Pure calendar math for the data-counter period. No clock, no zone — callers pass `today`. */
object DataPeriodCalculator {

    /**
     * First day of the current counting period.
     *
     * In [DataCounterMode.BILLING_CYCLE] the period is one month long and starts on
     * [cycleStartDay], clamped to each month's length via [YearMonth.lengthOfMonth]
     * — so a start day of 31 becomes Feb 28/29 in February and Apr 30 in April.
     * If this month's (clamped) start day is still in the future, the period began
     * in the previous month.
     */
    fun periodStart(mode: DataCounterMode, cycleStartDay: Int, today: LocalDate): LocalDate {
        if (mode == DataCounterMode.DAY) return today

        val day = cycleStartDay.coerceIn(1, 31)
        val thisMonth = YearMonth.from(today)
        val candidate = thisMonth.atDay(day.coerceAtMost(thisMonth.lengthOfMonth()))
        if (!candidate.isAfter(today)) return candidate

        val previousMonth = thisMonth.minusMonths(1)
        return previousMonth.atDay(day.coerceAtMost(previousMonth.lengthOfMonth()))
    }
}
