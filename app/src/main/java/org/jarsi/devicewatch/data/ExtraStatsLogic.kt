package org.jarsi.devicewatch.data

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Where the charge comes from, as `BatteryManager.EXTRA_PLUGGED` reports it. */
enum class ChargeSource { NONE, AC, USB, WIRELESS, DOCK }

/** One `StorageVolume`, with its size where the platform exposes its directory (API 30). */
data class VolumeInfo(
    val description: String,
    val primary: Boolean,
    val removable: Boolean,
    val mounted: Boolean,
    val totalBytes: Long,
    val freeBytes: Long,
)

/** A `UsageEvents` SCREEN_INTERACTIVE (on) or SCREEN_NON_INTERACTIVE (off) event. */
data class ScreenEvent(val timeMillis: Long, val on: Boolean)

/** How the battery is being charged, per the health HAL's BatteryChargingState. */
enum class ChargingState { UNKNOWN, NORMAL, TOO_COLD, TOO_HOT, LONG_LIFE, ADAPTIVE }

/** `PowerManager.getCurrentThermalStatus()` levels; [UNKNOWN] when unsupported. */
enum class ThermalLevel { UNKNOWN, NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN }

/** Android's private DNS setting as the active network applies it. */
enum class PrivateDnsMode { OFF, AUTOMATIC, HOSTNAME }

data class PrivateDns(val mode: PrivateDnsMode, val serverName: String?)

/** The Google Play system update, to the day when the version carries one. */
data class ModuleUpdate(val month: YearMonth, val day: Int?)

/** Whether traffic happened with its app in the foreground, per `NetworkStats.Bucket.getState()`. */
enum class TrafficState { FOREGROUND, BACKGROUND, UNKNOWN }

/** One per-UID `NetworkStats` bucket, reduced to what the breakdown needs. */
data class TrafficRecord(val mobile: Boolean, val state: TrafficState, val roaming: Boolean, val bytes: Long)

/** Foreground and background shares of one network's traffic; they add up to 100. */
data class TrafficSplit(val foregroundPercent: Int, val backgroundPercent: Int)

/**
 * The counting period's traffic split by app state per network, and the mobile
 * traffic spent roaming. A split is null when the network had no traffic or some
 * of it carries no state (the platform did not record one).
 */
data class DataBreakdown(val wifi: TrafficSplit?, val mobile: TrafficSplit?, val mobileRoamingBytes: Long) {
    companion object {
        val NONE = DataBreakdown(wifi = null, mobile = null, mobileRoamingBytes = 0L)
    }
}

/**
 * What the used internal storage holds: installed apps with their data (the
 * cache is part of it, shown on its own too), the shared photos, videos and
 * audio, and everything else on the data partition — downloads, documents and
 * the system's own files there.
 */
data class StorageBreakdown(
    val appsBytes: Long,
    val cacheBytes: Long,
    val imageBytes: Long,
    val videoBytes: Long,
    val audioBytes: Long,
    val otherBytes: Long,
)

/** One app's `StorageStats`: its code, its data (cache and own external dirs included) and cache. */
data class AppSizes(val codeBytes: Long, val dataBytes: Long, val cacheBytes: Long)

/**
 * Pure rules behind the readings added in 1.6.0 on top of the original stats:
 * charge source, the system's own battery estimate, deep sleep, thermal state,
 * private DNS, the Google Play system update and the data and storage
 * breakdowns. No Android types, so every rule runs on the JVM.
 */
object ExtraStatsLogic {

    /**
     * `BatteryManager.BATTERY_PLUGGED_AC` (1), `_USB` (2), `_WIRELESS` (4) and
     * `_DOCK` (8, API 33). Anything else, including 0 for unplugged, is no source.
     */
    fun chargeSource(plugged: Int): ChargeSource = when (plugged) {
        1 -> ChargeSource.AC
        2 -> ChargeSource.USB
        4 -> ChargeSource.WIRELESS
        8 -> ChargeSource.DOCK
        else -> ChargeSource.NONE
    }

    /**
     * `BatteryManager.EXTRA_CHARGING_STATUS` (API 34), which carries the health
     * HAL's BatteryChargingState: 1 normal, 2 paused too cold, 3 paused too hot,
     * 4 long-life (held below full to spare the battery), 5 adaptive. The platform
     * keeps the names hidden; 0 is invalid.
     */
    fun chargingState(status: Int): ChargingState = when (status) {
        1 -> ChargingState.NORMAL
        2 -> ChargingState.TOO_COLD
        3 -> ChargingState.TOO_HOT
        4 -> ChargingState.LONG_LIFE
        5 -> ChargingState.ADAPTIVE
        else -> ChargingState.UNKNOWN
    }

    /**
     * `ActivityManager.MemoryInfo.advertisedMem` (API 34) in whole gigabytes. Makers
     * report it in binary or decimal units, so the reading that lands closer to a
     * whole number wins (8 GiB and 8 000 000 000 bytes both read as 8).
     */
    fun advertisedRamGb(bytes: Long): Int? {
        if (bytes <= 0L) return null
        val binary = bytes / GIB
        val decimal = bytes / 1e9
        val binaryWhole = Math.round(binary).coerceAtLeast(1L)
        val decimalWhole = Math.round(decimal).coerceAtLeast(1L)
        val binaryError = Math.abs(binary - binaryWhole) / binaryWhole
        val decimalError = Math.abs(decimal - decimalWhole) / decimalWhole
        return (if (binaryError <= decimalError) binaryWhole else decimalWhole).toInt()
    }

    /**
     * "16 (API 36.1)": the release and the SDK level, with the minor version from
     * `Build.VERSION.SDK_INT_FULL` (API 36, major × 100 000 + minor) when non-zero.
     */
    fun androidVersionText(release: String, sdkInt: Int, sdkIntFull: Int?): String {
        val minor = sdkIntFull?.rem(100_000) ?: 0
        return if (minor > 0) "$release (API $sdkInt.$minor)" else "$release (API $sdkInt)"
    }

    private const val GIB = 1024.0 * 1024.0 * 1024.0

    /** SD cards and USB storage; the primary (internal) volume has its own row. */
    fun removableVolumes(volumes: List<VolumeInfo>): List<VolumeInfo> =
        volumes.filter { it.removable && !it.primary }

    /**
     * A USB device as "maker product", without doubling a maker the product name
     * already starts with, or as "USB vendor:product" in hex when it names neither.
     */
    fun usbDeviceName(manufacturer: String?, product: String?, vendorId: Int, productId: Int): String {
        val maker = manufacturer?.trim()?.takeIf { it.isNotEmpty() }
        val name = product?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            maker != null && name != null ->
                if (name.startsWith(maker, ignoreCase = true)) name else "$maker $name"
            name != null -> name
            maker != null -> maker
            else -> "USB %04x:%04x".format(vendorId, productId)
        }
    }

    /**
     * Time the screen was on per local day between [startMillis] and [endMillis],
     * from its on/off events. An interval over midnight is split between the days.
     * A first event that turns the screen off means it was on when the window
     * opened; one still on at the end counts until [endMillis]. Repeated events of
     * the same kind change nothing. No events give no time: the state is unknown.
     */
    fun screenOnByDay(
        events: List<ScreenEvent>,
        startMillis: Long,
        endMillis: Long,
        zone: ZoneId,
    ): Map<LocalDate, Long> {
        val sorted = events.filter { it.timeMillis in startMillis..endMillis }.sortedBy { it.timeMillis }
        if (sorted.isEmpty()) return emptyMap()
        val totals = linkedMapOf<LocalDate, Long>()
        fun addInterval(from: Long, to: Long) {
            var cursor = from
            while (cursor < to) {
                val day = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
                val nextMidnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val sliceEnd = minOf(to, nextMidnight)
                totals.merge(day, sliceEnd - cursor, Long::plus)
                cursor = sliceEnd
            }
        }
        var on = !sorted.first().on
        var since = startMillis
        for (event in sorted) {
            if (on && !event.on) addInterval(since, event.timeMillis)
            if (!on && event.on) since = event.timeMillis
            on = event.on
        }
        if (on) addInterval(since, endMillis)
        return totals
    }

    /**
     * `PowerManager.getBatteryDischargePrediction()` in whole minutes, or null when
     * the system has no prediction or one under a minute (nothing worth showing).
     */
    fun predictionMinutes(millis: Long?): Int? {
        if (millis == null || millis < MINUTE_MILLIS) return null
        return (millis / MINUTE_MILLIS).toInt()
    }

    /**
     * Time since boot the CPU spent in deep sleep: `SystemClock.elapsedRealtime()`
     * counts it, `uptimeMillis()` does not. Null when the clocks disagree.
     */
    fun deepSleepMillis(elapsedMillis: Long, awakeMillis: Long): Long? {
        if (elapsedMillis <= 0L || awakeMillis < 0L || awakeMillis > elapsedMillis) return null
        return elapsedMillis - awakeMillis
    }

    /** [deepSleepMillis] as a whole percentage of the time since boot. */
    fun deepSleepPercent(elapsedMillis: Long, awakeMillis: Long): Int? {
        val asleep = deepSleepMillis(elapsedMillis, awakeMillis) ?: return null
        return (asleep * 100 / elapsedMillis).toInt()
    }

    /** `PowerManager.THERMAL_STATUS_NONE` (0) .. `THERMAL_STATUS_SHUTDOWN` (6). */
    fun thermalLevel(status: Int): ThermalLevel = when (status) {
        0 -> ThermalLevel.NONE
        1 -> ThermalLevel.LIGHT
        2 -> ThermalLevel.MODERATE
        3 -> ThermalLevel.SEVERE
        4 -> ThermalLevel.CRITICAL
        5 -> ThermalLevel.EMERGENCY
        6 -> ThermalLevel.SHUTDOWN
        else -> ThermalLevel.UNKNOWN
    }

    /**
     * `PowerManager.getThermalHeadroom()` as a whole percentage of the point where
     * severe throttling starts (1.0); null when unsupported or rate-limited (NaN).
     */
    fun headroomPercent(headroom: Float): Int? {
        if (!headroom.isFinite() || headroom < 0f) return null
        return Math.round(headroom * 100f)
    }

    /**
     * Whether the headroom may be asked again: the platform answers NaN when asked
     * more often than every [HEADROOM_INTERVAL_MILLIS].
     */
    fun headroomDue(lastReadMillis: Long?, nowMillis: Long): Boolean {
        if (lastReadMillis == null || nowMillis < lastReadMillis) return true
        return nowMillis - lastReadMillis >= HEADROOM_INTERVAL_MILLIS
    }

    const val HEADROOM_INTERVAL_MILLIS = 10_000L

    /**
     * `LinkProperties.isPrivateDnsActive` and `getPrivateDnsServerName` (API 28):
     * active with a name is the "hostname" setting, active without one is
     * "automatic" (opportunistic), and inactive is off whatever name is left.
     */
    fun privateDns(active: Boolean, serverName: String?): PrivateDns {
        if (!active) return PrivateDns(PrivateDnsMode.OFF, null)
        val name = serverName?.trim()?.takeIf { it.isNotEmpty() }
            ?: return PrivateDns(PrivateDnsMode.AUTOMATIC, null)
        return PrivateDns(PrivateDnsMode.HOSTNAME, name)
    }

    /**
     * The Google Play system update as Settings shows it: the module-metadata
     * package's versionName, starting "yyyy-MM-dd" or "yyyy-MM". Anything else,
     * including an impossible date, is no date.
     */
    fun moduleUpdate(versionName: String?): ModuleUpdate? {
        val match = MODULE_VERSION.matchAt(versionName?.trim() ?: return null, 0) ?: return null
        val (year, month, day) = match.destructured
        return try {
            val yearMonth = YearMonth.of(year.toInt(), month.toInt())
            val dayOfMonth = day.takeIf { it.isNotEmpty() }?.toInt()
            if (dayOfMonth != null && !yearMonth.isValidDay(dayOfMonth)) return null
            ModuleUpdate(yearMonth, dayOfMonth)
        } catch (_: DateTimeException) {
            null
        }
    }

    // Anchored at the start only, like Settings' date parse: a build suffix such as
    // "2024-07-01S+" follows the date on some images.
    private val MODULE_VERSION = Regex("""(\d{4})-(\d{2})(?:-(\d{2}))?(?!\d)""")

    /** `NetworkStats.Bucket.STATE_DEFAULT` (1) is background, `STATE_FOREGROUND` (2) foreground. */
    fun trafficState(bucketState: Int): TrafficState = when (bucketState) {
        2 -> TrafficState.FOREGROUND
        1 -> TrafficState.BACKGROUND
        else -> TrafficState.UNKNOWN
    }

    fun dataBreakdown(records: List<TrafficRecord>): DataBreakdown {
        fun split(mobile: Boolean): TrafficSplit? {
            val network = records.filter { it.mobile == mobile && it.bytes > 0 }
            if (network.isEmpty() || network.any { it.state == TrafficState.UNKNOWN }) return null
            val total = network.sumOf { it.bytes }
            val foreground = network.filter { it.state == TrafficState.FOREGROUND }.sumOf { it.bytes }
            val foregroundPercent = Math.round(foreground * 100.0 / total).toInt()
            return TrafficSplit(foregroundPercent, 100 - foregroundPercent)
        }
        return DataBreakdown(
            wifi = split(mobile = false),
            mobile = split(mobile = true),
            mobileRoamingBytes = records.filter { it.mobile && it.roaming && it.bytes > 0 }.sumOf { it.bytes },
        )
    }

    /**
     * Every installed app's sizes, summed once per UID: packages that share a UID
     * report the same figures. Per-UID figures (`queryStatsForUid`) hold only each
     * app's own files; the user-wide `queryStatsForUser` also folds the whole shared
     * storage into its data figure, which would count downloads and photos as apps.
     */
    fun appSizeTotal(uids: Iterable<Int>, sizesOf: (Int) -> AppSizes?): AppSizes {
        var code = 0L
        var data = 0L
        var cache = 0L
        for (uid in uids.toSet()) {
            val sizes = sizesOf(uid) ?: continue
            code += sizes.codeBytes
            data += sizes.dataBytes
            cache += sizes.cacheBytes
        }
        return AppSizes(code, data, cache)
    }

    /**
     * App code and data summed per app ([appSizeTotal]; data already includes the
     * cache and each app's external files), media from `queryExternalStatsForUser`;
     * what the used space holds beyond them is "other".
     */
    fun storageBreakdown(
        usedBytes: Long,
        appCodeBytes: Long,
        appDataBytes: Long,
        appCacheBytes: Long,
        imageBytes: Long,
        videoBytes: Long,
        audioBytes: Long,
    ): StorageBreakdown {
        val apps = appCodeBytes + appDataBytes
        return StorageBreakdown(
            appsBytes = apps,
            cacheBytes = appCacheBytes,
            imageBytes = imageBytes,
            videoBytes = videoBytes,
            audioBytes = audioBytes,
            otherBytes = (usedBytes - apps - imageBytes - videoBytes - audioBytes).coerceAtLeast(0L),
        )
    }

    /**
     * The network's MTU (`LinkProperties.getMtu()`, API 29), which is 0 unless the
     * network configured one; the interface's own MTU fills in then.
     */
    fun mtu(networkMtu: Int, interfaceMtu: Int?): Int? =
        networkMtu.takeIf { it > 0 } ?: interfaceMtu?.takeIf { it > 0 }

    private const val MINUTE_MILLIS = 60_000L
}
