package org.jarsi.devicewatch.presentation

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.data.NotificationLogEntry
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class CsvExporterTest {

    @Test
    fun `plain fields pass through unquoted`() {
        assertThat(CsvExporter.escapeField("plain")).isEqualTo("plain")
        assertThat(CsvExporter.escapeField("")).isEqualTo("")
    }

    @Test
    fun `fields with commas quotes or newlines are quoted with quotes doubled`() {
        assertThat(CsvExporter.escapeField("a,b")).isEqualTo("\"a,b\"")
        assertThat(CsvExporter.escapeField("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"")
        assertThat(CsvExporter.escapeField("line1\nline2")).isEqualTo("\"line1\nline2\"")
    }

    @Test
    fun `usage history csv has a header and one ascending row per day`() {
        val days = listOf(
            HistoryDay(
                day = LocalDate.of(2026, 8, 13),
                screenTimeMillis = 90_000L,
                unlocks = 12,
                notifications = 7,
                boots = 1,
                charges = 2,
            ),
            HistoryDay(
                day = LocalDate.of(2026, 8, 14),
                screenTimeMillis = 3_600_000L,
                unlocks = 3,
                notifications = 0,
                boots = 0,
                charges = 1,
            ),
        )

        val csv = CsvExporter.usageHistoryCsv(days)

        val lines = csv.trimEnd().lines()
        assertThat(lines[0]).isEqualTo("day,screen_time_minutes,unlocks,notifications,boots,charges,screen_on_minutes")
        // 90 s rounds to 2 minutes; a full hour is exactly 60. Screen-on time was
        // never collected here, so its column stays empty.
        assertThat(lines[1]).isEqualTo("2026-08-13,2,12,7,1,2,")
        assertThat(lines[2]).isEqualTo("2026-08-14,60,3,0,0,1,")
        assertThat(lines).hasSize(3)
    }

    @Test
    fun `usage history csv leaves a cell empty before that metric was first collected`() {
        val days = listOf(
            HistoryDay(
                day = LocalDate.of(2026, 8, 13),
                screenTimeMillis = 90_000L,
                unlocks = 12,
                notifications = 0,
                boots = 0,
                charges = 0,
            ),
            HistoryDay(
                day = LocalDate.of(2026, 8, 14),
                screenTimeMillis = 3_600_000L,
                unlocks = 3,
                notifications = 7,
                boots = 1,
                charges = 1,
            ),
        )

        val lines = CsvExporter.usageHistoryCsv(days).trimEnd().lines()

        // Notifications, boots and charges were first recorded on the 14th; the
        // 13th's zeros are "not collected yet" and stay empty rather than 0.
        assertThat(lines[1]).isEqualTo("2026-08-13,2,12,,,,")
        assertThat(lines[2]).isEqualTo("2026-08-14,60,3,7,1,1,")
    }

    @Test
    fun `usage history csv carries screen-on minutes from the first day they were collected`() {
        val days = listOf(
            HistoryDay(LocalDate.of(2026, 8, 13), 60_000L, 1, 0, 0, 0, screenOnMillis = 0L),
            HistoryDay(LocalDate.of(2026, 8, 14), 60_000L, 1, 0, 0, 0, screenOnMillis = 5_400_000L),
        )

        val lines = CsvExporter.usageHistoryCsv(days).trimEnd().lines()

        assertThat(lines[1]).endsWith(",")
        assertThat(lines[2]).endsWith(",90")
    }

    @Test
    fun `notification log csv keeps log order and escapes free text`() {
        val entries = listOf(
            NotificationLogEntry(
                timeMillis = 1_786_692_600_000L, // 2026-08-14T07:30:00Z
                packageName = "org.example.app",
                appLabel = "Example, App",
                title = "Hello \"world\"",
                text = "first line",
            ),
        )

        val csv = CsvExporter.notificationLogCsv(entries, ZoneOffset.UTC)

        val lines = csv.trimEnd().lines()
        assertThat(lines[0]).isEqualTo("time,package,app,title,text")
        assertThat(lines[1]).isEqualTo(
            "2026-08-14T07:30:00,org.example.app,\"Example, App\",\"Hello \"\"world\"\"\",first line"
        )
    }

    @Test
    fun `fields a spreadsheet would run as a formula are neutralised with a leading apostrophe`() {
        assertThat(CsvExporter.escapeField("=1+1")).isEqualTo("\"'=1+1\"")
        assertThat(CsvExporter.escapeField("+5")).isEqualTo("\"'+5\"")
        assertThat(CsvExporter.escapeField("-5")).isEqualTo("\"'-5\"")
        assertThat(CsvExporter.escapeField("@SUM(A1)")).isEqualTo("\"'@SUM(A1)\"")
        assertThat(CsvExporter.escapeField("\tx")).isEqualTo("\"'\tx\"")
        assertThat(CsvExporter.escapeField("\rx")).isEqualTo("\"'\rx\"")
    }

    @Test
    fun `a formula-looking field that also needs quoting keeps its doubled quotes`() {
        assertThat(CsvExporter.escapeField("=HYPERLINK(\"x\")"))
            .isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"")
    }

    @Test
    fun `notification log csv neutralises formula-looking free text`() {
        val entries = listOf(
            NotificationLogEntry(
                timeMillis = 1_786_692_600_000L,
                packageName = "org.example.app",
                appLabel = "Example",
                title = "=cmd|' /C calc'!A0",
                text = "-1",
            ),
        )

        val csv = CsvExporter.notificationLogCsv(entries, ZoneOffset.UTC)

        assertThat(csv.trimEnd().lines()[1]).isEqualTo(
            "2026-08-14T07:30:00,org.example.app,Example,\"'=cmd|' /C calc'!A0\",\"'-1\""
        )
    }

    @Test
    fun `empty inputs produce a header-only file`() {
        assertThat(CsvExporter.usageHistoryCsv(emptyList()).trimEnd().lines()).hasSize(1)
        assertThat(
            CsvExporter.notificationLogCsv(emptyList(), ZoneOffset.UTC).trimEnd().lines()
        ).hasSize(1)
    }
}
