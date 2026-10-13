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
 *   any new column change nothing.
 * - Android 12 to 15: an indented tree, `Uid:` over `Package:` over the operation's
 *   name over `Attribution:` over one `Access […] at …` line per event.
 *
 * Android 11 and older keep no such record, and their dump has neither shape: the
 * parser answers null, and the page says so. Times in the dump are the phone's
 * local time without a zone, so they are read in [ZoneId.systemDefault] unless
 * told otherwise. Anything that does not fit is skipped, never guessed at.
 */
internal object AppOpsHistoryParser {

    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    private val TABLE_REQUIRED = setOf("UID", "PACKAGE_NAME", "OP_NAME", "ACCESS_TIME", "ACCESS_COUNTS")
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
        val lines = output.lines()
        val header = lines.indexOfFirst { isTableHeader(it) }
        if (header >= 0) return AppOpsHistory(table(lines, header, zone))
        val tree = lines.indexOfFirst { it.trimStart().startsWith(TREE_HEADER) }
        if (tree >= 0) return AppOpsHistory(tree(lines.subList(tree + 1, lines.size), zone))
        return null
    }

    /** The header names its columns; which comes first is its business. */
    private fun isTableHeader(line: String): Boolean {
        if (!line.contains('|')) return false
        val names = line.split('|').map { it.trim() }.toSet()
        return TABLE_REQUIRED.all { it in names }
    }

    private fun table(lines: List<String>, headerIndex: Int, zone: ZoneId): List<AppOpAccess> {
        val columns = lines[headerIndex].split('|').withIndex().associate { (index, name) -> name.trim() to index }
        val uid = columns["UID"] ?: return emptyList()
        val packageName = columns["PACKAGE_NAME"] ?: return emptyList()
        val op = columns["OP_NAME"] ?: return emptyList()
        val time = columns["ACCESS_TIME"] ?: return emptyList()
        val accesses = columns["ACCESS_COUNTS"] ?: return emptyList()
        val rejects = columns["REJECT_COUNTS"]
        val duration = columns["DURATION"]
        val state = columns["UID_STATE"]
        val flags = columns["OP_FLAGS"]
        val tag = columns["ATTRIBUTION_TAG"]
        val width = columns.size

        val result = ArrayList<AppOpAccess>()
        for (line in lines.subList(headerIndex + 1, lines.size)) {
            // A flag set printed with `|` would split into one field too many; such a
            // row cannot be told apart from a garbled one, so it goes with the garbled.
            val fields = line.split('|')
            if (fields.size != width) continue
            val uidValue = fields[uid].trim().toIntOrNull() ?: continue
            val timeValue = time(fields[time].trim(), zone) ?: continue
            val opValue = fields[op].trim()
            if (opValue.isEmpty()) continue
            result += AppOpAccess(
                uid = uidValue,
                packageName = fields[packageName].trim(),
                op = opValue,
                attributionTag = tag?.let { tagOrNull(fields[it]) },
                timeMillis = timeValue,
                accessCount = fields[accesses].trim().toIntOrNull() ?: 0,
                rejectCount = rejects?.let { fields[it].trim().toIntOrNull() } ?: 0,
                durationMillis = duration?.let { fields[it].trim().toLongOrNull() } ?: 0L,
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
}
