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

    @Synchronized
    override fun record(sample: BatterySample) {
        val today = clock()
        baseDir.mkdirs()
        purge(today)
        val previous = (lastSample ?: latestStored(today)).also { lastSample = it }
        if (!BatteryHistoryCodec.shouldSample(previous, sample)) return
        File(baseDir, BatteryHistoryCodec.fileNameFor(today))
            .appendText(BatteryHistoryCodec.encode(sample) + "\n")
        lastSample = sample
    }

    @Synchronized
    override fun samplesSince(sinceMillis: Long): List<BatterySample> =
        retainedFiles(clock())
            .flatMap { file -> file.readLines().mapNotNull(BatteryHistoryCodec::decodeOrNull) }
            .filter { it.timeMillis >= sinceMillis }
            .sortedBy { it.timeMillis }

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
