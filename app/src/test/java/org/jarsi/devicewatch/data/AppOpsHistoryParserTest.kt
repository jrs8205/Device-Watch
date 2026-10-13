package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AppOpsHistoryParserTest {

    private val helsinki: ZoneId = ZoneId.of("Europe/Helsinki")

    private fun at(text: String, zone: ZoneId = helsinki): Long =
        LocalDateTime.parse(text.replace(' ', 'T')).atZone(zone).toInstant().toEpochMilli()

    private fun parse(output: String) = AppOpsHistoryParser.parse(output, helsinki)!!.accesses

    // --- the table (met on Android 17) ---

    @Test
    fun `reads every row of the phone's table`() {
        val rows = parse(AppOpsFixtures.PIXEL_8A_TABLE)
        val printed = AppOpsFixtures.PIXEL_8A_TABLE.lines().count { Regex("^\\d+\\|").containsMatchIn(it) }
        assertThat(rows).hasSize(printed)
        assertThat(rows.map { it.op }.toSet()).containsAtLeast("CAMERA", "FINE_LOCATION", "COARSE_LOCATION", "GPS")
    }

    @Test
    fun `a camera use is read with its time, its length and its state`() {
        val camera = parse(AppOpsFixtures.PIXEL_8A_TABLE).filter { it.packageName == "com.google.android.GoogleCamera" && it.op == "CAMERA" }
        val count = camera.first { it.accessCount > 0 }
        val length = camera.first { it.durationMillis > 0 }
        assertThat(count.uid).isEqualTo(10171)
        assertThat(count.op).isEqualTo("CAMERA")
        assertThat(count.attributionTag).isNull()
        assertThat(count.timeMillis).isEqualTo(at("2026-10-12 09:36:03.307"))
        assertThat(count.uidState).isEqualTo(UidState.TOP)
        assertThat(count.flags).containsExactly(OpFlag.SELF)
        assertThat(count.background).isFalse()
        assertThat(length.durationMillis).isEqualTo(1863L)
        assertThat(length.accessCount).isEqualTo(0)
    }

    @Test
    fun `a refusal is read as rejects from a foreground service`() {
        val refused = parse(AppOpsFixtures.PIXEL_8A_TABLE)
            .first { it.packageName == "org.jarsi.devicewatch" && it.op == "FINE_LOCATION" && it.timeMillis == at("2026-10-13 07:12:01.974") }
        assertThat(refused.rejectCount).isEqualTo(6)
        assertThat(refused.accessCount).isEqualTo(0)
        assertThat(refused.uidState).isEqualTo(UidState.FOREGROUND_SERVICE)
    }

    @Test
    fun `an attribution tag and a background state come through`() {
        val rows = parse(AppOpsFixtures.PIXEL_8A_TABLE)
        val tagged = rows.first { it.packageName == "com.google.android.gms" && it.attributionTag == "nearby_connections" }
        assertThat(tagged.accessCount).isEqualTo(3)
        assertThat(tagged.uidState).isEqualTo(UidState.FOREGROUND)
        val weather = rows.first { it.packageName == "com.google.android.apps.weather" }
        assertThat(weather.uidState).isEqualTo(UidState.BACKGROUND)
        assertThat(weather.background).isTrue()
        val system = rows.first { it.uid == 1000 }
        assertThat(system.uidState).isEqualTo(UidState.PERSISTENT)
        assertThat(system.background).isFalse()
    }

    @Test
    fun `columns are found by name whatever their order`() {
        val dump = """
            ------------Discrete App Ops-------------

            OP_NAME|ACCESS_TIME|UID|PACKAGE_NAME|ACCESS_COUNTS|EXTRA|REJECT_COUNTS|DURATION|UID_STATE|OP_FLAGS|ATTRIBUTION_TAG
            CAMERA|2026-10-12 09:36:03.307|10171|com.example.cam|1|whatever|0|0|top|s|null
        """.trimIndent()
        val row = parse(dump).single()
        assertThat(row.op).isEqualTo("CAMERA")
        assertThat(row.uid).isEqualTo(10171)
        assertThat(row.packageName).isEqualTo("com.example.cam")
        assertThat(row.accessCount).isEqualTo(1)
        assertThat(row.timeMillis).isEqualTo(at("2026-10-12 09:36:03.307"))
    }

    @Test
    fun `rows that do not fit the header are skipped`() {
        val dump = """
            UID|PACKAGE_NAME|DEVICE_ID|OP_NAME|ATTRIBUTION_TAG|ACCESS_TIME|ACCESS_COUNTS|REJECT_COUNTS|DURATION|UID_STATE|OP_FLAGS|ATTRIBUTION_CHAIN_ID|ATTRIBUTION_FLAGS
            10171|com.example.cam|default:0|CAMERA|null|2026-10-12 09:36:03.307|1|0|0|top|s|-1|0
            10171|com.example.cam|default:0|CAMERA|null|2026-10-12 09:36:03.307|1|0|0|top|s|tp|-1|0
            10171|com.example.cam|default:0|CAMERA|null|yesterday|1|0|0|top|s|-1|0
            abc|com.example.cam|default:0|CAMERA|null|2026-10-12 09:36:03.307|1|0|0|top|s|-1|0
            10171|com.example.cam|default:0||null|2026-10-12 09:36:03.307|1|0|0|top|s|-1|0
            some other line entirely
            10172|com.example.mic|default:0|RECORD_AUDIO|voice|2026-10-12 09:40:00.000|0|0|2500|fgsvc|tpd|7|1

            Some later section:
            10173|com.example.later|default:0|CAMERA|null|2026-10-12 09:50:00.000|1|0|0|top|s|-1|0
        """.trimIndent()
        val rows = parse(dump)
        // The table ends at the blank line: the later section's row, same width, is not read.
        assertThat(rows).hasSize(2)
        assertThat(rows[1].attributionTag).isEqualTo("voice")
        assertThat(rows[1].flags).containsExactly(OpFlag.TRUSTED_PROXIED)
        assertThat(rows[1].durationMillis).isEqualTo(2500L)
        assertThat(rows[1].uidState).isEqualTo(UidState.FOREGROUND_SERVICE)
    }

    @Test
    fun `an unknown state or flag reads as unknown and empty, not as an error`() {
        val dump = """
            UID|PACKAGE_NAME|OP_NAME|ACCESS_TIME|ACCESS_COUNTS|UID_STATE|OP_FLAGS
            10171|com.example.cam|CAMERA|2026-10-12 09:36:03.307|1|nonsuch|zz
        """.trimIndent()
        val row = parse(dump).single()
        assertThat(row.uidState).isEqualTo(UidState.UNKNOWN)
        assertThat(row.flags).isEmpty()
        assertThat(row.rejectCount).isEqualTo(0)
        assertThat(row.durationMillis).isEqualTo(0L)
        assertThat(row.attributionTag).isNull()
    }

    @Test
    fun `the time is read in the zone given`() {
        val dump = """
            UID|PACKAGE_NAME|OP_NAME|ACCESS_TIME|ACCESS_COUNTS
            10171|com.example.cam|CAMERA|2026-10-12 09:36:03.307|1
        """.trimIndent()
        val tokyo = ZoneId.of("Asia/Tokyo")
        val row = AppOpsHistoryParser.parse(dump, tokyo)!!.accesses.single()
        assertThat(row.timeMillis).isEqualTo(at("2026-10-12 09:36:03.307", tokyo))
        assertThat(row.timeMillis).isNotEqualTo(at("2026-10-12 09:36:03.307"))
    }

    // --- the tree (Android 12 to 15) ---

    @Test
    fun `reads the emulator's tree, access by access`() {
        val rows = parse(AppOpsFixtures.EMULATOR_API34_TREE)
        val camera = rows.filter { it.packageName == "com.android.camera2" && it.op == "CAMERA" }
        assertThat(camera).hasSize(2)
        val count = camera.first { it.accessCount == 1 }
        val length = camera.first { it.durationMillis > 0 }
        assertThat(count.uid).isEqualTo(10144)
        assertThat(count.attributionTag).isNull()
        assertThat(count.timeMillis).isEqualTo(at("2026-10-13 05:01:00.000"))
        assertThat(count.uidState).isEqualTo(UidState.TOP)
        assertThat(count.flags).containsExactly(OpFlag.SELF)
        assertThat(length.accessCount).isEqualTo(0)
        assertThat(length.durationMillis).isEqualTo(60_000L)
        val mic = rows.filter { it.packageName == "com.android.camera2" && it.op == "RECORD_AUDIO" }
        assertThat(mic).isNotEmpty()
    }

    @Test
    fun `the tree keeps each access under its own tag and operation`() {
        val gms = parse(AppOpsFixtures.EMULATOR_API34_TREE).filter { it.packageName == "com.google.android.gms" }
        assertThat(gms).isNotEmpty()
        assertThat(gms.map { it.op }.toSet()).containsExactly("FINE_LOCATION")
        assertThat(gms.map { it.attributionTag }).containsAtLeast("network_location_provider", "fused_location_provider", "geofencer_provider")
        assertThat(gms.all { it.uid == 10128 }).isTrue()
        assertThat(gms.all { it.uidState == UidState.FOREGROUND }).isTrue()
        val nlp = gms.filter { it.attributionTag == "location_nlp_network" }
        assertThat(nlp).hasSize(3)
        assertThat(nlp.map { it.timeMillis }).contains(at("2026-10-11 12:38:00.000"))
    }

    @Test
    fun `the tree's preamble, chain ids and stray lines are passed over`() {
        val dump = """
            Current AppOps Service state:
              All op active watchers:
                227eee7 ->
                    [CAMERA]
            Discrete accesses:
              Largest chain id: 0
              Uid: 10144
                Device: default
                Package: com.android.camera2
                  CAMERA
                    Attribution: null
                      Access [top-s|tpd] at 2026-10-13 05:01:00.000 attribution flags=1 with chainId=-1
                      Access [top-s] at not a time
                  Access [top-s] at 2026-10-13 05:02:00.000
              Uid: nonsense
                Package: com.example.lost
                  CAMERA
                    Attribution: null
                      Access [top-s] at 2026-10-13 05:03:00.000
        """.trimIndent()
        val rows = parse(dump)
        // The access under the op (line 1), the one after a stray op-less line is still under CAMERA (line 2);
        // the one under a uid that could not be read is dropped.
        assertThat(rows).hasSize(2)
        assertThat(rows[0].flags).containsExactly(OpFlag.SELF, OpFlag.TRUSTED_PROXIED)
        assertThat(rows[1].timeMillis).isEqualTo(at("2026-10-13 05:02:00.000"))
    }

    // --- neither ---

    @Test
    fun `a dump without a discrete history is no history at all`() {
        assertThat(AppOpsHistoryParser.parse("", helsinki)).isNull()
        assertThat(AppOpsHistoryParser.parse("Current AppOps Service state:\n  Settings:\n", helsinki)).isNull()
    }

    @Test
    fun `a table with the header but no rows is an empty history, not none`() {
        val dump = "------------Discrete App Ops-------------\n\nUID|PACKAGE_NAME|OP_NAME|ACCESS_TIME|ACCESS_COUNTS\n"
        assertThat(AppOpsHistoryParser.parse(dump, helsinki)!!.accesses).isEmpty()
    }

    @Test
    fun `flags and states parse from their codes`() {
        assertThat(OpFlag.parse("s|tp")).containsExactly(OpFlag.SELF, OpFlag.TRUSTED_PROXY)
        assertThat(OpFlag.parse("upd")).containsExactly(OpFlag.UNTRUSTED_PROXIED)
        assertThat(OpFlag.parse("")).isEmpty()
        assertThat(UidState.of("cch")).isEqualTo(UidState.CACHED)
        assertThat(UidState.of("")).isEqualTo(UidState.UNKNOWN)
        assertThat(UidState.CACHED.background).isTrue()
        assertThat(UidState.FOREGROUND_SERVICE.background).isFalse()
    }

    @Test
    fun `a layout without a count column is one use per row, a length row its length`() {
        val dump = """
            UID|PACKAGE_NAME|DEVICE_ID|OP_NAME|ATTRIBUTION_TAG|UID_STATE|OP_FLAGS|ATTRIBUTION_FLAGS|CHAIN_ID|ACCESS_TIME|DURATION
            10171|com.example.cam|default:0|CAMERA|null|top|s|0|-1|2026-10-12 09:36:03.307|-1
            10171|com.example.cam|default:0|CAMERA|null|top|s|0|-1|2026-10-12 09:36:03.307|1863
        """.trimIndent()
        val rows = parse(dump)
        assertThat(rows).hasSize(2)
        assertThat(rows[0].accessCount).isEqualTo(1)
        assertThat(rows[0].durationMillis).isEqualTo(0L)
        assertThat(rows[0].rejectCount).isEqualTo(0)
        assertThat(rows[1].accessCount).isEqualTo(0)
        assertThat(rows[1].durationMillis).isEqualTo(1_863L)
        assertThat(AppOpsHistoryParser.parse(dump, helsinki)!!.minuteQuantized).isFalse()
    }

    @Test
    fun `negative counters read as none`() {
        val dump = """
            UID|PACKAGE_NAME|OP_NAME|ACCESS_TIME|ACCESS_COUNTS|REJECT_COUNTS|DURATION
            10171|com.example.cam|CAMERA|2026-10-12 09:36:03.307|-1|-1|-1
        """.trimIndent()
        val row = parse(dump).single()
        assertThat(row.accessCount).isEqualTo(0)
        assertThat(row.rejectCount).isEqualTo(0)
        assertThat(row.durationMillis).isEqualTo(0L)
    }

    @Test
    fun `digits of another script read as digits`() {
        // Persian digits, as a phone set to Persian prints them.
        val persian = "\u06F1\u06F0\u06F1\u06F7\u06F1|com.example.cam|CAMERA|\u06F2\u06F0\u06F2\u06F6-\u06F1\u06F0-\u06F1\u06F2 \u06F0\u06F9:\u06F3\u06F6:\u06F0\u06F3.\u06F3\u06F0\u06F7|\u06F1"
        val dump = "UID|PACKAGE_NAME|OP_NAME|ACCESS_TIME|ACCESS_COUNTS\n$persian"
        val row = parse(dump).single()
        assertThat(row.uid).isEqualTo(10171)
        assertThat(row.timeMillis).isEqualTo(at("2026-10-12 09:36:03.307"))
        assertThat(row.accessCount).isEqualTo(1)
        val tree = "Discrete accesses:\n  Uid: \u06F1\u06F0\u06F1\u06F4\u06F4\n    Package: com.android.camera2\n      CAMERA\n        Attribution: null\n          Access [top-s] at \u06F2\u06F0\u06F2\u06F6-\u06F1\u06F0-\u06F1\u06F3 \u06F0\u06F5:\u06F0\u06F1:\u06F0\u06F0.\u06F0\u06F0\u06F0"
        assertThat(parse(tree).single().uid).isEqualTo(10144)
        assertThat(AppOpsHistoryParser.asciiDigits("abc")).isEqualTo("abc")
    }

    @Test
    fun `reads Android 16's tree, whose device level sits between the operation and its attributions`() {
        val utc = ZoneId.of("UTC")
        val rows = AppOpsHistoryParser.parse(AppOpsFixtures.EMULATOR_API36_TREE, utc)!!.accesses
        val camera = rows.filter { it.packageName == "com.android.camera2" && it.op == "CAMERA" }
        assertThat(camera).hasSize(4)
        assertThat(camera.map { it.accessCount }).containsExactly(1, 0, 1, 0).inOrder()
        assertThat(camera.map { it.durationMillis }).containsExactly(0L, 15_290L, 0L, 6_224L).inOrder()
        assertThat(camera[0].timeMillis).isEqualTo(at("2026-10-13 15:30:47.442", utc))
        assertThat(camera.map { it.uid }.toSet()).containsExactly(10154)
        assertThat(camera.map { it.attributionTag }.toSet()).containsExactly(null)
        val gms = rows.filter { it.packageName == "com.google.android.gms" }
        assertThat(gms.map { it.attributionTag }).containsExactly(
            "network_location_provider", "com.google.android.gms.personalsafety", "com.google.android.gms.gcm", "nearby_connections",
        ).inOrder()
        assertThat(rows.filter { it.uid == 1000 && it.op == "FINE_LOCATION" }.map { it.attributionTag }).containsExactly(null, "SensorNotificationService")
        // "Device: default:0" is a level, not an operation or an access.
        assertThat(rows.map { it.op }.toSet()).containsExactly("COARSE_LOCATION", "FINE_LOCATION", "CAMERA")
        assertThat(rows).hasSize(14)
    }

    @Test
    fun `a tree whose every time is on the minute is kept by the minute, one with seconds or the table is not`() {
        assertThat(AppOpsHistoryParser.parse(AppOpsFixtures.EMULATOR_API34_TREE, helsinki)!!.minuteQuantized).isTrue()
        assertThat(AppOpsHistoryParser.parse(AppOpsFixtures.EMULATOR_API36_TREE, helsinki)!!.minuteQuantized).isFalse()
        assertThat(AppOpsHistoryParser.parse(AppOpsFixtures.PIXEL_8A_TABLE, helsinki)!!.minuteQuantized).isFalse()
        // A length that is not whole minutes gives the record away as well.
        val secondsLong = AppOpsFixtures.EMULATOR_API34_TREE.replace("for 60000 milliseconds", "for 61500 milliseconds")
        assertThat(AppOpsHistoryParser.parse(secondsLong, helsinki)!!.minuteQuantized).isFalse()
        // An empty tree says nothing about how it is kept.
        assertThat(AppOpsHistoryParser.parse("Discrete accesses:\n  Largest chain id: 0\n", helsinki)!!.minuteQuantized).isFalse()
    }

    @Test
    fun `a table under the tree's heading with a header the parser does not know is not read as empty`() {
        val dump = """
            Discrete accesses:
              SOMETHING|ELSE|ENTIRELY|HERE|NOW
              10171|com.example.cam|26|1760000000000|1
        """.trimIndent()
        assertThat(AppOpsHistoryParser.parse(dump, helsinki)).isNull()
        // The heading alone, with nothing under it, is an empty record.
        assertThat(AppOpsHistoryParser.parse("Discrete accesses:\n  Largest chain id: 0\n", helsinki)!!.accesses).isEmpty()
    }
}
