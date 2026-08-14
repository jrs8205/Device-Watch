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
        assertThat(lines[0]).isEqualTo("day,screen_time_minutes,unlocks,notifications,boots,charges")
        // 90 s rounds to 2 minutes; a full hour is exactly 60.
        assertThat(lines[1]).isEqualTo("2026-08-13,2,12,7,1,2")
        assertThat(lines[2]).isEqualTo("2026-08-14,60,3,0,0,1")
        assertThat(lines).hasSize(3)
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
    fun `empty inputs produce a header-only file`() {
        assertThat(CsvExporter.usageHistoryCsv(emptyList()).trimEnd().lines()).hasSize(1)
        assertThat(
            CsvExporter.notificationLogCsv(emptyList(), ZoneOffset.UTC).trimEnd().lines()
        ).hasSize(1)
    }
}
