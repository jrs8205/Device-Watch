package org.jarsi.devicewatch.quick

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.jarsi.devicewatch.data.DataBreakdown
import org.jarsi.devicewatch.data.DataUsageSince
import org.jarsi.devicewatch.data.DeviceInfo
import org.jarsi.devicewatch.data.DeviceState
import org.jarsi.devicewatch.data.MonthlyDataUsage
import org.jarsi.devicewatch.data.StorageBreakdown
import org.jarsi.devicewatch.data.SystemStats
import org.jarsi.devicewatch.data.SystemStatsRepository
import org.jarsi.devicewatch.data.startOfTodayMillis
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class QuickStatsSourceTest {

    private val zone = ZoneId.of("Europe/Helsinki")
    private var clock = 0L
    private val repo = FakeStatsRepository { clock }
    private val source = QuickStatsSource(repo, now = { clock }, today = { LocalDate.of(2026, 10, 25) }, zone = { zone })

    @Test
    fun `a fresh latest reading is reused`() = runTest {
        repo.latestStats.value = quickStats(readAtElapsedMillis = 10_000)
        clock = 12_000
        source.stats()
        assertThat(repo.getStatsCalls).isEqualTo(0)
    }

    @Test
    fun `a stale latest reading is read anew`() = runTest {
        repo.latestStats.value = quickStats(readAtElapsedMillis = 1_000)
        clock = 12_000
        source.stats()
        assertThat(repo.getStatsCalls).isEqualTo(1)
    }

    @Test
    fun `tiles opened together share one read`() = runTest {
        clock = 50_000
        List(8) { async { source.stats() } }.awaitAll()
        assertThat(repo.getStatsCalls).isEqualTo(1)
    }

    @Test
    fun `data today is counted from local midnight and cached`() = runTest {
        source.dataToday()
        source.dataToday()
        assertThat(repo.dataSinceStarts).containsExactly(startOfTodayMillis(LocalDate.of(2026, 10, 25), zone))
    }

    @Test
    fun `data today without usage access is null`() = runTest {
        repo.usage = DataUsageSince(wifiGb = -1.0, mobileGb = -1.0)
        assertThat(source.dataToday()).isNull()
    }

    private class FakeStatsRepository(private val clock: () -> Long) : SystemStatsRepository {
        override val latestStats = MutableStateFlow<SystemStats?>(null)
        var getStatsCalls = 0
        var usage = DataUsageSince(wifiGb = 1.1, mobileGb = 0.3)
        val dataSinceStarts = mutableListOf<Long>()

        override suspend fun getStats(): SystemStats {
            getStatsCalls++
            delay(100)
            return quickStats(readAtElapsedMillis = clock()).also { latestStats.value = it }
        }

        override suspend fun dataUsedSince(startMillis: Long): DataUsageSince {
            dataSinceStarts += startMillis
            return usage
        }

        override suspend fun getDeviceInfo(): DeviceInfo = error("unused")
        override suspend fun monthlyDataUsage(monthsBack: Int): List<MonthlyDataUsage> = error("unused")
        override suspend fun dataBreakdown(startMillis: Long): DataBreakdown = error("unused")
        override suspend fun storageBreakdown(): StorageBreakdown? = error("unused")
        override suspend fun deviceState(): DeviceState = error("unused")
    }
}
