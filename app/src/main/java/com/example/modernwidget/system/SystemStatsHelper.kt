package com.example.modernwidget.system

import android.annotation.TargetApi
import android.Manifest
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
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.AccessNetworkConstants
import android.telephony.NetworkRegistrationInfo
import android.telephony.ServiceState
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.modernwidget.R
import java.io.File
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

const val UNAVAILABLE_TEXT = "—"
const val UNAVAILABLE_INT = -1
const val UNAVAILABLE_DOUBLE = -1.0

data class SystemStats(
    val batteryLevel: Int,
    val batteryStatus: String,
    val batteryHealth: String,
    val batteryTemp: Double,
    val batteryVoltage: Double,
    val timeRemainingText: String,
    val batteryCycleCount: Int,
    val batteryCapacityPercent: Int,
    val totalRamGb: Double,
    val usedRamGb: Double,
    val ramPercent: Int,
    val cpuCores: Int,
    val cpuAbi: String,
    val cpuFreqGhz: Double,
    val cpuLoadPercent: Int,
    val cpuLoadLabel: String,
    val cpuTemp: Double,
    val totalStorageGb: Double,
    val usedStorageGb: Double,
    val storagePercent: Int,
    val wifiSsid: String,
    val wifiBand: String,
    val wifiSpeedDown: Int,
    val wifiSpeedUp: Int,
    val wifiBytesTodayGb: Double,
    val operatorName: String,
    val mobileNetworkType: String,
    val mobileSignalDbm: Int,
    val mobileDataUsedGb: Double,
    val mobileDataTotalGb: Double,
    val mobileDataLabel: String,
    val uptimeText: String
)

object SystemStatsHelper {

    private data class CpuSnapshot(val idle: Long, val total: Long)
    private data class CpuLoadResult(val percent: Int, val label: String)
    private data class CpuFreqResidencySnapshot(val statesByCore: Map<Int, Map<Long, Long>>)

    private var previousCpuSnapshot: CpuSnapshot? = null
    private var previousCpuFreqResidencySnapshot: CpuFreqResidencySnapshot? = null
    private val unavailableFilePaths = mutableSetOf<String>()
    @Volatile private var skipThermalRead = false

    @Suppress("DEPRECATION")
    fun getStats(context: Context): SystemStats {
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

        val timeRemainingText = buildBatteryTimeText(context, status, batteryManager)

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
        val cpuLoad = readCpuLoad(context, cpuCores)

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
        if (isWifiConnected) {
            wifiSpeedDown = capabilities.linkDownstreamBandwidthKbps.takeIf { it > 0 }?.div(1000) ?: UNAVAILABLE_INT
            wifiSpeedUp = capabilities.linkUpstreamBandwidthKbps.takeIf { it > 0 }?.div(1000) ?: UNAVAILABLE_INT

            if (canReadWifiIdentity(context)) {
                try {
                    val wifiInfo = wifiInfoFromCapabilities(capabilities) ?: wifiManager?.connectionInfo
                    wifiSsid = normalizedWifiSsid(wifiInfo?.ssid) ?: wifiSsid
                    wifiBand = wifiBandLabel(wifiInfo?.frequency ?: UNAVAILABLE_INT)
                } catch (_: SecurityException) {
                    wifiSsid = context.getString(R.string.wifi_connected)
                    wifiBand = UNAVAILABLE_TEXT
                }
            }
        }
        val wifiBytesTodayGb = readNetworkUsageGb(context, ConnectivityManager.TYPE_WIFI)

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
                mobileNetworkType = readMobileNetworkTypeLabel(context, telephonyManager)
                mobileSignalDbm = readMobileSignalDbm(context, telephonyManager)
            }
        } catch (_: SecurityException) {
            operatorName = UNAVAILABLE_TEXT
            mobileNetworkType = UNAVAILABLE_TEXT
            mobileSignalDbm = UNAVAILABLE_INT
        }
        val mobileDataTodayGb = readNetworkUsageGb(context, ConnectivityManager.TYPE_MOBILE)
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

    private fun buildBatteryTimeText(context: Context, status: Int, batteryManager: BatteryManager?): String {
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
        return if (chargeFull != null && chargeFullDesign != null && chargeFullDesign > 0L) {
            ((chargeFull.toDouble() / chargeFullDesign.toDouble()) * 100).toInt().coerceIn(0, 150)
        } else {
            UNAVAILABLE_INT
        }
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

    @Synchronized
    private fun readCpuLoad(context: Context, cpuCores: Int): CpuLoadResult {
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
        val current = readCpuSnapshot() ?: return UNAVAILABLE_INT
        val previous = previousCpuSnapshot
        previousCpuSnapshot = current
        if (previous == null) return UNAVAILABLE_INT

        val totalDelta = current.total - previous.total
        val idleDelta = current.idle - previous.idle
        return if (totalDelta > 0L) {
            (((totalDelta - idleDelta).toDouble() / totalDelta.toDouble()) * 100).toInt().coerceIn(0, 100)
        } else {
            UNAVAILABLE_INT
        }
    }

    private fun readCpuFreqResidencyLoadPercent(cpuCores: Int): Int {
        val current = readCpuFreqResidencySnapshot(cpuCores) ?: return UNAVAILABLE_INT
        val previous = previousCpuFreqResidencySnapshot
        previousCpuFreqResidencySnapshot = current
        if (previous == null) return UNAVAILABLE_INT

        var totalPercent = 0.0
        var countedCores = 0

        for ((coreIndex, currentStates) in current.statesByCore) {
            val previousStates = previous.statesByCore[coreIndex] ?: continue
            val minFreq = currentStates.keys.minOrNull() ?: continue
            val maxFreq = currentStates.keys.maxOrNull() ?: continue
            if (maxFreq <= minFreq) continue

            var totalTicks = 0L
            var weightedFreq = 0.0
            for ((freq, ticks) in currentStates) {
                val delta = ticks - (previousStates[freq] ?: 0L)
                if (delta > 0L) {
                    totalTicks += delta
                    weightedFreq += freq.toDouble() * delta.toDouble()
                }
            }

            if (totalTicks > 0L) {
                val averageFreq = weightedFreq / totalTicks.toDouble()
                val percent = ((averageFreq - minFreq.toDouble()) / (maxFreq - minFreq).toDouble()) * 100.0
                totalPercent += percent.coerceIn(0.0, 100.0)
                countedCores++
            }
        }

        return if (countedCores > 0) {
            (totalPercent / countedCores.toDouble()).roundToInt().coerceIn(0, 100)
        } else {
            UNAVAILABLE_INT
        }
    }

    private fun readCpuFreqResidencySnapshot(cpuCores: Int): CpuFreqResidencySnapshot? {
        val statesByCore = mutableMapOf<Int, Map<Long, Long>>()

        for (cpuIndex in 0 until cpuCores) {
            val text = readFileTextOnce("/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/stats/time_in_state")
                ?: continue
            val states = text.lineSequence()
                .mapNotNull { line ->
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 2) {
                        val frequency = parts[0].toLongOrNull()
                        val ticks = parts[1].toLongOrNull()
                        if (frequency != null && ticks != null) frequency to ticks else null
                    } else {
                        null
                    }
                }
                .toMap()

            if (states.size >= 2) {
                statesByCore[cpuIndex] = states
            }
        }

        return statesByCore.takeIf { it.isNotEmpty() }?.let { CpuFreqResidencySnapshot(it) }
    }

    private fun readCurrentCpuFrequencyPressurePercent(cpuCores: Int): Int {
        var totalPercent = 0.0
        var countedCores = 0

        for (cpuIndex in 0 until cpuCores) {
            val current = readLongFromFiles(
                "/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/scaling_cur_freq",
                "/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/cpuinfo_cur_freq"
            ) ?: continue
            val max = readLongFromFiles(
                "/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/scaling_max_freq",
                "/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/cpuinfo_max_freq"
            ) ?: continue
            val min = readLongFromFiles(
                "/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/scaling_min_freq",
                "/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/cpuinfo_min_freq"
            ) ?: 0L

            if (max > 0L && current > 0L) {
                val percent = if (max > min) {
                    ((current - min).toDouble() / (max - min).toDouble()) * 100.0
                } else {
                    (current.toDouble() / max.toDouble()) * 100.0
                }
                totalPercent += percent.coerceIn(0.0, 100.0)
                countedCores++
            }
        }

        return if (countedCores > 0) {
            (totalPercent / countedCores.toDouble()).roundToInt().coerceIn(0, 100)
        } else {
            UNAVAILABLE_INT
        }
    }

    private fun readCpuSnapshot(): CpuSnapshot? {
        val firstLine = readFileTextOnce("/proc/stat")?.lineSequence()?.firstOrNull() ?: return null
        val parts = firstLine
            .trim()
            .split(Regex("\\s+"))
            .drop(1)
            .mapNotNull { it.toLongOrNull() }
        if (parts.size < 7) return null
        val idle = parts[3] + parts.getOrElse(4) { 0L }
        val total = parts.sum()
        return CpuSnapshot(idle = idle, total = total)
    }

    private fun canReadWifiIdentity(context: Context): Boolean {
        val hasLocation = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        val hasNearbyWifi = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)
        return hasLocation || hasNearbyWifi
    }

    private fun wifiInfoFromCapabilities(capabilities: NetworkCapabilities): WifiInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            capabilities.transportInfo as? WifiInfo
        } else {
            null
        }
    }

    private fun normalizedWifiSsid(rawSsid: String?): String? {
        return rawSsid
            ?.trim()
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" && it != "0x" }
    }

    @Suppress("DEPRECATION")
    private fun readNetworkUsageGb(context: Context, networkType: Int): Double {
        if (!hasUsageStatsAccess(context)) return UNAVAILABLE_DOUBLE

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

    private fun mobileNetworkTypeLabel(networkType: Int): String {
        return when (networkType) {
            TelephonyManager.NETWORK_TYPE_LTE -> "4G"
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA,
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_GSM,
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G"
            TelephonyManager.NETWORK_TYPE_IWLAN -> UNAVAILABLE_TEXT
            else -> UNAVAILABLE_TEXT
        }
    }

    private fun readMobileNetworkTypeLabel(context: Context, telephonyManager: TelephonyManager): String {
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
            .map { mobileNetworkTypeLabel(it) }
            .firstOrNull { it != UNAVAILABLE_TEXT }
            ?: UNAVAILABLE_TEXT
        val serviceStateLabel = readServiceStateNetworkTypeLabel(context, telephonyManager)
        val cellInfoLabel = readCellInfoNetworkTypeLabel(context, telephonyManager)

        return when {
            // 5G NSA can be reported as LTE by TelephonyManager, so registered NR wins.
            serviceStateLabel == "5G" -> "5G"
            cellInfoLabel == "5G" -> "5G"
            reportedLabel != UNAVAILABLE_TEXT -> reportedLabel
            serviceStateLabel != UNAVAILABLE_TEXT -> serviceStateLabel
            else -> cellInfoLabel
        }
    }

    private fun readServiceStateNetworkTypeLabel(context: Context, telephonyManager: TelephonyManager): String {
        if (!hasPermission(context, Manifest.permission.READ_PHONE_STATE)) {
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
    private fun serviceStateNetworkTypeLabelApi30(serviceState: ServiceState): String {
        return serviceState.networkRegistrationInfoList
            .filter { it.isRegistered && it.transportType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN }
            .map { mobileNetworkTypeLabel(it.accessNetworkTechnology) }
            .filter { it != UNAVAILABLE_TEXT }
            .highestMobileGeneration()
            ?: UNAVAILABLE_TEXT
    }

    @TargetApi(Build.VERSION_CODES.R)
    private fun ServiceState.indicatesNrNsa(): Boolean {
        val compactState = toString().replace(" ", "")
        return compactState.contains("isNrAvailable=true") &&
            compactState.contains("isEnDcAvailable=true")
    }

    private fun readCellInfoNetworkTypeLabel(context: Context, telephonyManager: TelephonyManager): String {
        if (!hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            return UNAVAILABLE_TEXT
        }

        return try {
            @Suppress("DEPRECATION")
            val cellInfo = telephonyManager.allCellInfo.orEmpty()
            val registeredLabel = cellInfo
                .filter { it.isRegistered }
                .mapNotNull { cellInfoNetworkTypeLabel(it) }
                .highestMobileGeneration()
            registeredLabel ?: cellInfo
                .mapNotNull { cellInfoNetworkTypeLabel(it) }
                .highestMobileGeneration()
                ?: UNAVAILABLE_TEXT
        } catch (_: SecurityException) {
            UNAVAILABLE_TEXT
        }
    }

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

    private fun List<String>.highestMobileGeneration(): String? {
        return when {
            any { it == "5G" } -> "5G"
            any { it == "4G" } -> "4G"
            any { it == "3G" } -> "3G"
            any { it == "2G" } -> "2G"
            else -> firstOrNull()
        }
    }

    private fun readMobileSignalDbm(context: Context, telephonyManager: TelephonyManager): Int {
        val signalStrengthDbm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            hasPermission(context, Manifest.permission.READ_PHONE_STATE)
        ) {
            try {
                telephonyManager.signalStrength
                    ?.cellSignalStrengths
                    ?.map { it.dbm }
                    ?.filterValidDbm()
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

        if (!hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            return UNAVAILABLE_INT
        }

        return try {
            @Suppress("DEPRECATION")
            telephonyManager.allCellInfo
                ?.mapNotNull { cellInfoDbm(it) }
                ?.filterValidDbm()
                ?.maxOrNull()
                ?: UNAVAILABLE_INT
        } catch (_: SecurityException) {
            UNAVAILABLE_INT
        }
    }

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

    private fun Iterable<Int>.filterValidDbm(): List<Int> {
        return filter { it in -140..-40 }
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

    private fun wifiBandLabel(frequencyMhz: Int): String {
        return when (frequencyMhz) {
            in 2400..2500 -> "2,4 GHz"
            in 4900..5900 -> "5 GHz"
            in 5925..7125 -> "6 GHz"
            else -> UNAVAILABLE_TEXT
        }
    }

    private fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasUsageStatsAccess(context: Context): Boolean {
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

    private fun readFileTextOnce(path: String): String? {
        synchronized(unavailableFilePaths) {
            if (path in unavailableFilePaths) return null
        }

        return try {
            File(path).readText()
        } catch (_: Exception) {
            synchronized(unavailableFilePaths) {
                unavailableFilePaths.add(path)
            }
            null
        }
    }

    private const val GB_BYTES = 1024.0 * 1024.0 * 1024.0
}
