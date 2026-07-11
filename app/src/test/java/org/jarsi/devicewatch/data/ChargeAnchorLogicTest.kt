package com.example.modernwidget.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChargeAnchorLogicTest {

    @Test
    fun `full charge while plugged sets a FULL_CHARGE anchor`() {
        val state = ChargeAnchorLogic.onBatteryChanged(
            ChargeAnchorLogic.State(), level = 100, isPlugged = true, isFull = true, nowMillis = 1_000L
        )

        assertThat(state.anchor).isEqualTo(ChargeAnchor(1_000L, 100, ChargeAnchorType.FULL_CHARGE))
        assertThat(state.fullReachedThisPlug).isTrue()
    }

    @Test
    fun `repeated full events do not move the anchor`() {
        var state = ChargeAnchorLogic.onBatteryChanged(
            ChargeAnchorLogic.State(), level = 100, isPlugged = true, isFull = true, nowMillis = 1_000L
        )

        state = ChargeAnchorLogic.onBatteryChanged(
            state, level = 100, isPlugged = true, isFull = true, nowMillis = 2_000L
        )

        assertThat(state.anchor?.timeMillis).isEqualTo(1_000L)
    }

    @Test
    fun `unplug without reaching full anchors at the unplug moment`() {
        var state = ChargeAnchorLogic.onPowerConnected(ChargeAnchorLogic.State())
        state = ChargeAnchorLogic.onBatteryChanged(
            state, level = 78, isPlugged = true, isFull = false, nowMillis = 500L
        )

        state = ChargeAnchorLogic.onPowerDisconnected(state, level = 78, nowMillis = 900L)

        assertThat(state.anchor).isEqualTo(ChargeAnchor(900L, 78, ChargeAnchorType.UNPLUGGED))
        assertThat(state.fullReachedThisPlug).isFalse()
    }

    @Test
    fun `unplug after a full charge keeps the full-charge anchor`() {
        var state = ChargeAnchorLogic.onBatteryChanged(
            ChargeAnchorLogic.State(), level = 100, isPlugged = true, isFull = true, nowMillis = 1_000L
        )

        state = ChargeAnchorLogic.onPowerDisconnected(state, level = 100, nowMillis = 5_000L)

        assertThat(state.anchor).isEqualTo(ChargeAnchor(1_000L, 100, ChargeAnchorType.FULL_CHARGE))
        assertThat(state.fullReachedThisPlug).isFalse()
    }

    @Test
    fun `a new plug session can move the anchor again`() {
        var state = ChargeAnchorLogic.onBatteryChanged(
            ChargeAnchorLogic.State(), level = 100, isPlugged = true, isFull = true, nowMillis = 1_000L
        )
        state = ChargeAnchorLogic.onPowerDisconnected(state, level = 100, nowMillis = 5_000L)
        state = ChargeAnchorLogic.onPowerConnected(state)

        state = ChargeAnchorLogic.onBatteryChanged(
            state, level = 100, isPlugged = true, isFull = true, nowMillis = 9_000L
        )

        assertThat(state.anchor?.timeMillis).isEqualTo(9_000L)
    }

    @Test
    fun `full reported while unplugged does not anchor`() {
        val state = ChargeAnchorLogic.onBatteryChanged(
            ChargeAnchorLogic.State(), level = 100, isPlugged = false, isFull = true, nowMillis = 100L
        )

        assertThat(state.anchor).isNull()
    }

    @Test
    fun `a Samsung protected battery anchors at its capped level when reported full`() {
        val state = ChargeAnchorLogic.onBatteryChanged(
            ChargeAnchorLogic.State(), level = 85, isPlugged = true, isFull = true, nowMillis = 1_000L
        )

        assertThat(state.anchor).isEqualTo(ChargeAnchor(1_000L, 85, ChargeAnchorType.FULL_CHARGE))
    }
}
