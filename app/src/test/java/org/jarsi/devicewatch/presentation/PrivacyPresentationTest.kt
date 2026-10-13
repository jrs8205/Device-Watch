package org.jarsi.devicewatch.presentation

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.data.AccessEvent
import org.jarsi.devicewatch.data.AppSensorUse
import org.jarsi.devicewatch.data.PrivacyLog
import org.jarsi.devicewatch.data.PrivacySensor
import org.jarsi.devicewatch.data.SensorUse
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class PrivacyPresentationTest {

    private val helsinki: ZoneId = ZoneId.of("Europe/Helsinki")

    private fun at(text: String): Long = LocalDateTime.parse(text).atZone(helsinki).toInstant().toEpochMilli()

    private fun app(name: String, last: Long?, rejected: Int = 0) = AppSensorUse(
        uid = 1, packageName = name, label = name, accesses = if (last == null) 0 else 1, backgroundAccesses = 0,
        rejected = rejected, durationMillis = 0L, lastMillis = last, lastRejectedMillis = null, ops = emptySet(), events = emptyList(),
    )

    private fun event(time: Long) = AccessEvent(time, "CAMERA", null, 1, 0L, 0, false)

    @Test
    fun `the band counts the apps that used each sensor and takes the latest use`() {
        val log = PrivacyLog(
            oldestMillis = 1L, newestMillis = 9L,
            sensors = listOf(
                SensorUse(PrivacySensor.CAMERA, apps = listOf(app("a", 5L), app("b", 9L)), denied = listOf(app("c", null, rejected = 3))),
                SensorUse(PrivacySensor.MICROPHONE, apps = emptyList(), denied = emptyList()),
                SensorUse(PrivacySensor.LOCATION, apps = listOf(app("d", 7L)), denied = emptyList()),
                SensorUse(PrivacySensor.OTHER, apps = listOf(app("e", 8L)), denied = emptyList()),
            ),
        )
        val lines = PrivacyPresentation.bandLines(log)
        assertThat(lines.map { it.sensor }).containsExactly(PrivacySensor.CAMERA, PrivacySensor.MICROPHONE, PrivacySensor.LOCATION).inOrder()
        assertThat(lines[0].apps).isEqualTo(2)
        assertThat(lines[0].lastMillis).isEqualTo(9L)
        assertThat(lines[1].apps).isEqualTo(0)
        assertThat(lines[1].lastMillis).isNull()
        assertThat(lines[2].lastMillis).isEqualTo(7L)
    }

    @Test
    fun `a moment is today, yesterday or earlier by the phone's calendar`() {
        val now = at("2026-10-13T07:30:00")
        assertThat(PrivacyPresentation.relativeDay(at("2026-10-13T00:00:01"), now, helsinki)).isEqualTo(RelativeDay.TODAY)
        assertThat(PrivacyPresentation.relativeDay(at("2026-10-12T23:59:59"), now, helsinki)).isEqualTo(RelativeDay.YESTERDAY)
        assertThat(PrivacyPresentation.relativeDay(at("2026-10-12T00:00:00"), now, helsinki)).isEqualTo(RelativeDay.YESTERDAY)
        assertThat(PrivacyPresentation.relativeDay(at("2026-10-11T23:59:59"), now, helsinki)).isEqualTo(RelativeDay.EARLIER)
        // The same instant is still "yesterday" in Helsinki but "today" in a zone far enough west.
        val late = at("2026-10-12T23:30:00")
        assertThat(PrivacyPresentation.relativeDay(late, now, helsinki)).isEqualTo(RelativeDay.YESTERDAY)
        assertThat(PrivacyPresentation.relativeDay(late, now, ZoneId.of("America/Los_Angeles"))).isEqualTo(RelativeDay.TODAY)
    }

    @Test
    fun `a length splits into hours, minutes and seconds, or is under a second`() {
        assertThat(PrivacyPresentation.durationParts(0L).underSecond).isTrue()
        assertThat(PrivacyPresentation.durationParts(999L).underSecond).isTrue()
        assertThat(PrivacyPresentation.durationParts(1_863L)).isEqualTo(DurationParts(0, 0, 1))
        assertThat(PrivacyPresentation.durationParts(80_947L)).isEqualTo(DurationParts(0, 1, 20))
        assertThat(PrivacyPresentation.durationParts(3_600_000L + 125_000L)).isEqualTo(DurationParts(1, 2, 5))
        assertThat(PrivacyPresentation.durationParts(1_863L).underSecond).isFalse()
    }

    @Test
    fun `a minute-kept length is whole minutes, never under one, rounded up when Android did not`() {
        assertThat(PrivacyPresentation.aboutMinutes(60_000L)).isEqualTo(1L)
        assertThat(PrivacyPresentation.aboutMinutes(180_000L)).isEqualTo(3L)
        assertThat(PrivacyPresentation.aboutMinutes(90_000L)).isEqualTo(2L)
        assertThat(PrivacyPresentation.aboutMinutes(1L)).isEqualTo(1L)
    }

    @Test
    fun `an app's profile is the Android user its uid belongs to, zero for the owner`() {
        assertThat(PrivacyPresentation.profileOf(10_123)).isEqualTo(0)
        assertThat(PrivacyPresentation.profileOf(1_010_123)).isEqualTo(10)
        assertThat(PrivacyPresentation.profileOf(1000)).isEqualTo(0)
    }

    @Test
    fun `only the latest moments are listed, with a count of the earlier ones`() {
        val events = (1..50).map { event(it.toLong()) }.sortedByDescending { it.timeMillis }
        val (shown, left) = PrivacyPresentation.shownEvents(events, limit = 40)
        assertThat(shown).hasSize(40)
        assertThat(shown.first().timeMillis).isEqualTo(50L)
        assertThat(shown.last().timeMillis).isEqualTo(11L)
        assertThat(left).isEqualTo(10)
        assertThat(PrivacyPresentation.shownEvents(events.take(3), limit = 40).second).isEqualTo(0)
    }

    @Test
    fun `moments group by day, latest day first, each day latest first`() {
        val events = listOf(
            event(at("2026-10-13T07:12:00")),
            event(at("2026-10-13T06:41:00")),
            event(at("2026-10-12T21:54:00")),
            event(at("2026-10-11T22:10:00")),
        )
        val days = PrivacyPresentation.groupByDay(events, helsinki)
        assertThat(days.map { it.first }).containsExactly(
            LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 11),
        ).inOrder()
        assertThat(days[0].second.map { it.timeMillis }).containsExactly(at("2026-10-13T07:12:00"), at("2026-10-13T06:41:00")).inOrder()
    }

    @Test
    fun `operations under Other have a plain name when the page knows one`() {
        assertThat(PrivacyPresentation.opNameRes("BIND_ACCESSIBILITY_SERVICE")).isNotNull()
        assertThat(PrivacyPresentation.opNameRes("READ_DEVICE_IDENTIFIERS")).isNotNull()
        assertThat(PrivacyPresentation.opNameRes("SOME_NEW_OP")).isNull()
        assertThat(PrivacySensor.entries.map(PrivacyPresentation::sensorNameRes).toSet()).hasSize(PrivacySensor.entries.size)
    }
}
