package org.jarsi.devicewatch.presentation

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.data.BatterySample
import org.jarsi.devicewatch.data.MonthlyDataUsage
import org.jarsi.devicewatch.data.NotificationLogEntry
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset

class HtmlReportBuilderTest {

    private val labels = HtmlReportLabels(
        title = "Device Watch report",
        generatedAt = "Created %1\$s",
        summarySection = "Summary",
        summaryScreenTime = "Screen time / day",
        summaryUnlocks = "Unlocks / day",
        summaryNotifications = "Notifications / day",
        summaryData = "Data total",
        batterySection = "Battery level",
        batteryChargingNote = "Charging periods shaded",
        daysSection = "Daily usage",
        columnDay = "Day",
        columnScreenTime = "Screen time",
        columnUnlocks = "Unlocks",
        columnNotifications = "Notifications",
        columnBoots = "Restarts",
        columnCharges = "Charges",
        monthlySection = "Monthly data usage",
        columnMonth = "Month",
        columnMobile = "Mobile",
        columnWifi = "Wi-Fi",
        logSection = "Notification log",
        columnTime = "Time",
        columnApp = "App",
        columnTitle = "Title",
        columnText = "Text",
        empty = "No data yet",
        footer = "Everything in this report was read on the device.",
        hourUnit = "h",
        minuteUnit = "min",
    )

    private fun data(
        days: List<HistoryDay> = emptyList(),
        monthly: List<MonthlyDataUsage> = emptyList(),
        battery: List<BatterySample> = emptyList(),
        log: List<NotificationLogEntry> = emptyList(),
        deviceName: String = "Pixel 8a",
    ) = HtmlReportData(
        deviceName = deviceName,
        generatedAt = LocalDateTime.of(2026, 8, 14, 18, 30),
        days = days,
        monthly = monthly,
        batterySamples = battery,
        logEntries = log,
        zone = ZoneOffset.UTC,
    )

    private fun day(
        date: LocalDate,
        screenTimeMillis: Long = 0L,
        unlocks: Int = 0,
        notifications: Int = 0,
        boots: Int = 0,
        charges: Int = 0,
    ) = HistoryDay(date, screenTimeMillis, unlocks, notifications, boots, charges)

    @Test
    fun `document is a self-contained mobile page`() {
        val html = HtmlReportBuilder.build(data(), labels)

        assertThat(html).startsWith("<!DOCTYPE html>")
        assertThat(html).contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        assertThat(html).contains("<title>Device Watch report</title>")
        assertThat(html).contains("prefers-color-scheme: dark")
        assertThat(html).endsWith("</html>")
    }

    @Test
    fun `report never references anything off the device`() {
        val html = HtmlReportBuilder.build(
            data(
                days = listOf(day(LocalDate.of(2026, 8, 14), screenTimeMillis = 60_000L)),
                log = listOf(
                    NotificationLogEntry(0L, "org.example", "Example", "Title", "Text"),
                ),
            ),
            labels,
        )

        // No stylesheet, script, image or font may be fetched when the file is opened.
        assertThat(html).doesNotContain("src=\"http")
        assertThat(html).doesNotContain("href=\"http")
        assertThat(html).doesNotContain("<script")
        assertThat(html).doesNotContain("@import")
    }

    @Test
    fun `text from other apps is escaped, not rendered`() {
        val html = HtmlReportBuilder.build(
            data(
                log = listOf(
                    NotificationLogEntry(
                        timeMillis = 1_786_811_400_000L,
                        packageName = "org.example",
                        appLabel = "A & B",
                        title = "<img src=x onerror=\"alert(1)\">",
                        text = "5 < 6",
                    ),
                ),
            ),
            labels,
        )

        assertThat(html).doesNotContain("<img src=x")
        assertThat(html).contains("&lt;img src=x onerror=&quot;alert(1)&quot;&gt;")
        assertThat(html).contains("A &amp; B")
        assertThat(html).contains("5 &lt; 6")
    }

    @Test
    fun `escape handles every markup character`() {
        assertThat(HtmlReportBuilder.escape("<a href='x' title=\"y\">&</a>"))
            .isEqualTo("&lt;a href=&#39;x&#39; title=&quot;y&quot;&gt;&amp;&lt;/a&gt;")
        assertThat(HtmlReportBuilder.escape("plain")).isEqualTo("plain")
    }

    @Test
    fun `daily table lists the newest day first`() {
        val html = HtmlReportBuilder.build(
            data(
                days = listOf(
                    day(LocalDate.of(2026, 8, 12), screenTimeMillis = 5_400_000L, unlocks = 10),
                    day(LocalDate.of(2026, 8, 13), unlocks = 20),
                    day(LocalDate.of(2026, 8, 14), unlocks = 30),
                ),
            ),
            labels,
        )

        val rows = Regex("<td class=\"day\">(\\d{4}-\\d{2}-\\d{2})</td>")
            .findAll(html).map { it.groupValues[1] }.toList()
        assertThat(rows).containsExactly("2026-08-14", "2026-08-13", "2026-08-12").inOrder()
        // 5 400 000 ms = 1 h 30 min
        assertThat(html).contains("1 h 30 min")
    }

    @Test
    fun `screen time under an hour omits the hour part`() {
        val html = HtmlReportBuilder.build(
            data(days = listOf(day(LocalDate.of(2026, 8, 14), screenTimeMillis = 2_700_000L))),
            labels,
        )

        assertThat(html).contains("45 min")
        assertThat(html).doesNotContain("0 h 45 min")
    }

    @Test
    fun `summary averages over the days that carry data`() {
        val html = HtmlReportBuilder.build(
            data(
                days = listOf(
                    day(LocalDate.of(2026, 8, 13), screenTimeMillis = 3_600_000L, unlocks = 10, notifications = 40),
                    day(LocalDate.of(2026, 8, 14), screenTimeMillis = 7_200_000L, unlocks = 20, notifications = 60),
                ),
                monthly = listOf(MonthlyDataUsage(YearMonth.of(2026, 8), mobileGb = 1.5, wifiGb = 2.25)),
            ),
            labels,
        )

        assertThat(html).contains("1 h 30 min") // (1 h + 2 h) / 2
        assertThat(html).contains(">15<")       // unlocks per day
        assertThat(html).contains(">50<")       // notifications per day
        assertThat(html).contains("3.75 GB")    // mobile + Wi-Fi of the current month
    }

    @Test
    fun `battery chart draws one polyline per uninterrupted run`() {
        val hour = 3_600_000L
        val start = 1_786_700_000_000L
        val html = HtmlReportBuilder.build(
            data(
                battery = listOf(
                    BatterySample(start, 80, false),
                    BatterySample(start + hour, 75, false),
                    // A gap longer than the break threshold: the line must not span it.
                    BatterySample(start + 10 * hour, 40, false),
                    BatterySample(start + 11 * hour, 55, true),
                ),
            ),
            labels,
        )

        assertThat(html).contains("<svg")
        assertThat(Regex("<polyline").findAll(html).count()).isEqualTo(2)
        // The charging run is shaded.
        assertThat(html).contains("class=\"charging\"")
    }

    @Test
    fun `battery chart is readable - it carries a scale and its time range`() {
        val hour = 3_600_000L
        val start = 1_786_700_000_000L // 2026-08-14T09:33:20Z
        val html = HtmlReportBuilder.build(
            data(
                battery = listOf(
                    BatterySample(start, 80, false),
                    BatterySample(start + hour, 75, false),
                ),
            ),
            labels,
        )

        assertThat(html).contains("100 %")
        assertThat(html).contains("50 %")
        assertThat(html).contains("0 %")
        assertThat(html).contains("08-14 09:33")
        assertThat(html).contains("08-14 10:33")
    }

    @Test
    fun `sections with no data show the empty note instead of an empty table`() {
        val html = HtmlReportBuilder.build(data(), labels)

        assertThat(html).contains("Summary")
        assertThat(Regex("No data yet").findAll(html).count()).isAtLeast(3)
        assertThat(html).doesNotContain("<svg")
    }

    @Test
    fun `header carries the device name and creation time`() {
        val html = HtmlReportBuilder.build(data(deviceName = "Galaxy Z Flip4"), labels)

        assertThat(html).contains("Galaxy Z Flip4")
        assertThat(html).contains("Created 2026-08-14 18:30")
    }

    @Test
    fun `monthly rows render a dash when a figure is unavailable`() {
        val html = HtmlReportBuilder.build(
            data(monthly = listOf(MonthlyDataUsage(YearMonth.of(2026, 7), mobileGb = -1.0, wifiGb = 0.5))),
            labels,
        )

        assertThat(html).contains("2026-07")
        assertThat(html).contains("—")
        assertThat(html).contains("0.50 GB")
    }
}
