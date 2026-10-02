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

/** Small readings that need no parser of their own. */
internal object PrivilegedReadings {

    /** "12 %", "12" or "12%", as the graphics drivers variously print their load. */
    fun gpuLoadPercent(raw: String?): Int? =
        raw?.trim()?.removeSuffix("%")?.trim()?.toIntOrNull()?.takeIf { it in 0..100 }

    /** A date a kernel node gives as seconds since 1970, as Pixels do for the battery's dates. */
    fun epochSecondsDate(raw: String?): LocalDate? {
        // Bounded to this century: a node holding garbage must not overflow the date.
        val seconds = raw?.trim()?.toLongOrNull()?.takeIf { it in MIN_DATE_SECONDS..MAX_DATE_SECONDS } ?: return null
        return LocalDate.ofEpochDay(seconds / 86_400L)
    }

    private const val MIN_DATE_SECONDS = 946_684_800L // 2000-01-01
    private const val MAX_DATE_SECONDS = 4_102_444_800L // 2100-01-01
}
