package com.example.modernwidget.data

import java.time.LocalDate

/** One day's stored tallies; days with no records are all zeros. */
data class UsageDayTally(
    val day: LocalDate,
    val unlocks: Int,
    val screenTimeMillis: Long,
    val boots: Int,
    val charges: Int,
)

/**
 * Daily usage tallies kept by the app itself, because Android exposes no
 * retroactive history for these: unlock counts and screen time are recorded
 * (and backfilled up to ~7 days from Android's event history) on every refresh,
 * boots and charger connections are incremented as the events happen. Retention
 * matches [NotificationCounting.retainedDays] (62 days) so a full billing cycle
 * can be summed. Period sums are therefore complete from tracking start onward.
 */
interface UsageHistory {
    fun recordUnlocks(day: LocalDate, count: Int)

    fun recordScreenTime(day: LocalDate, millis: Long)

    /**
     * Registers the current Settings.Global.BOOT_COUNT and attributes the delta
     * since the previous registration to [day]. Duplicate BOOT_COMPLETED
     * deliveries (Android re-sends it to a package after every app update) don't
     * change BOOT_COUNT, so they add nothing.
     */
    fun registerBootCount(day: LocalDate, bootCountTotal: Int)

    fun incrementCharge(day: LocalDate)

    fun unlocksBetween(start: LocalDate, end: LocalDate): Int

    fun screenTimeBetween(start: LocalDate, end: LocalDate): Long

    fun bootsBetween(start: LocalDate, end: LocalDate): Int

    fun chargesBetween(start: LocalDate, end: LocalDate): Int

    /** Per-day tallies for [start]..[end] inclusive, ascending, zero-filled. */
    fun dailyTallies(start: LocalDate, end: LocalDate): List<UsageDayTally>

    fun purge(today: LocalDate)
}

internal val HISTORY_PREFIXES = setOf("unlocks", "screen", "boots", "charges")

/** Baseline key for the boot-count delta logic; survives purging. */
internal const val KEY_LAST_BOOT_COUNT = "last_boot_count"

/** Keys look like `<prefix>:<epochDay>`. Malformed or expired keys are purged. */
internal fun isRetainedHistoryKey(prefsKey: String, retained: Set<LocalDate>): Boolean {
    if (prefsKey == KEY_LAST_BOOT_COUNT) return true
    val parts = prefsKey.split(':', limit = 2)
    if (parts.size != 2 || parts[0] !in HISTORY_PREFIXES) return false
    val epochDay = parts[1].toLongOrNull() ?: return false
    return retained.any { it.toEpochDay() == epochDay }
}

/**
 * How many real boots the new BOOT_COUNT reading represents. Zero when there is
 * no previous baseline (first run), when nothing changed (duplicate broadcast),
 * or when the counter went backwards (factory reset).
 */
internal fun bootCountDelta(lastRegistered: Int, current: Int): Int =
    if (lastRegistered in 0 until current) current - lastRegistered else 0
