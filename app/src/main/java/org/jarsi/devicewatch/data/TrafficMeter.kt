package org.jarsi.devicewatch.data

import android.net.TrafficStats
import android.os.SystemClock
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToLong

/** Device-wide byte counters since boot, read at [elapsedMillis]. */
data class TrafficCounters(val elapsedMillis: Long, val rxBytes: Long, val txBytes: Long)

data class TrafficRate(val downBytesPerSecond: Long, val upBytesPerSecond: Long)

/** The live meter: the latest rate and the last minute of them, oldest first. */
data class LiveTraffic(val history: List<TrafficRate> = emptyList()) {
    val latest: TrafficRate? get() = history.lastOrNull()
    val peakDownBytesPerSecond: Long get() = history.maxOfOrNull { it.downBytesPerSecond } ?: 0L
    val peakUpBytesPerSecond: Long get() = history.maxOfOrNull { it.upBytesPerSecond } ?: 0L
}

object TrafficMeter {

    const val HISTORY_SIZE = 60

    /** Null when the counters went backwards (an interface restarted) or no time passed. */
    fun rate(previous: TrafficCounters, current: TrafficCounters): TrafficRate? {
        val millis = current.elapsedMillis - previous.elapsedMillis
        val down = current.rxBytes - previous.rxBytes
        val up = current.txBytes - previous.txBytes
        if (millis <= 0L || down < 0L || up < 0L) return null
        return TrafficRate(
            downBytesPerSecond = (down * 1000.0 / millis).roundToLong(),
            upBytesPerSecond = (up * 1000.0 / millis).roundToLong(),
        )
    }

    fun next(traffic: LiveTraffic, rate: TrafficRate): LiveTraffic =
        LiveTraffic((traffic.history + rate).takeLast(HISTORY_SIZE))

    /** "85 kb/s" or "1,2 Mb/s", in bits like a connection's advertised speed. */
    fun rateText(bytesPerSecond: Long, locale: Locale = Locale.getDefault()): String {
        val bits = bytesPerSecond.coerceAtLeast(0L) * 8
        if (bits < 1_000_000L) return "${(bits / 1000.0).roundToLong()} kb/s"
        val megabits = bits / 1_000_000.0
        return if (megabits < 10.0) {
            String.format(locale, "%.1f Mb/s", megabits)
        } else {
            String.format(locale, "%.0f Mb/s", megabits)
        }
    }
}

/** Where the meter's counters come from; a fake in tests. */
fun interface TrafficCounterSource {
    /** The counters now, or null where the device does not report them. */
    fun read(): TrafficCounters?
}

class TrafficStatsCounterSource @Inject constructor() : TrafficCounterSource {
    override fun read(): TrafficCounters? {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) return null
        return TrafficCounters(SystemClock.elapsedRealtime(), rx, tx)
    }
}
