package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The repository is a singleton shared by the app and the monitoring service.
 * Before this window existed, whichever of the two sampled second measured only
 * the gap between the two calls — so the widget's CPU figure moved while the
 * app's sat at 0 %.
 */
class CpuWindowTest {

    @Test
    fun `a window shorter than the minimum is not measured again`() {
        assertThat(SystemStatsParser.cpuWindowIsWideEnough(baselineMillis = 1_000L, nowMillis = 1_200L))
            .isFalse()
        assertThat(SystemStatsParser.cpuWindowIsWideEnough(1_000L, 1_000L)).isFalse()
    }

    @Test
    fun `the minimum window itself counts as wide enough`() {
        val now = 1_000L + SystemStatsParser.MIN_CPU_WINDOW_MILLIS
        assertThat(SystemStatsParser.cpuWindowIsWideEnough(1_000L, now)).isTrue()
    }

    @Test
    fun `an ordinary polling interval is measured`() {
        // Both the service and the open app poll every 15 s.
        assertThat(SystemStatsParser.cpuWindowIsWideEnough(1_000L, 16_000L)).isTrue()
    }

    @Test
    fun `the very first reading has no baseline to be too close to`() {
        // elapsedRealtime never goes backwards, so a zero baseline is always past.
        assertThat(SystemStatsParser.cpuWindowIsWideEnough(0L, 60_000L)).isTrue()
    }
}
