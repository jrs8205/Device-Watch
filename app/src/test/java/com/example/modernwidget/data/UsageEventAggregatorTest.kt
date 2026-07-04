package com.example.modernwidget.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class UsageEventAggregatorTest {

    private fun resume(pkg: String, t: Long, cls: String = "c1") =
        UsageEventSample(pkg, UsageEventKind.RESUME, t, cls)

    private fun close(pkg: String, t: Long, cls: String = "c1") =
        UsageEventSample(pkg, UsageEventKind.CLOSE, t, cls)

    private fun screenOff(t: Long) = UsageEventSample("", UsageEventKind.SCREEN_OFF, t)

    private fun screenTime(pkg: String, millis: Long) =
        AppScreenTime(pkg, pkg, millis, launchCount = 1, lastUsedMillis = 0L)

    private fun app(pkg: String, lastUsed: Long?) =
        LaunchableApp(pkg, pkg, lastUsed, isSystemApp = false)

    // --- aggregateForegroundTime ---

    @Test
    fun `given a simple session, when aggregating, then time launch and last use are recorded`() {
        val usage = UsageEventAggregator.aggregateForegroundTime(
            listOf(resume("a", 1_000), close("a", 4_000)),
            windowEndMillis = 10_000,
        )

        assertThat(usage["a"]).isEqualTo(PackageUsage(3_000, 1, 4_000))
    }

    @Test
    fun `given an in-app activity switch, when aggregating, then still one session and one launch`() {
        // Activity c2 resumes before activity c1 pauses inside the same app.
        val usage = UsageEventAggregator.aggregateForegroundTime(
            listOf(
                resume("a", 1_000, "c1"),
                resume("a", 2_000, "c2"),
                close("a", 2_100, "c1"),
                close("a", 5_000, "c2"),
            ),
            windowEndMillis = 10_000,
        )

        assertThat(usage["a"]).isEqualTo(PackageUsage(4_000, 1, 5_000))
    }

    @Test
    fun `given interleaved apps, when aggregating, then totals are independent`() {
        val usage = UsageEventAggregator.aggregateForegroundTime(
            listOf(
                resume("a", 1_000), close("a", 2_000),
                resume("b", 2_000), close("b", 5_000),
                resume("a", 6_000), close("a", 7_000),
            ),
            windowEndMillis = 10_000,
        )

        assertThat(usage["a"]).isEqualTo(PackageUsage(2_000, 2, 7_000))
        assertThat(usage["b"]).isEqualTo(PackageUsage(3_000, 1, 5_000))
    }

    @Test
    fun `given an unclosed session, when aggregating, then it is cut at the window end`() {
        val usage = UsageEventAggregator.aggregateForegroundTime(
            listOf(resume("a", 8_000)),
            windowEndMillis = 10_000,
        )

        assertThat(usage["a"]).isEqualTo(PackageUsage(2_000, 1, 10_000))
    }

    @Test
    fun `given a close without a prior resume, when aggregating, then it is ignored`() {
        val usage = UsageEventAggregator.aggregateForegroundTime(
            listOf(close("a", 3_000)),
            windowEndMillis = 10_000,
        )

        assertThat(usage).isEmpty()
    }

    @Test
    fun `given unsorted events, when aggregating, then they are ordered by time first`() {
        val usage = UsageEventAggregator.aggregateForegroundTime(
            listOf(close("a", 4_000), resume("a", 1_000)),
            windowEndMillis = 10_000,
        )

        assertThat(usage["a"]).isEqualTo(PackageUsage(3_000, 1, 4_000))
    }

    @Test
    fun `given pause and stop of one activity while another is open, then the session stays open`() {
        // The same activity emits both PAUSED and STOPPED; the duplicate close must
        // not end the session while another activity of the app is still resumed.
        val usage = UsageEventAggregator.aggregateForegroundTime(
            listOf(
                resume("a", 1_000, "c1"),
                resume("a", 1_500, "c2"),
                close("a", 2_000, "c1"),
                close("a", 2_100, "c1"),
                close("a", 5_000, "c2"),
            ),
            windowEndMillis = 10_000,
        )

        assertThat(usage["a"]).isEqualTo(PackageUsage(4_000, 1, 5_000))
    }

    @Test
    fun `given only a stop event, when aggregating, then the session still closes`() {
        // Some devices skip ACTIVITY_PAUSED entirely and only emit STOPPED.
        val usage = UsageEventAggregator.aggregateForegroundTime(
            listOf(resume("a", 1_000, "c1"), close("a", 3_000, "c1")),
            windowEndMillis = 10_000,
        )

        assertThat(usage["a"]).isEqualTo(PackageUsage(2_000, 1, 3_000))
    }

    @Test
    fun `given a screen-off event, when aggregating, then every open session closes`() {
        val usage = UsageEventAggregator.aggregateForegroundTime(
            listOf(
                resume("a", 1_000, "c1"),
                screenOff(3_000),
                resume("a", 5_000, "c1"),
                close("a", 6_000, "c1"),
            ),
            windowEndMillis = 10_000,
        )

        assertThat(usage["a"]).isEqualTo(PackageUsage(3_000, 2, 6_000))
    }

    // --- donutSegments ---

    @Test
    fun `given more apps than topN, when building segments, then an others bucket is added`() {
        val segments = UsageEventAggregator.donutSegments(
            listOf(screenTime("a", 6_000), screenTime("b", 3_000), screenTime("c", 1_000)),
            topN = 2,
        )

        assertThat(segments.map { it.packageName }).containsExactly("a", "b", null).inOrder()
        assertThat(segments[0].fraction).isWithin(0.001f).of(0.6f)
        assertThat(segments[1].fraction).isWithin(0.001f).of(0.3f)
        assertThat(segments[2].fraction).isWithin(0.001f).of(0.1f)
        assertThat(segments[2].label).isNull()
    }

    @Test
    fun `given fewer apps than topN, when building segments, then there is no others bucket`() {
        val segments = UsageEventAggregator.donutSegments(
            listOf(screenTime("a", 2_000), screenTime("b", 2_000)),
            topN = 6,
        )

        assertThat(segments.map { it.packageName }).containsExactly("a", "b")
        assertThat(segments.sumOf { it.fraction.toDouble() }).isWithin(0.001).of(1.0)
    }

    @Test
    fun `given no usage, when building segments, then the result is empty`() {
        assertThat(UsageEventAggregator.donutSegments(emptyList())).isEmpty()
        assertThat(
            UsageEventAggregator.donutSegments(listOf(screenTime("a", 0)))
        ).isEmpty()
    }

    // --- sortByLastUse ---

    @Test
    fun `given oldest first, when sorting, then never-used apps come first`() {
        val sorted = UsageEventAggregator.sortByLastUse(
            listOf(app("x", 200), app("y", null), app("z", 100)),
            oldestFirst = true,
        )

        assertThat(sorted.map { it.packageName }).containsExactly("y", "z", "x").inOrder()
    }

    @Test
    fun `given newest first, when sorting, then never-used apps come last`() {
        val sorted = UsageEventAggregator.sortByLastUse(
            listOf(app("x", 200), app("y", null), app("z", 100)),
            oldestFirst = false,
        )

        assertThat(sorted.map { it.packageName }).containsExactly("x", "z", "y").inOrder()
    }

    // --- daysSinceLastUse ---

    @Test
    fun `given no last use, when computing days, then null`() {
        assertThat(
            UsageEventAggregator.daysSinceLastUse(null, LocalDate.of(2026, 7, 4), ZoneOffset.UTC)
        ).isNull()
    }

    @Test
    fun `given use earlier today, when computing days, then zero`() {
        val today = LocalDate.of(2026, 7, 4)
        val millis = today.atTime(8, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

        assertThat(
            UsageEventAggregator.daysSinceLastUse(millis, today, ZoneId.of("UTC"))
        ).isEqualTo(0)
    }

    @Test
    fun `given use just before midnight yesterday, when computing days, then one`() {
        val today = LocalDate.of(2026, 7, 4)
        val millis = LocalDate.of(2026, 7, 3).atTime(23, 59).toInstant(ZoneOffset.UTC).toEpochMilli()

        assertThat(
            UsageEventAggregator.daysSinceLastUse(millis, today, ZoneId.of("UTC"))
        ).isEqualTo(1)
    }

    // --- lastUsedTier ---

    @Test
    fun `given recent use, when computing tier, then normal`() {
        assertThat(UsageEventAggregator.lastUsedTier(0)).isEqualTo(LastUsedTier.NORMAL)
        assertThat(UsageEventAggregator.lastUsedTier(29)).isEqualTo(LastUsedTier.NORMAL)
    }

    @Test
    fun `given one to three months, when computing tier, then aging`() {
        assertThat(UsageEventAggregator.lastUsedTier(30)).isEqualTo(LastUsedTier.AGING)
        assertThat(UsageEventAggregator.lastUsedTier(89)).isEqualTo(LastUsedTier.AGING)
    }

    @Test
    fun `given over three months or never, when computing tier, then stale`() {
        assertThat(UsageEventAggregator.lastUsedTier(90)).isEqualTo(LastUsedTier.STALE)
        assertThat(UsageEventAggregator.lastUsedTier(400)).isEqualTo(LastUsedTier.STALE)
        assertThat(UsageEventAggregator.lastUsedTier(null)).isEqualTo(LastUsedTier.STALE)
    }

    // --- topByLaunches ---

    @Test
    fun `given entries, when ranking by launches, then sorted and zero launches dropped`() {
        val entries = listOf(
            AppScreenTime("a", "a", 1_000, launchCount = 3, lastUsedMillis = 0),
            AppScreenTime("b", "b", 500, launchCount = 10, lastUsedMillis = 0),
            AppScreenTime("c", "c", 9_000, launchCount = 0, lastUsedMillis = 0),
        )

        val top = UsageEventAggregator.topByLaunches(entries)

        assertThat(top.map { it.packageName }).containsExactly("b", "a").inOrder()
    }

    @Test
    fun `given more entries than topN, when ranking, then truncated`() {
        val entries = listOf(
            AppScreenTime("a", "a", 0, launchCount = 3, lastUsedMillis = 0),
            AppScreenTime("b", "b", 0, launchCount = 10, lastUsedMillis = 0),
        )

        val top = UsageEventAggregator.topByLaunches(entries, topN = 1)

        assertThat(top.map { it.packageName }).containsExactly("b")
    }
}
