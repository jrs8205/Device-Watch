package org.jarsi.devicewatch.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class UsageHistoryImplTest {

    private val history = UsageHistoryImpl(ApplicationProvider.getApplicationContext())
    private val day = LocalDate.of(2026, 9, 27)
    private val gib = 1024L * 1024 * 1024

    private fun storedBytes() = history.dailyTallies(day, day).single().storageUsedBytes

    @Test
    fun `an older storage reading never replaces a newer one`() {
        // Codex round 8: the Home refresh read 64 GiB, the service then stored a
        // newer 80 GiB, and the refresh's late write put 64 GiB back.
        history.recordStorageUsed(day, 80 * gib, readAtElapsedMillis = 2_000L)
        history.recordStorageUsed(day, 64 * gib, readAtElapsedMillis = 1_000L)

        assertThat(storedBytes()).isEqualTo(80 * gib)
    }

    @Test
    fun `after a restart the first reading is taken, whatever the clock says`() {
        // Codex round 9: a wall-clock stamp kept on disk refused every later reading
        // once the clock was set back. The order now comes from the monotonic clock,
        // which starts over at boot, so nothing of it may outlive the process.
        history.recordStorageUsed(day, 80 * gib, readAtElapsedMillis = 5_000_000L)

        val restarted = UsageHistoryImpl(ApplicationProvider.getApplicationContext())
        restarted.recordStorageUsed(day, 64 * gib, readAtElapsedMillis = 1_000L)

        assertThat(restarted.dailyTallies(day, day).single().storageUsedBytes).isEqualTo(64 * gib)
    }

    @Test
    fun `a newer storage reading replaces the day's value`() {
        history.recordStorageUsed(day, 64 * gib, readAtElapsedMillis = 1_000L)
        history.recordStorageUsed(day, 80 * gib, readAtElapsedMillis = 2_000L)

        assertThat(storedBytes()).isEqualTo(80 * gib)
    }
}
