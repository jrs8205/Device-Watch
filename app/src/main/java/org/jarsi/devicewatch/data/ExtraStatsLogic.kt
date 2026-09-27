package org.jarsi.devicewatch.data

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/** Where the charge comes from, as `BatteryManager.EXTRA_PLUGGED` reports it. */
enum class ChargeSource { NONE, AC, USB, WIRELESS, DOCK }

/** A biometric sensor the device has, per its system features. */
enum class BiometricSensor { FINGERPRINT, FACE, IRIS }

/** The NFC controller: missing, switched off or on. */
enum class NfcState { NONE, OFF, ON }

/** One `MediaCodecInfo`, reduced to what the decoder list needs. */
data class CodecRecord(val types: List<String>, val hardware: Boolean, val encoder: Boolean)

/** Swap space (on phones, compressed RAM: ZRAM) from /proc/meminfo, in kilobytes. */
data class SwapInfo(val totalKb: Long, val freeKb: Long)

/** Whether the active network reaches the internet, per its `NetworkCapabilities`. */
enum class InternetState { VALIDATED, CAPTIVE_PORTAL, NOT_VALIDATED }

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

    /**
     * `WifiInfo.getCurrentSecurityType()` (API 31) by its Wi-Fi Alliance name. Open
     * networks, unknown and newer types give null: "open" is the caller's to say
     * in the user's language, and an unnamed type is better shown as unknown.
     */
    fun wifiSecurityName(type: Int): String? = when (type) {
        1 -> "WEP"
        2 -> "WPA2-Personal"
        3 -> "WPA2-Enterprise"
        4 -> "WPA3-Personal"
        5 -> "WPA3-Enterprise 192-bit"
        6 -> "Enhanced Open"
        7 -> "WAPI-PSK"
        8 -> "WAPI-CERT"
        9 -> "WPA3-Enterprise"
        10 -> "OSEN"
        11 -> "Passpoint"
        12 -> "Passpoint R3"
        13 -> "Easy Connect (DPP)"
        else -> null
    }

    /**
     * A captive portal (a sign-in page before the internet) wins over the
     * validation flag; otherwise validated or not.
     */
    fun internetState(validated: Boolean, captivePortal: Boolean): InternetState = when {
        captivePortal -> InternetState.CAPTIVE_PORTAL
        validated -> InternetState.VALIDATED
        else -> InternetState.NOT_VALIDATED
    }

    /**
     * The network's own bandwidth estimate (`linkDown/UpstreamBandwidthKbps`) in
     * megabits, one decimal under 10 Mb/s so a slow link does not read as zero.
     */
    fun bandwidthText(downKbps: Int, upKbps: Int, locale: Locale = Locale.getDefault()): String? {
        if (downKbps <= 0 && upKbps <= 0) return null
        fun mbps(kbps: Int): String {
            val value = kbps.coerceAtLeast(0) / 1000.0
            return if (value < 10.0) String.format(locale, "%.1f", value) else String.format(locale, "%.0f", value)
        }
        return "\u2193 ${mbps(downKbps)} Mb/s \u00b7 \u2191 ${mbps(upKbps)} Mb/s"
    }

    /** `CellInfo.UNAVAILABLE` (Integer.MAX_VALUE) is no value. */
    fun cellValue(value: Int): Int? = value.takeIf { it != Int.MAX_VALUE }

    /** "RSRP -95 dBm · RSRQ -11 dB · SINR 12 dB", each part only when reported. */
    fun signalDetails(rsrp: Int?, rsrq: Int?, sinr: Int?): String? = listOfNotNull(
        rsrp?.let { "RSRP $it dBm" },
        rsrq?.let { "RSRQ $it dB" },
        sinr?.let { "SINR $it dB" },
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")

    /** Bands as 3GPP writes them: "n78" for 5G NR, "B20" for LTE. */
    fun bandsText(nr: Boolean, bands: IntArray): String? =
        bands.takeIf { it.isNotEmpty() }?.joinToString(", ") { if (nr) "n$it" else "B$it" }

    /**
     * The Settings brightness slider's position for a `Settings.System.SCREEN_BRIGHTNESS`
     * value. The setting is linear over [min]..[max]; Settings draws the slider on
     * the perceptual HLG curve (BrightnessUtils.convertLinearToGamma), so the raw
     * share would read far lower than what the user set.
     */
    fun brightnessSliderPercent(raw: Int, min: Int = 1, max: Int = 255): Int {
        val normalized = (raw.coerceIn(min, max) - min).toDouble() / (max - min) * 12.0
        val gamma = if (normalized <= 1.0) {
            Math.sqrt(normalized) * HLG_R
        } else {
            HLG_A * Math.log(normalized - HLG_B) + HLG_C
        }
        return Math.round(gamma * 100).toInt().coerceIn(0, 100)
    }

    private const val HLG_R = 0.5
    private const val HLG_A = 0.17883277
    private const val HLG_B = 0.28466892
    private const val HLG_C = 0.55991073

    /** The display-size setting as a share of the device's default density. */
    fun displaySizePercent(densityDpi: Int, defaultDpi: Int): Int? {
        if (densityDpi <= 0 || defaultDpi <= 0) return null
        return Math.round(densityDpi * 100.0 / defaultDpi).toInt()
    }

    /** SwapTotal and SwapFree from /proc/meminfo; null without swap or without the lines. */
    fun swapFromMeminfo(meminfo: String?): SwapInfo? {
        if (meminfo == null) return null
        fun field(name: String): Long? = Regex("""(?m)^$name:\s+(\d+)\s*kB""")
            .find(meminfo)?.groupValues?.get(1)?.toLongOrNull()
        val total = field("SwapTotal") ?: return null
        val free = field("SwapFree") ?: return null
        return SwapInfo(total, free).takeIf { total > 0L }
    }

    /**
     * Cores grouped by their frequency range (`cpuinfo_min_freq`, `cpuinfo_max_freq`
     * in kHz), slowest cluster first: "4 × 0.3–1.8 GHz + 3 × 0.4–2.4 GHz".
     */
    fun cpuClustersText(cores: List<Pair<Long, Long>>, locale: Locale = Locale.getDefault()): String? {
        // No real core tops out under 100 MHz; emulators report a few kHz.
        val plausible = cores.filter { it.second >= MIN_PLAUSIBLE_MAX_KHZ }
        if (plausible.isEmpty()) return null
        return plausible.groupingBy { it }.eachCount().entries
            .sortedWith(compareBy({ it.key.second }, { it.key.first }))
            .joinToString(" + ") { (range, count) ->
                val min = String.format(locale, "%.1f", range.first / 1_000_000.0)
                val max = String.format(locale, "%.1f", range.second / 1_000_000.0)
                "$count \u00d7 $min\u2013$max GHz"
            }
    }

    private const val MIN_PLAUSIBLE_MAX_KHZ = 100_000L

    /**
     * The GNSS chip as "model (year)". The platform reports 0 for hardware from
     * before 2016, so only 2016 onwards is a year; a blank model is no model.
     */
    fun gnssHardwareText(model: String?, year: Int): String? {
        val name = model?.trim()?.takeIf { it.isNotEmpty() }
        val knownYear = year.takeIf { it >= 2016 }
        return when {
            name != null && knownYear != null -> "$name ($knownYear)"
            name != null -> name
            knownYear != null -> knownYear.toString()
            else -> null
        }
    }

    /**
     * `FEATURE_VULKAN_HARDWARE_VERSION` as "major.minor"; the feature packs
     * major << 22 | minor << 12 | patch. Null when the device has no Vulkan.
     */
    fun vulkanVersionText(encoded: Int): String? {
        if (encoded <= 0) return null
        val major = encoded ushr 22
        val minor = (encoded ushr 12) and 0x3ff
        return "$major.$minor"
    }

    /**
     * The video formats that need a hardware decoder to play smoothly — AV1, HEVC,
     * VP9, Dolby Vision — that one of the device's hardware decoders handles, in
     * that order. Software decoders and encoders say nothing about playback.
     */
    fun hardwareDecoders(codecs: List<CodecRecord>): List<String> {
        val decodable = codecs.filter { it.hardware && !it.encoder }
            .flatMap { codec -> codec.types.map { it.lowercase(Locale.ROOT) } }
            .toSet()
        return NOTABLE_VIDEO_FORMATS.filter { (mime, _) -> mime in decodable }.map { it.second }
    }

    private val NOTABLE_VIDEO_FORMATS = listOf(
        "video/av01" to "AV1",
        "video/hevc" to "HEVC",
        "video/x-vnd.on2.vp9" to "VP9",
        "video/dolby-vision" to "Dolby Vision",
    )

    /**
     * The biometric sensors the device declares (FEATURE_FINGERPRINT, and from
     * Android 10 FEATURE_FACE and FEATURE_IRIS). Whether one is enrolled would take
     * the USE_BIOMETRIC permission, which a read-only monitor does not ask for.
     */
    fun biometricSensors(fingerprint: Boolean, face: Boolean, iris: Boolean): List<BiometricSensor> = buildList {
        if (fingerprint) add(BiometricSensor.FINGERPRINT)
        if (face) add(BiometricSensor.FACE)
        if (iris) add(BiometricSensor.IRIS)
    }

    fun nfcState(present: Boolean, enabled: Boolean): NfcState = when {
        !present -> NfcState.NONE
        enabled -> NfcState.ON
        else -> NfcState.OFF
    }

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
