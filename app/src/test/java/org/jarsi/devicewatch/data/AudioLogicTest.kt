package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AudioLogicTest {

    // AudioDeviceInfo.TYPE_* values.
    private val speaker = 2
    private val wiredHeadset = 3
    private val bluetoothSco = 7
    private val bluetoothA2dp = 8
    private val builtinMic = 15
    private val telephony = 18
    private val usbHeadset = 22
    private val bleHeadset = 26

    @Test
    fun `built-in and internal routes are not connected devices`() {
        assertThat(AudioLogic.externalKind(speaker)).isNull()
        assertThat(AudioLogic.externalKind(builtinMic)).isNull()
        assertThat(AudioLogic.externalKind(telephony)).isNull()
        assertThat(AudioLogic.externalKind(bleHeadset)).isEqualTo(AudioDeviceKind.BLUETOOTH)
        assertThat(AudioLogic.externalKind(usbHeadset)).isEqualTo(AudioDeviceKind.USB)
    }

    @Test
    fun `a headset seen as several routes is one device`() {
        // A Bluetooth headset is an A2DP output, an SCO output and an SCO input at once.
        val devices = listOf(
            speaker to "Pixel 8a",
            bluetoothA2dp to "WH-1000XM4",
            bluetoothSco to "WH-1000XM4",
            bluetoothSco to "WH-1000XM4",
            wiredHeadset to "",
            wiredHeadset to "Pixel 8a",
        )

        assertThat(AudioLogic.connected(devices, builtInName = "Pixel 8a")).containsExactly(
            AudioEndpoint(AudioDeviceKind.WIRED, name = null),
            AudioEndpoint(AudioDeviceKind.BLUETOOTH, name = "WH-1000XM4"),
        ).inOrder()
    }

    @Test
    fun `nothing plugged in means no connected devices`() {
        assertThat(AudioLogic.connected(listOf(speaker to "Pixel 8a", builtinMic to "Pixel 8a"), "Pixel 8a"))
            .isEmpty()
    }

    @Test
    fun `spatial audio is known from Android 12L and says whether it is on`() {
        assertThat(AudioLogic.spatialAudio(sdkInt = 31, immersiveLevel = 1, enabled = true)).isNull()
        assertThat(AudioLogic.spatialAudio(sdkInt = 32, immersiveLevel = 0, enabled = false))
            .isEqualTo(SpatialAudio.NOT_SUPPORTED)
        assertThat(AudioLogic.spatialAudio(sdkInt = 33, immersiveLevel = 1, enabled = true)).isEqualTo(SpatialAudio.ON)
        assertThat(AudioLogic.spatialAudio(sdkInt = 33, immersiveLevel = 1, enabled = false)).isEqualTo(SpatialAudio.OFF)
    }
}
