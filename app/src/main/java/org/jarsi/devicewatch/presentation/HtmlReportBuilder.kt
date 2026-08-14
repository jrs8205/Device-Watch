package org.jarsi.devicewatch.presentation

import org.jarsi.devicewatch.data.BatterySample
import org.jarsi.devicewatch.data.MonthlyDataUsage
import org.jarsi.devicewatch.data.NotificationLogEntry
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the report renders. The caller reads it off the Historia state. */
data class HtmlReportData(
    val deviceName: String,
    val generatedAt: LocalDateTime,
    /** Daily tallies ascending; the report itself flips them newest first. */
    val days: List<HistoryDay>,
    val monthly: List<MonthlyDataUsage>,
    /** The retained battery window, ascending. */
    val batterySamples: List<BatterySample>,
    val logEntries: List<NotificationLogEntry>,
    val zone: ZoneId,
    /** Number formatting follows the phone's locale; tests pin it. */
    val locale: Locale = Locale.US,
)

/** Localized headings. Kept out of the builder so it stays a plain-JVM unit. */
data class HtmlReportLabels(
    val title: String,
    /** Format string with one `%1$s` placeholder for the timestamp. */
    val generatedAt: String,
    val summarySection: String,
    val summaryScreenTime: String,
    val summaryUnlocks: String,
    val summaryNotifications: String,
    val summaryData: String,
    val batterySection: String,
    val batteryChargingNote: String,
    val daysSection: String,
    val columnDay: String,
    val columnScreenTime: String,
    val columnUnlocks: String,
    val columnNotifications: String,
    val columnBoots: String,
    val columnCharges: String,
    val monthlySection: String,
    val columnMonth: String,
    val columnMobile: String,
    val columnWifi: String,
    val logSection: String,
    val columnTime: String,
    val columnApp: String,
    val columnTitle: String,
    val columnText: String,
    val empty: String,
    val footer: String,
    val hourUnit: String,
    val minuteUnit: String,
    val searchDays: String,
    val searchLog: String,
    val rangeWeek: String,
    val rangeMonth: String,
    val rangeAll: String,
    /** Two placeholders: rows shown, rows in total. */
    val showingCount: String,
    val noMatches: String,
)

/**
 * Renders the shareable report as a single self-contained HTML file: styles are
 * inline and the battery chart is inline SVG, so the file opens identically in a
 * mail attachment, a chat app or a browser with no network at all. Nothing from
 * another app is ever emitted unescaped — notification titles are arbitrary text
 * from third parties.
 */
object HtmlReportBuilder {

    /** Samples further apart than this are a collection gap, not a discharge line. */
    private const val CHART_GAP_MILLIS = 90L * 60 * 1000

    private const val CHART_WIDTH = 720
    private const val CHART_HEIGHT = 240
    private const val CHART_TOP = 8
    private const val CHART_BOTTOM = 216

    private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** Chart ends only need day + time, and the pattern must read the same in every locale. */
    private val CHART_STAMP = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    /** Two months of days is a long scroll on a phone; the rest is one tap away. */
    private const val DEFAULT_DAY_ROWS = 30

    /** The log can run to hundreds of rows over its 7-day retention. */
    private const val DEFAULT_LOG_ROWS = 100
    private val DASH = "—"

    fun escape(value: String): String = buildString(value.length) {
        value.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }

    fun build(data: HtmlReportData, labels: HtmlReportLabels): String = buildString {
        append("<!DOCTYPE html>\n")
        append("<html lang=\"").append(escape(data.locale.language.ifEmpty { "en" })).append("\">\n")
        append("<head>\n")
        append("<meta charset=\"utf-8\">\n")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        append("<title>").append(escape(labels.title)).append("</title>\n")
        append("<style>\n").append(STYLES).append("</style>\n")
        append("</head>\n")
        append("<body>\n")
        appendHeader(data, labels)
        append("<main>\n")
        appendSummary(data, labels)
        appendBattery(data, labels)
        appendDays(data, labels)
        appendMonthly(data, labels)
        appendLog(data, labels)
        append("</main>\n")
        append("<footer>").append(escape(labels.footer)).append("</footer>\n")
        append("<script>\n").append(FILTER_SCRIPT).append("</script>\n")
        append("</body>\n")
        append("</html>")
    }

    private fun StringBuilder.appendHeader(data: HtmlReportData, labels: HtmlReportLabels) {
        append("<header>\n")
        append("<h1>").append(escape(labels.title)).append("</h1>\n")
        append("<p class=\"sub\">").append(escape(data.deviceName)).append("</p>\n")
        val stamp = String.format(data.locale, labels.generatedAt, data.generatedAt.format(TIMESTAMP))
        append("<p class=\"sub\">").append(escape(stamp)).append("</p>\n")
        append("</header>\n")
    }

    private fun StringBuilder.appendSummary(data: HtmlReportData, labels: HtmlReportLabels) {
        appendSectionStart(labels.summarySection)
        val active = data.days.filter {
            it.screenTimeMillis > 0L || it.unlocks > 0 || it.notifications > 0
        }
        if (active.isEmpty() && data.monthly.isEmpty()) {
            appendEmpty(labels)
            append("</section>\n")
            return
        }
        append("<div class=\"cards\">\n")
        appendCard(
            labels.summaryScreenTime,
            if (active.isEmpty()) DASH
            else durationText(active.sumOf { it.screenTimeMillis } / active.size, labels),
        )
        appendCard(
            labels.summaryUnlocks,
            if (active.isEmpty()) DASH else (active.sumOf { it.unlocks } / active.size).toString(),
        )
        appendCard(
            labels.summaryNotifications,
            if (active.isEmpty()) DASH
            else (active.sumOf { it.notifications } / active.size).toString(),
        )
        appendCard(labels.summaryData, currentMonthDataText(data))
        append("</div>\n")
        append("</section>\n")
    }

    /** Mobile + Wi-Fi of the most recent month the table carries. */
    private fun currentMonthDataText(data: HtmlReportData): String {
        val newest = data.monthly.maxByOrNull { it.month } ?: return DASH
        val parts = listOf(newest.mobileGb, newest.wifiGb).filter { it >= 0.0 }
        if (parts.isEmpty()) return DASH
        return gbText(parts.sum(), data.locale)
    }

    private fun StringBuilder.appendCard(label: String, value: String) {
        append("<div class=\"card\">")
        append("<div class=\"k\">").append(escape(label)).append("</div>")
        append("<div class=\"v\">").append(escape(value)).append("</div>")
        append("</div>\n")
    }

    private fun StringBuilder.appendBattery(data: HtmlReportData, labels: HtmlReportLabels) {
        appendSectionStart(labels.batterySection)
        val samples = data.batterySamples
        if (samples.size < 2) {
            appendEmpty(labels)
            append("</section>\n")
            return
        }
        val first = samples.first().timeMillis
        val span = (samples.last().timeMillis - first).coerceAtLeast(1L)
        fun x(millis: Long) = (millis - first).toDouble() / span * CHART_WIDTH
        fun y(level: Int) =
            CHART_BOTTOM - (level.coerceIn(0, 100) / 100.0) * (CHART_BOTTOM - CHART_TOP)

        append("<div class=\"chart\">\n")
        append("<div class=\"plot\">\n")
        append("<svg viewBox=\"0 0 $CHART_WIDTH $CHART_HEIGHT\" ")
        append("preserveAspectRatio=\"none\" role=\"img\" aria-label=\"")
        append(escape(labels.batterySection)).append("\">\n")

        // Charging runs are shaded behind the line so the discharge slopes read cleanly.
        var runStart: Int? = null
        samples.forEachIndexed { index, sample ->
            if (sample.charging && runStart == null) runStart = index
            val startIndex = runStart
            if (startIndex != null && (!sample.charging || index == samples.lastIndex)) {
                val endIndex = if (sample.charging) index else index - 1
                val startX = x(samples[(startIndex - 1).coerceAtLeast(0)].timeMillis)
                val endX = x(samples[endIndex].timeMillis)
                val width = (endX - startX).coerceAtLeast(3.0)
                append("<rect class=\"charging\" x=\"").append(coord(startX))
                append("\" y=\"$CHART_TOP\" width=\"").append(coord(width))
                append("\" height=\"").append(coord((CHART_BOTTOM - CHART_TOP).toDouble()))
                append("\"/>\n")
                runStart = null
            }
        }

        for (level in listOf(0, 50, 100)) {
            append("<line class=\"grid\" x1=\"0\" x2=\"$CHART_WIDTH\" y1=\"")
            append(coord(y(level))).append("\" y2=\"").append(coord(y(level))).append("\"/>\n")
        }

        // A collection gap must break the line instead of drawing a straight lie across it.
        var run = mutableListOf<BatterySample>()
        fun flush() {
            if (run.size >= 2) {
                append("<polyline class=\"level\" points=\"")
                run.joinTo(this, separator = " ") { "${coord(x(it.timeMillis))},${coord(y(it.level))}" }
                append("\"/>\n")
            }
            run = mutableListOf()
        }
        samples.forEach { sample ->
            val previous = run.lastOrNull()
            if (previous != null && sample.timeMillis - previous.timeMillis > CHART_GAP_MILLIS) flush()
            run.add(sample)
        }
        flush()

        append("</svg>\n")
        // The percentage scale sits outside the SVG: the plot is stretched to the
        // page width, which would distort any text drawn inside it.
        append("<span class=\"ax top\">100 %</span>")
        append("<span class=\"ax mid\">50 %</span>")
        append("<span class=\"ax bot\">0 %</span>\n")
        append("</div>\n")
        append("<div class=\"range\"><span>")
        append(escape(chartStamp(samples.first().timeMillis, data.zone)))
        append("</span><span>")
        append(escape(chartStamp(samples.last().timeMillis, data.zone)))
        append("</span></div>\n")
        append("<p class=\"note\">").append(escape(labels.batteryChargingNote)).append("</p>\n")
        append("</div>\n")
        append("</section>\n")
    }

    private fun chartStamp(millis: Long, zone: ZoneId): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone).format(CHART_STAMP)

    private fun StringBuilder.appendDays(data: HtmlReportData, labels: HtmlReportLabels) {
        appendSectionStart(labels.daysSection)
        if (data.days.isEmpty()) {
            appendEmpty(labels)
            append("</section>\n")
            return
        }
        appendTools(
            name = "days",
            placeholder = labels.searchDays,
            initialRows = DEFAULT_DAY_ROWS,
            ranges = listOf(7 to labels.rangeWeek, 30 to labels.rangeMonth, 0 to labels.rangeAll),
            labels = labels,
        )
        appendTableStart(
            listOf(
                labels.columnDay,
                labels.columnScreenTime,
                labels.columnUnlocks,
                labels.columnNotifications,
                labels.columnBoots,
                labels.columnCharges,
            ),
            tableClass = "days",
            tableName = "days",
        )
        data.days.sortedByDescending { it.day }.forEach { day ->
            append("<tr>")
            append("<td class=\"day\">").append(escape(day.day.toString())).append("</td>")
            append("<td>").append(escape(durationText(day.screenTimeMillis, labels))).append("</td>")
            append("<td>").append(day.unlocks).append("</td>")
            append("<td>").append(day.notifications).append("</td>")
            append("<td>").append(day.boots).append("</td>")
            append("<td>").append(day.charges).append("</td>")
            append("</tr>\n")
        }
        appendTableEnd()
        append("</section>\n")
    }

    private fun StringBuilder.appendMonthly(data: HtmlReportData, labels: HtmlReportLabels) {
        appendSectionStart(labels.monthlySection)
        if (data.monthly.isEmpty()) {
            appendEmpty(labels)
            append("</section>\n")
            return
        }
        appendTableStart(listOf(labels.columnMonth, labels.columnMobile, labels.columnWifi))
        data.monthly.sortedByDescending { it.month }.forEach { month ->
            append("<tr>")
            append("<td class=\"day\">").append(escape(month.month.toString())).append("</td>")
            append("<td>").append(escape(gbOrDash(month.mobileGb, data.locale))).append("</td>")
            append("<td>").append(escape(gbOrDash(month.wifiGb, data.locale))).append("</td>")
            append("</tr>\n")
        }
        appendTableEnd()
        append("</section>\n")
    }

    private fun StringBuilder.appendLog(data: HtmlReportData, labels: HtmlReportLabels) {
        appendSectionStart(labels.logSection)
        if (data.logEntries.isEmpty()) {
            appendEmpty(labels)
            append("</section>\n")
            return
        }
        appendTools(
            name = "log",
            placeholder = labels.searchLog,
            initialRows = DEFAULT_LOG_ROWS,
            // A row count, not a day count: the log's own range is the 7 days it retains.
            ranges = listOf(DEFAULT_LOG_ROWS to DEFAULT_LOG_ROWS.toString(), 0 to labels.rangeAll),
            labels = labels,
        )
        appendTableStart(
            listOf(labels.columnTime, labels.columnApp, labels.columnTitle, labels.columnText),
            tableClass = "text",
            tableName = "log",
        )
        data.logEntries.forEach { entry ->
            val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(entry.timeMillis), data.zone)
            append("<tr>")
            append("<td class=\"day\">").append(escape(time.format(TIMESTAMP))).append("</td>")
            append("<td>").append(escape(entry.appLabel)).append("</td>")
            append("<td>").append(escape(entry.title)).append("</td>")
            append("<td>").append(escape(entry.text)).append("</td>")
            append("</tr>\n")
        }
        appendTableEnd()
        append("</section>\n")
    }

    private fun StringBuilder.appendSectionStart(title: String) {
        append("<section>\n<h2>").append(escape(title)).append("</h2>\n")
    }

    private fun StringBuilder.appendEmpty(labels: HtmlReportLabels) {
        append("<p class=\"empty\">").append(escape(labels.empty)).append("</p>\n")
    }

    /**
     * Search box and row-count shortcuts for a long table. The whole block is
     * hidden until the script switches it on, so a viewer that blocks scripts
     * sees the full table and no dead controls — the report degrades to what it
     * was before: everything, in order.
     */
    private fun StringBuilder.appendTools(
        name: String,
        placeholder: String,
        initialRows: Int,
        ranges: List<Pair<Int, String>>,
        labels: HtmlReportLabels,
    ) {
        append("<div class=\"tools\" data-tools=\"").append(name).append("\"")
        append(" data-initial=\"").append(initialRows).append("\"")
        append(" data-count=\"").append(escape(countTemplate(labels))).append("\"")
        append(" data-empty=\"").append(escape(labels.noMatches)).append("\">\n")
        append("<input type=\"search\" class=\"find\" placeholder=\"").append(escape(placeholder))
        append("\" aria-label=\"").append(escape(placeholder)).append("\">\n")
        append("<div class=\"chips\">")
        ranges.forEach { (rows, label) ->
            append("<button type=\"button\" data-rows=\"").append(rows).append("\">")
            append(escape(label)).append("</button>")
        }
        append("</div>\n")
        append("<p class=\"count\"></p>\n")
        append("</div>\n")
    }

    /** "12 / 62" with the numbers left as script placeholders. */
    private fun countTemplate(labels: HtmlReportLabels): String =
        String.format(Locale.US, labels.showingCount, "{0}", "{1}")

    private fun StringBuilder.appendTableStart(
        columns: List<String>,
        tableClass: String = "",
        tableName: String = "",
    ) {
        append("<div class=\"scroll\">\n<table")
        if (tableClass.isNotEmpty()) append(" class=\"").append(tableClass).append("\"")
        if (tableName.isNotEmpty()) append(" data-table=\"").append(tableName).append("\"")
        append(">\n<thead>\n<tr>")
        columns.forEach { append("<th>").append(escape(it)).append("</th>") }
        append("</tr>\n</thead>\n<tbody>\n")
    }

    private fun StringBuilder.appendTableEnd() {
        append("</tbody>\n</table>\n</div>\n")
    }

    private fun durationText(millis: Long, labels: HtmlReportLabels): String {
        val totalMinutes = (millis + 30_000L) / 60_000L
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            "$hours ${labels.hourUnit} $minutes ${labels.minuteUnit}"
        } else {
            "$minutes ${labels.minuteUnit}"
        }
    }

    private fun gbOrDash(value: Double, locale: Locale): String =
        if (value < 0.0) DASH else gbText(value, locale)

    private fun gbText(value: Double, locale: Locale): String =
        String.format(locale, "%.2f GB", value)

    /** Two decimals keep the SVG readable; more only inflates the file. */
    private fun coord(value: Double): String = String.format(Locale.US, "%.2f", value)

    /**
     * Filtering is the one thing static HTML cannot do, and a 62-row table on a
     * phone needs it. The script touches nothing outside this document: no fetch,
     * no storage, no cookies — it only hides and shows rows that are already here.
     */
    private val FILTER_SCRIPT = """
        (function () {
          var panels = document.querySelectorAll('[data-tools]');
          Array.prototype.forEach.call(panels, function (tools) {
            var table = document.querySelector('table[data-table="' + tools.getAttribute('data-tools') + '"]');
            if (!table || !table.tBodies.length) return;
            var rows = Array.prototype.slice.call(table.tBodies[0].rows);
            var input = tools.querySelector('.find');
            var count = tools.querySelector('.count');
            var buttons = Array.prototype.slice.call(tools.querySelectorAll('button[data-rows]'));
            var template = tools.getAttribute('data-count');
            var noMatches = tools.getAttribute('data-empty');
            var limit = parseInt(tools.getAttribute('data-initial'), 10) || 0;

            function apply() {
              var needle = input ? input.value.trim().toLowerCase() : '';
              var shown = 0;
              rows.forEach(function (row) {
                var hit = !needle || row.textContent.toLowerCase().indexOf(needle) !== -1;
                var visible = hit && (limit === 0 || shown < limit);
                if (visible) shown++;
                row.hidden = !visible;
              });
              if (count) {
                count.textContent = shown === 0
                  ? noMatches
                  : template.replace('{0}', shown).replace('{1}', rows.length);
              }
              buttons.forEach(function (button) {
                var on = parseInt(button.getAttribute('data-rows'), 10) === limit;
                button.className = on ? 'on' : '';
              });
            }

            buttons.forEach(function (button) {
              button.addEventListener('click', function () {
                limit = parseInt(button.getAttribute('data-rows'), 10) || 0;
                apply();
              });
            });
            // A search should look at every row, not only the ones the limit kept.
            if (input) input.addEventListener('input', function () {
              if (input.value.trim()) limit = 0;
              apply();
            });
            tools.classList.add('ready');
            apply();
          });
        })();
    """.trimIndent()

    private val STYLES = """
        :root {
          color-scheme: light dark;
          --bg: #f4f5f7;
          --card: #ffffff;
          --ink: #1a1d21;
          --muted: #6b7280;
          --line: rgba(0, 0, 0, 0.08);
          --accent: #0f9d58;
          --mobile: #0284c7;
        }
        @media (prefers-color-scheme: dark) {
          :root {
            --bg: #0d1014;
            --card: #12151a;
            --ink: #f3f4f6;
            --muted: #8b929c;
            --line: rgba(255, 255, 255, 0.09);
            --accent: #34d399;
            --mobile: #38bdf8;
          }
        }
        * { box-sizing: border-box; }
        body {
          margin: 0;
          padding: 0 16px 40px;
          background: var(--bg);
          color: var(--ink);
          font-family: -apple-system, "Segoe UI", Roboto, system-ui, sans-serif;
          font-size: 16px;
          line-height: 1.45;
          -webkit-text-size-adjust: 100%;
        }
        header { padding: 28px 4px 8px; max-width: 720px; margin: 0 auto; }
        main, footer { max-width: 720px; margin: 0 auto; }
        h1 { font-size: 1.5rem; margin: 0 0 4px; letter-spacing: -0.01em; }
        h2 {
          font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.08em;
          color: var(--muted); margin: 0 0 10px;
        }
        .sub { margin: 0; color: var(--muted); font-size: 0.9rem; }
        section {
          background: var(--card); border-radius: 18px; padding: 18px 16px;
          margin: 14px 0; border: 1px solid var(--line);
        }
        .cards { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
        .card { background: var(--bg); border-radius: 14px; padding: 12px; }
        .k { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--muted); }
        .v { font-size: 1.35rem; font-weight: 700; margin-top: 2px; }
        .empty { color: var(--muted); margin: 0; }
        .note { color: var(--muted); font-size: 0.8rem; margin: 8px 0 0; }
        .plot { position: relative; }
        .chart svg { width: 100%; height: 180px; display: block; }
        .ax {
          position: absolute; right: 0; font-size: 0.68rem; color: var(--muted);
          background: var(--card); padding: 0 0 0 6px; line-height: 1;
        }
        .ax.top { top: 2%; }
        .ax.mid { top: 47%; transform: translateY(-50%); }
        .ax.bot { top: 90%; transform: translateY(-50%); }
        .range {
          display: flex; justify-content: space-between; color: var(--muted);
          font-size: 0.72rem; font-variant-numeric: tabular-nums; margin-top: 4px;
        }
        .grid { stroke: var(--line); stroke-width: 1; }
        .charging { fill: var(--mobile); opacity: 0.14; }
        .level { fill: none; stroke: var(--accent); stroke-width: 3; stroke-linejoin: round; stroke-linecap: round; }
        /* Hidden until the script enables it, so a script-free viewer sees no dead controls. */
        .tools { display: none; }
        .tools.ready {
          display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 10px;
        }
        .find {
          flex: 1 1 160px; min-width: 0; padding: 9px 12px; border-radius: 999px;
          border: 1px solid var(--line); background: var(--bg); color: var(--ink);
          font: inherit; font-size: 0.9rem; -webkit-appearance: none; appearance: none;
        }
        .find:focus { outline: 2px solid var(--accent); outline-offset: 1px; }
        .chips { display: flex; gap: 6px; }
        .chips button {
          padding: 8px 12px; border-radius: 999px; border: 1px solid var(--line);
          background: var(--bg); color: var(--muted); font: inherit; font-size: 0.8rem;
          cursor: pointer; min-height: 36px;
        }
        .chips button.on { color: var(--ink); border-color: var(--accent); font-weight: 600; }
        .count {
          flex: 1 0 100%; margin: 0; color: var(--muted); font-size: 0.75rem;
          font-variant-numeric: tabular-nums;
        }
        tr[hidden] { display: none; }
        .scroll { overflow-x: auto; -webkit-overflow-scrolling: touch; }
        table { border-collapse: collapse; width: 100%; font-size: 0.9rem; }
        th, td { text-align: right; padding: 8px 10px; white-space: nowrap; }
        th:first-child, td:first-child { text-align: left; }
        /* Daily table: the duration column reads as text, the tallies as numbers. */
        table.days th:nth-child(2), table.days td:nth-child(2) { text-align: left; }
        table.text th, table.text td { text-align: left; }
        table.text td:last-child { white-space: normal; max-width: 40ch; }
        thead th {
          font-size: 0.7rem; text-transform: uppercase; letter-spacing: 0.05em;
          color: var(--muted); font-weight: 600; border-bottom: 1px solid var(--line);
        }
        tbody tr:nth-child(even) { background: var(--bg); }
        /* Keep the banding correct once rows are filtered out (ignored by older engines). */
        tbody tr:nth-child(odd of :not([hidden])) { background: transparent; }
        tbody tr:nth-child(even of :not([hidden])) { background: var(--bg); }
        td.day { font-variant-numeric: tabular-nums; color: var(--muted); }
        footer { color: var(--muted); font-size: 0.8rem; padding: 8px 4px 0; text-align: center; }
    """.trimIndent()
}
