package org.jarsi.devicewatch.system

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChargeStartLogicTest {

    private fun action(
        enabled: Boolean = true,
        screenOn: Boolean = false,
        dreamShowing: Boolean = false,
        selection: ChargeDream = ChargeDream.OURS,
        privileged: Boolean = false,
    ) = chargeStartAction(enabled, screenOn, dreamShowing, selection, privileged)

    @Test
    fun `nothing happens while the setting is off`() {
        assertThat(action(enabled = false)).isEqualTo(ChargeStartAction.NONE)
        assertThat(action(enabled = false, privileged = true)).isEqualTo(ChargeStartAction.NONE)
    }

    @Test
    fun `a screen that is on is never taken over`() {
        // Unlocked in use, a call or navigation over the lock screen, or the lock screen
        // itself, which times out into the screensaver on its own now that it charges.
        assertThat(action(screenOn = true)).isEqualTo(ChargeStartAction.NONE)
        assertThat(action(screenOn = true, privileged = true)).isEqualTo(ChargeStartAction.NONE)
    }

    @Test
    fun `a screensaver already up is left alone`() {
        assertThat(action(dreamShowing = true)).isEqualTo(ChargeStartAction.NONE)
        assertThat(action(dreamShowing = true, privileged = true)).isEqualTo(ChargeStartAction.NONE)
    }

    @Test
    fun `a charging phone that would not show ours gets nothing`() {
        assertThat(action(selection = ChargeDream.NOT_OURS)).isEqualTo(ChargeStartAction.NONE)
        assertThat(action(selection = ChargeDream.NOT_OURS, privileged = true)).isEqualTo(ChargeStartAction.NONE)
    }

    @Test
    fun `extended access starts the screensaver directly when Android would show ours`() {
        assertThat(action(privileged = true)).isEqualTo(ChargeStartAction.START_DREAM)
    }

    @Test
    fun `an uncertain choice is left to Android's own timeout even with extended access`() {
        // dream() skips Android's check of when to start, so it is used only on certainty.
        assertThat(action(selection = ChargeDream.UNKNOWN, privileged = true))
            .isEqualTo(ChargeStartAction.WAKE_SCREEN)
    }

    @Test
    fun `without extended access the screen is woken`() {
        assertThat(action()).isEqualTo(ChargeStartAction.WAKE_SCREEN)
        assertThat(action(selection = ChargeDream.UNKNOWN)).isEqualTo(ChargeStartAction.WAKE_SCREEN)
    }

    private val ours = "org.jarsi.devicewatch/org.jarsi.devicewatch.system.MonitorDreamService"
    private val clock = "com.android.deskclock/com.android.deskclock.Screensaver"

    private fun selection(enabled: String?, onCharge: String?, components: String?) =
        chargeDreamSelection(enabled, onCharge, components, ours)

    @Test
    fun `ours selected in full or short form, switched on for charging, is ours`() {
        assertThat(selection("1", "1", ours)).isEqualTo(ChargeDream.OURS)
        assertThat(selection("1", "1", "org.jarsi.devicewatch/.system.MonitorDreamService"))
            .isEqualTo(ChargeDream.OURS)
        assertThat(selection("1", "1", "$ours,$clock")).isEqualTo(ChargeDream.OURS)
    }

    @Test
    fun `Android shows the first screensaver of the list, so ours after another is not ours`() {
        assertThat(selection("1", "1", "$clock,$ours")).isEqualTo(ChargeDream.NOT_OURS)
        assertThat(selection("1", "1", clock)).isEqualTo(ChargeDream.NOT_OURS)
    }

    @Test
    fun `screensavers off, or not started while charging, are not ours`() {
        assertThat(selection("0", "1", ours)).isEqualTo(ChargeDream.NOT_OURS)
        assertThat(selection("1", "0", ours)).isEqualTo(ChargeDream.NOT_OURS)
    }

    @Test
    fun `no choice at all means the system's own screensaver`() {
        assertThat(selection("1", "1", null)).isEqualTo(ChargeDream.NOT_OURS)
        assertThat(selection("1", "1", "null")).isEqualTo(ChargeDream.NOT_OURS)
        assertThat(selection("1", "1", "")).isEqualTo(ChargeDream.NOT_OURS)
    }

    @Test
    fun `unset switches depend on the device's defaults, so they are uncertain`() {
        // AOSP leaves "while charging" off by default, a Pixel turns it on.
        assertThat(selection("1", null, ours)).isEqualTo(ChargeDream.UNKNOWN)
        assertThat(selection("1", "null", ours)).isEqualTo(ChargeDream.UNKNOWN)
        assertThat(selection(null, "1", ours)).isEqualTo(ChargeDream.UNKNOWN)
        assertThat(selection("null", "1", ours)).isEqualTo(ChargeDream.UNKNOWN)
    }

    @Test
    fun `the shell's answer to the dream call is read`() {
        assertThat(dreamCallOutcome("Result: Parcel(00000000    '....')")).isEqualTo(DreamCallOutcome.STARTED)
        assertThat(dreamCallOutcome("Result: Parcel(	00000000    '....')")).isEqualTo(DreamCallOutcome.STARTED)
        assertThat(dreamCallOutcome("dw-screen-on")).isEqualTo(DreamCallOutcome.SCREEN_ON)
        assertThat(dreamCallOutcome("Result: Parcel(fffffffc ffffffff '........')")).isEqualTo(DreamCallOutcome.FAILED)
        assertThat(dreamCallOutcome("service: Service dreams does not exist")).isEqualTo(DreamCallOutcome.FAILED)
        assertThat(dreamCallOutcome("")).isEqualTo(DreamCallOutcome.FAILED)
        assertThat(dreamCallOutcome(null)).isEqualTo(DreamCallOutcome.FAILED)
    }
}
