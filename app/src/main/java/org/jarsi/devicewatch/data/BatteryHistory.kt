package org.jarsi.devicewatch.data

import java.time.LocalDate
import kotlin.math.abs

/** One battery reading. Everything stays on the device. */
data class BatterySample(val timeMillis: Long, val level: Int, val charging: Boolean)

/** Rolling on-device history of battery levels, for the discharge chart. */
interface BatteryHistory {
    /** Appends [sample] if it passes [BatteryHistoryCodec.shouldSample] against the latest stored sample. */
    fun record(sample: BatterySample)

    /** Samples with timeMillis >= sinceMillis, ascending. */
    fun samplesSince(sinceMillis: Long): List<BatterySample>
}

/**
 * Line format for the daily history files: tab-separated
 * `v1<TAB>millis<TAB>level<TAB>0|1`. Every field is numeric, so nothing needs
 * escaping and one sample is always exactly one line. TSV instead of JSON
 * because org.json is not available to plain-JVM unit tests.
 */
object BatteryHistoryCodec {

    const val RETENTION_DAYS = 14L
    const val MIN_SAMPLE_INTERVAL_MS = 5L * 60 * 1000 // throttle ACTION_BATTERY_CHANGED spam
    const val MIN_LEVEL_DELTA = 1

    /** A level change is only worth its own sample once this much time has passed. */
    private const val MIN_LEVEL_CHANGE_INTERVAL_MS = 60L * 1000

    /** A charging-state flip jumps the queue, but not often enough for charger contact bounce to spam. */
    private const val MIN_FLIP_INTERVAL_MS = 30L * 1000

    /** Battery level is a percentage; anything else in a file is corruption. */
    private val LEVEL_RANGE = 0..100

    fun encode(sample: BatterySample): String = listOf(
        "v1",
        sample.timeMillis.toString(),
        sample.level.toString(),
        if (sample.charging) "1" else "0",
    ).joinToString("\t")

    fun decodeOrNull(line: String): BatterySample? {
        val parts = line.split('\t')
        if (parts.size != 4 || parts[0] != "v1") return null
        val millis = parts[1].toLongOrNull() ?: return null
        val level = parts[2].toIntOrNull()?.takeIf { it in LEVEL_RANGE } ?: return null
        val charging = when (parts[3]) {
            "1" -> true
            "0" -> false
            else -> return null
        }
        return BatterySample(timeMillis = millis, level = level, charging = charging)
    }

    fun fileNameFor(day: LocalDate): String = "${day.toEpochDay()}.log"

    fun isRetainedFileName(name: String, today: LocalDate): Boolean {
        val epochDay = name.removeSuffix(".log").toLongOrNull() ?: return false
        if (!name.endsWith(".log")) return false
        val age = today.toEpochDay() - epochDay
        return age in 0 until RETENTION_DAYS
    }

    /** True when [candidate] should be stored given the [previous] stored sample (null = always). */
    fun shouldSample(previous: BatterySample?, candidate: BatterySample): Boolean {
        if (candidate.level < 0) return false
        if (previous == null) return true
        val elapsed = candidate.timeMillis - previous.timeMillis
        if (previous.charging != candidate.charging) return elapsed >= MIN_FLIP_INTERVAL_MS
        if (elapsed >= MIN_SAMPLE_INTERVAL_MS) return true
        return abs(candidate.level - previous.level) >= MIN_LEVEL_DELTA &&
            elapsed >= MIN_LEVEL_CHANGE_INTERVAL_MS
    }
}
