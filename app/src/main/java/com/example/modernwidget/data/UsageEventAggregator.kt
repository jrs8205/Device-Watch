package com.example.modernwidget.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Platform-free projection of a usage event: what happened to which package and when. */
enum class UsageEventKind { RESUME, PAUSE }

data class UsageEventSample(
    val packageName: String,
    val kind: UsageEventKind,
    val timeMillis: Long,
)

/** Aggregated per-package result over the queried window. */
data class PackageUsage(
    val foregroundMillis: Long,
    val launchCount: Int,
    val lastUsedMillis: Long,
)

/** One arc of the screen-time donut. `packageName`/`label` are null for the "others" bucket. */
data class DonutSegment(
    val packageName: String?,
    val label: String?,
    val millis: Long,
    val fraction: Float,
)

/**
 * Staleness tier for the last-opened list. AGING approaches Google's ~3-month app
 * hibernation threshold; STALE has crossed it (or was never used at all).
 */
enum class LastUsedTier { NORMAL, AGING, STALE }

/** Pure aggregation math for the Apps tab. No Context, no I/O — mirrors SystemStatsParser. */
object UsageEventAggregator {

    /**
     * Folds a window of RESUME/PAUSE events into per-package foreground time,
     * launch count and last-use time.
     *
     * Sessions are tracked with an open-activity counter per package, because an
     * in-app activity switch fires RESUME(new) before PAUSE(old): the counter goes
     * 1→2→1 and the session stays open, so switching screens inside an app is not
     * a new "launch". A launch is only the 0→1 transition. A PAUSE with no open
     * session means the app was already foregrounded when the window started; the
     * pre-window sliver is deliberately dropped. A session still open at the end
     * of the window is cut at [windowEndMillis].
     */
    fun aggregateForegroundTime(
        events: List<UsageEventSample>,
        windowEndMillis: Long,
    ): Map<String, PackageUsage> {
        class State {
            var openCount = 0
            var sessionStartMillis = 0L
            var foregroundMillis = 0L
            var launchCount = 0
            var lastUsedMillis = 0L
        }

        val states = mutableMapOf<String, State>()
        for (event in events.sortedBy { it.timeMillis }) {
            val state = states.getOrPut(event.packageName) { State() }
            when (event.kind) {
                UsageEventKind.RESUME -> {
                    if (state.openCount == 0) {
                        state.sessionStartMillis = event.timeMillis
                        state.launchCount++
                    }
                    state.openCount++
                }

                UsageEventKind.PAUSE -> {
                    if (state.openCount > 0) {
                        state.openCount--
                        if (state.openCount == 0) {
                            state.foregroundMillis +=
                                (event.timeMillis - state.sessionStartMillis).coerceAtLeast(0)
                            state.lastUsedMillis = event.timeMillis
                        }
                    }
                }
            }
        }

        return states
            .filterValues { it.launchCount > 0 }
            .mapValues { (_, state) ->
                var foreground = state.foregroundMillis
                var lastUsed = state.lastUsedMillis
                if (state.openCount > 0) {
                    foreground += (windowEndMillis - state.sessionStartMillis).coerceAtLeast(0)
                    lastUsed = windowEndMillis
                }
                PackageUsage(foreground, state.launchCount, lastUsed)
            }
    }

    /**
     * Top-[topN] apps by foreground time as donut arcs plus one "others" bucket
     * (packageName/label null) for the remainder. Empty when nothing was used.
     */
    fun donutSegments(entries: List<AppScreenTime>, topN: Int = 6): List<DonutSegment> {
        val total = entries.sumOf { it.foregroundMillis }
        if (total <= 0L) return emptyList()

        val sorted = entries.sortedByDescending { it.foregroundMillis }
        val top = sorted.take(topN).filter { it.foregroundMillis > 0 }
        val othersMillis = sorted.drop(topN).sumOf { it.foregroundMillis }

        val segments = top.map {
            DonutSegment(
                packageName = it.packageName,
                label = it.label,
                millis = it.foregroundMillis,
                fraction = (it.foregroundMillis.toDouble() / total).toFloat(),
            )
        }
        return if (othersMillis > 0L) {
            segments + DonutSegment(
                packageName = null,
                label = null,
                millis = othersMillis,
                fraction = (othersMillis.toDouble() / total).toFloat(),
            )
        } else {
            segments
        }
    }

    /**
     * Oldest-first puts never-used apps (null last use) at the very top — they are
     * the strongest removal candidates. Newest-first is the exact reverse.
     */
    fun sortByLastUse(apps: List<LaunchableApp>, oldestFirst: Boolean): List<LaunchableApp> {
        val oldestFirstComparator =
            compareBy<LaunchableApp, Long?>(nullsFirst(naturalOrder())) { it.lastUsedEpochMillis }
        return if (oldestFirst) {
            apps.sortedWith(oldestFirstComparator)
        } else {
            apps.sortedWith(oldestFirstComparator.reversed())
        }
    }

    /** Whole calendar days between the last use and [today] in [zone]; null = never used. */
    fun daysSinceLastUse(lastUsedEpochMillis: Long?, today: LocalDate, zone: ZoneId): Int? {
        if (lastUsedEpochMillis == null) return null
        val lastDate = Instant.ofEpochMilli(lastUsedEpochMillis).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(lastDate, today).toInt().coerceAtLeast(0)
    }

    /** Tier for the last-opened highlighting; null days = never used. */
    fun lastUsedTier(daysSinceLastUse: Int?): LastUsedTier = when {
        daysSinceLastUse == null || daysSinceLastUse >= STALE_DAYS -> LastUsedTier.STALE
        daysSinceLastUse >= AGING_DAYS -> LastUsedTier.AGING
        else -> LastUsedTier.NORMAL
    }

    /** Most-opened apps today: sorted by launch count, zero-launch entries dropped. */
    fun topByLaunches(entries: List<AppScreenTime>, topN: Int = 5): List<AppScreenTime> =
        entries.filter { it.launchCount > 0 }
            .sortedByDescending { it.launchCount }
            .take(topN)

    /** Google hibernates unused apps after roughly three months. */
    private const val STALE_DAYS = 90
    private const val AGING_DAYS = 30
}
