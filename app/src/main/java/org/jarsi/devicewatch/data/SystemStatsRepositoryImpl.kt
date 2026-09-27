package org.jarsi.devicewatch.data

import android.Manifest
import android.annotation.TargetApi
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.os.storage.StorageManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.hardware.usb.UsbManager
import android.location.LocationManager
import android.media.MediaCodecList
import android.media.MediaDrm
import android.nfc.NfcAdapter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiInfo
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import android.webkit.WebView
import android.telephony.AccessNetworkConstants
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.ServiceState
import android.telephony.SubscriptionManager
import android.telephony.CellIdentityNr
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.di.DefaultDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Singleton
class SystemStatsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    private val settings: AppSettingsRepository,
) : SystemStatsRepository {

    // CPU load needs to compare two samples over time. These snapshots persist across
    // calls; the mutex below guarantees only one computation touches them at a time.
    // The timestamps and last readings beside them are what keep a second caller
    // arriving moments later from measuring a near-zero window — see
    // SystemStatsParser.MIN_CPU_WINDOW_MILLIS.
    private var previousCpuSnapshot: SystemStatsParser.CpuSnapshot? = null
    private var previousCpuSampleMillis = 0L
    private var lastCpuLoadPercent = UNAVAILABLE_INT
    private var previousResidencyByCore: Map<Int, Map<Long, Long>>? = null
    private var previousResidencySampleMillis = 0L
    private var lastResidencyLoadPercent = UNAVAILABLE_INT
    private val unavailableFilePaths = mutableSetOf<String>()
    private var skipThermalRead = false

    private val statsMutex = Mutex()

    override suspend fun getStats(): SystemStats = withContext(dispatcher) {
        statsMutex.withLock { computeStats() }
    }

    override suspend fun getDeviceInfo(): DeviceInfo = withContext(dispatcher) {
        computeDeviceInfo()
    }

    override suspend fun dataUsedSince(startMillis: Long): DataUsageSince = withContext(dispatcher) {
        DataUsageSince(
            wifiGb = readNetworkUsageGb(ConnectivityManager.TYPE_WIFI, startMillis),
            mobileGb = readNetworkUsageGb(ConnectivityManager.TYPE_MOBILE, startMillis),
        )
    }

    @Suppress("DEPRECATION") // querySummary(networkType, ...) is the public per-UID API
    override suspend fun dataBreakdown(startMillis: Long): DataBreakdown = withContext(dispatcher) {
        // The foreground state is recorded from Android 9 on; before that every
        // bucket reads as background, which would claim a split that is not there.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !hasUsageStatsAccess()) {
            return@withContext DataBreakdown.NONE
        }
        val statsManager = context.getSystemService(NetworkStatsManager::class.java)
            ?: return@withContext DataBreakdown.NONE
        val end = System.currentTimeMillis()
        val records = mutableListOf<TrafficRecord>()
        for (networkType in intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            try {
                statsManager.querySummary(networkType, null, startMillis, end)?.use { stats ->
                    val bucket = NetworkStats.Bucket()
                    while (stats.hasNextBucket()) {
                        stats.getNextBucket(bucket)
                        records += TrafficRecord(
                            mobile = networkType == ConnectivityManager.TYPE_MOBILE,
                            state = ExtraStatsLogic.trafficState(bucket.state),
                            roaming = bucket.roaming == NetworkStats.Bucket.ROAMING_YES,
                            bytes = bucket.rxBytes + bucket.txBytes,
                        )
                    }
                }
            } catch (_: Exception) {
                // One network type failing should not hide the other.
            }
        }
        ExtraStatsLogic.dataBreakdown(records)
    }

    override suspend fun storageBreakdown(): StorageBreakdown? = withContext(dispatcher) {
        if (!hasUsageStatsAccess()) return@withContext null
        val manager = context.getSystemService(StorageStatsManager::class.java) ?: return@withContext null
        try {
            val uuid = StorageManager.UUID_DEFAULT
            val user = android.os.Process.myUserHandle()
            // The same used figure the storage row above shows (the data partition),
            // so the parts add up to it; the system partition is not part of it.
            val dataStat = StatFs(Environment.getDataDirectory().path)
            val used = (dataStat.blockCountLong - dataStat.availableBlocksLong) * dataStat.blockSizeLong
            // Per app, not queryStatsForUser: the user-wide figure folds the whole
            // shared storage into dataBytes, so photos and downloads read as apps.
            @Suppress("DEPRECATION") // the flags overload is API 33+
            val uids = context.packageManager.getInstalledApplications(0).map { it.uid }
            val apps = ExtraStatsLogic.appSizeTotal(uids) { uid ->
                try {
                    manager.queryStatsForUid(uuid, uid).let { AppSizes(it.appBytes, it.dataBytes, it.cacheBytes) }
                } catch (_: Exception) {
                    null
                }
            }
            val media = manager.queryExternalStatsForUser(uuid, user)
            ExtraStatsLogic.storageBreakdown(
                usedBytes = used,
                appCodeBytes = apps.codeBytes,
                appDataBytes = apps.dataBytes,
                appCacheBytes = apps.cacheBytes,
                imageBytes = media.imageBytes,
                videoBytes = media.videoBytes,
                audioBytes = media.audioBytes,
            )
        } catch (_: Exception) {
            // IOException from a busy volume, SecurityException without usage access.
            null
        }
    }

    override suspend fun deviceState(): DeviceState = withContext(dispatcher) {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val cellDetails = readCellDetails()
        val wifiConnection = readWifiConnection()
        val network = readActiveNetwork()
        DeviceState(
            powerSaveMode = powerManager?.let { boolText(it.isPowerSaveMode) } ?: UNAVAILABLE_TEXT,
            deviceIdle = powerManager?.let { boolText(it.isDeviceIdleMode) } ?: UNAVAILABLE_TEXT,
            batteryOptimizationExempt = powerManager
                ?.let { boolText(it.isIgnoringBatteryOptimizations(context.packageName)) }
                ?: UNAVAILABLE_TEXT,
            swap = ExtraStatsLogic.swapFromMeminfo(readSysFile("/proc/meminfo"))?.let { swap ->
                context.getString(
                    R.string.swap_used_of,
                    "%.1f GB".format((swap.totalKb - swap.freeKb) / KB_PER_GB),
                    "%.1f GB".format(swap.totalKb / KB_PER_GB),
                )
            } ?: UNAVAILABLE_TEXT,
            brightness = readBrightness(),
            screenTimeout = readScreenTimeout(),
            fontSize = "${Math.round(context.resources.configuration.fontScale * 100)} %",
            displaySize = ExtraStatsLogic.displaySizePercent(
                context.resources.configuration.densityDpi,
                DisplayMetrics.DENSITY_DEVICE_STABLE,
            )?.let { "$it %" } ?: UNAVAILABLE_TEXT,
            darkTheme = boolText(
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            ),
            developerOptions = globalFlag(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, default = false),
            usbDebugging = globalFlag(Settings.Global.ADB_ENABLED, default = false),
            automaticTime = globalFlag(Settings.Global.AUTO_TIME, default = true),
            automaticTimeZone = globalFlag(Settings.Global.AUTO_TIME_ZONE, default = true),
            screenLock = context.getSystemService(KeyguardManager::class.java)
                ?.let { boolText(it.isDeviceSecure) } ?: UNAVAILABLE_TEXT,
            biometrics = readBiometricSensors(),
            strongBox = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                boolText(context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE))
            } else {
                UNAVAILABLE_TEXT
            },
            nfc = readNfc(),
            internetAccess = network.first,
            bandwidthEstimate = network.second,
            networkMetered = network.third,
            wifiSecurity = wifiConnection.first,
            wifiMloLinks = wifiConnection.second,
            cellSignalDetails = cellDetails.first,
            cellSignalBars = cellDetails.second,
            cellBand = cellDetails.third,
            removableVolumes = readRemovableVolumes(),
            usbDevices = readUsbDevices(),
        )
    }

    /**
     * A pseudo-file read without [readFileTextOnce]'s cache: that cache is only safe
     * under the stats mutex, and the device facts and state are read outside it.
     */
    private fun readSysFile(path: String): String? = try {
        File(path).readText()
    } catch (_: Exception) {
        null
    }

    /** Widevine's security level from a throwaway MediaDrm session; the DRM is closed at once. */
    private fun readWidevineLevel(): String {
        var drm: MediaDrm? = null
        return try {
            drm = MediaDrm(WIDEVINE_UUID)
            drm.getPropertyString("securityLevel").takeIf { it.isNotBlank() } ?: UNAVAILABLE_TEXT
        } catch (_: Exception) {
            // UnsupportedSchemeException: no Widevine at all.
            context.getString(R.string.widevine_none)
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) drm?.close() else @Suppress("DEPRECATION") drm?.release()
        }
    }

    private fun readHardwareDecoders(): String {
        val codecs = try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.map { info ->
                CodecRecord(
                    types = info.supportedTypes.toList(),
                    hardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        info.isHardwareAccelerated
                    } else {
                        // Before Android 10 the software codecs are the platform's own.
                        !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")
                    },
                    encoder = info.isEncoder,
                )
            }
        } catch (_: Exception) {
            return UNAVAILABLE_TEXT
        }
        return ExtraStatsLogic.hardwareDecoders(codecs).takeIf { it.isNotEmpty() }?.joinToString(", ")
            ?: context.getString(R.string.decoders_none)
    }

    private fun readGnssHardware(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return UNAVAILABLE_TEXT
        val location = context.getSystemService(LocationManager::class.java) ?: return UNAVAILABLE_TEXT
        return ExtraStatsLogic.gnssHardwareText(location.gnssHardwareModelName, location.gnssYearOfHardware)
            ?: UNAVAILABLE_TEXT
    }

    /**
     * Raw measurements and navigation messages (the inputs precise-positioning
     * apps use), antenna info and measurement corrections, as far as the running
     * Android reports them.
     */
    private fun readGnssCapabilities(): String {
        // The capability getters became public in Android 12, the corrections one in 14.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return UNAVAILABLE_TEXT
        val capabilities = context.getSystemService(LocationManager::class.java)?.gnssCapabilities
            ?: return UNAVAILABLE_TEXT
        val names = ExtraStatsLogic.gnssCapabilities(
            measurements = capabilities.hasMeasurements(),
            navigationMessages = capabilities.hasNavigationMessages(),
            antennaInfo = capabilities.hasAntennaInfo(),
            corrections = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                capabilities.hasMeasurementCorrections(),
        )
        if (names.isEmpty()) return context.getString(R.string.gnss_capabilities_none)
        return names.joinToString(", ") { capability ->
            context.getString(
                when (capability) {
                    GnssCapability.MEASUREMENTS -> R.string.gnss_measurements
                    GnssCapability.NAVIGATION_MESSAGES -> R.string.gnss_navigation_messages
                    GnssCapability.ANTENNA_INFO -> R.string.gnss_antenna_info
                    GnssCapability.CORRECTIONS -> R.string.gnss_corrections
                }
            )
        }
    }

    private fun readCpuClusters(): String {
        val cores = (0 until Runtime.getRuntime().availableProcessors()).mapNotNull { index ->
            val base = "/sys/devices/system/cpu/cpu$index/cpufreq"
            val max = readSysFile("$base/cpuinfo_max_freq")?.trim()?.toLongOrNull() ?: return@mapNotNull null
            val min = readSysFile("$base/cpuinfo_min_freq")?.trim()?.toLongOrNull() ?: return@mapNotNull null
            min to max
        }
        return ExtraStatsLogic.cpuClustersText(cores) ?: UNAVAILABLE_TEXT
    }

    private fun readBiometricSensors(): String {
        val packageManager = context.packageManager
        val q = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val sensors = ExtraStatsLogic.biometricSensors(
            fingerprint = packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT),
            face = q && packageManager.hasSystemFeature(PackageManager.FEATURE_FACE),
            iris = q && packageManager.hasSystemFeature(PackageManager.FEATURE_IRIS),
        )
        if (sensors.isEmpty()) return context.getString(R.string.biometrics_none)
        return sensors.joinToString(", ") { sensor ->
            context.getString(
                when (sensor) {
                    BiometricSensor.FINGERPRINT -> R.string.biometrics_fingerprint
                    BiometricSensor.FACE -> R.string.biometrics_face
                    BiometricSensor.IRIS -> R.string.biometrics_iris
                }
            )
        }
    }

    private fun readNfc(): String {
        val adapter = try {
            NfcAdapter.getDefaultAdapter(context)
        } catch (_: Exception) {
            null
        }
        val enabled = try {
            adapter?.isEnabled == true
        } catch (_: Exception) {
            false
        }
        return context.getString(
            when (ExtraStatsLogic.nfcState(present = adapter != null, enabled = enabled)) {
                NfcState.NONE -> R.string.nfc_none
                NfcState.OFF -> R.string.nfc_off
                NfcState.ON -> R.string.nfc_on
            }
        )
    }

    /** The slider position Settings shows, and whether it follows the light. */
    private fun readBrightness(): String {
        val raw = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (_: Exception) {
            return UNAVAILABLE_TEXT
        }
        val percent = ExtraStatsLogic.brightnessSliderPercent(raw)
        val automatic = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) ==
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (_: Exception) {
            false
        }
        return context.getString(
            if (automatic) R.string.display_brightness_auto else R.string.display_brightness_manual,
            percent
        )
    }

    private fun readScreenTimeout(): String {
        val millis = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT).toLong()
        } catch (_: Exception) {
            return UNAVAILABLE_TEXT
        }
        val seconds = millis / 1000
        return when {
            seconds <= 0 -> UNAVAILABLE_TEXT
            seconds < 60 -> context.getString(R.string.duration_seconds, seconds)
            seconds < 3600 -> context.getString(R.string.duration_minutes, seconds / 60)
            // Some makers offer "never" as a very large value.
            seconds >= 24 * 3600 -> context.getString(R.string.display_timeout_never)
            else -> context.getString(R.string.duration_hours, seconds / 3600)
        }
    }

    /** A never-written flag holds the platform default, so [default] stands in for it. */
    private fun globalFlag(name: String, default: Boolean): String = try {
        boolText(Settings.Global.getInt(context.contentResolver, name, if (default) 1 else 0) != 0)
    } catch (_: Exception) {
        UNAVAILABLE_TEXT
    }

    /** Internet reachability, bandwidth estimate and metering of the active network. */
    private fun readActiveNetwork(): Triple<String, String, String> {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
            ?: return Triple(context.getString(R.string.internet_no_network), UNAVAILABLE_TEXT, UNAVAILABLE_TEXT)
        val state = ExtraStatsLogic.internetState(
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            captivePortal = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
        )
        val access = context.getString(
            when (state) {
                InternetState.VALIDATED -> R.string.internet_validated
                InternetState.CAPTIVE_PORTAL -> R.string.internet_captive_portal
                InternetState.NOT_VALIDATED -> R.string.internet_not_validated
            }
        )
        val bandwidth = ExtraStatsLogic.bandwidthText(caps.linkDownstreamBandwidthKbps, caps.linkUpstreamBandwidthKbps)
            ?: UNAVAILABLE_TEXT
        val metered = boolText(!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
        return Triple(access, bandwidth, metered)
    }

    /** Security type and multi-link count of the Wi-Fi connection; dashes when not on Wi-Fi. */
    @Suppress("DEPRECATION") // connectionInfo still carries these, un-redacted
    private fun readWifiConnection(): Pair<String, String> {
        val none = UNAVAILABLE_TEXT to UNAVAILABLE_TEXT
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return none
        val onWifi = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        if (!onWifi) return none
        val info = try {
            context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo
        } catch (_: SecurityException) {
            null
        } ?: return none
        val security = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val type = info.currentSecurityType
            if (type == WifiInfo.SECURITY_TYPE_OPEN) {
                context.getString(R.string.wifi_security_open)
            } else {
                ExtraStatsLogic.wifiSecurityName(type) ?: UNAVAILABLE_TEXT
            }
        } else {
            UNAVAILABLE_TEXT
        }
        val mlo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val links = info.associatedMloLinks.size
            if (links > 0) links.toString() else context.getString(R.string.wifi_mlo_none)
        } else {
            UNAVAILABLE_TEXT
        }
        return security to mlo
    }

    /**
     * Signal quality, bars and band of the serving cell. With 5G on an LTE anchor
     * (non-standalone) both signals are reported; the NR one is shown. The band
     * needs the cell list, which needs the location permission.
     */
    private fun readCellDetails(): Triple<String, String, String> {
        val none = Triple(UNAVAILABLE_TEXT, UNAVAILABLE_TEXT, UNAVAILABLE_TEXT)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return none
        val telephony = context.getSystemService(TelephonyManager::class.java) ?: return none
        val strengths = try {
            telephony.signalStrength?.cellSignalStrengths.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
        val serving = strengths.firstOrNull { it is CellSignalStrengthNr } ?: strengths.firstOrNull()
        val details = when (serving) {
            is CellSignalStrengthNr -> ExtraStatsLogic.signalDetails(
                ExtraStatsLogic.cellValue(serving.ssRsrp),
                ExtraStatsLogic.cellValue(serving.ssRsrq),
                ExtraStatsLogic.cellValue(serving.ssSinr),
            )
            is CellSignalStrengthLte -> ExtraStatsLogic.signalDetails(
                ExtraStatsLogic.cellValue(serving.rsrp),
                ExtraStatsLogic.cellValue(serving.rsrq),
                ExtraStatsLogic.lteRssnrDb(ExtraStatsLogic.cellValue(serving.rssnr), Build.VERSION.SDK_INT),
            )
            else -> null
        }
        val bars = serving?.let { "${it.level} / 4" }
        val band = readRegisteredBands(telephony)
        return Triple(details ?: UNAVAILABLE_TEXT, bars ?: UNAVAILABLE_TEXT, band ?: UNAVAILABLE_TEXT)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun readRegisteredBands(telephony: TelephonyManager): String? {
        if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val cells = try {
            telephony.allCellInfo.orEmpty().filter { it.isRegistered }
        } catch (_: SecurityException) {
            return null
        }
        val parts = cells.mapNotNull { cell ->
            when (cell) {
                is CellInfoNr -> {
                    val identity = cell.cellIdentity as? CellIdentityNr ?: return@mapNotNull null
                    val bands = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        ExtraStatsLogic.bandsText(nr = true, identity.bands)
                    } else {
                        null
                    }
                    val arfcn = ExtraStatsLogic.cellValue(identity.nrarfcn)
                    listOfNotNull("5G NR", bands, arfcn?.let { "ARFCN $it" }).joinToString(" · ")
                }
                is CellInfoLte -> {
                    val identity = cell.cellIdentity
                    val bands = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        ExtraStatsLogic.bandsText(nr = false, identity.bands)
                    } else {
                        null
                    }
                    val earfcn = ExtraStatsLogic.cellValue(identity.earfcn)
                    listOfNotNull("LTE", bands, earfcn?.let { "EARFCN $it" }).joinToString(" · ")
                }
                else -> null
            }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" + ")
    }

    private fun readRemovableVolumes(): List<Pair<String, String>> {
        val volumes = try {
            context.getSystemService(StorageManager::class.java)?.storageVolumes.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        val infos = volumes.map { volume ->
            val directory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) volume.directory else null
            val stat = directory?.let { dir -> runCatching { StatFs(dir.path) }.getOrNull() }
            VolumeInfo(
                description = volume.getDescription(context),
                primary = volume.isPrimary,
                removable = volume.isRemovable,
                mounted = volume.state == Environment.MEDIA_MOUNTED ||
                    volume.state == Environment.MEDIA_MOUNTED_READ_ONLY,
                totalBytes = stat?.totalBytes ?: 0L,
                freeBytes = stat?.availableBytes ?: 0L,
            )
        }
        return ExtraStatsLogic.removableVolumes(infos).map { volume ->
            volume.description to when {
                !volume.mounted -> context.getString(R.string.volume_not_mounted)
                volume.totalBytes > 0L -> context.getString(
                    R.string.volume_free_of,
                    "%.1f GB".format(volume.freeBytes / GB_BYTES),
                    "%.1f GB".format(volume.totalBytes / GB_BYTES),
                )
                else -> context.getString(R.string.volume_mounted)
            }
        }
    }

    private fun readUsbDevices(): String {
        val devices = try {
            context.getSystemService(UsbManager::class.java)?.deviceList?.values.orEmpty()
        } catch (_: Exception) {
            return UNAVAILABLE_TEXT
        }
        if (devices.isEmpty()) return context.getString(R.string.usb_devices_none)
        return devices.joinToString(", ") { device ->
            val (maker, product) = try {
                device.manufacturerName to device.productName
            } catch (_: SecurityException) {
                null to null
            }
            ExtraStatsLogic.usbDeviceName(maker, product, device.vendorId, device.productId)
        }
    }

    override suspend fun monthlyDataUsage(monthsBack: Int): List<MonthlyDataUsage> = withContext(dispatcher) {
        val ranges = DataPeriodCalculator.monthRanges(LocalDate.now(), ZoneId.systemDefault(), monthsBack)
        if (!hasUsageStatsAccess()) {
            return@withContext ranges.map {
                MonthlyDataUsage(it.month, UNAVAILABLE_DOUBLE, UNAVAILABLE_DOUBLE)
            }
        }
        val currentMonth = ranges.first().month
        ranges.map { range ->
            var mobileGb = readNetworkUsageGb(
                ConnectivityManager.TYPE_MOBILE, range.startMillis, range.endMillis
            )
            var wifiGb = readNetworkUsageGb(
                ConnectivityManager.TYPE_WIFI, range.startMillis, range.endMillis
            )
            // A month entirely absent from Android's stats (beyond retention, or
            // before the device was set up) returns empty buckets — exact zeros on
            // both networks. Report those as unavailable rather than a made-up
            // "0 MB"; a real month with the device in use is never 0 B on both.
            if (range.month != currentMonth && mobileGb == 0.0 && wifiGb == 0.0) {
                mobileGb = UNAVAILABLE_DOUBLE
                wifiGb = UNAVAILABLE_DOUBLE
            }
            MonthlyDataUsage(month = range.month, mobileGb = mobileGb, wifiGb = wifiGb)
        }
    }

    @Suppress("DEPRECATION") // Display.getRealMetrics/isHdr are the broadest cross-version reads
    private fun computeDeviceInfo(): DeviceInfo {
        val manufacturer = Build.MANUFACTURER
            ?.replaceFirstChar { it.uppercase() }
            ?.takeIf { it.isNotBlank() } ?: UNAVAILABLE_TEXT
        val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: UNAVAILABLE_TEXT
        val codename = Build.DEVICE?.takeIf { it.isNotBlank() } ?: UNAVAILABLE_TEXT
        val androidVersion = ExtraStatsLogic.androidVersionText(
            release = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            sdkIntFull = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) Build.VERSION.SDK_INT_FULL else null,
        )
        val advertisedRam = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val memoryInfo = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(memoryInfo)
            ExtraStatsLogic.advertisedRamGb(memoryInfo.advertisedMem)?.let { "$it GB" } ?: UNAVAILABLE_TEXT
        } else {
            UNAVAILABLE_TEXT
        }
        val securityPatch = Build.VERSION.SECURITY_PATCH?.takeIf { it.isNotBlank() } ?: UNAVAILABLE_TEXT
        val buildNumber = Build.DISPLAY?.takeIf { it.isNotBlank() } ?: UNAVAILABLE_TEXT
        val bootloader = Build.BOOTLOADER?.takeIf { it.isNotBlank() && it != Build.UNKNOWN } ?: UNAVAILABLE_TEXT
        val radioVersion = Build.getRadioVersion()?.takeIf { it.isNotBlank() } ?: UNAVAILABLE_TEXT
        val soc = readSoc()
        val supportedAbis = Build.SUPPORTED_ABIS
            ?.joinToString(", ")
            ?.takeIf { it.isNotBlank() } ?: UNAVAILABLE_TEXT
        val kernelVersion = System.getProperty("os.version")?.takeIf { it.isNotBlank() } ?: UNAVAILABLE_TEXT
        val (gpuRenderer, glVersion) = readGpuInfo()

        val display = (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        var screenResolution = UNAVAILABLE_TEXT
        var screenDensity = UNAVAILABLE_TEXT
        var physicalSize = UNAVAILABLE_TEXT
        if (display != null) {
            val metrics = DisplayMetrics()
            display.getRealMetrics(metrics)
            if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
                screenResolution = "${metrics.widthPixels} × ${metrics.heightPixels} px"
                screenDensity = "${metrics.densityDpi} dpi · ${SystemStatsParser.densityBucketLabel(metrics.densityDpi)}"
                if (metrics.xdpi > 0f && metrics.ydpi > 0f) {
                    val widthInches = metrics.widthPixels / metrics.xdpi
                    val heightInches = metrics.heightPixels / metrics.ydpi
                    physicalSize = String.format(
                        Locale.US, "%.1f\"", sqrt(widthInches * widthInches + heightInches * heightInches)
                    )
                }
            }
        }
        val refreshRate = formatRefreshRate(display)
        val hdr = if (display != null) boolText(display.isHdr) else UNAVAILABLE_TEXT

        val totalRam = readTotalRamGb()
            ?.let { String.format(Locale.US, "%.1f GB", it) } ?: UNAVAILABLE_TEXT
        val totalStorage = readTotalStorageGb()
            ?.let { String.format(Locale.US, "%.0f GB", it) } ?: UNAVAILABLE_TEXT

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryTechnology = batteryIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
            ?.takeIf { it.isNotBlank() } ?: UNAVAILABLE_TEXT
        val batteryCapacityMah = readBatteryCapacityMah()

        val camera = readCameraSummary()
        val (sensorCount, sensors) = readSensorSummary()

        val locale = Locale.getDefault().toLanguageTag()
        val timezone = TimeZone.getDefault().id
        val webViewVersion = readWebViewVersion()
        val playServicesVersion = readPackageVersion("com.google.android.gms")
        val playSystemUpdate = readPlaySystemUpdate()
        val mainlineModules = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                context.packageManager.getInstalledModules(0).size.takeIf { it > 0 }?.toString()
            } catch (_: Exception) {
                null
            } ?: UNAVAILABLE_TEXT
        } else {
            UNAVAILABLE_TEXT
        }
        val deviceFeatures = readDeviceFeatures()
        val bootCountTotal = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT).toString()
        } catch (_: Exception) {
            UNAVAILABLE_TEXT
        }

        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = connManager?.activeNetwork
        val vpnActive = readVpnActive(connManager, activeNetwork)
        val dnsServers = readDnsServers(connManager, activeNetwork)
        val linkProperties = connManager?.getLinkProperties(activeNetwork)
        val privateDns = if (linkProperties != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val dns = ExtraStatsLogic.privateDns(
                linkProperties.isPrivateDnsActive,
                linkProperties.privateDnsServerName
            )
            when (dns.mode) {
                PrivateDnsMode.OFF -> context.getString(R.string.private_dns_off)
                PrivateDnsMode.AUTOMATIC -> context.getString(R.string.private_dns_automatic)
                PrivateDnsMode.HOSTNAME -> dns.serverName ?: UNAVAILABLE_TEXT
            }
        } else {
            UNAVAILABLE_TEXT
        }
        val networkInterface = linkProperties?.interfaceName?.takeIf { it.isNotBlank() } ?: UNAVAILABLE_TEXT
        val networkMtu = if (linkProperties != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            linkProperties.mtu
        } else {
            0
        }
        val interfaceMtu = try {
            linkProperties?.interfaceName?.let { java.net.NetworkInterface.getByName(it)?.mtu }
        } catch (_: Exception) {
            null
        }
        val mtu = ExtraStatsLogic.mtu(networkMtu, interfaceMtu)?.toString() ?: UNAVAILABLE_TEXT

        return DeviceInfo(
            manufacturer = manufacturer,
            model = model,
            codename = codename,
            androidVersion = androidVersion,
            securityPatch = securityPatch,
            buildNumber = buildNumber,
            bootloader = bootloader,
            radioVersion = radioVersion,
            soc = soc,
            supportedAbis = supportedAbis,
            kernelVersion = kernelVersion,
            gpuRenderer = gpuRenderer,
            glVersion = glVersion,
            screenResolution = screenResolution,
            screenDensity = screenDensity,
            physicalSize = physicalSize,
            refreshRate = refreshRate,
            hdr = hdr,
            totalRam = totalRam,
            advertisedRam = advertisedRam,
            totalStorage = totalStorage,
            batteryTechnology = batteryTechnology,
            batteryCapacityMah = batteryCapacityMah,
            cameraCount = camera.count,
            rearCamera = camera.rear,
            frontCamera = camera.front,
            cameraFlash = camera.flash,
            sensorCount = sensorCount,
            sensors = sensors,
            locale = locale,
            timezone = timezone,
            webViewVersion = webViewVersion,
            playServicesVersion = playServicesVersion,
            playSystemUpdate = playSystemUpdate,
            mainlineModules = mainlineModules,
            deviceFeatures = deviceFeatures,
            bootCountTotal = bootCountTotal,
            vpnActive = vpnActive,
            dnsServers = dnsServers,
            wifiCapabilities = readWifiCapabilities(),
            cpuClusters = readCpuClusters(),
            gnssHardware = readGnssHardware(),
            vulkanVersion = context.packageManager.systemAvailableFeatures
                .firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }
                ?.let { ExtraStatsLogic.vulkanVersionText(it.version) } ?: UNAVAILABLE_TEXT,
            widevineLevel = readWidevineLevel(),
            hardwareDecoders = readHardwareDecoders(),
            gnssCapabilities = readGnssCapabilities(),
            cpuGovernor = readSysFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
                ?.trim()?.takeIf { it.isNotEmpty() } ?: UNAVAILABLE_TEXT,
            privateDns = privateDns,
            networkInterface = networkInterface,
            mtu = mtu,
        )
    }

    private fun boolText(value: Boolean): String =
        context.getString(if (value) R.string.common_yes else R.string.common_no)

    private fun readBatteryCapacityMah(): String {
        return try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val instance = powerProfileClass.getConstructor(Context::class.java).newInstance(context)
            val mah = powerProfileClass.getMethod("getBatteryCapacity").invoke(instance) as Double
            if (mah > 0.0) "${mah.roundToInt()} mAh" else UNAVAILABLE_TEXT
        } catch (_: Throwable) {
            UNAVAILABLE_TEXT
        }
    }

    private data class CameraSummary(
        val count: String,
        val rear: String,
        val front: String,
        val flash: String,
    )

    private fun readCameraSummary(): CameraSummary {
        val unavailable = CameraSummary(UNAVAILABLE_TEXT, UNAVAILABLE_TEXT, UNAVAILABLE_TEXT, UNAVAILABLE_TEXT)
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return unavailable
        return try {
            val ids = cameraManager.cameraIdList
            var rearMp = 0
            var frontMp = 0
            var anyFlash = false
            for (id in ids) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                val size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                val megapixels = if (size != null) {
                    ((size.width.toLong() * size.height) / 1_000_000.0).roundToInt()
                } else {
                    0
                }
                if (characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) anyFlash = true
                when (facing) {
                    CameraCharacteristics.LENS_FACING_BACK -> if (megapixels > rearMp) rearMp = megapixels
                    CameraCharacteristics.LENS_FACING_FRONT -> if (megapixels > frontMp) frontMp = megapixels
                }
            }
            CameraSummary(
                count = if (ids.isNotEmpty()) ids.size.toString() else UNAVAILABLE_TEXT,
                rear = if (rearMp > 0) "$rearMp MP" else UNAVAILABLE_TEXT,
                front = if (frontMp > 0) "$frontMp MP" else UNAVAILABLE_TEXT,
                flash = boolText(anyFlash),
            )
        } catch (_: Exception) {
            unavailable
        }
    }

    private fun readSensorSummary(): Pair<String, String> {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return UNAVAILABLE_TEXT to UNAVAILABLE_TEXT
        val count = sensorManager.getSensorList(Sensor.TYPE_ALL).size
        val present = buildList {
            if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) add(context.getString(R.string.sensor_accelerometer))
            if (sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) add(context.getString(R.string.sensor_gyroscope))
            if (sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null) add(context.getString(R.string.sensor_magnetometer))
            if (sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null) add(context.getString(R.string.sensor_barometer))
            if (sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null) add(context.getString(R.string.sensor_proximity))
            if (sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) != null) add(context.getString(R.string.sensor_light))
            if (sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null) add(context.getString(R.string.sensor_step_counter))
            if (sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE) != null) add(context.getString(R.string.sensor_heart_rate))
        }
        val countText = if (count > 0) count.toString() else UNAVAILABLE_TEXT
        val listText = if (present.isEmpty()) UNAVAILABLE_TEXT else present.joinToString(", ")
        return countText to listText
    }

    private fun readDeviceFeatures(): String {
        val pm = context.packageManager
        val present = buildList {
            if (pm.hasSystemFeature(PackageManager.FEATURE_NFC)) add(context.getString(R.string.feature_nfc))
            if (pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) add(context.getString(R.string.feature_fingerprint))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pm.hasSystemFeature(PackageManager.FEATURE_FACE)) {
                add(context.getString(R.string.feature_face))
            }
            if (pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) add(context.getString(R.string.feature_bluetooth_le))
            if (pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) add(context.getString(R.string.feature_telephony))
            if (pm.hasSystemFeature(PackageManager.FEATURE_CONSUMER_IR)) add(context.getString(R.string.feature_ir))
        }
        return if (present.isEmpty()) UNAVAILABLE_TEXT else present.joinToString(", ")
    }

    private fun readWebViewVersion(): String {
        return try {
            WebView.getCurrentWebViewPackage()?.versionName?.takeIf { it.isNotBlank() } ?: UNAVAILABLE_TEXT
        } catch (_: Exception) {
            UNAVAILABLE_TEXT
        }
    }

    @Suppress("DEPRECATION") // versionName (not the long code) is what users recognise here
    private fun readPackageVersion(packageName: String): String {
        return try {
            context.packageManager.getPackageInfo(packageName, 0).versionName
                ?.takeIf { it.isNotBlank() } ?: UNAVAILABLE_TEXT
        } catch (_: Exception) {
            UNAVAILABLE_TEXT
        }
    }

    /**
     * The date Settings shows as "Google Play system update": the versionName of the
     * module-metadata package (Google's on GMS devices, AOSP's otherwise).
     */
    private fun readPlaySystemUpdate(): String {
        val version = listOf("com.google.android.modulemetadata", "com.android.modulemetadata")
            .map(::readPackageVersion)
            .firstOrNull { it != UNAVAILABLE_TEXT }
            ?: return UNAVAILABLE_TEXT
        val update = ExtraStatsLogic.moduleUpdate(version) ?: return version
        val locale = context.resources.configuration.locales[0]
        return if (update.day != null) {
            update.month.atDay(update.day).format(
                java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG).withLocale(locale)
            )
        } else {
            update.month.format(java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        }
    }

    /** The Wi-Fi chip's support for 6 GHz (API 30), WPA3-SAE (API 29) and Wi-Fi 7 (API 30). */
    private fun readWifiCapabilities(): String {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return UNAVAILABLE_TEXT
        val features = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wifi.is6GHzBandSupported) add("6 GHz")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && wifi.isWpa3SaeSupported) add("WPA3")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                wifi.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11BE)
            ) {
                add("Wi-Fi 7")
            }
        }
        return if (features.isEmpty()) context.getString(R.string.wifi_capabilities_basic) else features.joinToString(", ")
    }

    private fun readVpnActive(connManager: ConnectivityManager?, network: Network?): String {
        val caps = connManager?.getNetworkCapabilities(network) ?: return UNAVAILABLE_TEXT
        return boolText(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
    }

    private fun readDnsServers(connManager: ConnectivityManager?, network: Network?): String {
        val dns = connManager?.getLinkProperties(network)?.dnsServers?.mapNotNull { it.hostAddress }
        return if (dns.isNullOrEmpty()) UNAVAILABLE_TEXT else dns.joinToString(", ")
    }

    /** Reads GPU renderer + GL version via a throwaway offscreen EGL/GLES context. */
    private fun readGpuInfo(): Pair<String, String> {
        return try {
            val eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) return UNAVAILABLE_TEXT to UNAVAILABLE_TEXT
            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                return UNAVAILABLE_TEXT to UNAVAILABLE_TEXT
            }
            try {
                val configAttribs = intArrayOf(
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_NONE
                )
                val configs = arrayOfNulls<EGLConfig>(1)
                val numConfig = IntArray(1)
                if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfig, 0) ||
                    numConfig[0] == 0 || configs[0] == null
                ) {
                    return UNAVAILABLE_TEXT to UNAVAILABLE_TEXT
                }
                val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
                val eglContext = EGL14.eglCreateContext(
                    eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0
                )
                if (eglContext == EGL14.EGL_NO_CONTEXT) return UNAVAILABLE_TEXT to UNAVAILABLE_TEXT
                val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
                val eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0], surfaceAttribs, 0)
                try {
                    if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                        return UNAVAILABLE_TEXT to UNAVAILABLE_TEXT
                    }
                    val renderer = GLES20.glGetString(GLES20.GL_RENDERER)?.takeIf { it.isNotBlank() } ?: UNAVAILABLE_TEXT
                    val gl = GLES20.glGetString(GLES20.GL_VERSION)?.takeIf { it.isNotBlank() } ?: UNAVAILABLE_TEXT
                    renderer to gl
                } finally {
                    EGL14.eglMakeCurrent(
                        eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
                    )
                    if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                }
            } finally {
                EGL14.eglTerminate(eglDisplay)
            }
        } catch (_: Throwable) {
            UNAVAILABLE_TEXT to UNAVAILABLE_TEXT
        }
    }

    private fun readSoc(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mfr = Build.SOC_MANUFACTURER.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
            val mdl = Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
            val combined = listOfNotNull(mfr, mdl).joinToString(" ")
            if (combined.isNotBlank()) return combined
        }
        return Build.HARDWARE?.takeIf { it.isNotBlank() } ?: UNAVAILABLE_TEXT
    }

    private fun formatRefreshRate(display: Display?): String {
        if (display == null) return UNAVAILABLE_TEXT
        val rates = display.supportedModes.map { it.refreshRate }.filter { it > 0f }
        if (rates.isEmpty()) {
            return display.refreshRate.takeIf { it > 0f }?.let { "${it.roundToInt()} Hz" } ?: UNAVAILABLE_TEXT
        }
        val min = rates.min().roundToInt()
        val max = rates.max().roundToInt()
        return if (max - min > 1) "$min–$max Hz" else "$max Hz"
    }

    private fun readTotalRamGb(): Double? {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return null
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return (memoryInfo.totalMem.toDouble() / GB_BYTES).takeIf { memoryInfo.totalMem > 0 }
    }

    private fun readTotalStorageGb(): Double? {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            (totalBytes.toDouble() / GB_BYTES).takeIf { totalBytes > 0 }
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun computeStats(): SystemStats {
        val batteryStatusIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val level = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryLevel = if (level >= 0 && scale > 0) (level * 100) / scale else UNAVAILABLE_INT

        val status = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val batteryStatus = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> context.getString(R.string.battery_status_charging)
            BatteryManager.BATTERY_STATUS_DISCHARGING -> context.getString(R.string.battery_status_discharging)
            BatteryManager.BATTERY_STATUS_FULL -> context.getString(R.string.battery_status_full)
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> context.getString(R.string.battery_status_not_charging)
            else -> UNAVAILABLE_TEXT
        }

        val chargeSource = ExtraStatsLogic.chargeSource(
            batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        )
        val chargingState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ExtraStatsLogic.chargingState(
                batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_CHARGING_STATUS, 0) ?: 0
            )
        } else {
            ChargingState.UNKNOWN
        }

        val health = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val batteryHealth = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> context.getString(R.string.battery_health_good)
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> context.getString(R.string.battery_health_overheat)
            BatteryManager.BATTERY_HEALTH_DEAD -> context.getString(R.string.battery_health_dead)
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> context.getString(R.string.battery_health_over_voltage)
            BatteryManager.BATTERY_HEALTH_COLD -> context.getString(R.string.battery_health_cold)
            else -> UNAVAILABLE_TEXT
        }

        val tempRaw = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val batteryTemp = if (tempRaw != -1) tempRaw / 10.0 else UNAVAILABLE_DOUBLE

        val voltRaw = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val batteryVoltage = when {
            voltRaw > 1000 -> voltRaw / 1000.0
            voltRaw > 0 -> voltRaw.toDouble()
            else -> UNAVAILABLE_DOUBLE
        }

        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryCycleCount = batteryStatusIntent
            ?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, UNAVAILABLE_INT)
            ?.takeIf { it >= 0 }
            ?: readIntFromFiles(
                "/sys/class/power_supply/battery/cycle_count",
                "/sys/class/power_supply/bms/cycle_count"
            )
            ?: UNAVAILABLE_INT
        val batteryCapacityPercent = readBatteryCapacityPercent()

        val timeRemainingText = buildBatteryTimeText(status, batteryManager)
        val (systemEstimateText, systemEstimatePersonalized) = readSystemEstimate(status)
        val thermalLevel = readThermalLevel()
        val thermalHeadroomPercent = readThermalHeadroomPercent()

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalRamGb = memoryInfo.totalMem.toDouble() / GB_BYTES
        val availRamGb = memoryInfo.availMem.toDouble() / GB_BYTES
        val usedRamGb = totalRamGb - availRamGb
        val ramPercent = if (totalRamGb > 0) ((usedRamGb / totalRamGb) * 100).toInt() else UNAVAILABLE_INT

        val cpuCores = Runtime.getRuntime().availableProcessors()
        val cpuAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: UNAVAILABLE_TEXT
        val cpuFreqGhz = readCpuFreqGhz(cpuCores)
        val cpuTemp = readCpuTemperature()
        val cpuLoad = readCpuLoad(cpuCores)

        val stat = StatFs(Environment.getDataDirectory().path)
        val blockSize = stat.blockSizeLong
        val totalStorageGb = (stat.blockCountLong * blockSize).toDouble() / GB_BYTES
        val freeStorageGb = (stat.availableBlocksLong * blockSize).toDouble() / GB_BYTES
        val usedStorageGb = totalStorageGb - freeStorageGb
        val storagePercent = if (totalStorageGb > 0) {
            ((usedStorageGb / totalStorageGb) * 100).toInt()
        } else {
            UNAVAILABLE_INT
        }

        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val activeNetwork = connManager?.activeNetwork
        val capabilities = connManager?.getNetworkCapabilities(activeNetwork)
        val isWifiConnected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        var wifiSsid = if (isWifiConnected) {
            context.getString(R.string.wifi_connected)
        } else {
            context.getString(R.string.not_connected)
        }
        var wifiBand = UNAVAILABLE_TEXT
        var wifiSpeedDown = UNAVAILABLE_INT
        var wifiSpeedUp = UNAVAILABLE_INT
        if (isWifiConnected && capabilities != null) {
            wifiSpeedDown = capabilities.linkDownstreamBandwidthKbps.takeIf { it > 0 }?.div(1000) ?: UNAVAILABLE_INT
            wifiSpeedUp = capabilities.linkUpstreamBandwidthKbps.takeIf { it > 0 }?.div(1000) ?: UNAVAILABLE_INT

            if (canReadWifiIdentity()) {
                try {
                    val wifiInfo = wifiInfoFromCapabilities(capabilities) ?: wifiManager?.connectionInfo
                    wifiSsid = SystemStatsParser.normalizedWifiSsid(wifiInfo?.ssid) ?: wifiSsid
                    wifiBand = SystemStatsParser.wifiBandLabel(wifiInfo?.frequency ?: UNAVAILABLE_INT)
                } catch (_: SecurityException) {
                    wifiSsid = context.getString(R.string.wifi_connected)
                    wifiBand = UNAVAILABLE_TEXT
                }
            }
        }
        // The settings screen shows the real SSID; the deprecated connectionInfo returns it
        // un-redacted (the transportInfo from a synchronous getNetworkCapabilities() is always
        // location-redacted to <unknown ssid>). The widget keeps the compact wifiSsid label
        // instead, because a full network name doesn't fit the widget tile.
        val wifiSsidName = if (isWifiConnected && canReadWifiIdentity()) {
            val realSsid = try {
                SystemStatsParser.normalizedWifiSsid(wifiManager?.connectionInfo?.ssid)
                    ?: capabilities?.let { SystemStatsParser.normalizedWifiSsid(wifiInfoFromCapabilities(it)?.ssid) }
            } catch (_: SecurityException) {
                null
            }
            realSsid ?: wifiSsid
        } else {
            wifiSsid
        }

        // Read before any data setting: a change landing during this read then
        // leaves the reading with an older generation than the settings, and the
        // monitor drops it instead of acting on a mix of old and new settings.
        val dataSettingsGeneration = settings.dataSettingsGeneration()
        // Data counters cover the user-selected period: the current calendar day (default)
        // or a one-month billing cycle starting on the chosen day of month.
        val counterMode = settings.dataCounterMode()
        val periodStartDay = DataPeriodCalculator
            .periodStart(counterMode, settings.cycleStartDay(), LocalDate.now())
        val periodStartMillis = periodStartDay
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val periodLabel = context.getString(
            if (counterMode == DataCounterMode.DAY) {
                R.string.mobile_data_today_label
            } else {
                R.string.data_period_label
            }
        )

        val wifiBytesTodayGb = readNetworkUsageGb(ConnectivityManager.TYPE_WIFI, periodStartMillis)

        // RSSI / link speed / standard are not redacted by the location permission (only the
        // SSID/BSSID are), so these populate even when the identity read above is blocked.
        var wifiRssiDbm = UNAVAILABLE_INT
        var wifiLinkSpeedMbps = UNAVAILABLE_INT
        var wifiStandard = UNAVAILABLE_TEXT
        if (isWifiConnected && capabilities != null) {
            val wifiInfo = wifiInfoFromCapabilities(capabilities) ?: wifiManager?.connectionInfo
            if (wifiInfo != null) {
                wifiRssiDbm = wifiInfo.rssi.takeIf { it != -127 && it < 0 } ?: UNAVAILABLE_INT
                wifiLinkSpeedMbps = wifiInfo.linkSpeed.takeIf { it > 0 } ?: UNAVAILABLE_INT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    wifiStandard = SystemStatsParser.wifiStandardLabel(wifiInfo.wifiStandard)
                }
            }
        }
        val ipAddress = readIpAddress(connManager, activeNetwork)

        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        var operatorName = UNAVAILABLE_TEXT
        var mobileNetworkType = UNAVAILABLE_TEXT
        var mobileSignalDbm = UNAVAILABLE_INT
        try {
            if (telephonyManager != null) {
                operatorName = firstNonBlank(
                    telephonyManager.networkOperatorName,
                    telephonyManager.simOperatorName
                ) ?: UNAVAILABLE_TEXT
                mobileNetworkType = readMobileNetworkTypeLabel(telephonyManager)
                mobileSignalDbm = readMobileSignalDbm(telephonyManager)
            }
        } catch (_: SecurityException) {
            operatorName = UNAVAILABLE_TEXT
            mobileNetworkType = UNAVAILABLE_TEXT
            mobileSignalDbm = UNAVAILABLE_INT
        }
        val mobileDataTodayGb = readNetworkUsageGb(ConnectivityManager.TYPE_MOBILE, periodStartMillis)
        val mobileTrafficSinceBootGb = readMobileTrafficStatsGb()
        val mobileDataUsedGb = if (mobileDataTodayGb >= 0.0) mobileDataTodayGb else mobileTrafficSinceBootGb
        // The user's plan allowance, when set: the widget and the overview render
        // "used / quota" off this pair, and 0 (no quota) stays unavailable. Only
        // exposed when the period figure itself is available — comparing the
        // since-boot fallback against a period quota would draw a nonsense ratio.
        val mobileDataTotalGb = if (mobileDataTodayGb >= 0.0) {
            settings.dataQuotaGb().takeIf { it > 0.0 } ?: UNAVAILABLE_DOUBLE
        } else {
            UNAVAILABLE_DOUBLE
        }
        // The TrafficStats fallback always counts since boot, so it keeps its own label
        // regardless of the selected counter mode.
        val mobileDataLabel = when {
            mobileDataTodayGb >= 0.0 -> periodLabel
            mobileTrafficSinceBootGb >= 0.0 -> context.getString(R.string.mobile_data_since_boot_label)
            else -> context.getString(R.string.mobile_data_label)
        }

        // SIM facts that need no runtime permission.
        val simOperator = telephonyManager?.let {
            firstNonBlank(it.simOperatorName, it.networkOperatorName)
        } ?: UNAVAILABLE_TEXT
        val networkCountry = telephonyManager?.let {
            firstNonBlank(it.networkCountryIso, it.simCountryIso)?.uppercase()
        } ?: UNAVAILABLE_TEXT
        val simState = readSimStateLabel(telephonyManager)
        val simSlots = readSimSlotCount(telephonyManager)
        val dataSimName = readDataSimName()

        // Awake time first: elapsedRealtime() counts everything uptimeMillis() does,
        // so reading it second can never leave it behind on a phone that never slept.
        val awakeMs = SystemClock.uptimeMillis()
        val uptimeMs = SystemClock.elapsedRealtime()
        val uptimeHours = uptimeMs / (1000 * 60 * 60)
        val uptimeMins = (uptimeMs / (1000 * 60)) % 60
        val uptimeText = context.getString(R.string.uptime_short, uptimeHours, uptimeMins)
        val deepSleepText = ExtraStatsLogic.deepSleepMillis(uptimeMs, awakeMs)?.let { asleepMs ->
            val percent = ExtraStatsLogic.deepSleepPercent(uptimeMs, awakeMs) ?: 0
            context.getString(
                R.string.deep_sleep_value,
                context.getString(R.string.uptime_short, asleepMs / 3_600_000L, (asleepMs / 60_000L) % 60),
                percent
            )
        } ?: UNAVAILABLE_TEXT

        return SystemStats(
            batteryLevel = batteryLevel,
            batteryStatus = batteryStatus,
            batteryHealth = batteryHealth,
            batteryTemp = batteryTemp,
            batteryVoltage = batteryVoltage,
            timeRemainingText = timeRemainingText,
            batteryCycleCount = batteryCycleCount,
            batteryCapacityPercent = batteryCapacityPercent,
            totalRamGb = totalRamGb,
            usedRamGb = usedRamGb,
            ramPercent = ramPercent,
            cpuCores = cpuCores,
            cpuAbi = cpuAbi,
            cpuFreqGhz = cpuFreqGhz,
            cpuLoadPercent = cpuLoad.percent,
            cpuLoadLabel = cpuLoad.label,
            cpuTemp = cpuTemp,
            totalStorageGb = totalStorageGb,
            usedStorageGb = usedStorageGb,
            storagePercent = storagePercent,
            wifiSsid = wifiSsid,
            wifiSsidName = wifiSsidName,
            wifiBand = wifiBand,
            wifiSpeedDown = wifiSpeedDown,
            wifiSpeedUp = wifiSpeedUp,
            wifiBytesTodayGb = wifiBytesTodayGb,
            wifiDataLabel = periodLabel,
            operatorName = operatorName,
            mobileNetworkType = mobileNetworkType,
            mobileSignalDbm = mobileSignalDbm,
            mobileDataUsedGb = mobileDataUsedGb,
            mobileDataTotalGb = mobileDataTotalGb,
            mobileDataLabel = mobileDataLabel,
            simOperator = simOperator,
            simState = simState,
            simSlots = simSlots,
            dataSimName = dataSimName,
            networkCountry = networkCountry,
            wifiRssiDbm = wifiRssiDbm,
            wifiLinkSpeedMbps = wifiLinkSpeedMbps,
            wifiStandard = wifiStandard,
            ipAddress = ipAddress,
            uptimeText = uptimeText,
            uptimeMillis = uptimeMs,
            dataPeriodStartEpochDay = periodStartDay.toEpochDay(),
            dataSettingsGeneration = dataSettingsGeneration,
            chargeSource = chargeSource,
            chargingState = chargingState,
            systemEstimateText = systemEstimateText,
            systemEstimatePersonalized = systemEstimatePersonalized,
            deepSleepText = deepSleepText,
            thermalLevel = thermalLevel,
            thermalHeadroomPercent = thermalHeadroomPercent,
        )
    }

    /**
     * Carrier name of the SIM selected for mobile data, shown only on multi-SIM devices
     * ([SystemStatsParser.dataSimDisplayName]). Data usage itself can never be split per
     * SIM — subscriber ids are privileged since Android 10 — so this only labels which
     * SIM the aggregate mobile counter currently runs on.
     */
    private fun readDataSimName(): String {
        // Catches Exception, not just SecurityException: on Android 15 these
        // SubscriptionManager calls throw UnsupportedOperationException on devices
        // without FEATURE_TELEPHONY_SUBSCRIPTION (Wi-Fi-only tablets, Chromebooks).
        return try {
            val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
                ?: return UNAVAILABLE_TEXT
            // Single-SIM early-out before the more expensive per-subscription read —
            // this runs on the service's 5-second tick.
            val activeCount = subscriptionManager.activeSubscriptionInfoCount
            if (activeCount < 2) return UNAVAILABLE_TEXT
            val dataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
            val carrierName = if (dataSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                subscriptionManager.getActiveSubscriptionInfo(dataSubId)?.carrierName?.toString()
            } else {
                null
            }
            SystemStatsParser.dataSimDisplayName(carrierName, activeCount)
        } catch (_: SecurityException) {
            UNAVAILABLE_TEXT
        } catch (_: Exception) {
            UNAVAILABLE_TEXT
        }
    }

    private data class CpuLoadResult(val percent: Int, val label: String)

    private fun buildBatteryTimeText(status: Int, batteryManager: BatteryManager?): String {
        if (batteryManager == null) return UNAVAILABLE_TEXT

        return if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
            val chargeTimeMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                batteryManager.computeChargeTimeRemaining()
            } else {
                -1L
            }
            if (chargeTimeMs > 0) {
                val hours = chargeTimeMs / (1000 * 60 * 60)
                val minutes = (chargeTimeMs / (1000 * 60)) % 60
                if (hours > 0) {
                    context.getString(R.string.battery_time_until_full_hours, hours, minutes)
                } else {
                    context.getString(R.string.battery_time_until_full_minutes, minutes)
                }
            } else {
                UNAVAILABLE_TEXT
            }
        } else {
            val chargeCounter = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            val currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val totalMinutes = SystemStatsParser.dischargeTimeRemainingMinutes(chargeCounter, currentNow)
            totalMinutes?.let(::remainingText) ?: UNAVAILABLE_TEXT
        }
    }

    private fun remainingText(totalMinutes: Int): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            context.getString(R.string.battery_time_remaining_hours, hours, minutes)
        } else {
            context.getString(R.string.battery_time_remaining_minutes, minutes)
        }
    }

    private fun readThermalLevel(): ThermalLevel {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ThermalLevel.UNKNOWN
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return ThermalLevel.UNKNOWN
        return ExtraStatsLogic.thermalLevel(powerManager.currentThermalStatus)
    }

    // The headroom is rate-limited by the platform; the last answer is reused in
    // between. Only touched from computeStats, which runs under the stats mutex.
    private var lastHeadroomReadMillis: Long? = null
    private var lastHeadroomPercent: Int = UNAVAILABLE_INT

    private fun readThermalHeadroomPercent(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return UNAVAILABLE_INT
        val now = SystemClock.elapsedRealtime()
        if (!ExtraStatsLogic.headroomDue(lastHeadroomReadMillis, now)) return lastHeadroomPercent
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return UNAVAILABLE_INT
        lastHeadroomReadMillis = now
        lastHeadroomPercent = ExtraStatsLogic.headroomPercent(powerManager.getThermalHeadroom(0))
            ?: UNAVAILABLE_INT
        return lastHeadroomPercent
    }

    /**
     * The system's own discharge prediction (API 31), which Android fills from its
     * battery-usage model — on Pixels learned from the user's habits. Only asked on
     * battery: while charging the prediction is about discharge and says nothing.
     */
    private fun readSystemEstimate(status: Int): Pair<String, Boolean> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return UNAVAILABLE_TEXT to false
        if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) {
            return UNAVAILABLE_TEXT to false
        }
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return UNAVAILABLE_TEXT to false
        return try {
            val minutes = ExtraStatsLogic.predictionMinutes(powerManager.batteryDischargePrediction?.toMillis())
                ?: return UNAVAILABLE_TEXT to false
            remainingText(minutes) to powerManager.isBatteryDischargePredictionPersonalized
        } catch (_: RuntimeException) {
            UNAVAILABLE_TEXT to false
        }
    }

    private fun readBatteryCapacityPercent(): Int {
        val chargeFull = readLongFromFiles(
            "/sys/class/power_supply/battery/charge_full",
            "/sys/class/power_supply/battery/charge_full_uah",
            "/sys/class/power_supply/bms/charge_full"
        )
        val chargeFullDesign = readLongFromFiles(
            "/sys/class/power_supply/battery/charge_full_design",
            "/sys/class/power_supply/battery/charge_full_design_uah",
            "/sys/class/power_supply/bms/charge_full_design"
        )
        return SystemStatsParser.batteryCapacityPercent(chargeFull, chargeFullDesign)
    }

    private fun readCpuFreqGhz(cpuCores: Int): Double {
        for (cpuIndex in 0 until cpuCores) {
            val value = readLongFromFiles(
                "/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/scaling_cur_freq",
                "/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/cpuinfo_cur_freq"
            )
            if (value != null && value > 0L) {
                return value.toDouble() / 1_000_000.0
            }
        }
        return UNAVAILABLE_DOUBLE
    }

    private fun readCpuTemperature(): Double {
        if (skipThermalRead) return UNAVAILABLE_DOUBLE

        return try {
            val zones = File("/sys/class/thermal")
                .listFiles { file -> file.name.startsWith("thermal_zone") }
                ?: run {
                    skipThermalRead = true
                    return UNAVAILABLE_DOUBLE
                }

            val values = zones
                .asSequence()
                .mapNotNull { zone ->
                    val raw = readFileTextOnce(File(zone, "temp").absolutePath)?.trim()?.toDoubleOrNull()
                    raw?.let { if (it > 1000) it / 1000.0 else it }
                }
                .filter { it in 1.0..125.0 }
                .toList()

            if (values.isEmpty()) {
                skipThermalRead = true
                UNAVAILABLE_DOUBLE
            } else {
                values.maxOrNull() ?: UNAVAILABLE_DOUBLE
            }
        } catch (_: Exception) {
            skipThermalRead = true
            UNAVAILABLE_DOUBLE
        }
    }

    private fun readCpuLoad(cpuCores: Int): CpuLoadResult {
        val procStatLoad = readProcStatCpuLoadPercent()
        if (procStatLoad != UNAVAILABLE_INT) {
            return CpuLoadResult(procStatLoad, context.getString(R.string.cpu_load_label))
        }

        val residencyLoad = readCpuFreqResidencyLoadPercent(cpuCores)
        if (residencyLoad != UNAVAILABLE_INT) {
            return CpuLoadResult(residencyLoad, context.getString(R.string.cpu_frequency_label))
        }

        val currentFreqLoad = readCurrentCpuFrequencyPressurePercent(cpuCores)
        if (currentFreqLoad != UNAVAILABLE_INT) {
            return CpuLoadResult(currentFreqLoad, context.getString(R.string.cpu_frequency_label))
        }

        return CpuLoadResult(UNAVAILABLE_INT, UNAVAILABLE_TEXT)
    }

    private fun readProcStatCpuLoadPercent(): Int {
        val now = SystemClock.elapsedRealtime()
        val previous = previousCpuSnapshot
        // Too soon to measure again: hand back the reading that was taken over a
        // window wide enough to mean something, and let the baseline keep ageing.
        if (previous != null && !SystemStatsParser.cpuWindowIsWideEnough(previousCpuSampleMillis, now)) {
            return lastCpuLoadPercent
        }
        val current = SystemStatsParser.parseCpuSnapshot(
            readFileTextOnce("/proc/stat")?.lineSequence()?.firstOrNull()
        ) ?: return UNAVAILABLE_INT
        previousCpuSnapshot = current
        previousCpuSampleMillis = now
        if (previous == null) return UNAVAILABLE_INT
        return SystemStatsParser.cpuLoadPercent(previous, current)
            .also { lastCpuLoadPercent = it }
    }

    private fun readCpuFreqResidencyLoadPercent(cpuCores: Int): Int {
        val now = SystemClock.elapsedRealtime()
        val previous = previousResidencyByCore
        if (previous != null &&
            !SystemStatsParser.cpuWindowIsWideEnough(previousResidencySampleMillis, now)
        ) {
            return lastResidencyLoadPercent
        }
        val current = readCpuFreqResidencyByCore(cpuCores) ?: return UNAVAILABLE_INT
        previousResidencyByCore = current
        previousResidencySampleMillis = now
        if (previous == null) return UNAVAILABLE_INT
        return SystemStatsParser.residencyLoadPercent(previous, current)
            .also { lastResidencyLoadPercent = it }
    }

    private fun readCpuFreqResidencyByCore(cpuCores: Int): Map<Int, Map<Long, Long>>? {
        val statesByCore = mutableMapOf<Int, Map<Long, Long>>()

        for (cpuIndex in 0 until cpuCores) {
            val states = SystemStatsParser.parseTimeInState(
                readFileTextOnce("/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/stats/time_in_state")
            )
            if (states.size >= 2) {
                statesByCore[cpuIndex] = states
            }
        }

        return statesByCore.takeIf { it.isNotEmpty() }
    }

    private fun readCurrentCpuFrequencyPressurePercent(cpuCores: Int): Int {
        val cores = (0 until cpuCores).mapNotNull { cpuIndex ->
            val current = readLongFromFiles(
                "/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/scaling_cur_freq",
                "/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/cpuinfo_cur_freq"
            ) ?: return@mapNotNull null
            val max = readLongFromFiles(
                "/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/scaling_max_freq",
                "/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/cpuinfo_max_freq"
            ) ?: return@mapNotNull null
            val min = readLongFromFiles(
                "/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/scaling_min_freq",
                "/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/cpuinfo_min_freq"
            ) ?: 0L
            SystemStatsParser.CoreFreq(current = current, max = max, min = min)
        }
        return SystemStatsParser.frequencyPressurePercent(cores)
    }

    private fun canReadWifiIdentity(): Boolean {
        val hasLocation = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        val hasNearbyWifi = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
        return hasLocation || hasNearbyWifi
    }

    private fun wifiInfoFromCapabilities(capabilities: NetworkCapabilities): WifiInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            capabilities.transportInfo as? WifiInfo
        } else {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun readNetworkUsageGb(
        networkType: Int,
        startMillis: Long,
        endMillis: Long = System.currentTimeMillis(),
    ): Double {
        if (!hasUsageStatsAccess()) return UNAVAILABLE_DOUBLE

        return try {
            val statsManager = context.getSystemService(NetworkStatsManager::class.java)
                ?: return UNAVAILABLE_DOUBLE
            val bucket = statsManager.querySummaryForDevice(
                networkType,
                null,
                startMillis,
                endMillis
            )
            (bucket.rxBytes + bucket.txBytes).toDouble() / GB_BYTES
        } catch (_: Exception) {
            UNAVAILABLE_DOUBLE
        }
    }

    private fun readMobileNetworkTypeLabel(telephonyManager: TelephonyManager): String {
        val dataNetworkType = try {
            telephonyManager.dataNetworkType
        } catch (_: SecurityException) {
            TelephonyManager.NETWORK_TYPE_UNKNOWN
        }
        val fallbackNetworkType = try {
            @Suppress("DEPRECATION")
            telephonyManager.networkType
        } catch (_: SecurityException) {
            TelephonyManager.NETWORK_TYPE_UNKNOWN
        }

        val reportedLabel = sequenceOf(dataNetworkType, fallbackNetworkType)
            .map { SystemStatsParser.mobileGenerationLabel(it) }
            .firstOrNull { it != UNAVAILABLE_TEXT }
            ?: UNAVAILABLE_TEXT
        val serviceStateLabel = readServiceStateNetworkTypeLabel(telephonyManager)
        val cellInfoLabel = readCellInfoNetworkTypeLabel(telephonyManager)

        return when {
            // 5G NSA can be reported as LTE by TelephonyManager, so registered NR wins.
            serviceStateLabel == "5G" -> "5G"
            cellInfoLabel == "5G" -> "5G"
            reportedLabel != UNAVAILABLE_TEXT -> reportedLabel
            serviceStateLabel != UNAVAILABLE_TEXT -> serviceStateLabel
            else -> cellInfoLabel
        }
    }

    private fun readServiceStateNetworkTypeLabel(telephonyManager: TelephonyManager): String {
        if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            return UNAVAILABLE_TEXT
        }

        val serviceState = try {
            telephonyManager.serviceState
        } catch (_: SecurityException) {
            null
        } ?: return UNAVAILABLE_TEXT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && serviceState.indicatesNrNsa()) {
            return "5G"
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            serviceStateNetworkTypeLabelApi30(serviceState)
        } else {
            UNAVAILABLE_TEXT
        }
    }

    @TargetApi(Build.VERSION_CODES.R)
    @Suppress("DEPRECATION")
    private fun serviceStateNetworkTypeLabelApi30(serviceState: ServiceState): String {
        val labels = serviceState.networkRegistrationInfoList
            .filter { it.isRegistered && it.transportType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN }
            .map { SystemStatsParser.mobileGenerationLabel(it.accessNetworkTechnology) }
            .filter { it != UNAVAILABLE_TEXT }
        return SystemStatsParser.highestMobileGeneration(labels) ?: UNAVAILABLE_TEXT
    }

    @TargetApi(Build.VERSION_CODES.R)
    private fun ServiceState.indicatesNrNsa(): Boolean {
        val compactState = toString().replace(" ", "")
        return compactState.contains("isNrAvailable=true") &&
            compactState.contains("isEnDcAvailable=true")
    }

    private fun readCellInfoNetworkTypeLabel(telephonyManager: TelephonyManager): String {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            return UNAVAILABLE_TEXT
        }

        return try {
            @Suppress("DEPRECATION")
            val cellInfo = telephonyManager.allCellInfo.orEmpty()
            val registeredLabel = SystemStatsParser.highestMobileGeneration(
                cellInfo.filter { it.isRegistered }.mapNotNull { cellInfoNetworkTypeLabel(it) }
            )
            registeredLabel
                ?: SystemStatsParser.highestMobileGeneration(
                    cellInfo.mapNotNull { cellInfoNetworkTypeLabel(it) }
                )
                ?: UNAVAILABLE_TEXT
        } catch (_: SecurityException) {
            UNAVAILABLE_TEXT
        }
    }

    @Suppress("DEPRECATION") // CellInfoCdma is deprecated but still emitted on legacy networks
    private fun cellInfoNetworkTypeLabel(cellInfo: CellInfo): String? {
        return when (cellInfo) {
            is CellInfoLte -> "4G"
            is CellInfoWcdma -> "3G"
            is CellInfoCdma -> "3G"
            is CellInfoGsm -> "2G"
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cellInfoNetworkTypeLabelApi29(cellInfo) else null
        }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun cellInfoNetworkTypeLabelApi29(cellInfo: CellInfo): String? {
        return when (cellInfo) {
            is CellInfoNr -> "5G"
            is CellInfoTdscdma -> "3G"
            else -> null
        }
    }

    private fun readMobileSignalDbm(telephonyManager: TelephonyManager): Int {
        val signalStrengthDbm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            hasPermission(Manifest.permission.READ_PHONE_STATE)
        ) {
            try {
                telephonyManager.signalStrength
                    ?.cellSignalStrengths
                    ?.map { it.dbm }
                    ?.let { SystemStatsParser.filterValidDbm(it) }
                    ?.maxOrNull()
                    ?: UNAVAILABLE_INT
            } catch (_: SecurityException) {
                UNAVAILABLE_INT
            }
        } else {
            UNAVAILABLE_INT
        }

        if (signalStrengthDbm != UNAVAILABLE_INT) {
            return signalStrengthDbm
        }

        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            return UNAVAILABLE_INT
        }

        return try {
            @Suppress("DEPRECATION")
            val readings = telephonyManager.allCellInfo?.mapNotNull { cellInfoDbm(it) } ?: emptyList()
            SystemStatsParser.filterValidDbm(readings).maxOrNull() ?: UNAVAILABLE_INT
        } catch (_: SecurityException) {
            UNAVAILABLE_INT
        }
    }

    @Suppress("DEPRECATION") // CellInfoCdma is deprecated but still emitted on legacy networks
    private fun cellInfoDbm(cellInfo: CellInfo): Int? {
        return when (cellInfo) {
            is CellInfoLte -> cellInfo.cellSignalStrength.dbm
            is CellInfoGsm -> cellInfo.cellSignalStrength.dbm
            is CellInfoCdma -> cellInfo.cellSignalStrength.dbm
            is CellInfoWcdma -> cellInfo.cellSignalStrength.dbm
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cellInfoDbmApi29(cellInfo) else null
        }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun cellInfoDbmApi29(cellInfo: CellInfo): Int? {
        return when (cellInfo) {
            is CellInfoNr -> cellInfo.cellSignalStrength.dbm
            is CellInfoTdscdma -> cellInfo.cellSignalStrength.dbm
            else -> null
        }
    }

    private fun readMobileTrafficStatsGb(): Double {
        val rx = TrafficStats.getMobileRxBytes()
        val tx = TrafficStats.getMobileTxBytes()
        return if (rx != TrafficStats.UNSUPPORTED.toLong() && tx != TrafficStats.UNSUPPORTED.toLong()) {
            (rx + tx).toDouble() / GB_BYTES
        } else {
            UNAVAILABLE_DOUBLE
        }
    }

    private fun readSimStateLabel(telephonyManager: TelephonyManager?): String {
        val state = telephonyManager?.simState ?: return UNAVAILABLE_TEXT
        return when (state) {
            TelephonyManager.SIM_STATE_READY -> context.getString(R.string.sim_state_ready)
            TelephonyManager.SIM_STATE_ABSENT -> context.getString(R.string.sim_state_absent)
            TelephonyManager.SIM_STATE_PIN_REQUIRED,
            TelephonyManager.SIM_STATE_PUK_REQUIRED,
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> context.getString(R.string.sim_state_locked)
            else -> UNAVAILABLE_TEXT
        }
    }

    private fun readSimSlotCount(telephonyManager: TelephonyManager?): Int {
        if (telephonyManager == null) return UNAVAILABLE_INT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            telephonyManager.activeModemCount.takeIf { it > 0 } ?: UNAVAILABLE_INT
        } else {
            @Suppress("DEPRECATION")
            telephonyManager.phoneCount.takeIf { it > 0 } ?: UNAVAILABLE_INT
        }
    }

    /** The device's own IPv4 address on the active network. Reading your own link needs no permission. */
    private fun readIpAddress(connManager: ConnectivityManager?, network: Network?): String {
        if (connManager == null || network == null) return UNAVAILABLE_TEXT
        return try {
            val linkProperties = connManager.getLinkProperties(network) ?: return UNAVAILABLE_TEXT
            linkProperties.linkAddresses
                .map { it.address }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress }
                ?.hostAddress ?: UNAVAILABLE_TEXT
        } catch (_: Exception) {
            UNAVAILABLE_TEXT
        }
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("DEPRECATION") // unsafeCheckOpNoThrow / checkOpNoThrow remain the supported op-check path
    private fun hasUsageStatsAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun readIntFromFiles(vararg paths: String): Int? {
        return readLongFromFiles(*paths)?.toInt()
    }

    private fun readLongFromFiles(vararg paths: String): Long? {
        for (path in paths) {
            val value = readFileTextOnce(path)?.trim()?.toLongOrNull()
            if (value != null) return value
        }
        return null
    }

    /**
     * Reads a pseudo-file once and caches paths that fail (SELinux-blocked sysfs nodes)
     * so repeated polls don't keep hitting the same denied path. Always called inside the
     * stats mutex, so the cache needs no extra synchronization.
     */
    private fun readFileTextOnce(path: String): String? {
        if (path in unavailableFilePaths) return null

        return try {
            File(path).readText()
        } catch (_: Exception) {
            unavailableFilePaths.add(path)
            null
        }
    }

    private companion object {
        private const val GB_BYTES = 1024.0 * 1024.0 * 1024.0
        private const val KB_PER_GB = 1024.0 * 1024.0
        private val WIDEVINE_UUID = java.util.UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")
    }
}
