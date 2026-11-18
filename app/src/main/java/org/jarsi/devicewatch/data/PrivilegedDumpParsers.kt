package org.jarsi.devicewatch.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** One sensor of the thermal HAL as `dumpsys thermalservice` lists it. */
data class HalTemperature(val type: Int, val name: String, val celsius: Double)

/** Reads the temperatures out of `dumpsys thermalservice`, which a [PrivilegedShell] may run. */
internal object ThermalServiceParser {

    // android.os.Temperature's types.
    const val TYPE_CPU = 0
    const val TYPE_GPU = 1
    const val TYPE_SKIN = 3

    private const val CURRENT = "Current temperatures from HAL:"
    private const val CACHED = "Cached temperatures:"
    private val reading = Regex("""Temperature\{mValue=(-?[\d.]+), mType=(-?\d+), mName=([^,}]+)""")

    /** The readings taken for this dump; the service's cache from earlier only when it has none. */
    fun parse(dump: String): List<HalTemperature> =
        section(dump, CURRENT).ifEmpty { section(dump, CACHED) }

    private fun section(dump: String, heading: String): List<HalTemperature> =
        dump.lineSequence()
            .dropWhile { it.trim() != heading }
            .drop(1)
            .takeWhile { it.trimStart().startsWith("Temperature{") }
            .mapNotNull { line ->
                val match = reading.find(line) ?: return@mapNotNull null
                val celsius = match.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
                val type = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                HalTemperature(type, match.groupValues[3], celsius)
            }
            .toList()

    /** The hottest plausible sensor of [type]; unused sensors read zero or below. */
    fun hottest(readings: List<HalTemperature>, type: Int): Double? =
        readings.filter { it.type == type && it.celsius in 1.0..125.0 }.maxOfOrNull { it.celsius }
}

/** What Samsung's battery service keeps about the battery itself, beyond the standard fields. */
data class SamsungBattery(
    /** Samsung's own state-of-health figure, in percent of the design capacity. */
    val healthPercent: Int?,
    val cycles: Int?,
    val firstUse: LocalDate?,
    val manufactured: LocalDate?,
)

/** Reads those out of `dumpsys battery` on a Samsung phone. */
internal object SamsungBatteryParser {

    private val asoc = Regex("""mSavedBatteryAsoc: \[(-?\d+)""")
    private val usage = Regex("""mSavedBatteryUsage: \[(-?\d+)""")
    private val firstUse = Regex("""battery FirstUseDate: \[(\d{8})""")
    // The factory's dates for the battery: of manufacture where given, of calibration otherwise.
    private val made = Regex("""LLB MAN: (\d{8})""")
    private val calibrated = Regex("""LLB CAL: (\d{8})""")
    private val date = DateTimeFormatter.BASIC_ISO_DATE

    /** Null when the dump has none of Samsung's fields: another maker's phone. */
    fun parse(dump: String): SamsungBattery? {
        val battery = SamsungBattery(
            healthPercent = asoc.find(dump)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..100 },
            // Counted in percent discharged: a hundred of them make a cycle.
            cycles = usage.find(dump)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it >= 0 }?.let { it / 100 },
            firstUse = firstUse.find(dump)?.groupValues?.get(1)?.let(::dateOrNull),
            manufactured = (made.find(dump) ?: calibrated.find(dump))?.groupValues?.get(1)?.let(::dateOrNull),
        )
        return battery.takeIf {
            it.healthPercent != null || it.cycles != null || it.firstUse != null || it.manufactured != null
        }
    }

    private fun dateOrNull(text: String): LocalDate? = try {
        LocalDate.parse(text, date).takeIf { it.year >= 2000 }
    } catch (_: DateTimeParseException) {
        null
    }
}

/**
 * The battery's health as Android itself keeps it, from Android 14: given only
 * to a holder of BATTERY_STATS, which an app gets from `pm grant` alone.
 */
internal data class AndroidBatteryHealth(
    val healthPercent: Int?,
    val firstUse: LocalDate?,
    val manufactured: LocalDate?,
)

/** Small readings that need no parser of their own. */
internal object PrivilegedReadings {

    /**
     * Android's own battery health, asked through [property] (the battery manager's
     * long properties). Null before Android 14, where the figures do not exist, and
     * when Android refuses them. A phone that keeps a figure gives a real value:
     * zero, a negative number and the "unsupported" minimum are no figure.
     */
    fun androidBatteryHealth(sdkInt: Int, property: (Int) -> Long): AndroidBatteryHealth? {
        if (sdkInt < ANDROID_14) return null
        return try {
            AndroidBatteryHealth(
                healthPercent = property(BATTERY_PROPERTY_STATE_OF_HEALTH).takeIf { it in 1L..100L }?.toInt(),
                firstUse = epochSecondsDate(property(BATTERY_PROPERTY_FIRST_USAGE_DATE)),
                manufactured = epochSecondsDate(property(BATTERY_PROPERTY_MANUFACTURING_DATE)),
            )
        } catch (_: SecurityException) {
            null
        }
    }

    /** "12 %", "12" or "12%", as the graphics drivers variously print their load. */
    fun gpuLoadPercent(raw: String?): Int? =
        raw?.trim()?.removeSuffix("%")?.trim()?.toIntOrNull()?.takeIf { it in 0..100 }

    /** A date a kernel node gives as seconds since 1970, as Pixels do for the battery's dates. */
    fun epochSecondsDate(raw: String?): LocalDate? = raw?.trim()?.toLongOrNull()?.let(::epochSecondsDate)

    // Bounded to this century: a node holding garbage must not overflow the date.
    private fun epochSecondsDate(seconds: Long): LocalDate? =
        seconds.takeIf { it in MIN_DATE_SECONDS..MAX_DATE_SECONDS }?.let { LocalDate.ofEpochDay(it / 86_400L) }

    /** `settings get global …` as the shell prints it: `1`, `0`, or `null` for a key never set. */
    fun settingsFlag(raw: String?): Boolean? = when (raw?.trim()) {
        "1" -> true
        "0" -> false
        else -> null
    }

    private const val ANDROID_14 = 34

    // The battery manager's own ids for these, which its public constants leave out.
    private const val BATTERY_PROPERTY_MANUFACTURING_DATE = 7
    private const val BATTERY_PROPERTY_FIRST_USAGE_DATE = 8
    private const val BATTERY_PROPERTY_STATE_OF_HEALTH = 10

    private const val MIN_DATE_SECONDS = 946_684_800L // 2000-01-01
    private const val MAX_DATE_SECONDS = 4_102_444_800L // 2100-01-01
}
