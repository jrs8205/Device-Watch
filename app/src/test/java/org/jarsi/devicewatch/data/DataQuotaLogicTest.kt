package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DataQuotaLogicTest {

    private val gb = 1024L * 1024 * 1024

    @Test
    fun `a reading taken under the current quota is checked`() {
        assertThat(DataQuotaLogic.classifyReading(readingQuotaGb = 10.0, currentQuotaGb = 10.0))
            .isEqualTo(QuotaReadingAction.CHECK)
    }

    @Test
    fun `a reading taken under another quota is stale`() {
        // The quota was raised while the stats were being read: the old reading
        // must not latch alerts for the new quota.
        assertThat(DataQuotaLogic.classifyReading(readingQuotaGb = 10.0, currentQuotaGb = 20.0))
            .isEqualTo(QuotaReadingAction.IGNORE)
        assertThat(DataQuotaLogic.classifyReading(readingQuotaGb = 10.0, currentQuotaGb = 0.0))
            .isEqualTo(QuotaReadingAction.IGNORE)
    }

    @Test
    fun `a reading without a quota releases the watch only while the quota is off`() {
        assertThat(DataQuotaLogic.classifyReading(readingQuotaGb = UNAVAILABLE_DOUBLE, currentQuotaGb = 0.0))
            .isEqualTo(QuotaReadingAction.RELEASE_WATCH)
    }

    @Test
    fun `a reading without a quota taken before the quota was switched on is stale`() {
        // Delivered after a newer reading under the new quota armed the usage
        // watch, it must not take that watch down.
        assertThat(DataQuotaLogic.classifyReading(readingQuotaGb = UNAVAILABLE_DOUBLE, currentQuotaGb = 10.0))
            .isEqualTo(QuotaReadingAction.IGNORE)
    }

    @Test
    fun `the watch points at the warning level while under it`() {
        val bytes = DataQuotaLogic.bytesToNextCheck(
            quotaGb = 10.0, usedGb = 2.0, notified80 = false, notified100 = false
        )

        assertThat(bytes).isEqualTo(6 * gb)
    }

    @Test
    fun `the watch skips a level already passed or already notified`() {
        // 9 of 10 GB used: the 80 % alert is the caller's job right now, the
        // callback must watch for the limit itself.
        assertThat(
            DataQuotaLogic.bytesToNextCheck(
                quotaGb = 10.0, usedGb = 9.0, notified80 = false, notified100 = false
            )
        ).isEqualTo(1 * gb)
        assertThat(
            DataQuotaLogic.bytesToNextCheck(
                quotaGb = 10.0, usedGb = 5.0, notified80 = true, notified100 = false
            )
        ).isEqualTo(5 * gb)
    }

    @Test
    fun `after both alerts the watch stays on for the next period`() {
        // The next period's first alert needs 80 % of the quota counted from its
        // start, and the watch counts from now, so it cannot fire any later than
        // that crossing — whether the period turns with the screen on or off.
        assertThat(
            DataQuotaLogic.bytesToNextCheck(
                quotaGb = 10.0, usedGb = 10.5, notified80 = true, notified100 = true
            )
        ).isEqualTo(8 * gb)
    }

    @Test
    fun `the watch never waits longer than a new period's first alert`() {
        // Only the warning was sent, far below the limit (the counting period was
        // just moved): the limit is 9.5 GB away, a fresh period's warning only 8.
        assertThat(
            DataQuotaLogic.bytesToNextCheck(
                quotaGb = 10.0, usedGb = 0.5, notified80 = true, notified100 = false
            )
        ).isEqualTo(8 * gb)
    }

    @Test
    fun `there is nothing to watch without a quota or a usage figure`() {
        assertThat(
            DataQuotaLogic.bytesToNextCheck(
                quotaGb = 0.0, usedGb = 1.0, notified80 = false, notified100 = false
            )
        ).isNull()
        assertThat(
            DataQuotaLogic.bytesToNextCheck(
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
