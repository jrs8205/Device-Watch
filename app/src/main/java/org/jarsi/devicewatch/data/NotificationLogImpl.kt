package org.jarsi.devicewatch.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Daily line files under filesDir/notif_log ("epochDay.log"). Appends are
 * synchronized and tiny; retention (7 days) is enforced on every append so no
 * separate cleanup pass is needed. Corrupted lines are skipped on read.
 */
@Singleton
class NotificationLogImpl internal constructor(
    private val baseDir: File,
    private val clock: () -> LocalDate,
) : NotificationLog {

    @Inject
    constructor(@ApplicationContext context: Context) :
        this(File(context.filesDir, "notif_log"), LocalDate::now)

    /** Lines already in today's file; -1 = unknown until the first append counts them. */
    private var todayLineCount = -1
    private var countedFileName: String? = null

    @Synchronized
    override fun append(entry: NotificationLogEntry) {
        val today = clock()
        baseDir.mkdirs()
        purge(today)
        val file = File(baseDir, NotificationLogCodec.fileNameFor(today))
        if (countedFileName != file.name || todayLineCount < 0) {
            todayLineCount = if (file.exists()) file.readLines().size else 0
            countedFileName = file.name
        }
        if (todayLineCount >= NotificationLogCodec.MAX_ENTRIES_PER_DAY) return
        file.appendText(NotificationLogCodec.encode(entry) + "\n")
        todayLineCount += 1
    }

    @Synchronized
    override fun entriesNewestFirst(): List<NotificationLogEntry> {
        val files = baseDir.listFiles() ?: return emptyList()
        return files
            .filter { NotificationLogCodec.isRetainedFileName(it.name, clock()) }
            .sortedByDescending { it.name.removeSuffix(".log").toLongOrNull() ?: Long.MIN_VALUE }
            .flatMap { file ->
                file.readLines().asReversed().mapNotNull(NotificationLogCodec::decodeOrNull)
            }
            .sortedByDescending { it.timeMillis }
    }

    private fun purge(today: LocalDate) {
        baseDir.listFiles()?.forEach { file ->
            if (!NotificationLogCodec.isRetainedFileName(file.name, today)) file.delete()
        }
    }
}
