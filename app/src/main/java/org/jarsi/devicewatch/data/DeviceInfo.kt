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
    val gpuRenderer: String,
    val glVersion: String,
    // Display
    val screenResolution: String,
    val screenDensity: String,
    val physicalSize: String,
    val refreshRate: String,
    val hdr: String,
    // Memory / storage
    val totalRam: String,
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
    val deviceFeatures: String,
    /** Total boots since factory reset (Settings.Global.BOOT_COUNT, API 24+). */
    val bootCountTotal: String,
    // Network (snapshot)
    val vpnActive: String,
    val dnsServers: String,
)
