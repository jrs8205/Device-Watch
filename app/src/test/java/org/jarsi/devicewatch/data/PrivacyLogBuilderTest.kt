package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneId

class PrivacyLogBuilderTest {

    private fun line(
        uid: Int = 10001,
        packageName: String = "com.example.one",
        op: String = "CAMERA",
        tag: String? = null,
        time: Long = 1_000L,
        count: Int = 1,
        rejected: Int = 0,
        duration: Long = 0L,
        state: UidState = UidState.TOP,
        flags: Set<OpFlag> = setOf(OpFlag.SELF),
    ) = AppOpAccess(uid, packageName, op, tag, time, count, rejected, duration, state, flags)

    private fun build(vararg lines: AppOpAccess) = PrivacyLogBuilder.build(AppOpsHistory(lines.toList())) { uid, pkg -> "$pkg#$uid" }

    private fun at(text: String): Long =
        java.time.LocalDateTime.parse(text.replace(' ', 'T')).atZone(ZoneId.of("Europe/Helsinki")).toInstant().toEpochMilli()

    @Test
    fun `operations sort into their sensors`() {
        assertThat(PrivacyOps.sensorOf("CAMERA")).isEqualTo(PrivacySensor.CAMERA)
        assertThat(PrivacyOps.sensorOf("PHONE_CALL_CAMERA")).isEqualTo(PrivacySensor.CAMERA)
        assertThat(PrivacyOps.sensorOf("RECORD_AUDIO")).isEqualTo(PrivacySensor.MICROPHONE)
        assertThat(PrivacyOps.sensorOf("RECEIVE_AMBIENT_TRIGGER_AUDIO")).isEqualTo(PrivacySensor.MICROPHONE)
        assertThat(PrivacyOps.sensorOf("FINE_LOCATION")).isEqualTo(PrivacySensor.LOCATION)
        assertThat(PrivacyOps.sensorOf("MONITOR_HIGH_POWER_LOCATION")).isEqualTo(PrivacySensor.LOCATION)
        assertThat(PrivacyOps.sensorOf("GPS")).isEqualTo(PrivacySensor.LOCATION)
        assertThat(PrivacyOps.sensorOf("BIND_ACCESSIBILITY_SERVICE")).isEqualTo(PrivacySensor.OTHER)
        assertThat(PrivacyOps.sensorOf("")).isEqualTo(PrivacySensor.OTHER)
    }

    @Test
    fun `every sensor is listed even when nothing used it`() {
        val log = build(line())
        assertThat(log.sensors.map { it.sensor }).containsExactly(*PrivacySensor.entries.toTypedArray()).inOrder()
        assertThat(log.sensor(PrivacySensor.MICROPHONE).empty).isTrue()
        assertThat(log.sensor(PrivacySensor.CAMERA).empty).isFalse()
        assertThat(log.empty).isFalse()
        assertThat(build().empty).isTrue()
        assertThat(build().oldestMillis).isNull()
    }

    @Test
    fun `a count line and a length line at the same moment are one event`() {
        val log = build(
            line(time = 5_000L, count = 1, duration = 0L),
            line(time = 5_000L, count = 0, duration = 1_863L),
            line(time = 9_000L, count = 1, duration = 0L),
            line(time = 9_000L, count = 0, duration = 500L),
        )
        val app = log.sensor(PrivacySensor.CAMERA).apps.single()
        assertThat(app.events).hasSize(2)
        assertThat(app.events.map { it.timeMillis }).containsExactly(9_000L, 5_000L).inOrder()
        assertThat(app.events[1].count).isEqualTo(1)
        assertThat(app.events[1].durationMillis).isEqualTo(1_863L)
        assertThat(app.accesses).isEqualTo(2)
        assertThat(app.durationMillis).isEqualTo(2_363L)
        assertThat(app.lastMillis).isEqualTo(9_000L)
        assertThat(app.label).isEqualTo("com.example.one#10001")
        assertThat(app.ops).containsExactly("CAMERA")
    }

    @Test
    fun `the same moment under another tag or operation stays its own event`() {
        val log = build(
            line(op = "FINE_LOCATION", tag = "a", time = 5_000L),
            line(op = "FINE_LOCATION", tag = "b", time = 5_000L),
            line(op = "COARSE_LOCATION", tag = "a", time = 5_000L),
        )
        val app = log.sensor(PrivacySensor.LOCATION).apps.single()
        assertThat(app.events).hasSize(3)
        assertThat(app.accesses).isEqualTo(3)
        assertThat(app.ops).containsExactly("FINE_LOCATION", "COARSE_LOCATION")
    }

    @Test
    fun `background uses are counted apart`() {
        val log = build(
            line(op = "COARSE_LOCATION", time = 1_000L, state = UidState.BACKGROUND),
            line(op = "COARSE_LOCATION", time = 2_000L, state = UidState.CACHED, count = 2),
            line(op = "COARSE_LOCATION", time = 3_000L, state = UidState.FOREGROUND_SERVICE),
            line(op = "COARSE_LOCATION", time = 4_000L, state = UidState.PERSISTENT),
        )
        val app = log.sensor(PrivacySensor.LOCATION).apps.single()
        assertThat(app.accesses).isEqualTo(5)
        assertThat(app.backgroundAccesses).isEqualTo(3)
        assertThat(app.events.first { it.timeMillis == 2_000L }.background).isTrue()
        assertThat(app.events.first { it.timeMillis == 3_000L }.background).isFalse()
    }

    @Test
    fun `an app only ever refused is listed as denied, one refused as well stays with the used`() {
        val log = build(
            line(packageName = "com.example.denied", op = "FINE_LOCATION", time = 1_000L, count = 0, rejected = 21, state = UidState.FOREGROUND_SERVICE),
            line(packageName = "com.example.denied", op = "COARSE_LOCATION", time = 2_000L, count = 0, rejected = 7, state = UidState.FOREGROUND_SERVICE),
            line(packageName = "com.example.both", op = "FINE_LOCATION", time = 3_000L, count = 1),
            line(packageName = "com.example.both", op = "FINE_LOCATION", time = 4_000L, count = 0, rejected = 2),
        )
        val location = log.sensor(PrivacySensor.LOCATION)
        val denied = location.denied.single()
        assertThat(denied.packageName).isEqualTo("com.example.denied")
        assertThat(denied.accesses).isEqualTo(0)
        assertThat(denied.rejected).isEqualTo(28)
        assertThat(denied.lastMillis).isNull()
        assertThat(denied.lastRejectedMillis).isEqualTo(2_000L)
        val both = location.apps.single()
        assertThat(both.packageName).isEqualTo("com.example.both")
        assertThat(both.accesses).isEqualTo(1)
        assertThat(both.rejected).isEqualTo(2)
        assertThat(both.lastMillis).isEqualTo(3_000L)
        assertThat(both.lastRejectedMillis).isEqualTo(4_000L)
    }

    @Test
    fun `apps come latest first, denied by their latest refusal`() {
        val log = build(
            line(packageName = "com.example.old", time = 1_000L),
            line(packageName = "com.example.new", time = 9_000L),
            line(packageName = "com.example.mid", time = 5_000L),
            line(packageName = "com.example.d1", time = 2_000L, count = 0, rejected = 1),
            line(packageName = "com.example.d2", time = 8_000L, count = 0, rejected = 1),
        )
        val camera = log.sensor(PrivacySensor.CAMERA)
        assertThat(camera.apps.map { it.packageName }).containsExactly("com.example.new", "com.example.mid", "com.example.old").inOrder()
        assertThat(camera.denied.map { it.packageName }).containsExactly("com.example.d2", "com.example.d1").inOrder()
    }

    @Test
    fun `two packages sharing a uid are two apps, and one package under two uids too`() {
        val log = build(
            line(uid = 1000, packageName = "android"),
            line(uid = 1000, packageName = "com.google.android.settings.future.biometrics.faceenroll"),
            line(uid = 10001, packageName = "com.example.one"),
            line(uid = 1010001, packageName = "com.example.one"),
        )
        assertThat(log.sensor(PrivacySensor.CAMERA).apps).hasSize(4)
    }

    @Test
    fun `only the lines the privacy dashboard would count are taken`() {
        val log = build(
            line(time = 1_000L, flags = setOf(OpFlag.SELF)),
            line(time = 2_000L, flags = setOf(OpFlag.TRUSTED_PROXY)),
            line(time = 3_000L, flags = setOf(OpFlag.TRUSTED_PROXIED)),
            line(time = 4_000L, flags = setOf(OpFlag.UNTRUSTED_PROXY)),
            line(time = 5_000L, flags = setOf(OpFlag.UNTRUSTED_PROXIED)),
            line(time = 6_000L, flags = emptySet()),
        )
        val app = log.sensor(PrivacySensor.CAMERA).apps.single()
        assertThat(app.events.map { it.timeMillis }).containsExactly(6_000L, 3_000L, 2_000L, 1_000L).inOrder()
        assertThat(log.oldestMillis).isEqualTo(1_000L)
        assertThat(log.newestMillis).isEqualTo(6_000L)
    }

    @Test
    fun `the span covers every sensor, not just the ones with apps`() {
        val log = build(
            line(op = "BIND_ACCESSIBILITY_SERVICE", time = 100L, state = UidState.CACHED),
            line(op = "CAMERA", time = 900L),
        )
        assertThat(log.oldestMillis).isEqualTo(100L)
        assertThat(log.newestMillis).isEqualTo(900L)
        val other = log.sensor(PrivacySensor.OTHER).apps.single()
        assertThat(other.ops).containsExactly("BIND_ACCESSIBILITY_SERVICE")
        assertThat(other.backgroundAccesses).isEqualTo(1)
    }

    @Test
    fun `the phone's table builds into the lists the page shows`() {
        val history = AppOpsHistoryParser.parse(AppOpsFixtures.PIXEL_8A_TABLE, ZoneId.of("Europe/Helsinki"))!!
        val log = PrivacyLogBuilder.build(history) { _, pkg -> pkg }

        val camera = log.sensor(PrivacySensor.CAMERA)
        assertThat(camera.denied).isEmpty()
        val googleCamera = camera.apps.first { it.packageName == "com.google.android.GoogleCamera" }
        assertThat(googleCamera.accesses).isAtLeast(1)
        assertThat(googleCamera.durationMillis).isAtLeast(1_863L)
        assertThat(googleCamera.events.all { it.count > 0 || it.durationMillis > 0 }).isTrue()

        val location = log.sensor(PrivacySensor.LOCATION)
        // Allowed while its screen was open the evening before, refused from its service since: with the used, not the denied.
        val self = location.apps.first { it.packageName == "org.jarsi.devicewatch" }
        assertThat(location.denied.map { it.packageName }).doesNotContain("org.jarsi.devicewatch")
        assertThat(self.accesses).isEqualTo(42)
        assertThat(self.rejected).isEqualTo(8)
        assertThat(self.backgroundAccesses).isEqualTo(0)
        assertThat(self.lastRejectedMillis).isGreaterThan(self.lastMillis)
        assertThat(self.ops).containsExactly("FINE_LOCATION", "COARSE_LOCATION")
        // Three of the weather app's five location reads came from the background or from a cached process.
        // Four coarse reads count; its MONITOR_LOCATION line is listed but not counted.
        val weather = location.apps.first { it.packageName == "com.google.android.apps.weather" }
        assertThat(weather.accesses).isEqualTo(4)
        assertThat(weather.backgroundAccesses).isEqualTo(2)
        assertThat(weather.durationMillis).isEqualTo(1_016L)
        assertThat(weather.ops).containsExactly("COARSE_LOCATION", "MONITOR_LOCATION")
        // One geotag is one use, not four: FINE_LOCATION counts, GPS and the two MONITOR ops come along.
        val cameraLocation = location.apps.first { it.packageName == "com.google.android.GoogleCamera" }
        assertThat(cameraLocation.events.count { it.timeMillis in at("2026-10-12 09:36:03.000")..at("2026-10-12 09:36:04.000") && it.allowed }).isEqualTo(4)
        assertThat(cameraLocation.accesses).isEqualTo(cameraLocation.events.count { it.op == "FINE_LOCATION" && it.count > 0 })

        assertThat(log.sensor(PrivacySensor.MICROPHONE).empty).isTrue()
        // Accessibility services bind and are allowed; the contacts app asked for device identifiers and was refused every time.
        val other = log.sensor(PrivacySensor.OTHER)
        assertThat(other.apps.map { it.ops }.flatten()).containsExactly("BIND_ACCESSIBILITY_SERVICE")
        assertThat(other.denied.single().ops).containsExactly("READ_DEVICE_IDENTIFIERS")
        assertThat(log.oldestMillis).isLessThan(log.newestMillis)
    }

    @Test
    fun `location monitoring is listed with the events but not counted as a use`() {
        val log = build(
            line(op = "FINE_LOCATION", time = 1_000L),
            line(op = "MONITOR_LOCATION", time = 1_000L),
            line(op = "MONITOR_HIGH_POWER_LOCATION", time = 1_000L),
            line(op = "GPS", time = 1_000L),
            line(op = "GPS", time = 1_000L, count = 0, duration = 14L),
            line(packageName = "com.example.listener", op = "MONITOR_LOCATION", time = 5_000L, state = UidState.BACKGROUND),
        )
        val location = log.sensor(PrivacySensor.LOCATION)
        val app = location.apps.first { it.packageName == "com.example.one" }
        assertThat(app.accesses).isEqualTo(1)
        assertThat(app.events.count { it.allowed }).isEqualTo(4)
        assertThat(app.ops).hasSize(4)
        assertThat(app.monitoringOnly).isFalse()
        // An app that only ever listened is still listed, latest first, as monitoring only.
        val listener = location.apps.first { it.packageName == "com.example.listener" }
        assertThat(listener.accesses).isEqualTo(0)
        assertThat(listener.backgroundAccesses).isEqualTo(0)
        assertThat(listener.lastMillis).isEqualTo(5_000L)
        assertThat(listener.monitoringOnly).isTrue()
        assertThat(location.denied).isEmpty()
        assertThat(location.apps.first()).isSameInstanceAs(listener)
    }

    @Test
    fun `lines at one moment in different states stay apart, so foreground use is never called background`() {
        val log = build(
            line(time = 5_000L, count = 1, state = UidState.TOP),
            line(time = 5_000L, count = 0, duration = 60_000L, state = UidState.BACKGROUND),
        )
        val app = log.sensor(PrivacySensor.CAMERA).apps.single()
        assertThat(app.events).hasSize(2)
        assertThat(app.accesses).isEqualTo(1)
        assertThat(app.backgroundAccesses).isEqualTo(0)
        assertThat(app.durationMillis).isEqualTo(60_000L)
    }

    @Test
    fun `a record kept by the minute, or without refusals, says so`() {
        val quantized = PrivacyLogBuilder.build(AppOpsHistory(listOf(line()), minuteQuantized = true)) { _, p -> p }
        assertThat(quantized.minuteQuantized).isTrue()
        assertThat(quantized.refusalsRecorded).isFalse()
        assertThat(build(line()).minuteQuantized).isFalse()
        val withRefusals = PrivacyLogBuilder.build(AppOpsHistory(listOf(line()), refusalsRecorded = true)) { _, p -> p }
        assertThat(withRefusals.refusalsRecorded).isTrue()
        assertThat(withRefusals.minuteQuantized).isFalse()
    }
}
