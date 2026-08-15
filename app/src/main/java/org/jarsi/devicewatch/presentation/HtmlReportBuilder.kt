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
    /** What the day search matches, with an example the reader can copy. */
    val searchHintDays: String,
    val searchHintLog: String,
    val rangeWeek: String,
    val rangeMonth: String,
    val rangeAll: String,
    val rangeFrom: String,
    val rangeTo: String,
    val rangeReset: String,
    /** Two placeholders: rows shown, rows in total. */
    val showingCount: String,
    val noMatches: String,
)

/**
 * One theme's colours. Both palettes are asserted against the WCAG AAA contrast
 * ratios in the unit tests: the report is read outdoors on a phone, where a
 * merely AA grey stops being legible.
 */
internal data class ReportPalette(
    /** Page behind the cards, and the fill of nested boxes and table banding. */
    val bg: String,
    /** Card surface — the other background text is read against. */
    val card: String,
    val ink: String,
    val muted: String,
    /** Decorative card outline only; anything load-bearing uses [grid]. */
    val line: String,
    /** Chart grid, table rules and control borders: 3:1 against both surfaces. */
    val grid: String,
    val accent: String,
    val mobile: String,
)

internal val LIGHT_PALETTE = ReportPalette(
    bg = "#f4f5f7",
    card = "#ffffff",
    ink = "#1a1d21",
    muted = "#44505e",
    line = "#dfe3e8",
    grid = "#848d99",
    accent = "#0a7a44",
    mobile = "#0369a1",
)

internal val DARK_PALETTE = ReportPalette(
    bg = "#0d1014",
    card = "#12151a",
    ink = "#f3f4f6",
    muted = "#a7afba",
    line = "#252a31",
    grid = "#6b7280",
    accent = "#34d399",
    mobile = "#38bdf8",
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

    /** The solid charging marker lives in the strip below the plot area. */
    private const val CHARGING_BAR_HEIGHT = 12

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
                // A 18 %-opacity tint is invisible in daylight; the solid bar below
                // the plot is what still marks the run outdoors.
                append("<rect class=\"charging-bar\" x=\"").append(coord(startX))
                append("\" y=\"").append(CHART_BOTTOM + 8).append("\" width=\"").append(coord(width))
                append("\" height=\"$CHARGING_BAR_HEIGHT\"/>\n")
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
            hint = labels.searchHintDays,
            initialRows = DEFAULT_DAY_ROWS,
            ranges = listOf(7 to labels.rangeWeek, 30 to labels.rangeMonth, 0 to labels.rangeAll),
            firstDate = data.days.minOf { it.day }.toString(),
            lastDate = data.days.maxOf { it.day }.toString(),
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
            // The ISO date drives the from/to filter; ISO strings compare correctly
            // as plain text, so the script needs no date parsing.
            append("<tr data-date=\"").append(escape(day.day.toString())).append("\">")
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
        val logTimes = data.logEntries.map {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it.timeMillis), data.zone)
        }
        appendTools(
            name = "log",
            placeholder = labels.searchLog,
            hint = labels.searchHintLog,
            initialRows = DEFAULT_LOG_ROWS,
            // A row count, not a day count: the log's own range is the 7 days it retains.
            ranges = listOf(DEFAULT_LOG_ROWS to DEFAULT_LOG_ROWS.toString(), 0 to labels.rangeAll),
            firstDate = logTimes.min().toLocalDate().toString(),
            lastDate = logTimes.max().toLocalDate().toString(),
            labels = labels,
        )
        appendTableStart(
            listOf(labels.columnTime, labels.columnApp, labels.columnTitle, labels.columnText),
            tableClass = "text",
            tableName = "log",
        )
        data.logEntries.forEach { entry ->
            val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(entry.timeMillis), data.zone)
            append("<tr data-date=\"").append(escape(time.toLocalDate().toString())).append("\">")
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
     * Search box, row-count shortcuts and a from/to date range for a long table.
     * The whole block is hidden until the script switches it on, so a viewer that
     * blocks scripts sees the full table and no dead controls — the report
     * degrades to what it was before: everything, in order.
     */
    private fun StringBuilder.appendTools(
        name: String,
        placeholder: String,
        hint: String,
        initialRows: Int,
        ranges: List<Pair<Int, String>>,
        firstDate: String,
        lastDate: String,
        labels: HtmlReportLabels,
    ) {
        append("<div class=\"tools\" data-tools=\"").append(name).append("\"")
        append(" data-initial=\"").append(initialRows).append("\"")
        append(" data-count=\"").append(escape(countTemplate(labels))).append("\"")
        append(" data-empty=\"").append(escape(labels.noMatches)).append("\">\n")
        val hintId = "hint-$name"
        append("<input type=\"search\" class=\"find\" placeholder=\"").append(escape(placeholder))
        append("\" aria-label=\"").append(escape(placeholder))
        // The hint is the field's description, so a screen reader reads it too.
        append("\" aria-describedby=\"").append(hintId).append("\">\n")
        append("<div class=\"chips\">")
        ranges.forEach { (rows, label) ->
            append("<button type=\"button\" data-rows=\"").append(rows).append("\">")
            append(escape(label)).append("</button>")
        }
        append("</div>\n")
        // Bounded by the data the report actually carries, so the picker cannot
        // offer a day this report knows nothing about.
        append("<div class=\"dates\">")
        appendDateField("from", labels.rangeFrom, firstDate, lastDate)
        appendDateField("to", labels.rangeTo, firstDate, lastDate)
        append("<button type=\"button\" class=\"reset\">").append(escape(labels.rangeReset))
        append("</button>")
        append("</div>\n")
        append("<p class=\"hint\" id=\"").append(hintId).append("\">").append(escape(hint))
        append("</p>\n")
        append("<p class=\"count\"></p>\n")
        append("</div>\n")
    }

    private fun StringBuilder.appendDateField(
        cssClass: String,
        label: String,
        firstDate: String,
        lastDate: String,
    ) {
        append("<label><span>").append(escape(label)).append("</span>")
        append("<input type=\"date\" class=\"").append(cssClass)
        append("\" min=\"").append(escape(firstDate))
        append("\" max=\"").append(escape(lastDate)).append("\"></label>")
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
            var from = tools.querySelector('.from');
            var to = tools.querySelector('.to');
            var reset = tools.querySelector('.reset');
            var template = tools.getAttribute('data-count');
            var noMatches = tools.getAttribute('data-empty');
            var initial = parseInt(tools.getAttribute('data-initial'), 10) || 0;
            var limit = initial;

            // ISO dates compare correctly as plain strings, so no parsing is needed.
            function inRange(row) {
              var day = row.getAttribute('data-date');
              if (!day) return true;
              if (from && from.value && day < from.value) return false;
              if (to && to.value && day > to.value) return false;
              return true;
            }

            function apply() {
              var needle = input ? input.value.trim().toLowerCase() : '';
              var shown = 0;
              rows.forEach(function (row) {
                var hit = inRange(row) &&
                  (!needle || row.textContent.toLowerCase().indexOf(needle) !== -1);
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
            // A search or a chosen period should look at every row, not only the
            // ones the row limit happened to keep.
            if (input) input.addEventListener('input', function () {
              if (input.value.trim()) limit = 0;
              apply();
            });
            [from, to].forEach(function (field) {
              if (!field) return;
              field.addEventListener('change', function () {
                if (from.value || to.value) limit = 0;
                apply();
              });
            });
            if (reset) reset.addEventListener('click', function () {
              if (input) input.value = '';
              if (from) from.value = '';
              if (to) to.value = '';
              limit = initial;
              apply();
            });
            tools.classList.add('ready');
            apply();
          });
        })();
    """.trimIndent()

    private fun themeBlock(palette: ReportPalette): String = """
        --bg: ${palette.bg};
        --card: ${palette.card};
        --ink: ${palette.ink};
        --muted: ${palette.muted};
        --line: ${palette.line};
        --grid: ${palette.grid};
        --accent: ${palette.accent};
        --mobile: ${palette.mobile};
    """.trimIndent()

    /**
     * Sizes and weights are deliberately generous: the report is read on a phone
     * held outdoors, where the previous 11 px greys disappeared. Every colour pair
     * here is one the palette test holds to a WCAG AAA ratio.
     */
    private val BASE_STYLES = """
        * { box-sizing: border-box; }
        body {
          margin: 0;
          padding: 0 16px 40px;
          background: var(--bg);
          color: var(--ink);
          font-family: -apple-system, "Segoe UI", Roboto, system-ui, sans-serif;
          font-size: 17px;
          line-height: 1.6;
          -webkit-text-size-adjust: 100%;
        }
        header { padding: 28px 4px 8px; max-width: 720px; margin: 0 auto; }
        main, footer { max-width: 720px; margin: 0 auto; }
        h1 { font-size: 1.5rem; margin: 0 0 4px; letter-spacing: -0.01em; }
        h2 {
          font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.08em;
          color: var(--muted); margin: 0 0 10px; font-weight: 700;
        }
        .sub { margin: 0; color: var(--muted); font-size: 0.95rem; }
        section {
          background: var(--card); border-radius: 18px; padding: 18px 16px;
          margin: 14px 0; border: 1px solid var(--line);
        }
        .cards { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
        .card { background: var(--bg); border-radius: 14px; padding: 12px; }
        .k {
          font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.05em;
          color: var(--muted); font-weight: 600;
        }
        .v { font-size: 1.4rem; font-weight: 700; margin-top: 2px; }
        .empty { color: var(--muted); margin: 0; }
        .note { color: var(--muted); font-size: 0.85rem; margin: 8px 0 0; }
        .plot { position: relative; }
        .chart svg { width: 100%; height: 190px; display: block; }
        .ax {
          position: absolute; right: 0; font-size: 0.78rem; color: var(--muted);
          background: var(--card); padding: 0 0 0 6px; line-height: 1; font-weight: 600;
        }
        .ax.top { top: 2%; }
        .ax.mid { top: 44%; transform: translateY(-50%); }
        .ax.bot { top: 84%; transform: translateY(-50%); }
        .range {
          display: flex; justify-content: space-between; color: var(--muted);
          font-size: 0.8rem; font-variant-numeric: tabular-nums; margin-top: 4px;
        }
        .grid { stroke: var(--grid); stroke-width: 1.5; }
        /* The tint shows the shape of a charging run at a glance; the solid bar
           under the plot is what still carries in daylight. */
        .charging { fill: var(--mobile); opacity: 0.18; }
        .charging-bar { fill: var(--mobile); }
        .level {
          fill: none; stroke: var(--accent); stroke-width: 3.5;
          stroke-linejoin: round; stroke-linecap: round;
        }
        /* Hidden until the script enables it, so a script-free viewer sees no dead controls. */
        .tools { display: none; }
        .tools.ready {
          display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 12px;
        }
        .find {
          flex: 1 1 160px; min-width: 0; padding: 10px 14px; border-radius: 999px;
          border: 1px solid var(--grid); background: var(--bg); color: var(--ink);
          font: inherit; font-size: 0.95rem; -webkit-appearance: none; appearance: none;
        }
        .chips { display: flex; gap: 6px; flex-wrap: wrap; }
        .chips button, .reset {
          padding: 8px 14px; border-radius: 999px; border: 1px solid var(--grid);
          background: var(--bg); color: var(--muted); font: inherit; font-size: 0.85rem;
          cursor: pointer; min-height: 40px;
        }
        .chips button.on {
          color: var(--ink); border-color: var(--accent); border-width: 2px; font-weight: 700;
        }
        .dates {
          display: flex; flex-wrap: wrap; align-items: center; gap: 8px; flex: 1 0 100%;
        }
        .dates label {
          display: inline-flex; align-items: center; gap: 6px;
          color: var(--muted); font-size: 0.85rem; font-weight: 600;
        }
        .dates input {
          padding: 8px 10px; border-radius: 12px; border: 1px solid var(--grid);
          background: var(--bg); color: var(--ink); font: inherit; font-size: 0.9rem;
          min-height: 40px;
        }
        :focus-visible { outline: 3px solid var(--accent); outline-offset: 2px; }
        .hint, .count {
          flex: 1 0 100%; margin: 0; color: var(--muted); font-size: 0.85rem;
        }
        .count { font-variant-numeric: tabular-nums; }
        tr[hidden] { display: none; }
        .scroll { overflow-x: auto; -webkit-overflow-scrolling: touch; }
        table { border-collapse: collapse; width: 100%; font-size: 0.95rem; }
        th, td { text-align: right; padding: 9px 10px; white-space: nowrap; }
        th:first-child, td:first-child { text-align: left; }
        /* Daily table: the duration column reads as text, the tallies as numbers. */
        table.days th:nth-child(2), table.days td:nth-child(2) { text-align: left; }
        table.text th, table.text td { text-align: left; }
        table.text td:last-child { white-space: normal; max-width: 40ch; }
        thead th {
          font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.05em;
          color: var(--muted); font-weight: 700; border-bottom: 2px solid var(--grid);
        }
        tbody tr:nth-child(even) { background: var(--bg); }
        /* Keep the banding correct once rows are filtered out (ignored by older engines). */
        tbody tr:nth-child(odd of :not([hidden])) { background: transparent; }
        tbody tr:nth-child(even of :not([hidden])) { background: var(--bg); }
        td.day { font-variant-numeric: tabular-nums; color: var(--muted); font-weight: 600; }
        footer { color: var(--muted); font-size: 0.85rem; padding: 8px 4px 0; text-align: center; }
    """.trimIndent()

    private val STYLES: String = buildString {
        append(":root {\ncolor-scheme: light dark;\n")
        append(themeBlock(LIGHT_PALETTE)).append("\n}\n")
        append("@media (prefers-color-scheme: dark) {\n:root {\n")
        append(themeBlock(DARK_PALETTE)).append("\n}\n}\n")
        append(BASE_STYLES)
    }
}
