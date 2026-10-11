package org.jarsi.devicewatch.system

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChargeStartLogicTest {

    private fun action(
        enabled: Boolean = true,
        screenOn: Boolean = false,
        dreamShowing: Boolean = false,
        ourDreamSelected: Boolean? = true,
        privileged: Boolean = false,
    ) = chargeStartAction(enabled, screenOn, dreamShowing, ourDreamSelected, privileged)

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
    fun `another screensaver selected means nothing to start`() {
        assertThat(action(ourDreamSelected = false)).isEqualTo(ChargeStartAction.NONE)
        assertThat(action(ourDreamSelected = false, privileged = true)).isEqualTo(ChargeStartAction.NONE)
    }

    @Test
    fun `extended access starts the screensaver directly`() {
        assertThat(action(privileged = true)).isEqualTo(ChargeStartAction.START_DREAM)
    }

    @Test
    fun `without extended access the screen is woken`() {
        assertThat(action()).isEqualTo(ChargeStartAction.WAKE_SCREEN)
    }

    @Test
    fun `a selection that could not be read does not stop the wake`() {
        assertThat(action(ourDreamSelected = null)).isEqualTo(ChargeStartAction.WAKE_SCREEN)
    }

    private val ours = "org.jarsi.devicewatch/org.jarsi.devicewatch.system.MonitorDreamService"

    @Test
    fun `our screensaver selected in full or short form counts`() {
        assertThat(ourDreamStartsOnCharge("1", "1", ours, ours)).isTrue()
        assertThat(ourDreamStartsOnCharge("1", "1", "org.jarsi.devicewatch/.system.MonitorDreamService", ours)).isTrue()
        assertThat(ourDreamStartsOnCharge("1", "1", "com.android.deskclock/com.android.deskclock.Screensaver,$ours", ours)).isTrue()
    }

    @Test
    fun `screensavers switched off or another one selected do not count`() {
        assertThat(ourDreamStartsOnCharge("0", "1", ours, ours)).isFalse()
        assertThat(ourDreamStartsOnCharge("1", "1", "com.android.deskclock/com.android.deskclock.Screensaver", ours)).isFalse()
    }

    @Test
    fun `a screensaver set to start only when docked does not count`() {
        assertThat(ourDreamStartsOnCharge("1", "0", ours, ours)).isFalse()
    }

    @Test
    fun `unset settings mean the system default`() {
        // An unset switch is on by default; an unset choice is the system's own screensaver.
        assertThat(ourDreamStartsOnCharge(null, "1", ours, ours)).isTrue()
        assertThat(ourDreamStartsOnCharge("null", "1", ours, ours)).isTrue()
        // Whether an unset "while charging" is on varies by device; it is left to Android.
        assertThat(ourDreamStartsOnCharge("1", null, ours, ours)).isTrue()
        assertThat(ourDreamStartsOnCharge("1", "null", ours, ours)).isTrue()
        assertThat(ourDreamStartsOnCharge("1", "1", null, ours)).isFalse()
        assertThat(ourDreamStartsOnCharge("1", "1", "null", ours)).isFalse()
        assertThat(ourDreamStartsOnCharge("1", "1", "", ours)).isFalse()
    }

    @Test
    fun `the shell's answer to the dream call is read`() {
        assertThat(dreamCallSucceeded("Result: Parcel(00000000    '....')")).isTrue()
        assertThat(dreamCallSucceeded("Result: Parcel(fffffffc ffffffff '........')")).isFalse()
        assertThat(dreamCallSucceeded("service: Service dreams does not exist")).isFalse()
        assertThat(dreamCallSucceeded("")).isFalse()
        assertThat(dreamCallSucceeded(null)).isFalse()
    }
}
