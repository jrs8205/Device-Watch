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
        history.recordStorageUsed(day, 80 * gib, readAtMillis = 2_000L)
        history.recordStorageUsed(day, 64 * gib, readAtMillis = 1_000L)

        assertThat(storedBytes()).isEqualTo(80 * gib)
    }

    @Test
    fun `a newer storage reading replaces the day's value`() {
        history.recordStorageUsed(day, 64 * gib, readAtMillis = 1_000L)
        history.recordStorageUsed(day, 80 * gib, readAtMillis = 2_000L)

        assertThat(storedBytes()).isEqualTo(80 * gib)
    }
}
