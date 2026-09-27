package org.jarsi.devicewatch.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Daily line files under filesDir/battery_log ("epochDay.log"). Writes are
 * synchronized and tiny; retention (14 days) is enforced on every record so no
 * separate cleanup pass is needed. Corrupted lines are skipped on read.
 */
@Singleton
class BatteryHistoryImpl internal constructor(
    private val baseDir: File,
    private val clock: () -> LocalDate,
) : BatteryHistory {

    @Inject
    constructor(@ApplicationContext context: Context) :
        this(File(context.filesDir, "battery_log"), LocalDate::now)

    /** Latest stored sample; null until the first record reads it back from disk. */
    private var lastSample: BatterySample? = null

    /**
     * A plug or unplug that came too soon after the latest stored sample to tell
     * from charger bounce. Dropping it lost the end of a charge unplugged right
     * after a stored sample: with no charger, the next sample can be many minutes
     * and levels later. Held in memory only; a restart loses it, as before.
     */
    private var heldFlip: BatterySample? = null

    @Synchronized
    override fun record(sample: BatterySample) {
        if (sample.level < 0) return
        val today = clock()
        baseDir.mkdirs()
        purge(today)
        var previous = (lastSample ?: latestStored(today)).also { lastSample = it }
        heldFlip?.let { held ->
            heldFlip = null
            // The new state held: a real plug or unplug. A flip back was bounce.
            if (sample.charging == held.charging) {
                append(today, held)
                previous = held
            }
        }
        if (BatteryHistoryCodec.shouldSample(previous, sample)) {
            append(today, sample)
        } else if (BatteryHistoryCodec.isHeldFlip(previous, sample)) {
            heldFlip = sample
        }
    }

    /** Includes a held plug or unplug: it is the latest state seen, confirmed or not. */
    @Synchronized
    override fun samplesSince(sinceMillis: Long): List<BatterySample> =
        (retainedFiles(clock()).flatMap { file -> file.readLines().mapNotNull(BatteryHistoryCodec::decodeOrNull) } +
            listOfNotNull(heldFlip))
            .filter { it.timeMillis >= sinceMillis }
            .sortedBy { it.timeMillis }

    private fun append(today: LocalDate, sample: BatterySample) {
        File(baseDir, BatteryHistoryCodec.fileNameFor(today))
            .appendText(BatteryHistoryCodec.encode(sample) + "\n")
        lastSample = sample
    }

    private fun latestStored(today: LocalDate): BatterySample? =
        retainedFiles(today).lastOrNull()
            ?.readLines()
            ?.asReversed()
            ?.firstNotNullOfOrNull(BatteryHistoryCodec::decodeOrNull)

    /** Retained day files, oldest day first. */
    private fun retainedFiles(today: LocalDate): List<File> =
        baseDir.listFiles().orEmpty()
            .filter { BatteryHistoryCodec.isRetainedFileName(it.name, today) }
            .sortedBy { it.name.removeSuffix(".log").toLongOrNull() ?: Long.MIN_VALUE }

    private fun purge(today: LocalDate) {
        baseDir.listFiles()?.forEach { file ->
            if (!BatteryHistoryCodec.isRetainedFileName(file.name, today)) file.delete()
        }
    }
}
