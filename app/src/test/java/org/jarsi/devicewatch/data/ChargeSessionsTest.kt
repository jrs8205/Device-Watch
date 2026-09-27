package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChargeSessionsTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val t0 = 1_786_700_000_000L

    private fun s(atMinutes: Long, level: Int, charging: Boolean, temp: Int? = null) =
        BatterySample(t0 + atMinutes * minute, level, charging, temp)

    @Test
    fun `a charge runs from the plug-in sample to the unplug sample`() {
        val samples = listOf(
            s(-30, 25, false),
            s(0, 20, true, temp = 300),
            s(30, 50, true, temp = 355),
            s(60, 80, true, temp = 340),
            s(62, 80, false),
            s(90, 78, false),
        )

        val sessions = ChargeSessions.from(samples, nowMillis = t0 + 2 * hour)

        assertThat(sessions).containsExactly(
            ChargeSession(
                startMillis = t0,
                endMillis = t0 + 62 * minute,
                startLevel = 20,
                endLevel = 80,
                ongoing = false,
                peakTemperatureDeciC = 355,
            )
        )
    }

    @Test
    fun `a charge still plugged in at the latest sample is ongoing`() {
        val samples = listOf(s(0, 40, true), s(20, 55, true))

        val session = ChargeSessions.from(samples, nowMillis = t0 + 25 * minute).single()

        assertThat(session.ongoing).isTrue()
        assertThat(session.endLevel).isEqualTo(55)
    }

    @Test
    fun `a collection gap ends a charge at its last sample`() {
        // Ten hours without samples: the phone was off, not charging all along.
        val samples = listOf(
            s(0, 30, true),
            s(30, 60, true),
            s(630, 40, true),
            s(660, 70, true),
            s(670, 70, false),
        )

        val sessions = ChargeSessions.from(samples, nowMillis = t0 + 12 * hour)

        assertThat(sessions.map { it.startLevel to it.endLevel }).containsExactly(40 to 70, 30 to 60).inOrder()
        assertThat(sessions.last().ongoing).isFalse()
        assertThat(sessions.last().endMillis).isEqualTo(t0 + 30 * minute)
    }

    @Test
    fun `charger contact bounce leaves no charge behind`() {
        val samples = listOf(s(0, 50, false), s(1, 50, true), s(2, 50, false))

        assertThat(ChargeSessions.from(samples, nowMillis = t0 + hour)).isEmpty()
    }

    @Test
    fun `a charge without temperatures has no peak`() {
        val samples = listOf(s(0, 20, true), s(40, 60, true), s(41, 60, false))

        assertThat(ChargeSessions.from(samples, nowMillis = t0 + hour).single().peakTemperatureDeciC).isNull()
    }

    @Test
    fun `charges are listed newest first`() {
        val samples = listOf(
            s(0, 20, true), s(30, 50, true), s(31, 50, false),
            s(120, 45, true), s(150, 75, true), s(151, 75, false),
        )

        assertThat(ChargeSessions.from(samples, nowMillis = t0 + 4 * hour).map { it.startLevel })
            .containsExactly(45, 20).inOrder()
    }

    @Test
    fun `the charging rate is levels per hour, known only for a charge of some length`() {
        val charge = ChargeSession(t0, t0 + 30 * minute, 20, 50, ongoing = false, peakTemperatureDeciC = null)
        assertThat(charge.percentPerHour).isEqualTo(60)

        val brief = ChargeSession(t0, t0 + 3 * minute, 20, 22, ongoing = false, peakTemperatureDeciC = null)
        assertThat(brief.percentPerHour).isNull()
    }
}
