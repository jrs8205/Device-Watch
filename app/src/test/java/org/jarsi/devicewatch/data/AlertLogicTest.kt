package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AlertLogicTest {

    private val minute = 60_000L
    private val now = 1_786_700_000_000L

    private fun s(minutesAgo: Long, level: Int, charging: Boolean = false) =
        BatterySample(now - minutesAgo * minute, level, charging)

    @Test
    fun `a hot battery alerts once, and again only after it has cooled`() {
        assertThat(AlertLogic.hotBattery(tempDeciC = 452, latched = false)).isEqualTo(AlertStep.FIRE)
        assertThat(AlertLogic.hotBattery(tempDeciC = 470, latched = true)).isEqualTo(AlertStep.NONE)
        // Hovering just under the limit does not re-arm: it would alert on every wobble.
        assertThat(AlertLogic.hotBattery(tempDeciC = 440, latched = true)).isEqualTo(AlertStep.NONE)
        assertThat(AlertLogic.hotBattery(tempDeciC = 400, latched = true)).isEqualTo(AlertStep.REARM)
        assertThat(AlertLogic.hotBattery(tempDeciC = 380, latched = false)).isEqualTo(AlertStep.NONE)
        assertThat(AlertLogic.hotBattery(tempDeciC = null, latched = false)).isEqualTo(AlertStep.NONE)
    }

    @Test
    fun `low storage alerts under a tenth free and re-arms with some room back`() {
        assertThat(AlertLogic.lowStorage(freePercent = 9, latched = false)).isEqualTo(AlertStep.FIRE)
        assertThat(AlertLogic.lowStorage(freePercent = 11, latched = true)).isEqualTo(AlertStep.NONE)
        assertThat(AlertLogic.lowStorage(freePercent = 12, latched = true)).isEqualTo(AlertStep.REARM)
        assertThat(AlertLogic.lowStorage(freePercent = null, latched = false)).isEqualTo(AlertStep.NONE)
    }

    @Test
    fun `free storage is the whole percent left, rounded down`() {
        // 9.9 % free is under ten: rounding up would hide the alert until 9.5 %.
        assertThat(AlertLogic.freePercent(totalGb = 100.0, usedGb = 90.1)).isEqualTo(9)
        assertThat(AlertLogic.freePercent(totalGb = 0.0, usedGb = 0.0)).isNull()
        assertThat(AlertLogic.freePercent(totalGb = 100.0, usedGb = -1.0)).isNull()
    }

    @Test
    fun `the drain rate is the level lost per hour over the last hour off the charger`() {
        val samples = listOf(s(50, 80), s(35, 76), s(20, 72), s(5, 68))

        // 12 levels in 45 minutes.
        assertThat(AlertLogic.drainPercentPerHour(samples, now)).isEqualTo(16)
    }

    @Test
    fun `no drain rate across a charge or over too short a stretch`() {
        assertThat(AlertLogic.drainPercentPerHour(listOf(s(50, 60), s(30, 62, charging = true), s(5, 58)), now))
            .isNull()
        assertThat(AlertLogic.drainPercentPerHour(listOf(s(20, 80), s(5, 70)), now)).isNull()
        // Older samples than the hour do not stretch the window.
        assertThat(AlertLogic.drainPercentPerHour(listOf(s(200, 99), s(20, 80), s(5, 70)), now)).isNull()
    }

    @Test
    fun `fast drain alerts at 20 percent an hour and re-arms on charging or a calm hour`() {
        assertThat(AlertLogic.fastDrain(percentPerHour = 24, charging = false, latched = false)).isEqualTo(AlertStep.FIRE)
        assertThat(AlertLogic.fastDrain(percentPerHour = 15, charging = false, latched = true)).isEqualTo(AlertStep.NONE)
        assertThat(AlertLogic.fastDrain(percentPerHour = 8, charging = false, latched = true)).isEqualTo(AlertStep.REARM)
        assertThat(AlertLogic.fastDrain(percentPerHour = null, charging = true, latched = true)).isEqualTo(AlertStep.REARM)
        assertThat(AlertLogic.fastDrain(percentPerHour = 30, charging = true, latched = false)).isEqualTo(AlertStep.NONE)
    }
}
