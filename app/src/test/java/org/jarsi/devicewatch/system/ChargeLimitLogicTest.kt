package org.jarsi.devicewatch.system

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Pure-JVM tests for the charge-limit reminder's fire-once-per-plug-session rules. */
class ChargeLimitLogicTest {

    @Test
    fun `a disabled limit never notifies`() {
        val decision = ChargeLimitLogic.onBatteryChanged(
            ChargeLimitLogic.State(), limitPercent = 0, level = 100, isPlugged = true
        )

        assertThat(decision.notify).isFalse()
        assertThat(decision.state.notifiedThisPlug).isFalse()
    }

    @Test
    fun `charging below the limit does not notify`() {
        val decision = ChargeLimitLogic.onBatteryChanged(
            ChargeLimitLogic.State(), limitPercent = 80, level = 79, isPlugged = true
        )

        assertThat(decision.notify).isFalse()
        assertThat(decision.state.notifiedThisPlug).isFalse()
    }

    @Test
    fun `reaching the limit while charging notifies once and latches`() {
        val decision = ChargeLimitLogic.onBatteryChanged(
            ChargeLimitLogic.State(), limitPercent = 80, level = 80, isPlugged = true
        )

        assertThat(decision.notify).isTrue()
        assertThat(decision.state.notifiedThisPlug).isTrue()
    }

    @Test
    fun `staying above the limit on the same plug session does not notify again`() {
        val first = ChargeLimitLogic.onBatteryChanged(
            ChargeLimitLogic.State(), limitPercent = 80, level = 80, isPlugged = true
        )

        val second = ChargeLimitLogic.onBatteryChanged(
            first.state, limitPercent = 80, level = 85, isPlugged = true
        )

        assertThat(second.notify).isFalse()
        assertThat(second.state.notifiedThisPlug).isTrue()
    }

    @Test
    fun `unplugging resets the latch so the next session notifies again`() {
        val notified = ChargeLimitLogic.onBatteryChanged(
            ChargeLimitLogic.State(), limitPercent = 80, level = 82, isPlugged = true
        )

        val afterUnplug = ChargeLimitLogic.onPowerDisconnected(notified.state)
        assertThat(afterUnplug.notifiedThisPlug).isFalse()

        val nextSession = ChargeLimitLogic.onBatteryChanged(
            afterUnplug, limitPercent = 80, level = 80, isPlugged = true
        )
        assertThat(nextSession.notify).isTrue()
    }

    @Test
    fun `a limit of 100 never notifies because the battery-full alert covers it`() {
        val decision = ChargeLimitLogic.onBatteryChanged(
            ChargeLimitLogic.State(), limitPercent = 100, level = 100, isPlugged = true
        )

        assertThat(decision.notify).isFalse()
        assertThat(decision.state.notifiedThisPlug).isFalse()
    }

    @Test
    fun `a level above the limit while unplugged never notifies`() {
        val decision = ChargeLimitLogic.onBatteryChanged(
            ChargeLimitLogic.State(), limitPercent = 80, level = 95, isPlugged = false
        )

        assertThat(decision.notify).isFalse()
        assertThat(decision.state.notifiedThisPlug).isFalse()
    }
}
