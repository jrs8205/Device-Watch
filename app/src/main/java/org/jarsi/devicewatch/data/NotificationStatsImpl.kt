package org.jarsi.devicewatch.data

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SharedPreferences-backed store for the notification day counter. The dataset is
 * tiny (a few hundred ints, retention today + yesterday), and the listener needs
 * synchronous increments from binder callbacks — the same trade-off as
 * [AppSettingsRepositoryImpl].
 */
@Singleton
class NotificationStatsImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : NotificationStats {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun totalForDay(day: LocalDate): Int = prefs.getInt(totalKey(day), 0)

    override fun countForPackage(packageName: String, day: LocalDate): Int =
        prefs.getInt(packageKey(packageName, day), 0)

    override fun totalBetween(start: LocalDate, end: LocalDate): Int {
        var day = start
        var sum = 0
        while (!day.isAfter(end)) {
            sum += totalForDay(day)
            day = day.plusDays(1)
        }
        return sum
    }

    override fun increment(packageName: String, day: LocalDate) {
        prefs.edit()
            .putInt(totalKey(day), totalForDay(day) + 1)
            .putInt(packageKey(packageName, day), countForPackage(packageName, day) + 1)
            .apply()
    }

    override fun purge(today: LocalDate) {
        val retained = NotificationCounting.retainedDays(today)
        val stale = prefs.all.keys.filterNot { NotificationCounting.isRetainedStatsKey(it, retained) }
        if (stale.isEmpty()) return
        prefs.edit().apply { stale.forEach(::remove) }.apply()
    }

    override fun isListenerEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    private fun totalKey(day: LocalDate) = "total:${day.toEpochDay()}"

    private fun packageKey(packageName: String, day: LocalDate) =
        "pkg:${day.toEpochDay()}:$packageName"

    companion object {
        const val PREFS_NAME = "notification_stats"
    }
}
