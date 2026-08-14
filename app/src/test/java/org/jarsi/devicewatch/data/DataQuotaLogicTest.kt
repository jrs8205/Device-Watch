package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DataQuotaLogicTest {

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
