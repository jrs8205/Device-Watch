package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class DataPeriodCalculatorTest {

    @Test
    fun `given day mode, when computing start, then today is returned`() {
        val today = LocalDate.of(2026, 7, 4)

        val start = DataPeriodCalculator.periodStart(DataCounterMode.DAY, 15, today)

        assertThat(start).isEqualTo(today)
    }

    @Test
    fun `given cycle day 1, when computing start, then first of current month`() {
        val start = DataPeriodCalculator.periodStart(
            DataCounterMode.BILLING_CYCLE, 1, LocalDate.of(2026, 7, 4)
        )

        assertThat(start).isEqualTo(LocalDate.of(2026, 7, 1))
    }

    @Test
    fun `given today is the start day, when computing start, then today`() {
        val start = DataPeriodCalculator.periodStart(
            DataCounterMode.BILLING_CYCLE, 15, LocalDate.of(2026, 7, 15)
        )

        assertThat(start).isEqualTo(LocalDate.of(2026, 7, 15))
    }

    @Test
    fun `given today after start day, when computing start, then this month start day`() {
        val start = DataPeriodCalculator.periodStart(
            DataCounterMode.BILLING_CYCLE, 15, LocalDate.of(2026, 7, 20)
        )

        assertThat(start).isEqualTo(LocalDate.of(2026, 7, 15))
    }

    @Test
    fun `given today before start day, when computing start, then previous month start day`() {
        val start = DataPeriodCalculator.periodStart(
            DataCounterMode.BILLING_CYCLE, 15, LocalDate.of(2026, 7, 4)
        )

        assertThat(start).isEqualTo(LocalDate.of(2026, 6, 15))
    }

    @Test
    fun `given start day 31 and a 30-day previous month, when computing start, then clamped to day 30`() {
        val start = DataPeriodCalculator.periodStart(
            DataCounterMode.BILLING_CYCLE, 31, LocalDate.of(2026, 5, 5)
        )

        assertThat(start).isEqualTo(LocalDate.of(2026, 4, 30))
    }

    @Test
    fun `given start day 31 and non-leap february, when computing start, then clamped to feb 28`() {
        val start = DataPeriodCalculator.periodStart(
            DataCounterMode.BILLING_CYCLE, 31, LocalDate.of(2026, 3, 5)
        )

        assertThat(start).isEqualTo(LocalDate.of(2026, 2, 28))
    }

    @Test
    fun `given start day 30 and leap february, when computing start, then clamped to feb 29`() {
        val start = DataPeriodCalculator.periodStart(
            DataCounterMode.BILLING_CYCLE, 30, LocalDate.of(2024, 3, 5)
        )

        assertThat(start).isEqualTo(LocalDate.of(2024, 2, 29))
    }

    @Test
    fun `given january before start day, when computing start, then december of previous year`() {
        val start = DataPeriodCalculator.periodStart(
            DataCounterMode.BILLING_CYCLE, 15, LocalDate.of(2026, 1, 3)
        )

        assertThat(start).isEqualTo(LocalDate.of(2025, 12, 15))
    }

    @Test
    fun `given clamped start day equals today, when computing start, then today`() {
        // June has 30 days; a start day of 31 clamps to June 30, which is today.
        val start = DataPeriodCalculator.periodStart(
            DataCounterMode.BILLING_CYCLE, 31, LocalDate.of(2026, 6, 30)
        )

        assertThat(start).isEqualTo(LocalDate.of(2026, 6, 30))
    }

    @Test
    fun `given out-of-range start day, when computing start, then value is coerced`() {
        val high = DataPeriodCalculator.periodStart(
            DataCounterMode.BILLING_CYCLE, 99, LocalDate.of(2026, 7, 31)
        )
        val low = DataPeriodCalculator.periodStart(
            DataCounterMode.BILLING_CYCLE, 0, LocalDate.of(2026, 7, 4)
        )

        assertThat(high).isEqualTo(LocalDate.of(2026, 7, 31))
        assertThat(low).isEqualTo(LocalDate.of(2026, 7, 1))
    }
}
