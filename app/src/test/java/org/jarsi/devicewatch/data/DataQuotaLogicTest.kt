package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DataQuotaLogicTest {

    private val gb = 1024L * 1024 * 1024

    @Test
    fun `a reading taken under another quota is stale`() {
        // The quota was raised while the stats were being read: the old reading
        // must not latch alerts for the new quota.
        assertThat(DataQuotaLogic.readingIsCurrent(readingQuotaGb = 10.0, currentQuotaGb = 20.0)).isFalse()
        assertThat(DataQuotaLogic.readingIsCurrent(readingQuotaGb = 10.0, currentQuotaGb = 10.0)).isTrue()
    }

    @Test
    fun `bytes to the next threshold points at the warning level while under it`() {
        val bytes = DataQuotaLogic.bytesToNextThreshold(
            quotaGb = 10.0, usedGb = 2.0, notified80 = false, notified100 = false
        )

        assertThat(bytes).isEqualTo(6 * gb)
    }

    @Test
    fun `bytes to the next threshold skips a level already passed or already notified`() {
        // 9 of 10 GB used: the 80 % alert is the caller's job right now, the
        // callback must watch for the limit itself.
        assertThat(
            DataQuotaLogic.bytesToNextThreshold(
                quotaGb = 10.0, usedGb = 9.0, notified80 = false, notified100 = false
            )
        ).isEqualTo(1 * gb)
        assertThat(
            DataQuotaLogic.bytesToNextThreshold(
                quotaGb = 10.0, usedGb = 5.0, notified80 = true, notified100 = false
            )
        ).isEqualTo(5 * gb)
    }

    @Test
    fun `bytes to the next threshold is null when nothing is left to watch`() {
        assertThat(
            DataQuotaLogic.bytesToNextThreshold(
                quotaGb = 10.0, usedGb = 10.5, notified80 = true, notified100 = true
            )
        ).isNull()
        assertThat(
            DataQuotaLogic.bytesToNextThreshold(
                quotaGb = 0.0, usedGb = 1.0, notified80 = false, notified100 = false
            )
        ).isNull()
        assertThat(
            DataQuotaLogic.bytesToNextThreshold(
                quotaGb = 10.0, usedGb = UNAVAILABLE_DOUBLE, notified80 = false, notified100 = false
            )
        ).isNull()
    }

    @Test
    fun `given no quota, when evaluating, then nothing is pending`() {
        val pending = DataQuotaLogic.pendingThresholds(
            quotaGb = 0.0, usedGb = 12.0, notified80 = false, notified100 = false
        )

        assertThat(pending).isEmpty()
        assertThat(DataQuotaLogic.percentUsed(quotaGb = 0.0, usedGb = 12.0)).isNull()
    }

    @Test
    fun `given usage is unavailable, when evaluating, then nothing is pending`() {
        val pending = DataQuotaLogic.pendingThresholds(
            quotaGb = 10.0, usedGb = UNAVAILABLE_DOUBLE, notified80 = false, notified100 = false
        )

        assertThat(pending).isEmpty()
        assertThat(DataQuotaLogic.percentUsed(quotaGb = 10.0, usedGb = UNAVAILABLE_DOUBLE)).isNull()
    }

    @Test
    fun `given usage just under the warning level, when evaluating, then nothing is pending`() {
        val pending = DataQuotaLogic.pendingThresholds(
            quotaGb = 10.0, usedGb = 7.9, notified80 = false, notified100 = false
        )

        assertThat(pending).isEmpty()
    }

    @Test
    fun `given usage at the warning level, when evaluating, then only 80 is pending`() {
        val pending = DataQuotaLogic.pendingThresholds(
            quotaGb = 10.0, usedGb = 8.0, notified80 = false, notified100 = false
        )

        assertThat(pending).containsExactly(80)
    }

    @Test
    fun `given both levels were already sent, when evaluating over the quota, then nothing is pending`() {
        val pending = DataQuotaLogic.pendingThresholds(
            quotaGb = 10.0, usedGb = 12.0, notified80 = true, notified100 = true
        )

        assertThat(pending).isEmpty()
    }

    @Test
    fun `given the warning was already sent, when evaluating again, then nothing is pending`() {
        val pending = DataQuotaLogic.pendingThresholds(
            quotaGb = 10.0, usedGb = 8.5, notified80 = true, notified100 = false
        )

        assertThat(pending).isEmpty()
    }

    @Test
    fun `given usage jumps past the whole quota, when evaluating, then both levels are pending`() {
        val pending = DataQuotaLogic.pendingThresholds(
            quotaGb = 10.0, usedGb = 10.4, notified80 = false, notified100 = false
        )

        assertThat(pending).containsExactly(80, 100).inOrder()
    }

    @Test
    fun `given the quota is full and only the warning was sent, when evaluating, then 100 is pending`() {
        val pending = DataQuotaLogic.pendingThresholds(
            quotaGb = 10.0, usedGb = 10.0, notified80 = true, notified100 = false
        )

        assertThat(pending).containsExactly(100)
    }

    @Test
    fun `given a fractional percentage, when computing, then it truncates instead of rounding up`() {
        // 79.9 % must not read as 80 % — the alert and the shown value stay honest.
        assertThat(DataQuotaLogic.percentUsed(quotaGb = 10.0, usedGb = 7.99)).isEqualTo(79)
        assertThat(DataQuotaLogic.percentUsed(quotaGb = 10.0, usedGb = 9.999)).isEqualTo(99)
    }

    @Test
    fun `given usage over the quota, when computing, then the percentage passes 100`() {
        assertThat(DataQuotaLogic.percentUsed(quotaGb = 10.0, usedGb = 15.0)).isEqualTo(150)
    }

    @Test
    fun `given no usage yet, when computing, then zero percent`() {
        assertThat(DataQuotaLogic.percentUsed(quotaGb = 10.0, usedGb = 0.0)).isEqualTo(0)
    }
}
