package org.jarsi.devicewatch.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Platform-free projection of a usage event. CLOSE covers both ACTIVITY_PAUSED and
 * ACTIVITY_STOPPED — some devices (e.g. Pixel 10 Pro) skip the PAUSED event for a
 * quarter of resumes and only emit STOPPED, which left sessions open forever and
 * inflated daily screen time past 60 hours. SCREEN_OFF closes everything at once.
 */
enum class UsageEventKind { RESUME, CLOSE, SCREEN_OFF }

data class UsageEventSample(
    val packageName: String,
    val kind: UsageEventKind,
    val timeMillis: Long,
    val className: String = "",
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
     * Folds a window of RESUME/CLOSE/SCREEN_OFF events into per-package foreground
     * time, launch count and last-use time.
     *
     * Sessions are tracked with a per-package SET of open activity class names —
     * not a counter — because devices emit both PAUSED and STOPPED for the same
     * activity (a counter would double-decrement), and some devices skip PAUSED
     * entirely (a counter would leave the session open forever and inflate daily
     * screen time past 60 hours, as seen on a Pixel 10 Pro). An in-app activity
     * switch (RESUME new before CLOSE old) keeps the session open; a launch is
     * only the empty→non-empty transition at or after [windowStartMillis]. SCREEN_OFF
     * force-closes every open session. A CLOSE for an unknown activity is ignored
     * (session began before the queried events; the pre-query sliver is deliberately
     * dropped). A session still open at the end of the window is cut at [windowEndMillis].
     *
     * [windowStartMillis] supports anchored windows (the since-charge page): callers
     * pass events queried from BEFORE the window start so that a session already open
     * at the anchor is visible, and only its in-window part — start clamped to the
     * anchor — is counted. Time accrued before the window is never included.
     */
    fun aggregateForegroundTime(
        events: List<UsageEventSample>,
        windowEndMillis: Long,
        windowStartMillis: Long = 0L,
    ): Map<String, PackageUsage> {
        class State {
            val openClasses = mutableSetOf<String>()
            var sessionStartMillis = 0L
            var foregroundMillis = 0L
            var launchCount = 0
            var lastUsedMillis = 0L

            fun closeSession(timeMillis: Long) {
                foregroundMillis +=
                    (timeMillis - sessionStartMillis.coerceAtLeast(windowStartMillis)).coerceAtLeast(0)
                lastUsedMillis = timeMillis
                openClasses.clear()
            }
        }

        val states = mutableMapOf<String, State>()
        for (event in events.sortedBy { it.timeMillis }) {
            when (event.kind) {
                UsageEventKind.RESUME -> {
                    val state = states.getOrPut(event.packageName) { State() }
                    if (state.openClasses.isEmpty()) {
                        state.sessionStartMillis = event.timeMillis
                        if (event.timeMillis >= windowStartMillis) state.launchCount++
                    }
                    state.openClasses.add(event.className)
                }

                UsageEventKind.CLOSE -> {
                    val state = states[event.packageName] ?: continue
                    if (state.openClasses.remove(event.className) && state.openClasses.isEmpty()) {
                        state.closeSession(event.timeMillis)
                    }
                }

                UsageEventKind.SCREEN_OFF -> {
                    for (state in states.values) {
                        if (state.openClasses.isNotEmpty()) {
                            state.closeSession(event.timeMillis)
                        }
                    }
                }
            }
        }

        return states
            .mapValues { (_, state) ->
                var foreground = state.foregroundMillis
                var lastUsed = state.lastUsedMillis
                if (state.openClasses.isNotEmpty()) {
                    foreground += (windowEndMillis - state.sessionStartMillis.coerceAtLeast(windowStartMillis))
                        .coerceAtLeast(0)
                    lastUsed = windowEndMillis
                }
                PackageUsage(foreground, state.launchCount, lastUsed)
            }
            // launchCount alone is not enough: a session that began before the window
            // has no in-window launch but real in-window foreground time.
            .filterValues { it.launchCount > 0 || it.foregroundMillis > 0 }
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

    /**
     * Drops home-screen launchers from usage rankings: every trip to the home screen
     * counts as a launcher "launch", which otherwise dominates the donut and the
     * most-opened list (Digital Wellbeing hides launchers the same way).
     */
    fun excludeLaunchers(
        screenTimes: List<AppScreenTime>,
        launcherPackages: Set<String>,
    ): List<AppScreenTime> = screenTimes.filterNot { it.packageName in launcherPackages }

    /** Google hibernates unused apps after roughly three months. */
    private const val STALE_DAYS = 90
    private const val AGING_DAYS = 30
}
