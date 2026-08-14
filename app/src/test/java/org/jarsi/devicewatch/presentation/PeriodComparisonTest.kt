package org.jarsi.devicewatch.presentation

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.data.DataCounterMode
import org.junit.Test
import java.time.LocalDate

class PeriodComparisonTest {

    @Test
    fun `day mode compares today against yesterday`() {
        val today = LocalDate.of(2026, 8, 14)

        val windows = PeriodComparison.windows(DataCounterMode.DAY, 1, today)

        assertThat(windows).isNotNull()
        windows!!
        assertThat(windows.currentStart).isEqualTo(today)
        assertThat(windows.currentEnd).isEqualTo(today)
        assertThat(windows.previousStart).isEqualTo(today.minusDays(1))
        assertThat(windows.previousEnd).isEqualTo(today.minusDays(1))
        assertThat(windows.daysCompared).isEqualTo(1)
    }

    @Test
    fun `billing cycle compares the same number of elapsed days from each cycle start`() {
        val today = LocalDate.of(2026, 8, 14)

        val windows = PeriodComparison.windows(DataCounterMode.BILLING_CYCLE, 1, today)

        assertThat(windows).isNotNull()
        windows!!
        assertThat(windows.currentStart).isEqualTo(LocalDate.of(2026, 8, 1))
        assertThat(windows.currentEnd).isEqualTo(today)
        assertThat(windows.previousStart).isEqualTo(LocalDate.of(2026, 7, 1))
        assertThat(windows.previousEnd).isEqualTo(LocalDate.of(2026, 7, 14))
        assertThat(windows.daysCompared).isEqualTo(14)
    }

    @Test
    fun `billing cycle start day clamps in short months`() {
        // Cycle day 31 early in March: the current cycle started on the clamped
        // Feb 28, six elapsed days ago, so the previous slice is Jan 31..Feb 5.
        val today = LocalDate.of(2026, 3, 5)

        val windows = PeriodComparison.windows(DataCounterMode.BILLING_CYCLE, 31, today)

        assertThat(windows).isNotNull()
        windows!!
        assertThat(windows.currentStart).isEqualTo(LocalDate.of(2026, 2, 28))
        assertThat(windows.previousStart).isEqualTo(LocalDate.of(2026, 1, 31))
        assertThat(windows.previousEnd).isEqualTo(LocalDate.of(2026, 2, 5))
        assertThat(windows.daysCompared).isEqualTo(6)
    }

    @Test
    fun `previous window at the retention boundary is still returned`() {
        // Cycle day 1 on the last day of a 31-day month: the previous cycle start
        // lands exactly 61 days back, the oldest day the stores still retain.
        val today = LocalDate.of(2026, 8, 31)

        val windows = PeriodComparison.windows(DataCounterMode.BILLING_CYCLE, 1, today)

        assertThat(windows).isNotNull()
        assertThat(windows!!.previousStart).isEqualTo(today.minusDays(61))
    }

    @Test
    fun `change percent is signed and null against a zero baseline`() {
        assertThat(PeriodComparison.changePercent(150, 100)).isEqualTo(50)
        assertThat(PeriodComparison.changePercent(50, 100)).isEqualTo(-50)
        assertThat(PeriodComparison.changePercent(0, 100)).isEqualTo(-100)
        assertThat(PeriodComparison.changePercent(100, 0)).isNull()
        assertThat(PeriodComparison.changePercent(0, 0)).isNull()
    }

    @Test
    fun `change percent rounds to the nearest whole percent`() {
        assertThat(PeriodComparison.changePercent(106, 80)).isEqualTo(33)
        assertThat(PeriodComparison.changePercent(81, 80)).isEqualTo(1)
    }
}
