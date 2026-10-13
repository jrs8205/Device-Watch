package org.jarsi.devicewatch.data

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Reads the discrete history out of `dumpsys appops --history --include-discrete 0`,
 * in either shape Android has printed it:
 *
 * - Android 16 and later: a pipe-separated table under a header row that names its
 *   columns (`UID|PACKAGE_NAME|…`). Columns are found by name, so their order and
 *   any new column change nothing; a column the layout lacks has a default.
 * - Android 12 to 15: an indented tree, `Uid:` over `Package:` over the operation's
 *   name over `Attribution:` over one `Access […] at …` line per event, kept by the
 *   minute ([AppOpsHistory.minuteQuantized]).
 *
 * A dump with neither shape, or with a table whose header the parser does not know,
 * answers null: the caller decides whether that means an Android too old to keep
 * the record or a dump that could not be read. Times in the dump are the phone's
 * local time without a zone, so they are read in [ZoneId.systemDefault] unless told
 * otherwise. Anything that does not fit is skipped, never guessed at.
 */
internal object AppOpsHistoryParser {

    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    private val TABLE_REQUIRED = setOf("UID", "PACKAGE_NAME", "OP_NAME", "ACCESS_TIME")
    private const val TREE_HEADER = "Discrete accesses"

    /** Whatever follows `Uid:`: one that is not a number still ends the uid before it. */
    private val TREE_UID = Regex("""^Uid:\s*(\S+)\s*$""")
    private val TREE_PACKAGE = Regex("""^Package:\s*(\S+)\s*$""")
    private val TREE_ATTRIBUTION = Regex("""^Attribution:\s*(.*?)\s*$""")
    private val TREE_OP = Regex("""^[A-Z][A-Z0-9_]*$""")
    private val TREE_ACCESS = Regex(
        """^Access \[([a-z]+)-([a-z|]+)] at (\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})(?: for (\d+) milliseconds)?.*$"""
    )

    fun parse(output: String, zone: ZoneId = ZoneId.systemDefault()): AppOpsHistory? {
        val lines = output.lines().map(::asciiDigits)
        val header = lines.indexOfFirst { isTableHeader(it) }
        if (header >= 0) return AppOpsHistory(table(lines, header, zone))
        val tree = lines.indexOfFirst { it.trimStart().startsWith(TREE_HEADER) }
        if (tree >= 0) {
            val accesses = tree(lines.subList(tree + 1, lines.size), zone)
            // Rows of a table whose header is not known: a layout this parser has not
            // met, which is not the same as an empty record.
            if (accesses.isEmpty() && lines.any { looksLikeRow(it) }) return null
            return AppOpsHistory(accesses, minuteQuantized = true)
        }
        return null
    }

    /** The header names its columns; which comes first is its business. */
    private fun isTableHeader(line: String): Boolean {
        if (!line.contains('|')) return false
        val names = line.split('|').map { it.trim() }.toSet()
        return TABLE_REQUIRED.all { it in names }
    }

    private fun looksLikeRow(line: String): Boolean = line.count { it == '|' } >= 4 && line.trimStart().firstOrNull()?.isDigit() == true

    private fun table(lines: List<String>, headerIndex: Int, zone: ZoneId): List<AppOpAccess> {
        val columns = lines[headerIndex].split('|').withIndex().associate { (index, name) -> name.trim() to index }
        val uid = columns.getValue("UID")
        val packageName = columns.getValue("PACKAGE_NAME")
        val op = columns.getValue("OP_NAME")
        val time = columns.getValue("ACCESS_TIME")
        val accesses = columns["ACCESS_COUNTS"]
        val rejects = columns["REJECT_COUNTS"]
        val duration = columns["DURATION"]
        val state = columns["UID_STATE"]
        val flags = columns["OP_FLAGS"]
        val tag = columns["ATTRIBUTION_TAG"]
        val width = columns.size

        val result = ArrayList<AppOpAccess>()
        for (line in lines.subList(headerIndex + 1, lines.size)) {
            // The table ends at the first blank line; whatever a later section prints is not it.
            if (line.isBlank()) break
            // A flag set printed with `|` would split into one field too many; such a
            // row cannot be told apart from a garbled one, so it goes with the garbled.
            val fields = line.split('|')
            if (fields.size != width) continue
            val uidValue = fields[uid].trim().toIntOrNull() ?: continue
            val timeValue = time(fields[time].trim(), zone) ?: continue
            val opValue = fields[op].trim()
            if (opValue.isEmpty()) continue
            // A missing counter is 0; a negative one (−1 for "none") is 0 as well.
            val durationValue = (duration?.let { fields[it].trim().toLongOrNull() } ?: 0L).coerceAtLeast(0L)
            // A layout without a count column prints one row per allowed use, and a row
            // with a length is that use's length, as in the layouts that count.
            val accessValue = accesses?.let { fields[it].trim().toIntOrNull()?.coerceAtLeast(0) }
                ?: if (durationValue > 0L) 0 else 1
            result += AppOpAccess(
                uid = uidValue,
                packageName = fields[packageName].trim(),
                op = opValue,
                attributionTag = tag?.let { tagOrNull(fields[it]) },
                timeMillis = timeValue,
                accessCount = accessValue,
                rejectCount = (rejects?.let { fields[it].trim().toIntOrNull() } ?: 0).coerceAtLeast(0),
                durationMillis = durationValue,
                uidState = state?.let { UidState.of(fields[it].trim()) } ?: UidState.UNKNOWN,
                flags = flags?.let { OpFlag.parse(fields[it]) } ?: emptySet(),
            )
        }
        return result
    }

    private fun tree(lines: List<String>, zone: ZoneId): List<AppOpAccess> {
        val result = ArrayList<AppOpAccess>()
        var uid: Int? = null
        var packageName: String? = null
        var op: String? = null
        var tag: String? = null
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val uidMatch = TREE_UID.matchEntire(line)
            val packageMatch = TREE_PACKAGE.matchEntire(line)
            val attributionMatch = TREE_ATTRIBUTION.matchEntire(line)
            val accessMatch = TREE_ACCESS.matchEntire(line)
            when {
                uidMatch != null -> {
                    uid = uidMatch.groupValues[1].toIntOrNull()
                    packageName = null
                    op = null
                    tag = null
                }
                packageMatch != null -> {
                    packageName = packageMatch.groupValues[1]
                    op = null
                    tag = null
                }
                attributionMatch != null -> tag = tagOrNull(attributionMatch.groupValues[1])
                accessMatch != null -> access(accessMatch, uid, packageName, op, tag, zone)?.let(result::add)
                TREE_OP.matches(line) -> {
                    op = line
                    tag = null
                }
                // Anything else ("Largest chain id", a device line, a later section) is passed over.
            }
        }
        return result
    }

    private fun access(
        match: MatchResult,
        uid: Int?,
        packageName: String?,
        op: String?,
        tag: String?,
        zone: ZoneId,
    ): AppOpAccess? {
        if (uid == null || packageName == null || op == null) return null
        val timeMillis = time(match.groupValues[3], zone) ?: return null
        val duration = match.groupValues[4].toLongOrNull()
        return AppOpAccess(
            uid = uid,
            packageName = packageName,
            op = op,
            attributionTag = tag,
            timeMillis = timeMillis,
            // One line per allowed access; a line with a duration is the same access
            // told again with its length, as the table does it.
            accessCount = if (duration == null) 1 else 0,
            rejectCount = 0,
            durationMillis = duration ?: 0L,
            uidState = UidState.of(match.groupValues[1]),
            flags = OpFlag.parse(match.groupValues[2]),
        )
    }

    private fun tagOrNull(text: String): String? = text.trim().let { if (it.isEmpty() || it == "null") null else it }

    private fun time(text: String, zone: ZoneId): Long? = try {
        LocalDateTime.parse(text, TIME).atZone(zone).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }

    /**
     * The system formats the times in the phone's own locale; a locale with its own
     * digits (Persian, Bengali, Burmese, …) prints them, and the pattern above reads
     * only ASCII ones. Any digit of any script becomes its ASCII twin.
     */
    internal fun asciiDigits(line: String): String {
        if (line.all { it < '\u0080' }) return line
        val out = StringBuilder(line.length)
        for (c in line) {
            val digit = if (c > '\u007F') Character.digit(c, 10) else -1
            out.append(if (digit in 0..9) ('0' + digit) else c)
        }
        return out.toString()
    }
}
