package org.jarsi.devicewatch.data

/**
 * What Android's own record of app operations says: each time an app used, or was
 * refused, something Android counts as private (the camera, the microphone, the
 * location), who did it, when, for how long and from what state. Read from
 * `dumpsys appops --history --include-discrete 0`, the same record the system's
 * Privacy dashboard shows.
 */
data class AppOpsHistory(
    val accesses: List<AppOpAccess>,
    /**
     * Android 12 to 15 keep the record by the minute: a length is rounded up to
     * whole minutes, uses within one minute are written once, and refusals are not
     * written at all. The page says so and prints lengths as "about N min". Told
     * by the record itself (every time on a whole minute), not by the Android version:
     * Android 16 prints the same tree to the millisecond.
     */
    val minuteQuantized: Boolean = false,
    /**
     * Whether a refused ask is written into this record at all: the table (Android 17)
     * has reject counts, the tree of Android 12 to 16 holds only the uses that went
     * ahead (on an Android 16 emulator a refused camera left no line). The page says
     * so, or its empty "Refused attempts" would pass for a clean record.
     */
    val refusalsRecorded: Boolean = false,
)

/** One line of the record: one app, one operation, one moment. */
data class AppOpAccess(
    val uid: Int,
    val packageName: String,
    /** Android's name for the operation, `CAMERA` or `FINE_LOCATION` for example. */
    val op: String,
    /** The app's own label for the part of it that asked, when it gave one. */
    val attributionTag: String?,
    /** Wall-clock time of the access, in epoch milliseconds. */
    val timeMillis: Long,
    /** How many times the operation was allowed at [timeMillis]. */
    val accessCount: Int,
    /** How many times it was refused at [timeMillis]. */
    val rejectCount: Int,
    /** How long the operation ran, for ones with a start and an end (the camera, a recording). */
    val durationMillis: Long,
    val uidState: UidState,
    val flags: Set<OpFlag>,
) {
    /** A use the phone's owner would not have seen happen: the app was not on screen. */
    val background: Boolean get() = uidState.background
}

/** How Android saw the app at the moment, in the abbreviations `dumpsys appops` prints. */
enum class UidState(internal val code: String) {
    /** A system process that is always running. */
    PERSISTENT("pers"),
    /** On screen. */
    TOP("top"),
    /** Running a foreground service, with its notification showing. */
    FOREGROUND_SERVICE("fgsvc"),
    /** Bound to something in the foreground, or otherwise counted as foreground. */
    FOREGROUND("fg"),
    BACKGROUND("bg"),
    /** Kept in memory only; nothing of it was running on purpose. */
    CACHED("cch"),
    UNKNOWN("");

    val background: Boolean get() = this == BACKGROUND || this == CACHED

    companion object {
        fun of(code: String): UidState = entries.firstOrNull { it != UNKNOWN && it.code == code } ?: UNKNOWN
    }
}

/** Who did the operation on whose behalf, in the abbreviations `dumpsys appops` prints. */
enum class OpFlag(internal val code: String) {
    /** The app itself. */
    SELF("s"),
    /** A system component doing it for another app, recorded on the component. */
    TRUSTED_PROXY("tp"),
    /** An ordinary app doing it for another app, recorded on the first app. */
    UNTRUSTED_PROXY("up"),
    /** Done for this app by a system component, recorded on this app. */
    TRUSTED_PROXIED("tpd"),
    /** Done for this app by an ordinary app, recorded on this app. */
    UNTRUSTED_PROXIED("upd");

    companion object {
        /** `s`, `tp|s` and the like; letters that are not flags are dropped. */
        fun parse(text: String): Set<OpFlag> =
            text.split('|').mapNotNull { code -> entries.firstOrNull { it.code == code.trim() } }.toSet()

        /**
         * The flags the system's own Privacy dashboard asks the record for
         * (`OP_FLAGS_ALL_TRUSTED`): the app itself, a trusted system component acting
         * for another app, and the app that component acted for. A use made through a
         * trusted proxy is therefore on record twice, once on the proxy (`tp`) and once
         * on the app it was for (`tpd`), and the page lists both, as the record does.
         * An untrusted proxy's word, either way round, is not taken.
         */
        val TRUSTED: Set<OpFlag> = setOf(SELF, TRUSTED_PROXY, TRUSTED_PROXIED)
    }
}
