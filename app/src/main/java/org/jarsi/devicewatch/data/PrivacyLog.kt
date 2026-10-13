package org.jarsi.devicewatch.data

/** The private things Android keeps a record of, in the order the page shows them. */
enum class PrivacySensor { CAMERA, MICROPHONE, LOCATION, OTHER }

/** Which operations count as which sensor. */
object PrivacyOps {
    val CAMERA: Set<String> = setOf("CAMERA", "PHONE_CALL_CAMERA")
    val MICROPHONE: Set<String> = setOf(
        "RECORD_AUDIO", "PHONE_CALL_MICROPHONE", "RECEIVE_AMBIENT_TRIGGER_AUDIO", "RECEIVE_SANDBOX_TRIGGER_AUDIO",
        "RECEIVE_EXPLICIT_USER_INTERACTION_AUDIO", "RECEIVE_SOUNDTRIGGER_AUDIO",
    )
    val LOCATION: Set<String> = setOf("COARSE_LOCATION", "FINE_LOCATION", "GPS", "MONITOR_LOCATION", "MONITOR_HIGH_POWER_LOCATION")

    fun sensorOf(op: String): PrivacySensor = when (op) {
        in CAMERA -> PrivacySensor.CAMERA
        in MICROPHONE -> PrivacySensor.MICROPHONE
        in LOCATION -> PrivacySensor.LOCATION
        else -> PrivacySensor.OTHER
    }
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
    /** Times the use was allowed. */
    val accesses: Int,
    /** Of [accesses], the ones made while the app was not on screen. */
    val backgroundAccesses: Int,
    /** Times the use was refused. */
    val rejected: Int,
    /** How long the sensor was in use in all, where the operation has a length. */
    val durationMillis: Long,
    /** The latest allowed use, or null when every line is a refusal. */
    val lastMillis: Long?,
    val lastRejectedMillis: Long?,
    /** Android's names for what was done, `FINE_LOCATION` and the like. */
    val ops: Set<String>,
    /** Every moment on record, latest first. */
    val events: List<AccessEvent>,
)

/** One moment of one app's use of a sensor; a count line and a length line at the same moment are one event. */
data class AccessEvent(
    val timeMillis: Long,
    val op: String,
    val tag: String?,
    val count: Int,
    val durationMillis: Long,
    val rejected: Int,
    val background: Boolean,
)

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
        )
    }

    private fun sensorUse(sensor: PrivacySensor, lines: List<AppOpAccess>, label: (Int, String) -> String): SensorUse {
        val apps = lines.groupBy { it.uid to it.packageName }.map { (key, appLines) -> app(key.first, key.second, appLines, label) }
        val (used, denied) = apps.partition { it.accesses > 0 || it.durationMillis > 0 }
        return SensorUse(
            sensor = sensor,
            apps = used.sortedByDescending { it.lastMillis },
            denied = denied.sortedByDescending { it.lastRejectedMillis },
        )
    }

    private fun app(uid: Int, packageName: String, lines: List<AppOpAccess>, label: (Int, String) -> String): AppSensorUse {
        val events = lines
            .groupBy { Triple(it.timeMillis, it.op, it.attributionTag) }
            .map { (key, same) ->
                AccessEvent(
                    timeMillis = key.first,
                    op = key.second,
                    tag = key.third,
                    count = same.sumOf { it.accessCount },
                    durationMillis = same.sumOf { it.durationMillis },
                    rejected = same.sumOf { it.rejectCount },
                    background = same.any { it.background },
                )
            }
            .sortedByDescending { it.timeMillis }
        val allowed = events.filter { it.count > 0 || it.durationMillis > 0 }
        return AppSensorUse(
            uid = uid,
            packageName = packageName,
            label = label(uid, packageName),
            accesses = events.sumOf { it.count },
            backgroundAccesses = events.filter { it.background }.sumOf { it.count },
            rejected = events.sumOf { it.rejected },
            durationMillis = events.sumOf { it.durationMillis },
            lastMillis = allowed.maxOfOrNull { it.timeMillis },
            lastRejectedMillis = events.filter { it.rejected > 0 }.maxOfOrNull { it.timeMillis },
            ops = lines.map { it.op }.toSet(),
            events = events,
        )
    }
}
