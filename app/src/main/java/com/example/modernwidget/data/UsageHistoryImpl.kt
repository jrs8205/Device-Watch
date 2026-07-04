package com.example.modernwidget.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SharedPreferences-backed daily tallies ("usage_history"). Tiny dataset
 * (four ints/longs per day, 62-day retention) with synchronous writes from
 * broadcast receivers — the same trade-off as [NotificationStatsImpl].
 */
@Singleton
class UsageHistoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : UsageHistory {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun recordUnlocks(day: LocalDate, count: Int) {
        prefs.edit().putInt(key(PREFIX_UNLOCKS, day), count).apply()
    }

    override fun recordScreenTime(day: LocalDate, millis: Long) {
        prefs.edit().putLong(key(PREFIX_SCREEN, day), millis).apply()
    }

    override fun registerBootCount(day: LocalDate, bootCountTotal: Int) {
        val delta = bootCountDelta(prefs.getInt(KEY_LAST_BOOT_COUNT, -1), bootCountTotal)
        val edit = prefs.edit().putInt(KEY_LAST_BOOT_COUNT, bootCountTotal)
        if (delta > 0) {
            val storageKey = key(PREFIX_BOOTS, day)
            edit.putInt(storageKey, prefs.getInt(storageKey, 0) + delta)
        }
        edit.apply()
    }

    override fun incrementCharge(day: LocalDate) = increment(PREFIX_CHARGES, day)

    override fun unlocksBetween(start: LocalDate, end: LocalDate): Int =
        sumInt(PREFIX_UNLOCKS, start, end)

    override fun screenTimeBetween(start: LocalDate, end: LocalDate): Long =
        sumLong(PREFIX_SCREEN, start, end)

    override fun bootsBetween(start: LocalDate, end: LocalDate): Int =
        sumInt(PREFIX_BOOTS, start, end)

    override fun chargesBetween(start: LocalDate, end: LocalDate): Int =
        sumInt(PREFIX_CHARGES, start, end)

    override fun purge(today: LocalDate) {
        val retained = NotificationCounting.retainedDays(today)
        val stale = prefs.all.keys.filterNot { isRetainedHistoryKey(it, retained) }
        if (stale.isEmpty()) return
        prefs.edit().apply { stale.forEach(::remove) }.apply()
    }

    private fun increment(prefix: String, day: LocalDate) {
        val storageKey = key(prefix, day)
        prefs.edit().putInt(storageKey, prefs.getInt(storageKey, 0) + 1).apply()
    }

    private fun sumInt(prefix: String, start: LocalDate, end: LocalDate): Int {
        var day = start
        var sum = 0
        while (!day.isAfter(end)) {
            sum += prefs.getInt(key(prefix, day), 0)
            day = day.plusDays(1)
        }
        return sum
    }

    private fun sumLong(prefix: String, start: LocalDate, end: LocalDate): Long {
        var day = start
        var sum = 0L
        while (!day.isAfter(end)) {
            sum += prefs.getLong(key(prefix, day), 0L)
            day = day.plusDays(1)
        }
        return sum
    }

    private fun key(prefix: String, day: LocalDate) = "$prefix:${day.toEpochDay()}"

    companion object {
        const val PREFS_NAME = "usage_history"
        private const val PREFIX_UNLOCKS = "unlocks"
        private const val PREFIX_SCREEN = "screen"
        private const val PREFIX_BOOTS = "boots"
        private const val PREFIX_CHARGES = "charges"
    }
}
