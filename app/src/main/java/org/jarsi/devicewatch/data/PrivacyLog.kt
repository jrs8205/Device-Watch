package org.jarsi.devicewatch.data

/** The private things Android keeps a record of, in the order the page shows them. */
enum class PrivacySensor { CAMERA, MICROPHONE, LOCATION, OTHER }

/** Which operations count as which sensor, and which of them count as a use. */
object PrivacyOps {
    val CAMERA: Set<String> = setOf("CAMERA", "PHONE_CALL_CAMERA")
    val MICROPHONE: Set<String> = setOf(
        "RECORD_AUDIO", "PHONE_CALL_MICROPHONE", "RECEIVE_AMBIENT_TRIGGER_AUDIO", "RECEIVE_SANDBOX_TRIGGER_AUDIO",
        "RECEIVE_EXPLICIT_USER_INTERACTION_AUDIO", "RECEIVE_SOUNDTRIGGER_AUDIO",
    )

    /** The location permission's own operations: what the system's dashboard counts as a location use. */
    val LOCATION_USES: Set<String> = setOf("COARSE_LOCATION", "FINE_LOCATION")

    /**
     * Location operations that come with a use rather than being one: the system notes
     * `MONITOR_LOCATION`, `MONITOR_HIGH_POWER_LOCATION` and `GPS` alongside a
     * `FINE_LOCATION` the moment an app starts listening. Counting them would make
     * one geotag four uses, so they are shown with the events but not counted.
     */
    val LOCATION_MONITORING: Set<String> = setOf("GPS", "MONITOR_LOCATION", "MONITOR_HIGH_POWER_LOCATION")
    val LOCATION: Set<String> = LOCATION_USES + LOCATION_MONITORING

    fun sensorOf(op: String): PrivacySensor = when (op) {
        in CAMERA -> PrivacySensor.CAMERA
        in MICROPHONE -> PrivacySensor.MICROPHONE
        in LOCATION -> PrivacySensor.LOCATION
        else -> PrivacySensor.OTHER
    }

    /** Whether an allowed line of [op] counts as one use of its sensor. */
    fun counts(op: String): Boolean = op !in LOCATION_MONITORING
}

/**
 * Android's record of private things used, app by app, as the page shows it. Every
 * sensor is listed, empty or not, in [PrivacySensor]'s order. [oldestMillis] and
 * [newestMillis] span the whole record read, so the page can say how far back it goes.
 */
data class PrivacyLog(
    val oldestMillis: Long?,
    val newestMillis: Long?,
    val sensors: List<SensorUse>,
    /** See [AppOpsHistory.minuteQuantized]. */
    val minuteQuantized: Boolean = false,
) {
    fun sensor(sensor: PrivacySensor): SensorUse = sensors.first { it.sensor == sensor }

    val empty: Boolean get() = sensors.all { it.empty }
}

/** One sensor: the apps that used it, latest first, and the ones only refused it. */
data class SensorUse(
    val sensor: PrivacySensor,
    val apps: List<AppSensorUse>,
    val denied: List<AppSensorUse>,
) {
    val empty: Boolean get() = apps.isEmpty() && denied.isEmpty()
}

/** One app's dealings with one sensor over the whole record. */
data class AppSensorUse(
    val uid: Int,
    val packageName: String,
    val label: String,
    /** Times a use was allowed, counting the operations that are uses ([PrivacyOps.counts]). */
    val accesses: Int,
    /** Of [accesses], the ones made while the app was not on screen. */
    val backgroundAccesses: Int,
    /** Times the sensor was refused, any operation. */
    val rejected: Int,
    /** How long the sensor was in use in all, where the operation has a length. */
    val durationMillis: Long,
    /** The latest allowed line of any operation, or null when every line is a refusal. */
    val lastMillis: Long?,
    val lastRejectedMillis: Long?,
    /** Android's names for what was done, `FINE_LOCATION` and the like. */
    val ops: Set<String>,
    /** Every moment on record, latest first. */
    val events: List<AccessEvent>,
) {
    /** The app only listened for the sensor (location monitoring) and never made a counted use. */
    val monitoringOnly: Boolean get() = accesses == 0 && lastMillis != null
}

/** One moment of one app's use of a sensor; a count line and a length line at the same moment are one event. */
data class AccessEvent(
    val timeMillis: Long,
    val op: String,
    val tag: String?,
    val count: Int,
    val durationMillis: Long,
    val rejected: Int,
    val background: Boolean,
) {
    val allowed: Boolean get() = count > 0 || durationMillis > 0L
}

/**
 * Turns the record's lines into the page's lists. Only the lines Android's own
 * Privacy dashboard would count are taken ([OpFlag.TRUSTED]); a line whose flags
 * could not be read is kept, since dropping it would hide a use on a hunch.
 */
object PrivacyLogBuilder {

    fun build(history: AppOpsHistory, label: (uid: Int, packageName: String) -> String): PrivacyLog {
        val counted = history.accesses.filter { it.flags.isEmpty() || it.flags.any { flag -> flag in OpFlag.TRUSTED } }
        val bySensor = counted.groupBy { PrivacyOps.sensorOf(it.op) }
        return PrivacyLog(
            oldestMillis = counted.minOfOrNull { it.timeMillis },
            newestMillis = counted.maxOfOrNull { it.timeMillis },
            sensors = PrivacySensor.entries.map { sensor -> sensorUse(sensor, bySensor[sensor].orEmpty(), label) },
            minuteQuantized = history.minuteQuantized,
        )
    }

    private fun sensorUse(sensor: PrivacySensor, lines: List<AppOpAccess>, label: (Int, String) -> String): SensorUse {
        val apps = lines.groupBy { it.uid to it.packageName }.map { (key, appLines) -> app(key.first, key.second, appLines, label) }
        val (used, denied) = apps.partition { it.lastMillis != null }
        return SensorUse(
            sensor = sensor,
            apps = used.sortedByDescending { it.lastMillis },
            denied = denied.filter { it.rejected > 0 }.sortedByDescending { it.lastRejectedMillis },
        )
    }

    private data class EventKey(val timeMillis: Long, val op: String, val tag: String?, val state: UidState)

    private fun app(uid: Int, packageName: String, lines: List<AppOpAccess>, label: (Int, String) -> String): AppSensorUse {
        val events = lines
            .groupBy { EventKey(it.timeMillis, it.op, it.attributionTag, it.uidState) }
            .map { (key, same) ->
                AccessEvent(
                    timeMillis = key.timeMillis,
                    op = key.op,
                    tag = key.tag,
                    count = same.sumOf { it.accessCount },
                    durationMillis = same.sumOf { it.durationMillis },
                    rejected = same.sumOf { it.rejectCount },
                    background = key.state.background,
                )
            }
            .sortedByDescending { it.timeMillis }
        val uses = events.filter { PrivacyOps.counts(it.op) }
        return AppSensorUse(
            uid = uid,
            packageName = packageName,
            label = label(uid, packageName),
            accesses = uses.sumOf { it.count },
            backgroundAccesses = uses.filter { it.background }.sumOf { it.count },
            rejected = events.sumOf { it.rejected },
            durationMillis = events.sumOf { it.durationMillis },
            lastMillis = events.filter { it.allowed }.maxOfOrNull { it.timeMillis },
            lastRejectedMillis = events.filter { it.rejected > 0 }.maxOfOrNull { it.timeMillis },
            ops = lines.map { it.op }.toSet(),
            events = events,
        )
    }
}
