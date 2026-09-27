package org.jarsi.devicewatch.data

/**
 * Static device facts that any app can read without root. Produced once by
 * [SystemStatsRepository.getDeviceInfo]; every field is a display-ready string and uses
 * [UNAVAILABLE_TEXT] when the platform does not expose the value.
 */
data class DeviceInfo(
    // Build / identity
    val manufacturer: String,
    val model: String,
    val codename: String,
    val androidVersion: String,
    val securityPatch: String,
    val buildNumber: String,
    val bootloader: String,
    val radioVersion: String,
    // Chipset / hardware
    val soc: String,
    val supportedAbis: String,
    val kernelVersion: String,
    /** Cores grouped by frequency range, e.g. "4 × 0,3–1,8 GHz + 4 × 0,4–2,4 GHz". */
    val cpuClusters: String = UNAVAILABLE_TEXT,
    /** The CPU frequency governor, e.g. "schedutil". */
    val cpuGovernor: String = UNAVAILABLE_TEXT,
    val gpuRenderer: String,
    val glVersion: String,
    /** The GNSS (satellite positioning) chip, e.g. "Broadcom BCM4776 (2023)". */
    val gnssHardware: String = UNAVAILABLE_TEXT,
    /** What the GNSS chip can do beyond a position fix (Android 12+). */
    val gnssCapabilities: String = UNAVAILABLE_TEXT,
    /** Vulkan API version the GPU supports, e.g. "1.3". */
    val vulkanVersion: String = UNAVAILABLE_TEXT,
    /** Widevine DRM security level: L1 plays HD streams, L3 caps them. */
    val widevineLevel: String = UNAVAILABLE_TEXT,
    /** Video formats a hardware decoder plays, e.g. "AV1, HEVC, VP9". */
    val hardwareDecoders: String = UNAVAILABLE_TEXT,
    // Display
    val screenResolution: String,
    val screenDensity: String,
    val physicalSize: String,
    val refreshRate: String,
    val hdr: String,
    // Memory / storage
    val totalRam: String,
    /** The RAM the device is sold with (Android 14+), e.g. "8 GB". */
    val advertisedRam: String = UNAVAILABLE_TEXT,
    val totalStorage: String,
    // Battery (static)
    val batteryTechnology: String,
    val batteryCapacityMah: String,
    // Cameras
    val cameraCount: String,
    val rearCamera: String,
    val frontCamera: String,
    val cameraFlash: String,
    // Sensors
    val sensorCount: String,
    val sensors: String,
    // System / software
    val locale: String,
    val timezone: String,
    val webViewVersion: String,
    val playServicesVersion: String,
    /** Google Play system update date (Mainline), localised; the raw version when not a date. */
    val playSystemUpdate: String = UNAVAILABLE_TEXT,
    /** Number of Mainline modules installed (Android 10+). */
    val mainlineModules: String = UNAVAILABLE_TEXT,
    val deviceFeatures: String,
    /** Total boots since factory reset (Settings.Global.BOOT_COUNT, API 24+). */
    val bootCountTotal: String,
    /** What the Wi-Fi chip supports beyond the basics: 6 GHz, WPA3, Wi-Fi 7. */
    val wifiCapabilities: String = UNAVAILABLE_TEXT,
    // Network (snapshot)
    val vpnActive: String,
    val dnsServers: String,
    /** Private DNS on the active network: off, automatic, or the server name. */
    val privateDns: String = UNAVAILABLE_TEXT,
    /** The active network's interface, e.g. "wlan0" or "rmnet_data0". */
    val networkInterface: String = UNAVAILABLE_TEXT,
    val mtu: String = UNAVAILABLE_TEXT,
)
