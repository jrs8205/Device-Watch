package org.jarsi.devicewatch.data

import kotlin.math.abs

/**
 * Reads what `grep -s -H '^' <files>` prints: every line of every file as
 * `path:line`. Only paths matching the patterns below count; anything else, a
 * file the command never asked for or a line without a path, is skipped. Since
 * grep starts every line with its file's path, no file's content can pass for
 * another file's.
 */
internal object KernelSysfsParser {

    private val CPU = Regex(
        "/sys/devices/system/cpu/cpufreq/(policy\\d{1,4})/(related_cpus|cpuinfo_min_freq|cpuinfo_max_freq|" +
            "scaling_min_freq|scaling_max_freq|scaling_cur_freq|scaling_governor|scaling_available_governors|" +
            "scaling_driver|scaling_available_frequencies|stats/time_in_state)"
    )
    private val THERMAL_ZONE = Regex("/sys/class/thermal/thermal_zone(\\d{1,4})/(type|temp)")
    private val COOLING = Regex("/sys/class/thermal/cooling_device(\\d{1,4})/(type|cur_state|max_state)")
    private val MALI = Regex("/sys/class/misc/mali0/device/(cur_freq|max_freq|min_freq|available_frequencies|power_policy)")
    private val KGSL = Regex("/sys/class/kgsl/kgsl-3d0/(gpuclk|max_gpuclk|gpu_available_frequencies)")
    private val ZRAM = Regex("/sys/block/zram0/(disksize|comp_algorithm|mm_stat)")
    private val IO_SCHEDULER = Regex("/sys/block/(sd[a-z]|mmcblk\\d)/queue/scheduler")
    private const val OS_RELEASE = "/proc/sys/kernel/osrelease"
    private const val SELINUX = "/sys/fs/selinux/enforce"
    private val WAKEUP = Regex(
        "/sys/class/wakeup/wakeup(\\d{1,5})/(name|active_count|event_count|wakeup_count|expire_count|active_time_ms|" +
            "total_time_ms|max_time_ms|last_change_ms)"
    )
    private val SUSPEND = Regex(
        "/sys/power/suspend_stats/(success|fail|failed_freeze|failed_prepare|failed_suspend|failed_suspend_late|" +
            "failed_suspend_noirq|failed_resume|failed_resume_early|failed_resume_noirq|last_failed_dev|last_failed_errno|" +
            "last_failed_step)"
    )

    /** Zone types that report something other than a temperature (Pixel: cycle count, charge level). */
    private val NOT_TEMPERATURES = setOf("battery_cycle", "soc")
    private const val MIN_CELSIUS = -40.0
    private const val MAX_CELSIUS = 150.0

    private val WHITESPACE = Regex("\\s+")

    fun parse(output: String): KernelSnapshot {
        val cpu = LinkedHashMap<String, MutableMap<String, MutableList<String>>>()
        val zones = HashMap<Int, MutableMap<String, String>>()
        val cooling = HashMap<Int, MutableMap<String, String>>()
        val mali = HashMap<String, String>()
        val kgsl = HashMap<String, String>()
        val zram = HashMap<String, String>()
        val io = LinkedHashMap<String, String>()
        var release: String? = null
        var selinux: SelinuxMode? = null

        for ((path, value) in lines(output)) {
            CPU.matchEntire(path)?.let { m ->
                cpu.getOrPut(m.groupValues[1]) { HashMap() }.getOrPut(m.groupValues[2]) { ArrayList() } += value
                continue
            }
            THERMAL_ZONE.matchEntire(path)?.let { m ->
                zones.getOrPut(m.groupValues[1].toInt()) { HashMap() }[m.groupValues[2]] = value.trim()
                continue
            }
            COOLING.matchEntire(path)?.let { m ->
                cooling.getOrPut(m.groupValues[1].toInt()) { HashMap() }[m.groupValues[2]] = value.trim()
                continue
            }
            MALI.matchEntire(path)?.let { mali[it.groupValues[1]] = value; continue }
            KGSL.matchEntire(path)?.let { kgsl[it.groupValues[1]] = value; continue }
            ZRAM.matchEntire(path)?.let { zram[it.groupValues[1]] = value; continue }
            IO_SCHEDULER.matchEntire(path)?.let { m ->
                activeChoice(value)?.let { io[m.groupValues[1]] = it }
                continue
            }
            when (path) {
                OS_RELEASE -> release = value.trim().ifEmpty { null }
                SELINUX -> selinux = when (value.trim()) {
                    "1" -> SelinuxMode.ENFORCING
                    "0" -> SelinuxMode.PERMISSIVE
                    else -> null
                }
            }
        }

        return KernelSnapshot(
            policies = cpu.mapNotNull { (name, files) -> policy(name, files) }
                .sortedWith(
                    compareBy<CpuPolicySnapshot>({ it.cpuinfoMaxKHz ?: Long.MAX_VALUE }, { it.relatedCpus.firstOrNull() ?: Int.MAX_VALUE })
                        .thenBy { it.id.name.removePrefix("policy").toInt() }
                ),
            gpu = gpu(mali, kgsl),
            coolingDevices = cooling.toSortedMap().mapNotNull { (index, files) ->
                val type = files["type"]?.ifEmpty { null } ?: return@mapNotNull null
                val cur = files["cur_state"]?.toIntOrNull() ?: return@mapNotNull null
                val max = files["max_state"]?.toIntOrNull() ?: return@mapNotNull null
                CoolingDevice(index, type, cur, max)
            },
            thermalZones = zones.toSortedMap().mapNotNull { (index, files) ->
                val type = files["type"]?.ifEmpty { null } ?: return@mapNotNull null
                val raw = files["temp"]?.toLongOrNull() ?: return@mapNotNull null
                celsius(type, raw)?.let { ThermalZone(index, type, it) }
            },
            zram = if (zram.isEmpty()) null else zram(zram),
            kernelRelease = release,
            selinux = selinux,
            ioSchedulers = io.toSortedMap(),
        )
    }

    /** The `scaling_max_freq` values in [output], by policy. */
    fun maxFreqs(output: String): Map<CpuPolicyId, Long> {
        val result = LinkedHashMap<CpuPolicyId, Long>()
        for ((path, value) in lines(output)) {
            val m = CPU.matchEntire(path) ?: continue
            if (m.groupValues[2] != "scaling_max_freq") continue
            val id = CpuPolicyId.of(m.groupValues[1]) ?: continue
            value.trim().toLongOrNull()?.let { result[id] = it }
        }
        return result
    }

    /**
     * What [KernelCommands.WAKE_READ] prints. A source counts only with its name;
     * a counter that is missing or garbled reads as zero, since the kernel keeps
     * every one of them and a zero is what an idle source shows anyway.
     */
    fun parseWake(output: String): WakeSnapshot {
        val sources = HashMap<Int, MutableMap<String, String>>()
        val suspend = HashMap<String, String>()
        for ((path, value) in lines(output)) {
            WAKEUP.matchEntire(path)?.let { m ->
                sources.getOrPut(m.groupValues[1].toInt()) { HashMap() }[m.groupValues[2]] = value.trim()
                continue
            }
            SUSPEND.matchEntire(path)?.let { m -> suspend[m.groupValues[1]] = value.trim() }
        }
        fun Map<String, String>.count(file: String): Long = this[file]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        return WakeSnapshot(
            sources = sources.toSortedMap().mapNotNull { (index, files) ->
                val name = files["name"]?.ifEmpty { null } ?: return@mapNotNull null
                WakeupSource(
                    index = index,
                    name = name,
                    activeCount = files.count("active_count"),
                    eventCount = files.count("event_count"),
                    wakeupCount = files.count("wakeup_count"),
                    expireCount = files.count("expire_count"),
                    activeTimeMs = files.count("active_time_ms"),
                    totalTimeMs = files.count("total_time_ms"),
                    maxTimeMs = files.count("max_time_ms"),
                    lastChangeMs = files.count("last_change_ms"),
                )
            },
            suspend = if (suspend.isEmpty()) {
                null
            } else {
                SuspendStats(
                    success = suspend.count("success"),
                    fail = suspend.count("fail"),
                    failedFreeze = suspend.count("failed_freeze"),
                    failedPrepare = suspend.count("failed_prepare"),
                    failedSuspend = suspend.count("failed_suspend"),
                    failedSuspendLate = suspend.count("failed_suspend_late"),
                    failedSuspendNoirq = suspend.count("failed_suspend_noirq"),
                    failedResume = suspend.count("failed_resume"),
                    failedResumeEarly = suspend.count("failed_resume_early"),
                    failedResumeNoirq = suspend.count("failed_resume_noirq"),
                    lastFailedDev = suspend["last_failed_dev"]?.ifEmpty { null },
                    lastFailedErrno = suspend["last_failed_errno"]?.toIntOrNull(),
                    lastFailedStep = suspend["last_failed_step"]?.ifEmpty { null },
                )
            },
        )
    }

    /** The bracketed choice in a sysfs menu such as `coarse_demand [adaptive] always_on`, or the only word. */
    fun activeChoice(text: String): String? {
        val words = text.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        words.firstOrNull { it.startsWith("[") && it.endsWith("]") }?.let { return it.substring(1, it.length - 1) }
        return words.singleOrNull()
    }

    private fun lines(output: String): Sequence<Pair<String, String>> =
        output.lineSequence().mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) null else line.substring(0, colon) to line.substring(colon + 1)
        }

    private fun policy(name: String, files: Map<String, List<String>>): CpuPolicySnapshot? {
        val id = CpuPolicyId.of(name) ?: return null
        fun one(file: String) = files[file]?.firstOrNull()?.trim()
        fun khz(file: String) = one(file)?.toLongOrNull()?.takeIf { it > 0L }
        val min = khz("cpuinfo_min_freq")
        val max = khz("cpuinfo_max_freq")
        val listed = one("scaling_available_frequencies")?.let(::numbers).orEmpty()
        val counted = files["stats/time_in_state"].orEmpty().mapNotNull { it.trim().substringBefore(' ').toLongOrNull() }
        val (raw, source) = when {
            listed.isNotEmpty() -> listed to StepsSource.AVAILABLE
            counted.isNotEmpty() -> counted to StepsSource.TIME_IN_STATE
            else -> emptyList<Long>() to StepsSource.NONE
        }
        val steps = raw.filter { it > 0L && (min == null || it >= min) && (max == null || it <= max) }.distinct().sorted()
        return CpuPolicySnapshot(
            id = id,
            relatedCpus = one("related_cpus")?.split(WHITESPACE)?.mapNotNull(String::toIntOrNull).orEmpty(),
            cpuinfoMinKHz = min,
            cpuinfoMaxKHz = max,
            scalingMinKHz = khz("scaling_min_freq"),
            scalingMaxKHz = khz("scaling_max_freq"),
            curKHz = khz("scaling_cur_freq"),
            governor = one("scaling_governor")?.ifEmpty { null },
            availableGovernors = one("scaling_available_governors")?.split(WHITESPACE)?.filter(String::isNotEmpty).orEmpty(),
            driver = one("scaling_driver")?.ifEmpty { null },
            steps = steps,
            stepsSource = if (steps.isEmpty()) StepsSource.NONE else source,
        )
    }

    private fun gpu(mali: Map<String, String>, kgsl: Map<String, String>): GpuSnapshot? {
        if (mali.isNotEmpty()) {
            val steps = mali["available_frequencies"]?.let(::numbers).orEmpty().filter { it > 0L }.distinct().sorted()
            return GpuSnapshot(
                curKHz = mali["cur_freq"]?.trim()?.toLongOrNull(),
                maxKHz = mali["max_freq"]?.trim()?.toLongOrNull(),
                minKHz = mali["min_freq"]?.trim()?.toLongOrNull(),
                steps = steps,
                powerPolicy = mali["power_policy"]?.let(::activeChoice),
            )
        }
        if (kgsl.isNotEmpty()) {
            // Adreno's driver counts in hertz.
            val steps = kgsl["gpu_available_frequencies"]?.let(::numbers).orEmpty().filter { it > 0L }.map { it / 1000 }.distinct().sorted()
            return GpuSnapshot(
                curKHz = kgsl["gpuclk"]?.trim()?.toLongOrNull()?.div(1000),
                maxKHz = kgsl["max_gpuclk"]?.trim()?.toLongOrNull()?.div(1000),
                minKHz = steps.firstOrNull(),
                steps = steps,
                powerPolicy = null,
            )
        }
        return null
    }

    private fun zram(files: Map<String, String>): ZramSnapshot {
        val stat = files["mm_stat"]?.trim()?.split(WHITESPACE)?.map(String::toLongOrNull).orEmpty()
        return ZramSnapshot(
            disksizeBytes = files["disksize"]?.trim()?.toLongOrNull(),
            algorithm = files["comp_algorithm"]?.let(::activeChoice),
            origBytes = stat.getOrNull(0),
            comprBytes = stat.getOrNull(1),
            memUsedBytes = stat.getOrNull(2),
        )
    }

    /** Millidegrees as the ABI says, though some old sensors report whole degrees. */
    private fun celsius(type: String, raw: Long): Double? {
        if (type in NOT_TEMPERATURES || raw == 0L) return null
        val celsius = if (abs(raw) >= 1000L) raw / 1000.0 else raw.toDouble()
        return celsius.takeIf { it in MIN_CELSIUS..MAX_CELSIUS }
    }

    private fun numbers(text: String): List<Long> = text.trim().split(WHITESPACE).mapNotNull(String::toLongOrNull)
}
