package org.jarsi.devicewatch.quick

import android.os.SystemClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jarsi.devicewatch.data.DataToday
import org.jarsi.devicewatch.data.SystemStats
import org.jarsi.devicewatch.data.SystemStatsRepository
import org.jarsi.devicewatch.data.dataTodayOf
import org.jarsi.devicewatch.data.startOfTodayMillis
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the tiles and the single-figure widget get their figures. Eight tiles
 * open in the panel at once must not mean eight passes over the device, so a
 * reading younger than [FRESH_MS] (the monitor's own 5-second one, usually) is
 * shared, and callers arriving together wait for the one read under way.
 */
@Singleton
class QuickStatsSource(
    private val repository: SystemStatsRepository,
    private val now: () -> Long,
    private val today: () -> LocalDate,
    private val zone: () -> ZoneId,
) {
    @Inject
    constructor(repository: SystemStatsRepository) :
        this(repository, SystemClock::elapsedRealtime, LocalDate::now, ZoneId::systemDefault)

    private val statsMutex = Mutex()
    private val dataMutex = Mutex()
    private var dataToday: DataToday? = null
    private var dataTodayDay: LocalDate? = null
    private var dataTodayReadAt = 0L

    suspend fun stats(): SystemStats = statsMutex.withLock {
        repository.latestStats.value?.takeIf { now() - it.readAtElapsedMillis < FRESH_MS }
            ?: repository.getStats()
    }

    /**
     * Mobile and Wi-Fi data since local midnight, whatever period the data counter
     * uses; null without usage access. Read only by a surface showing it, and kept
     * for [DATA_TODAY_MS] since it costs two queries of Android's network stats.
     */
    suspend fun dataToday(): DataToday? = dataMutex.withLock {
        val day = today()
        val cached = dataTodayDay == day && dataTodayReadAt != 0L && now() - dataTodayReadAt < DATA_TODAY_MS
        if (!cached) {
            dataToday = dataTodayOf(repository.dataUsedSince(startOfTodayMillis(day, zone())))
            dataTodayDay = day
            dataTodayReadAt = now().coerceAtLeast(1L)
        }
        dataToday
    }

    private companion object {
        const val FRESH_MS = 4_000L
        const val DATA_TODAY_MS = 15_000L
    }
}
