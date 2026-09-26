package org.jarsi.devicewatch.presentation

import java.time.LocalDate

/**
 * The first day each metric recorded a value. The tally stores answer zero for
 * days they have nothing on, so a zero before that day is "not collected yet"
 * (listener not granted, app not installed), not a quiet day — the same rule the
 * history list applies per metric. A null means the metric was never collected.
 * Zero days after the first data day are real zeros and stay in every figure.
 */
internal data class HistoryCoverage(
    val screenTimeSince: LocalDate?,
    val unlocksSince: LocalDate?,
    val notificationsSince: LocalDate?,
    val bootsSince: LocalDate?,
    val chargesSince: LocalDate?,
) {
    fun screenTimeKnown(day: LocalDate): Boolean = known(screenTimeSince, day)
    fun unlocksKnown(day: LocalDate): Boolean = known(unlocksSince, day)
    fun notificationsKnown(day: LocalDate): Boolean = known(notificationsSince, day)
    fun bootsKnown(day: LocalDate): Boolean = known(bootsSince, day)
    fun chargesKnown(day: LocalDate): Boolean = known(chargesSince, day)

    private fun known(since: LocalDate?, day: LocalDate): Boolean =
        since != null && !day.isBefore(since)

    companion object {
        fun of(days: List<HistoryDay>): HistoryCoverage = HistoryCoverage(
            screenTimeSince = days.firstDayWhere { it.screenTimeMillis > 0L },
            unlocksSince = days.firstDayWhere { it.unlocks > 0 },
            notificationsSince = days.firstDayWhere { it.notifications > 0 },
            bootsSince = days.firstDayWhere { it.boots > 0 },
            chargesSince = days.firstDayWhere { it.charges > 0 },
        )

        private fun List<HistoryDay>.firstDayWhere(hasData: (HistoryDay) -> Boolean): LocalDate? =
            filter(hasData).minOfOrNull { it.day }
    }
}
