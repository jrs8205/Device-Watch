package com.example.modernwidget.data

import android.Manifest
import android.annotation.TargetApi
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.telephony.AccessNetworkConstants
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.ServiceState
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.modernwidget.R
import com.example.modernwidget.di.DefaultDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class SystemStatsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : SystemStatsRepository {

    // CPU load needs to compare two samples over time. These snapshots persist across
    // calls; the mutex below guarantees only one computation touches them at a time.
    private var previousCpuSnapshot: SystemStatsParser.CpuSnapshot? = null
    private var previousResidencyByCore: Map<Int, Map<Long, Long>>? = null
    private val unavailableFilePaths = mutableSetOf<String>()
    private var skipThermalRead = false

    private val statsMutex = Mutex()

    override suspend fun getStats(): SystemStats = withContext(dispatcher) {
        statsMutex.withLock { computeStats() }
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
        val wifiBytesTodayGb = readNetworkUsageGb(ConnectivityManager.TYPE_WIFI)

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
        val mobileDataTodayGb = readNetworkUsageGb(ConnectivityManager.TYPE_MOBILE)
        val mobileTrafficSinceBootGb = readMobileTrafficStatsGb()
        val mobileDataUsedGb = if (mobileDataTodayGb >= 0.0) mobileDataTodayGb else mobileTrafficSinceBootGb
        val mobileDataTotalGb = UNAVAILABLE_DOUBLE
        val mobileDataLabel = when {
            mobileDataTodayGb >= 0.0 -> context.getString(R.string.mobile_data_today_label)
            mobileTrafficSinceBootGb >= 0.0 -> context.getString(R.string.mobile_data_since_boot_label)
            else -> context.getString(R.string.mobile_data_label)
        }

        val uptimeMs = SystemClock.elapsedRealtime()
        val uptimeHours = uptimeMs / (1000 * 60 * 60)
        val uptimeMins = (uptimeMs / (1000 * 60)) % 60
        val uptimeText = context.getString(R.string.uptime_short, uptimeHours, uptimeMins)

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
            wifiBand = wifiBand,
            wifiSpeedDown = wifiSpeedDown,
            wifiSpeedUp = wifiSpeedUp,
            wifiBytesTodayGb = wifiBytesTodayGb,
            operatorName = operatorName,
            mobileNetworkType = mobileNetworkType,
            mobileSignalDbm = mobileSignalDbm,
            mobileDataUsedGb = mobileDataUsedGb,
            mobileDataTotalGb = mobileDataTotalGb,
            mobileDataLabel = mobileDataLabel,
            uptimeText = uptimeText
        )
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
            if (chargeCounter > 0 && currentNow < 0 && currentNow != Int.MIN_VALUE) {
                val currentAbs = abs(currentNow)
                val remainingHours = chargeCounter.toDouble() / currentAbs.toDouble()
                val totalMinutes = (remainingHours * 60).toInt()
                val hours = totalMinutes / 60
                val minutes = totalMinutes % 60
                if (hours > 0) {
                    context.getString(R.string.battery_time_remaining_hours, hours, minutes)
                } else {
                    context.getString(R.string.battery_time_remaining_minutes, minutes)
                }
            } else {
                UNAVAILABLE_TEXT
            }
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
        val current = SystemStatsParser.parseCpuSnapshot(
            readFileTextOnce("/proc/stat")?.lineSequence()?.firstOrNull()
        ) ?: return UNAVAILABLE_INT
        val previous = previousCpuSnapshot
        previousCpuSnapshot = current
        if (previous == null) return UNAVAILABLE_INT
        return SystemStatsParser.cpuLoadPercent(previous, current)
    }

    private fun readCpuFreqResidencyLoadPercent(cpuCores: Int): Int {
        val current = readCpuFreqResidencyByCore(cpuCores) ?: return UNAVAILABLE_INT
        val previous = previousResidencyByCore
        previousResidencyByCore = current
        if (previous == null) return UNAVAILABLE_INT
        return SystemStatsParser.residencyLoadPercent(previous, current)
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
    private fun readNetworkUsageGb(networkType: Int): Double {
        if (!hasUsageStatsAccess()) return UNAVAILABLE_DOUBLE

        return try {
            val statsManager = context.getSystemService(NetworkStatsManager::class.java)
                ?: return UNAVAILABLE_DOUBLE
            val startOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val bucket = statsManager.querySummaryForDevice(
                networkType,
                null,
                startOfDay,
                System.currentTimeMillis()
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
    }
}
