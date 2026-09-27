package org.jarsi.devicewatch.data

/** The kinds of audio device a user plugs in or pairs, in the order they are listed. */
enum class AudioDeviceKind { WIRED, USB, BLUETOOTH, HEARING_AID, HDMI, DOCK, LINE }

/** A connected audio device; [name] only when the device reports one of its own. */
data class AudioEndpoint(val kind: AudioDeviceKind, val name: String?)

enum class SpatialAudio { ON, OFF, NOT_SUPPORTED }

object AudioLogic {

    /** AudioDeviceInfo.TYPE_* of a device the user connects; null for built-in and internal routes. */
    fun externalKind(type: Int): AudioDeviceKind? = when (type) {
        3, 4 -> AudioDeviceKind.WIRED // WIRED_HEADSET, WIRED_HEADPHONES
        5, 6, 19 -> AudioDeviceKind.LINE // LINE_ANALOG, LINE_DIGITAL, AUX_LINE
        7, 8, 26, 27, 30 -> AudioDeviceKind.BLUETOOTH // SCO, A2DP, BLE_HEADSET, BLE_SPEAKER, BLE_BROADCAST
        9, 10, 29 -> AudioDeviceKind.HDMI // HDMI, HDMI_ARC, HDMI_EARC
        11, 12, 22 -> AudioDeviceKind.USB // USB_DEVICE, USB_ACCESSORY, USB_HEADSET
        13, 31 -> AudioDeviceKind.DOCK // DOCK, DOCK_ANALOG
        23 -> AudioDeviceKind.HEARING_AID
        else -> null
    }

    /**
     * The connected devices among `(type, productName)` routes. One headset shows
     * up as several routes (playback, call audio, its microphone), so routes of
     * the same kind and name are one device. Built-in routes report the phone's
     * own model as their name, which is no name of the device's.
     */
    fun connected(routes: List<Pair<Int, String?>>, builtInName: String): List<AudioEndpoint> =
        routes.mapNotNull { (type, name) ->
            val kind = externalKind(type) ?: return@mapNotNull null
            AudioEndpoint(kind, name?.trim()?.takeIf { it.isNotEmpty() && it != builtInName })
        }
            .distinct()
            .sortedWith(compareBy({ it.kind }, { it.name.orEmpty() }))

    /** Spatializer state (Android 12L+); an immersive level of 0 means the device has none. */
    fun spatialAudio(sdkInt: Int, immersiveLevel: Int, enabled: Boolean): SpatialAudio? = when {
        sdkInt < 32 -> null
        immersiveLevel <= 0 -> SpatialAudio.NOT_SUPPORTED
        enabled -> SpatialAudio.ON
        else -> SpatialAudio.OFF
    }
}
