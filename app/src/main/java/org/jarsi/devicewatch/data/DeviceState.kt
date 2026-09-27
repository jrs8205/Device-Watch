package org.jarsi.devicewatch.data

/**
 * Device settings and states that change while the app runs — power modes,
 * connectivity checks, attached storage, security — read on each screen refresh
 * rather than once like [DeviceInfo]. Every field is display-ready and uses
 * [UNAVAILABLE_TEXT] when the platform does not expose the value.
 */
data class DeviceState(
    // Power
    val powerSaveMode: String = UNAVAILABLE_TEXT,
    val deviceIdle: String = UNAVAILABLE_TEXT,
    val batteryOptimizationExempt: String = UNAVAILABLE_TEXT,
    // Active network
    /** Whether the active network reaches the internet, or waits at a sign-in page. */
    val internetAccess: String = UNAVAILABLE_TEXT,
    /** The network's own bandwidth estimate, down and up. */
    val bandwidthEstimate: String = UNAVAILABLE_TEXT,
    /** Whether the active network is metered (billed by use). */
    val networkMetered: String = UNAVAILABLE_TEXT,
    // Wi-Fi connection
    /** The connected network's security, e.g. "WPA3-Personal" (Android 12+). */
    val wifiSecurity: String = UNAVAILABLE_TEXT,
    /** Wi-Fi 7 multi-link: how many links the connection uses (Android 14+). */
    val wifiMloLinks: String = UNAVAILABLE_TEXT,
    // Mobile network, beyond the dBm figure the stats carry
    /** RSRP / RSRQ / SINR of the serving cell (Android 10+). */
    val cellSignalDetails: String = UNAVAILABLE_TEXT,
    /** The signal level the status bar draws, as "3 / 4". */
    val cellSignalBars: String = UNAVAILABLE_TEXT,
    /** Technology, band and channel of the registered cells, e.g. "5G NR n78 · ARFCN 636666". */
    val cellBand: String = UNAVAILABLE_TEXT,
    // Attached storage and USB
    /** SD cards and USB storage, each as its name and a free/total or state text. */
    val removableVolumes: List<Pair<String, String>> = emptyList(),
    /** Devices attached to the USB host port, comma-separated; a "none" text when empty. */
    val usbDevices: String = UNAVAILABLE_TEXT,
)
